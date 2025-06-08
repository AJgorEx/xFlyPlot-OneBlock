package com.AJgorEx.xFlyPlot;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class OneBlockManager {

    private final Plugin plugin;
    private final Map<UUID, Location> playerGenerators = new HashMap<>();
    // total amount of blocks each player has generated. Used for phase handling
    private final Map<UUID, Integer> playerProgress = new HashMap<>();
    private final Map<UUID, BossBar> playerBossbars = new HashMap<>();
    private final Map<UUID, Integer> islandPoints = new HashMap<>();
    private final Map<UUID, Integer> islandUpgrades = new HashMap<>();
    private final Map<UUID, Long> sessionStart = new HashMap<>();
    private final Map<UUID, Long> totalSessionTime = new HashMap<>();
    private final List<Phase> phases = new ArrayList<>();
    private final File phasesFile;
    private final String worldPrefix;
    private final Sound soundStart;
    private final Sound soundTeleport;
    private final Sound soundPhaseComplete;
    private final Sound soundDelete;
    private final Sound soundBonus;
    private boolean bonusDropsEnabled = true;
    private final int levelBlocks;
    private final double borderSize;
    private final int upgradeCost;
    private final double upgradeIncrement;
    private final MessageManager messages;
    private final Set<UUID> pausedPlayers = new HashSet<>();
    private final Map<Material, Integer> blockPointValues = new HashMap<>();
    private int defaultBlockPoints = 1;

    public OneBlockManager(Plugin plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.phasesFile = new File(plugin.getDataFolder(), "phases.yml");

        FileConfiguration cfg = plugin.getConfig();
        this.worldPrefix = cfg.getString("world-prefix", "xflyplot-");
        this.soundStart = Sound.valueOf(cfg.getString("sounds.start", "ENTITY_PLAYER_LEVELUP"));
        this.soundTeleport = Sound.valueOf(cfg.getString("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT"));
        this.soundPhaseComplete = Sound.valueOf(cfg.getString("sounds.phase-complete", "UI_TOAST_CHALLENGE_COMPLETE"));
        this.soundDelete = Sound.valueOf(cfg.getString("sounds.island-delete", "ENTITY_WITHER_DEATH"));
        this.soundBonus = Sound.valueOf(cfg.getString("sounds.bonus", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        this.levelBlocks = cfg.getInt("level-blocks", 100);
        this.borderSize = cfg.getDouble("island-border-size", 64);
        this.upgradeCost = cfg.getInt("upgrade.cost", 500);
        this.upgradeIncrement = cfg.getDouble("upgrade.increment", 16);

        if (cfg.isConfigurationSection("block-points")) {
            var section = cfg.getConfigurationSection("block-points");
            defaultBlockPoints = section.getInt("default", 1);
            for (String key : section.getKeys(false)) {
                if ("default".equalsIgnoreCase(key)) continue;
                try {
                    blockPointValues.put(Material.valueOf(key.toUpperCase()), section.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown material in block-points: " + key);
                }
            }
        }

        if (!phasesFile.exists()) {
            plugin.saveResource("phases.yml", false);
        }

        loadPhases();
        loadSessions();
        loadUpgrades();
        saveServerInfo();
    }

    public void startIsland(Player player) {
        UUID uuid = player.getUniqueId();

        if (!playerGenerators.containsKey(uuid)) {
            createGenerator(uuid);
            teleportHome(player);
            messages.send(player, "island_created");
            player.playSound(player.getLocation(), soundStart, 1f, 1f);
            createBossbar(player);
        } else {
            messages.send(player, "already_has_island");
        }
    }

    public void teleportHome(Player player) {
        UUID uuid = player.getUniqueId();
        Location home = playerGenerators.get(uuid);

        if (home != null) {
            player.teleport(home.clone().add(0.5, 1, 0.5));
            // ensure border is visible when player returns
            WorldBorder border = home.getWorld().getWorldBorder();
            border.setCenter(0.5, 0.5);
            border.setSize(borderSize);

            startSession(uuid);

            messages.send(player, "teleport_home");
            player.playSound(player.getLocation(), soundTeleport, 1f, 1f);
        } else {
            messages.send(player, "no_island");
        }
    }

    public void createGenerator(UUID uuid) {
        String worldName = worldPrefix + uuid.toString().substring(0, 8);

        World world = Bukkit.createWorld(new WorldCreator(worldName)
                .generator(new VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT));

        // set up a visible world border for the island
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(borderSize);

        Location genLoc = new Location(world, 0, 64, 0);

        world.getBlockAt(genLoc).setType(Material.GRASS_BLOCK);
        world.getBlockAt(genLoc.clone().subtract(0, 1, 0)).setType(Material.BEDROCK);

        playerGenerators.put(uuid, genLoc);
        playerProgress.put(uuid, 0);
        islandPoints.put(uuid, 0);
        saveStats();
    }

    public void handleBlockBreak(Player player) {
        UUID uuid = player.getUniqueId();

        if (!playerGenerators.containsKey(uuid)) return;
        if (pausedPlayers.contains(uuid)) {
            messages.send(player, "island_paused");
            return;
        }

        int total = playerProgress.getOrDefault(uuid, 0);
        Phase currentPhase = getCurrentPhase(total);
        Material nextBlock = getRandomBlock(currentPhase);

        if (!nextBlock.isBlock()) {
            player.sendMessage(ChatColor.RED + "Błąd: " + nextBlock + " nie jest blokiem.");
            return;
        }

        Location genLoc = playerGenerators.get(uuid);
        genLoc.getBlock().setType(nextBlock);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);

        int newTotal = total + 1;
        playerProgress.put(uuid, newTotal);

        int phaseIndex = getPhaseIndex(total);
        int blocksBefore = getBlocksBeforePhase(phaseIndex);
        int progressInPhase = newTotal - blocksBefore;

        updateBossbar(player, progressInPhase, currentPhase.getBlockCount(), currentPhase.getName());

        if (progressInPhase >= currentPhase.getBlockCount()) {
            messages.send(player, "next_phase");
            spawnFirework(genLoc.getWorld(), genLoc.clone().add(0.5, 1, 0.5));
            player.playSound(player.getLocation(), soundPhaseComplete, 1f, 1f);
        }

        maybeDropBonus(player, genLoc);
    }

    private Material getRandomBlock(Phase phase) {
        List<Material> blocks = phase.getMaterials();
        List<Material> valid = blocks.stream().filter(Material::isBlock).toList();
        return valid.isEmpty() ? Material.DIRT : valid.get(new Random().nextInt(valid.size()));
    }

    private void loadPhases() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(phasesFile);

        for (String phaseName : config.getKeys(false)) {
            int blockCount = config.getInt(phaseName + ".blocks");
            List<String> blockList = config.getStringList(phaseName + ".blocksList");
            List<Material> materials = new ArrayList<>();

            for (String mat : blockList) {
                try {
                    materials.add(Material.valueOf(mat.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Nieprawidłowy blok w fazie '" + phaseName + "': " + mat);
                }
            }

            phases.add(new Phase(phaseName, blockCount, materials));
        }

        if (phases.isEmpty()) {
            plugin.getLogger().severe("Brak zdefiniowanych faz w phases.yml!");
        }
    }

    public void reloadPhases() {
        phases.clear();
        loadPhases();
    }

    public Phase getCurrentPhase(int blocksBroken) {
        int sum = 0;
        for (Phase phase : phases) {
            sum += phase.getBlockCount();
            if (blocksBroken < sum) return phase;
        }
        return phases.get(phases.size() - 1);
    }

    private int getPhaseIndex(int blocksBroken) {
        int sum = 0;
        for (int i = 0; i < phases.size(); i++) {
            sum += phases.get(i).getBlockCount();
            if (blocksBroken < sum) return i;
        }
        return phases.size() - 1;
    }

    private int getBlocksBeforePhase(int phaseIndex) {
        int sum = 0;
        for (int i = 0; i < phaseIndex; i++) {
            sum += phases.get(i).getBlockCount();
        }
        return sum;
    }

    private void spawnFirework(World world, Location loc) {
        world.spawn(loc, org.bukkit.entity.Firework.class, fw -> {
            var meta = fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder().withColor(Color.AQUA).trail(true).build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        });
    }

    private void maybeDropBonus(Player player, Location loc) {
        if (!bonusDropsEnabled) return;
        if (Math.random() < 0.05) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), new org.bukkit.inventory.ItemStack(Material.DIAMOND));
            messages.send(player, "bonus");
            player.playSound(player.getLocation(), soundBonus, 1f, 1f);
        }
    }

    private void createBossbar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bar = Bukkit.createBossBar("OneBlock", BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.addPlayer(player);
        bar.setVisible(true);
        playerBossbars.put(uuid, bar);
    }

    private void updateBossbar(Player player, int progress, int required, String phaseName) {
        UUID uuid = player.getUniqueId();
        BossBar bar = playerBossbars.get(uuid);
        if (bar == null) {
            createBossbar(player);
            bar = playerBossbars.get(uuid);
        }

        double ratio = Math.min(1.0, (double) progress / required);
        bar.setProgress(ratio);
        int level = getIslandLevel(uuid);
        bar.setTitle(ChatColor.YELLOW + "Faza: " + ChatColor.GOLD + phaseName +
                ChatColor.GRAY + " [" + progress + "/" + required + "] " + ChatColor.AQUA + "Lvl " + level);
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.GOLD + "OneBlock");
        inv.setItem(0, createItem(Material.GRASS_BLOCK, ChatColor.GREEN + "Start"));
        inv.setItem(1, createItem(Material.OAK_DOOR, ChatColor.YELLOW + "Home"));
        inv.setItem(2, createItem(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Progress"));
        inv.setItem(3, createItem(Material.PAPER, ChatColor.AQUA + "Level"));
        inv.setItem(4, createItem(Material.BOOK, ChatColor.AQUA + "Phases"));
        inv.setItem(5, createItem(Material.NETHER_STAR, ChatColor.GREEN + "Upgrade"));
        inv.setItem(6, createItem(Material.BARRIER, ChatColor.RED + "Delete"));
        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack createItem(Material mat, String name) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isIslandBlock(Player player, Location loc) {
        Location genLoc = playerGenerators.get(player.getUniqueId());
        if (genLoc == null) return false;

        return genLoc.getBlockX() == loc.getBlockX()
                && genLoc.getBlockY() == loc.getBlockY()
                && genLoc.getBlockZ() == loc.getBlockZ();
    }

    public Location getGenerator(UUID uuid) {
        return playerGenerators.get(uuid);
    }

    public void removePlayer(UUID uuid) {
        playerGenerators.remove(uuid);
        playerProgress.remove(uuid);
        islandPoints.remove(uuid);
        islandUpgrades.remove(uuid);
        endSession(uuid);
        BossBar bar = playerBossbars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
        saveStats();
    }

    public void deleteIsland(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = playerGenerators.get(uuid);
        if (loc == null) {
            messages.send(player, "no_island_delete");
            return;
        }

        World world = loc.getWorld();
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());

        endSession(uuid);

        removePlayer(uuid);

        Bukkit.unloadWorld(world, false);
        deleteWorldFolder(world.getWorldFolder());

        player.playSound(player.getLocation(), soundDelete, 1f, 1f);
        messages.send(player, "island_deleted");
    }

    private void deleteWorldFolder(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteWorldFolder(f);
                }
            }
        }
        file.delete();
    }

    public void clearAll() {
        for (UUID uuid : new HashSet<>(playerGenerators.keySet())) {
            removePlayer(uuid);
        }
    }

    public List<Phase> getPhases() {
        return Collections.unmodifiableList(phases);
    }

    public int getPlayerProgress(UUID uuid) {
        return playerProgress.getOrDefault(uuid, 0);
    }

    public Phase getPlayerPhase(UUID uuid) {
        return getCurrentPhase(getPlayerProgress(uuid));
    }

    public void sendProgress(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerGenerators.containsKey(uuid)) {
            messages.send(player, "no_island");
            return;
        }
        int total = getPlayerProgress(uuid);
        int phaseIndex = getPhaseIndex(total);
        Phase phase = phases.get(phaseIndex);
        int progress = total - getBlocksBeforePhase(phaseIndex);
        player.sendMessage(ChatColor.YELLOW + "Faza: " + ChatColor.GOLD + phase.getName()
                + ChatColor.GRAY + " [" + progress + "/" + phase.getBlockCount() + "]");
    }

    public void sendLevel(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerGenerators.containsKey(uuid)) {
            messages.send(player, "no_island");
            return;
        }
        int points = getIslandPoints(uuid);
        int level = getIslandLevel(uuid);
        String msg = messages.get("level_info")
                .replace("%level%", String.valueOf(level))
                .replace("%points%", String.valueOf(points));
        player.sendMessage(msg);
    }

    public void listPhases(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Dostępne fazy:");
        for (Phase phase : phases) {
            player.sendMessage(ChatColor.GRAY + "- " + ChatColor.GOLD + phase.getName()
                    + ChatColor.GRAY + " (" + phase.getBlockCount() + ")");
        }
    }

    // --- Additional innovative functions ---

    public boolean hasIsland(UUID uuid) {
        return playerGenerators.containsKey(uuid);
    }

    public World getPlayerWorld(UUID uuid) {
        Location loc = playerGenerators.get(uuid);
        return loc != null ? loc.getWorld() : null;
    }

    public void pauseIsland(UUID uuid) {
        pausedPlayers.add(uuid);
    }

    public void resumeIsland(UUID uuid) {
        pausedPlayers.remove(uuid);
    }

    public void toggleBonusDrops(boolean enabled) {
        this.bonusDropsEnabled = enabled;
    }

    public boolean isBonusDropsEnabled() {
        return bonusDropsEnabled;
    }

    public void broadcastStart(Player player) {
        String msg = ChatColor.GREEN + player.getName() + " rozpocz\u0105\u0142 swoj\u0105 przygod\u0119 z OneBlock!";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    public Location getIslandLocation(Player player) {
        return playerGenerators.get(player.getUniqueId());
    }

    public List<UUID> listPlayersWithIslands() {
        return new ArrayList<>(playerGenerators.keySet());
    }

    public void resetPlayerProgress(UUID uuid) {
        playerProgress.put(uuid, 0);
    }

    public void setPlayerProgress(UUID uuid, int progress) {
        playerProgress.put(uuid, progress);
    }

    public BossBar getBossBar(Player player) {
        return playerBossbars.get(player.getUniqueId());
    }

    public void toggleBossbar(Player player) {
        BossBar bar = playerBossbars.get(player.getUniqueId());
        if (bar != null) {
            bar.setVisible(!bar.isVisible());
        }
    }

    public void deleteAllIslands() {
        for (UUID uuid : new HashSet<>(playerGenerators.keySet())) {
            Location loc = playerGenerators.get(uuid);
            if (loc != null) {
                World world = loc.getWorld();
                removePlayer(uuid);
                Bukkit.unloadWorld(world, false);
                deleteWorldFolder(world.getWorldFolder());
            }
        }
    }

    public void addPhase(Phase phase) {
        phases.add(phase);
    }

    public MessageManager getMessages() {
        return messages;
    }

    public boolean isPlayerIslandWorld(Player player, World world) {
        Location loc = playerGenerators.get(player.getUniqueId());
        return loc != null && loc.getWorld().equals(world);
    }

    public void addIslandPoint(UUID uuid, Material block) {
        int pts = blockPointValues.getOrDefault(block, defaultBlockPoints);
        islandPoints.put(uuid, islandPoints.getOrDefault(uuid, 0) + pts);
        saveStats();
    }

    public int getIslandPoints(UUID uuid) {
        return islandPoints.getOrDefault(uuid, 0);
    }

    public int getIslandLevel(UUID uuid) {
        return (getIslandPoints(uuid) / levelBlocks) + 1;
    }

    public void upgradeIsland(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerGenerators.containsKey(uuid)) {
            messages.send(player, "no_island");
            return;
        }
        int points = getIslandPoints(uuid);
        if (points < upgradeCost) {
            String msg = messages.get("upgrade_not_enough").replace("%cost%", String.valueOf(upgradeCost));
            player.sendMessage(msg);
            return;
        }
        islandPoints.put(uuid, points - upgradeCost);
        int lvl = islandUpgrades.getOrDefault(uuid, 0) + 1;
        islandUpgrades.put(uuid, lvl);
        WorldBorder border = playerGenerators.get(uuid).getWorld().getWorldBorder();
        border.setSize(border.getSize() + upgradeIncrement);
        player.sendMessage(messages.get("upgrade_success"));
        saveUpgrades();
        saveStats();
    }

    private void saveStats() {
        File file = new File(plugin.getDataFolder(), "stats.json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[");
            boolean first = true;
            for (UUID id : islandPoints.keySet()) {
                if (!first) writer.write(",");
                first = false;
                String name = Bukkit.getOfflinePlayer(id).getName();
                int pts = islandPoints.get(id);
                int lvl = getIslandLevel(id);
                writer.write(String.format("{\"uuid\":\"%s\",\"name\":\"%s\",\"points\":%d,\"level\":%d}", id, name == null ? "unknown" : name, pts, lvl));
            }
            writer.write("]");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save stats: " + e.getMessage());
        }
        saveServerInfo();
    }

    private void saveSessions() {
        File file = new File(plugin.getDataFolder(), "sessions.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        for (UUID id : totalSessionTime.keySet()) {
            cfg.set(id.toString(), totalSessionTime.get(id));
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save sessions: " + e.getMessage());
        }
        saveServerInfo();
    }

    private void saveUpgrades() {
        File file = new File(plugin.getDataFolder(), "upgrades.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        for (UUID id : islandUpgrades.keySet()) {
            cfg.set(id.toString(), islandUpgrades.get(id));
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save upgrades: " + e.getMessage());
        }
    }

    private void saveServerInfo() {
        File file = new File(plugin.getDataFolder(), "server.json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(String.format("{\"online\":%d,\"islands\":%d,\"version\":\"%s\"}",
                    Bukkit.getOnlinePlayers().size(), playerGenerators.size(), Bukkit.getVersion()));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save server info: " + e.getMessage());
        }
    }

    private void loadSessions() {
        File file = new File(plugin.getDataFolder(), "sessions.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                totalSessionTime.put(id, cfg.getLong(key));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in sessions.yml: " + key);
            }
        }
    }

    private void loadUpgrades() {
        File file = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                islandUpgrades.put(id, cfg.getInt(key));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in upgrades.yml: " + key);
            }
        }
    }

    private void startSession(UUID uuid) {
        sessionStart.put(uuid, System.currentTimeMillis());
    }

    private void endSession(UUID uuid) {
        Long start = sessionStart.remove(uuid);
        if (start != null) {
            long duration = System.currentTimeMillis() - start;
            totalSessionTime.put(uuid, totalSessionTime.getOrDefault(uuid, 0L) + duration);
            saveSessions();
        }
    }
}

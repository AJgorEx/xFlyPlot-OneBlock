package com.AJgorEx.xFlyPlot;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.util.*;

public class OneBlockManager {

    private final Plugin plugin;
    private final Map<UUID, Location> playerGenerators = new HashMap<>();
    // total amount of blocks each player has generated. Used for phase handling
    private final Map<UUID, Integer> playerProgress = new HashMap<>();
    private final Map<UUID, BossBar> playerBossbars = new HashMap<>();
    private final List<Phase> phases = new ArrayList<>();
    private final File phasesFile;
    private final String worldPrefix;
    private final Sound soundStart;
    private final Sound soundTeleport;
    private final Sound soundPhaseComplete;
    private final Sound soundDelete;
    private final Sound soundBonus;

    public OneBlockManager(Plugin plugin) {
        this.plugin = plugin;
        this.phasesFile = new File(plugin.getDataFolder(), "phases.yml");

        FileConfiguration cfg = plugin.getConfig();
        this.worldPrefix = cfg.getString("world-prefix", "xflyplot-");
        this.soundStart = Sound.valueOf(cfg.getString("sounds.start", "ENTITY_PLAYER_LEVELUP"));
        this.soundTeleport = Sound.valueOf(cfg.getString("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT"));
        this.soundPhaseComplete = Sound.valueOf(cfg.getString("sounds.phase-complete", "UI_TOAST_CHALLENGE_COMPLETE"));
        this.soundDelete = Sound.valueOf(cfg.getString("sounds.island-delete", "ENTITY_WITHER_DEATH"));
        this.soundBonus = Sound.valueOf(cfg.getString("sounds.bonus", "ENTITY_EXPERIENCE_ORB_PICKUP"));

        if (!phasesFile.exists()) {
            plugin.saveResource("phases.yml", false);
        }

        loadPhases();
    }

    public void startIsland(Player player) {
        UUID uuid = player.getUniqueId();

        if (!playerGenerators.containsKey(uuid)) {
            createGenerator(uuid);
            teleportHome(player);
            player.sendMessage(ChatColor.GREEN + "Twoja wyspa OneBlock została stworzona!");
            player.playSound(player.getLocation(), soundStart, 1f, 1f);
            createBossbar(player);
        } else {
            player.sendMessage(ChatColor.RED + "Masz już wyspę!");
        }
    }

    public void teleportHome(Player player) {
        UUID uuid = player.getUniqueId();
        Location home = playerGenerators.get(uuid);

        if (home != null) {
            player.teleport(home.clone().add(0.5, 1, 0.5));
            player.sendMessage(ChatColor.YELLOW + "Teleportowano na wyspę OneBlock!");
            player.playSound(player.getLocation(), soundTeleport, 1f, 1f);
        } else {
            player.sendMessage(ChatColor.RED + "Nie masz jeszcze wyspy. Użyj /oneblock, żeby ją stworzyć.");
        }
    }

    public void createGenerator(UUID uuid) {
        String worldName = worldPrefix + uuid.toString().substring(0, 8);

        World world = Bukkit.createWorld(new WorldCreator(worldName)
                .generator(new VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT));

        Location genLoc = new Location(world, 0, 64, 0);

        world.getBlockAt(genLoc).setType(Material.GRASS_BLOCK);
        world.getBlockAt(genLoc.clone().subtract(0, 1, 0)).setType(Material.BEDROCK);

        playerGenerators.put(uuid, genLoc);
        playerProgress.put(uuid, 0);
    }

    public void handleBlockBreak(Player player) {
        UUID uuid = player.getUniqueId();

        if (!playerGenerators.containsKey(uuid)) return;

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
            player.sendMessage(ChatColor.GOLD + "Przechodzisz do następnej fazy!");
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
        if (Math.random() < 0.05) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), new org.bukkit.inventory.ItemStack(Material.DIAMOND));
            player.sendMessage(ChatColor.GOLD + "Bonusowy diament!");
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
        bar.setTitle(ChatColor.YELLOW + "Faza: " + ChatColor.GOLD + phaseName +
                ChatColor.GRAY + " [" + progress + "/" + required + "]");
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.GOLD + "OneBlock");
        inv.setItem(0, createItem(Material.GRASS_BLOCK, ChatColor.GREEN + "Start"));
        inv.setItem(1, createItem(Material.OAK_DOOR, ChatColor.YELLOW + "Home"));
        inv.setItem(2, createItem(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Progress"));
        inv.setItem(3, createItem(Material.BOOK, ChatColor.AQUA + "Phases"));
        inv.setItem(4, createItem(Material.BARRIER, ChatColor.RED + "Delete"));
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
        BossBar bar = playerBossbars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void deleteIsland(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = playerGenerators.get(uuid);
        if (loc == null) {
            player.sendMessage(ChatColor.RED + "Nie masz wyspy do usunięcia.");
            return;
        }

        World world = loc.getWorld();
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());

        removePlayer(uuid);

        Bukkit.unloadWorld(world, false);
        deleteWorldFolder(world.getWorldFolder());

        player.playSound(player.getLocation(), soundDelete, 1f, 1f);
        player.sendMessage(ChatColor.YELLOW + "Twoja wyspa została usunięta.");
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
            player.sendMessage(ChatColor.RED + "Nie masz jeszcze wyspy.");
            return;
        }
        int total = getPlayerProgress(uuid);
        int phaseIndex = getPhaseIndex(total);
        Phase phase = phases.get(phaseIndex);
        int progress = total - getBlocksBeforePhase(phaseIndex);
        player.sendMessage(ChatColor.YELLOW + "Faza: " + ChatColor.GOLD + phase.getName()
                + ChatColor.GRAY + " [" + progress + "/" + phase.getBlockCount() + "]");
    }

    public void listPhases(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Dostępne fazy:");
        for (Phase phase : phases) {
            player.sendMessage(ChatColor.GRAY + "- " + ChatColor.GOLD + phase.getName()
                    + ChatColor.GRAY + " (" + phase.getBlockCount() + ")");
        }
    }
}

package com.AJgorEx.xFlyPlot;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class OneBlockManager {

    private final Plugin plugin;
    private final Map<UUID, Location> playerGenerators = new HashMap<>();
    private final Map<UUID, Integer> playerProgress = new HashMap<>();
    private final Map<UUID, BossBar> playerBossbars = new HashMap<>();
    private final List<Phase> phases = new ArrayList<>();
    private final File phasesFile;

    public OneBlockManager(Plugin plugin) {
        this.plugin = plugin;
        this.phasesFile = new File(plugin.getDataFolder(), "phases.yml");

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
        } else {
            player.sendMessage(ChatColor.RED + "Nie masz jeszcze wyspy. Użyj /oneblock, żeby ją stworzyć.");
        }
    }

    public void createGenerator(UUID uuid) {
        String worldName = "xflyplot-" + uuid.toString().substring(0, 8);

        World world = Bukkit.createWorld(new WorldCreator(worldName)
                .generator(new VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT));

        Location genLoc = new Location(world, 0, 64, 0);

        world.getBlockAt(genLoc).setType(Material.GRASS_BLOCK);
        world.getBlockAt(genLoc.clone().subtract(0, 1, 0)).setType(Material.BEDROCK);

        playerGenerators.put(uuid, genLoc.add(0.5, 0.0, 0.5));
        playerProgress.put(uuid, 0);
    }

    public void handleBlockBreak(Player player) {
        UUID uuid = player.getUniqueId();

        if (!playerGenerators.containsKey(uuid)) return;

        int progress = playerProgress.getOrDefault(uuid, 0);
        Phase currentPhase = getCurrentPhase(progress);
        Material nextBlock = getRandomBlock(currentPhase);

        if (!nextBlock.isBlock()) {
            player.sendMessage(ChatColor.RED + "Błąd: " + nextBlock + " nie jest blokiem.");
            return;
        }

        Location genLoc = playerGenerators.get(uuid);
        genLoc.getBlock().setType(nextBlock);

        int newProgress = progress + 1;
        playerProgress.put(uuid, newProgress);

        updateBossbar(player, newProgress, currentPhase.getBlockCount(), currentPhase.getName());

        if (newProgress >= currentPhase.getBlockCount()) {
            player.sendMessage(ChatColor.GOLD + "Przechodzisz do następnej fazy!");
            playerProgress.put(uuid, 0);
        }
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

    private Phase getCurrentPhase(int blocksBroken) {
        int sum = 0;
        for (Phase phase : phases) {
            sum += phase.getBlockCount();
            if (blocksBroken < sum) return phase;
        }
        return phases.get(phases.size() - 1);
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

    public void clearAll() {
        for (UUID uuid : new HashSet<>(playerGenerators.keySet())) {
            removePlayer(uuid);
        }
    }

    public List<Phase> getPhases() {
        return Collections.unmodifiableList(phases);
    }
}

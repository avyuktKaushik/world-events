package dev.arc2.worldevents.core;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.util.Util;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** data.yml — balances, pending deliveries, active events, maze restore points. */
public final class DataStore {

    public record Restore(Location location, GameMode gameMode) {}

    private final WorldEventsPlugin plugin;
    private final File file;
    private YamlConfiguration yml = new YamlConfiguration();

    public DataStore(WorldEventsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (file.exists()) yml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    public YamlConfiguration yml() {
        return yml;
    }

    public ConfigurationSection section(String path) {
        ConfigurationSection s = yml.getConfigurationSection(path);
        return s != null ? s : yml.createSection(path);
    }

    // ------------------------------------------------------------ balances

    public Map<UUID, Double> loadBalances() {
        Map<UUID, Double> out = new HashMap<>();
        ConfigurationSection s = yml.getConfigurationSection("balances");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            try {
                out.put(UUID.fromString(k), s.getDouble(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public void saveBalances(Map<UUID, Double> balances) {
        yml.set("balances", null);
        for (var e : balances.entrySet()) yml.set("balances." + e.getKey(), Math.round(e.getValue() * 100.0) / 100.0);
    }

    // ------------------------------------------------------------ pending deliveries

    public void addPending(UUID uuid, List<ItemStack> items) {
        List<String> list = new ArrayList<>(yml.getStringList("pending." + uuid));
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            list.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        yml.set("pending." + uuid, list);
        save();
    }

    public List<ItemStack> takePending(UUID uuid) {
        List<ItemStack> out = new ArrayList<>();
        List<String> list = yml.getStringList("pending." + uuid);
        if (list.isEmpty()) return out;
        for (String s : list) {
            try {
                out.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(s)));
            } catch (Exception e) {
                plugin.getLogger().warning("Could not decode a pending item for " + uuid);
            }
        }
        yml.set("pending." + uuid, null);
        save();
        return out;
    }

    // ------------------------------------------------------------ maze restore points

    public boolean hasRestore(UUID uuid) {
        return yml.isConfigurationSection("restore." + uuid);
    }

    public void setRestore(UUID uuid, Location loc, GameMode gm) {
        ConfigurationSection s = yml.createSection("restore." + uuid);
        Util.writeLocation(s, loc);
        s.set("gamemode", gm.name());
        save();
    }

    public Restore takeRestore(UUID uuid) {
        ConfigurationSection s = yml.getConfigurationSection("restore." + uuid);
        if (s == null) return null;
        Location loc = Util.readLocation(s);
        GameMode gm;
        try {
            gm = GameMode.valueOf(s.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException e) {
            gm = GameMode.SURVIVAL;
        }
        yml.set("restore." + uuid, null);
        save();
        return new Restore(loc, gm);
    }
}

package dev.arc2.worldevents.auction;

import dev.arc2.worldevents.WorldEventsPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns sacrificed items into auction money.
 * Golden apples ≈ $20 per stack, diamond armor pieces $1–8 depending on enchants, etc.
 * Damaged gear is worth proportionally less; filled shulkers/bundles are refused.
 */
public final class SacrificeValuer {

    private final WorldEventsPlugin plugin;
    private final Map<Material, Double> values = new EnumMap<>(Material.class);
    private final Map<Material, Double> gearBase = new EnumMap<>(Material.class);
    private final Map<String, Double> enchantValues = new HashMap<>();
    private double defaultEnchantValue;
    private double gearCap;
    private double bookBase;
    private boolean allowBooks;
    private final NamespacedKey tradedKey;

    public SacrificeValuer(WorldEventsPlugin plugin) {
        this.plugin = plugin;
        this.tradedKey = new NamespacedKey(plugin, "villager_traded");
        reload();
    }

    public void reload() {
        values.clear();
        gearBase.clear();
        enchantValues.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("auction.sacrifice");
        if (root == null) return;
        ConfigurationSection v = root.getConfigurationSection("values");
        if (v != null) for (String k : v.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null) values.put(m, v.getDouble(k));
        }
        ConfigurationSection g = root.getConfigurationSection("gear-base");
        if (g != null) for (String k : g.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null) gearBase.put(m, g.getDouble(k));
        }
        ConfigurationSection e = root.getConfigurationSection("enchant-values");
        if (e != null) for (String k : e.getKeys(false)) enchantValues.put(k.toLowerCase(Locale.ROOT), e.getDouble(k));
        defaultEnchantValue = root.getDouble("default-enchant-value-per-level", 0.25);
        gearCap = root.getDouble("gear-max-value", 8);
        bookBase = root.getDouble("enchanted-book-base", 0.5);
        allowBooks = root.getBoolean("allow-enchanted-books", false);
    }

    /** True for items worth tagging when they come out of a villager trade. */
    public boolean isValuable(Material m) {
        return values.containsKey(m) || gearBase.containsKey(m) || m == Material.ENCHANTED_BOOK;
    }

    public NamespacedKey tradedKey() {
        return tradedKey;
    }

    private double enchantSum(Map<Enchantment, Integer> map) {
        double sum = 0;
        for (var en : map.entrySet()) {
            String key = en.getKey().getKey().getKey().toLowerCase(Locale.ROOT);
            sum += enchantValues.getOrDefault(key, defaultEnchantValue) * en.getValue();
        }
        return sum;
    }

    /** Value of the whole stack in dollars (0 = refused / worthless). */
    public double value(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0;
        Material m = it.getType();
        ItemMeta meta = it.getItemMeta();

        // bought from a villager / wandering trader -> worthless (anti trading-hall money printing)
        if (meta != null && meta.getPersistentDataContainer().has(tradedKey)) return 0;

        // refuse containers with contents (empty them first)
        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) if (inner != null && !inner.getType().isAir()) return 0;
        }
        if (meta instanceof BundleMeta bm && bm.hasItems()) return 0;

        if (gearBase.containsKey(m)) {
            double v = gearBase.get(m) + (meta == null ? 0 : enchantSum(meta.getEnchants()));
            v = Math.min(v, gearCap);
            if (meta instanceof Damageable d && m.getMaxDurability() > 0) {
                double frac = 1.0 - (double) d.getDamage() / m.getMaxDurability();
                v *= Math.max(0, Math.min(1, frac));
            }
            return round(v * it.getAmount());
        }
        if (m == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm) {
            if (!allowBooks) return 0;
            return round((bookBase + enchantSum(esm.getStoredEnchants())) * it.getAmount());
        }
        Double per = values.get(m);
        if (per == null) return 0;
        return round(per * it.getAmount());
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

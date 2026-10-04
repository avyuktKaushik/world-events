package dev.arc2.worldevents.reward;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.util.Util;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One configurable item entry. Example YAML:
 * <pre>
 * - {material: GOLDEN_APPLE, amount: "8-16", weight: 10}
 * - {material: DIAMOND_SWORD, enchants: {sharpness: 4, unbreaking: 3}, name: "&lt;aqua&gt;Blade"}
 * - {material: SPLASH_POTION, potion: strong_healing, amount: 4}
 * - {special: ARMOR_SHULKER}           # jackpot shulker of prot 3 armor
 * - {special: ARMOR_SET}               # 4 piece prot 3 / unb 3 / mending set
 * - {special: "POTION_SHULKER:strong_strength:POTION"}
 * - {special: OP_VILLAGER}
 * </pre>
 */
public final class ItemSpec {

    private final String material;
    private final String special;
    private final int min, max;
    private final double weight;
    private final String name;
    private final List<String> lore;
    private final Object enchants;
    private final String potion;

    private ItemSpec(Map<?, ?> m) {
        this.material = str(m.get("material"));
        this.special = str(m.get("special"));
        int[] r = Util.parseRange(m.get("amount"), 1);
        this.min = Math.max(1, r[0]);
        this.max = Math.max(this.min, r[1]);
        double w;
        try {
            w = m.get("weight") == null ? 1 : Double.parseDouble(String.valueOf(m.get("weight")));
        } catch (NumberFormatException e) {
            w = 1;
        }
        this.weight = w;
        this.name = str(m.get("name"));
        Object l = m.get("lore");
        List<String> loreList = new ArrayList<>();
        if (l instanceof List<?> list) for (Object o : list) loreList.add(String.valueOf(o));
        this.lore = loreList;
        this.enchants = m.get("enchants");
        this.potion = str(m.get("potion"));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static List<ItemSpec> parse(List<Map<?, ?>> raw) {
        List<ItemSpec> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<?, ?> m : raw) {
            if (m == null) continue;
            out.add(new ItemSpec(m));
        }
        return out;
    }

    public double weight() {
        return weight;
    }

    public List<ItemStack> build(WorldEventsPlugin plugin) {
        int amount = Util.range(min, max);
        List<ItemStack> out = new ArrayList<>();
        if (special != null && !special.isBlank()) {
            String[] parts = special.split(":");
            switch (parts[0].trim().toUpperCase(Locale.ROOT)) {
                case "ARMOR_SHULKER" -> {
                    for (int i = 0; i < amount; i++) out.add(ItemFactory.armorShulker(plugin));
                }
                case "ARMOR_SET" -> {
                    for (int i = 0; i < amount; i++) out.addAll(ItemFactory.armorSet());
                }
                case "ARMOR_PIECE" -> {
                    Material[] armor = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
                    for (int i = 0; i < amount; i++) out.add(ItemFactory.armorPiece(armor[Util.rnd().nextInt(4)]));
                }
                case "POTION_SHULKER" -> {
                    PotionType type = Util.potion(parts.length > 1 ? parts[1] : "strong_strength");
                    Material pm = Material.matchMaterial(parts.length > 2 ? parts[2] : "POTION");
                    for (int i = 0; i < amount; i++) out.add(ItemFactory.potionShulker(pm, type, name));
                }
                case "OP_VILLAGER" -> {
                    for (int i = 0; i < amount; i++) out.add(plugin.opVillager().egg());
                }
                default -> plugin.getLogger().warning("Unknown special item '" + special + "' in config.");
            }
            return out;
        }
        Material m = material == null ? null : Material.matchMaterial(material);
        if (m == null || m.isAir() || !m.isItem()) {
            plugin.getLogger().warning("Unknown material '" + material + "' in config.");
            return out;
        }
        ItemStack base = new ItemStack(m);
        if (potion != null && base.getItemMeta() instanceof PotionMeta pm) {
            PotionType type = Util.potion(potion);
            if (type != null) {
                pm.setBasePotionType(type);
                base.setItemMeta(pm);
            }
        }
        if (enchants != null) ItemFactory.enchant(base, ItemFactory.parseEnchants(enchants));
        if (name != null || !lore.isEmpty()) ItemFactory.named(base, name, lore);
        out.addAll(ItemFactory.splitStacks(base, amount));
        return out;
    }
}

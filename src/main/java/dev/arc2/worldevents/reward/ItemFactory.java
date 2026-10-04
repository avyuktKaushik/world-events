package dev.arc2.worldevents.reward;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds all special reward items. Never creates netherite or Protection IV. */
public final class ItemFactory {

    private static final Material[] DIAMOND_ARMOR = {
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
    };

    private ItemFactory() {}

    public static ItemStack named(ItemStack it, String name, List<String> lore) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (name != null && !name.isEmpty()) meta.displayName(Msg.item(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.item("<gray>" + s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack enchant(ItemStack it, Map<Enchantment, Integer> enchants) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        for (var e : enchants.entrySet()) {
            if (e.getKey() == null) continue;
            if (meta instanceof EnchantmentStorageMeta esm) esm.addStoredEnchant(e.getKey(), e.getValue(), true);
            else meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Prot III / Unbreaking III / Mending diamond piece. */
    public static ItemStack armorPiece(Material m) {
        Map<Enchantment, Integer> e = new LinkedHashMap<>();
        e.put(Enchantment.PROTECTION, 3);
        e.put(Enchantment.UNBREAKING, 3);
        e.put(Enchantment.MENDING, 1);
        return enchant(new ItemStack(m), e);
    }

    public static List<ItemStack> armorSet() {
        List<ItemStack> list = new ArrayList<>();
        for (Material m : DIAMOND_ARMOR) list.add(armorPiece(m));
        return list;
    }

    public static ItemStack shulker(Material shulkerMaterial, String name, List<ItemStack> contents) {
        if (shulkerMaterial == null || !shulkerMaterial.name().endsWith("SHULKER_BOX")) shulkerMaterial = Material.SHULKER_BOX;
        ItemStack box = new ItemStack(shulkerMaterial);
        if (box.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox sb) {
            for (int i = 0; i < contents.size() && i < 27; i++) sb.getInventory().setItem(i, contents.get(i));
            meta.setBlockState(sb);
            if (name != null) meta.displayName(Msg.item(name));
            box.setItemMeta(meta);
        }
        return box;
    }

    /** The jackpot: a shulker box full of Prot III / Unb III / Mending diamond armor. */
    public static ItemStack armorShulker(WorldEventsPlugin plugin) {
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < 27; i++) contents.add(armorPiece(DIAMOND_ARMOR[i % 4]));
        Material mat = Material.matchMaterial(plugin.getConfig().getString("rewards.jackpot.shulker-color", "CYAN_SHULKER_BOX"));
        String name = plugin.getConfig().getString("rewards.jackpot.name", "<gradient:#00c6ff:#0072ff><bold>Arc 2 Armory</bold></gradient>");
        return shulker(mat, name, contents);
    }

    public static ItemStack potion(Material potionMaterial, PotionType type) {
        if (potionMaterial == null) potionMaterial = Material.POTION;
        ItemStack it = new ItemStack(potionMaterial);
        if (type != null && it.getItemMeta() instanceof PotionMeta pm) {
            pm.setBasePotionType(type);
            it.setItemMeta(pm);
        }
        return it;
    }

    public static ItemStack potionShulker(Material potionMaterial, PotionType type, String name) {
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < 27; i++) contents.add(potion(potionMaterial, type));
        return shulker(Material.SHULKER_BOX, name, contents);
    }

    public static ItemStack book(Enchantment e, int level) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        m.put(e, level);
        return enchant(new ItemStack(Material.ENCHANTED_BOOK), m);
    }

    public static List<ItemStack> splitStacks(ItemStack base, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, base.getMaxStackSize());
        while (amount > 0) {
            ItemStack c = base.clone();
            int a = Math.min(max, amount);
            c.setAmount(a);
            out.add(c);
            amount -= a;
        }
        return out;
    }

    public static Map<Enchantment, Integer> parseEnchants(Object o) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                Enchantment ench = Util.enchantment(String.valueOf(e.getKey()));
                if (ench == null) continue;
                int lvl;
                try {
                    lvl = Integer.parseInt(String.valueOf(e.getValue()));
                } catch (NumberFormatException ex) {
                    lvl = 1;
                }
                out.put(ench, lvl);
            }
        }
        return out;
    }
}

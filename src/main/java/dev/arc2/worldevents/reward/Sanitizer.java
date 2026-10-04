package dev.arc2.worldevents.reward;

import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * Hard safety net for every reward the plugin hands out:
 * no netherite anything, and Protection is capped (default III).
 */
public final class Sanitizer {

    private Sanitizer() {}

    /** @return the cleaned item, or null if it must not be given at all. */
    public static ItemStack clean(ItemStack it, int maxProtection) {
        if (it == null || it.getType().isAir()) return null;
        if (it.getType().name().toUpperCase(Locale.ROOT).contains("NETHERITE")) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        boolean changed = false;

        int lvl = meta.getEnchantLevel(Enchantment.PROTECTION);
        if (lvl > maxProtection) {
            meta.removeEnchant(Enchantment.PROTECTION);
            meta.addEnchant(Enchantment.PROTECTION, maxProtection, true);
            changed = true;
        }
        if (meta instanceof EnchantmentStorageMeta esm) {
            int stored = esm.getStoredEnchantLevel(Enchantment.PROTECTION);
            if (stored > maxProtection) {
                esm.removeStoredEnchant(Enchantment.PROTECTION);
                esm.addStoredEnchant(Enchantment.PROTECTION, maxProtection, true);
                changed = true;
            }
        }
        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
            boolean boxChanged = false;
            for (int i = 0; i < box.getInventory().getSize(); i++) {
                ItemStack inner = box.getInventory().getItem(i);
                if (inner == null) continue;
                ItemStack cleaned = clean(inner, maxProtection);
                if (cleaned != inner) {
                    box.getInventory().setItem(i, cleaned);
                    boxChanged = true;
                } else if (cleaned != null) {
                    box.getInventory().setItem(i, cleaned);
                    boxChanged = true;
                }
            }
            if (boxChanged) {
                bsm.setBlockState(box);
                changed = true;
            }
        }
        if (changed) it.setItemMeta(meta);
        return it;
    }
}

package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ore and mob drops ×4.
 * <ul>
 *   <li>Only <b>naturally generated</b> ore is multiplied — any ore a player ever placed (or pushed
 *       with a piston) is remembered in chunk data and drops normally, so place-and-mine duping is impossible.</li>
 *   <li>Silk touch is never multiplied (prevents turning 1 ore into 4 ore blocks).</li>
 *   <li>Mob drops only for real kills by a player, never for spawner/egg/bred/split/golem-farm mobs,
 *       and never for items the mob was holding/wearing (prevents duping items you give to a mob).</li>
 * </ul>
 */
public final class Looting3000Event extends WorldEvent implements Listener {

    public Looting3000Event(WorldEventsPlugin plugin) {
        super(plugin, EventType.LOOTING_3000);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        return true;
    }

    private int multiplier() {
        return Math.max(1, Math.min(16, cfg().getInt("multiplier", 4)));
    }

    // ------------------------------------------------------------ ores

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOreDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (!affected(p)) return;
        Material type = e.getBlockState().getType();
        if (!plugin.tracker().isOre(type)) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!tool.getType().isAir() && tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        if (plugin.tracker().isPlaced(e.getBlock())) return;

        int mult = multiplier();
        List<ItemStack> extra = new ArrayList<>();
        for (Item item : e.getItems()) {
            ItemStack s = item.getItemStack();
            // never multiply the ore block itself
            if (s.getType() == type) continue;
            multiplyInto(s, mult, extra);
            item.setItemStack(s);
        }
        Location loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack it : extra) loc.getWorld().dropItemNaturally(loc, it);
    }

    // ------------------------------------------------------------ mobs

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        if (!cfg().getBoolean("multiply-mob-drops", true)) return;
        LivingEntity ent = e.getEntity();
        if (ent instanceof Player) return;
        Player killer = ent.getKiller();
        if (killer == null || !affected(killer)) return;
        if (ent.fromMobSpawner()) return;
        String reason = ent.getEntitySpawnReason().name();
        for (String s : cfg().getStringList("excluded-spawn-reasons")) if (s.equalsIgnoreCase(reason)) return;
        if (ent instanceof InventoryHolder) return; // villagers, piglins, allays, donkeys...
        if (ent instanceof Tameable t && t.isTamed()) return;

        Set<Material> blacklist = new HashSet<>();
        for (String s : cfg().getStringList("mob-drop-blacklist")) {
            Material m = Material.matchMaterial(s);
            if (m != null) blacklist.add(m);
        }

        List<ItemStack> equipment = new ArrayList<>();
        EntityEquipment eq = ent.getEquipment();
        if (eq != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                try {
                    ItemStack it = eq.getItem(slot);
                    if (it != null && !it.getType().isAir()) equipment.add(it);
                } catch (Throwable ignored) {
                    // slot not supported by this entity
                }
            }
        }

        int mult = multiplier();
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack d : e.getDrops()) {
            if (d == null || d.getType().isAir()) continue;
            if (blacklist.contains(d.getType())) continue;
            if (d.getType().name().toUpperCase(Locale.ROOT).contains("NETHERITE")) continue;
            if (d.hasItemMeta()) {
                ItemMeta meta = d.getItemMeta();
                if (meta.hasDisplayName() || meta.hasEnchants() || meta.hasLore()) continue;
            }
            boolean held = false;
            for (ItemStack q : equipment) {
                if (q.isSimilar(d)) {
                    held = true;
                    break;
                }
            }
            if (held) continue;
            multiplyInto(d, mult, extra);
        }
        e.getDrops().addAll(extra);
    }

    private static void multiplyInto(ItemStack s, int mult, List<ItemStack> extra) {
        int total = s.getAmount() * mult;
        int max = Math.max(1, s.getMaxStackSize());
        int first = Math.min(total, max);
        s.setAmount(first);
        total -= first;
        while (total > 0) {
            ItemStack c = s.clone();
            int a = Math.min(total, max);
            c.setAmount(a);
            extra.add(c);
            total -= a;
        }
    }
}

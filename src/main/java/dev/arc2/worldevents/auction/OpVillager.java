package dev.arc2.worldevents.auction;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.reward.ItemFactory;
import dev.arc2.worldevents.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The OP Villager: a master librarian selling Protection III, Mending, Unbreaking III and
 * Sharpness V books for 1 emerald each, with unlimited uses. Given to auction winners as a
 * special spawn item; the villager can't change job or gain/lose trades.
 */
public final class OpVillager implements Listener {

    private final WorldEventsPlugin plugin;
    private final NamespacedKey eggKey;
    private final NamespacedKey villagerKey;

    public OpVillager(WorldEventsPlugin plugin) {
        this.plugin = plugin;
        this.eggKey = new NamespacedKey(plugin, "op_villager_egg");
        this.villagerKey = new NamespacedKey(plugin, "op_villager");
    }

    public ItemStack egg() {
        ItemStack it = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(plugin.getConfig().getString("auction.op-villager.name", "<gradient:#ffd700:#ff8c00><bold>OP Villager</bold></gradient>")));
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.item("<gray>Master Librarian — 1 emerald each:"));
        lore.add(Msg.item("<aqua> • Protection III"));
        lore.add(Msg.item("<aqua> • Mending"));
        lore.add(Msg.item("<aqua> • Unbreaking III"));
        lore.add(Msg.item("<aqua> • Sharpness V"));
        lore.add(Msg.item(""));
        lore.add(Msg.item("<yellow>Right-click a block to place."));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(eggKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isEgg(ItemStack it) {
        return it != null && it.getType() == Material.VILLAGER_SPAWN_EGG && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(eggKey);
    }

    public boolean isOpVillager(AbstractVillager v) {
        return v.getPersistentDataContainer().has(villagerKey);
    }

    private List<MerchantRecipe> recipes() {
        List<MerchantRecipe> list = new ArrayList<>();
        Object[][] books = {
                {Enchantment.PROTECTION, 3}, {Enchantment.MENDING, 1}, {Enchantment.UNBREAKING, 3}, {Enchantment.SHARPNESS, 5}
        };
        int price = Math.max(1, plugin.getConfig().getInt("auction.op-villager.emerald-price", 1));
        for (Object[] b : books) {
            MerchantRecipe r = new MerchantRecipe(ItemFactory.book((Enchantment) b[0], (Integer) b[1]), 0, Integer.MAX_VALUE, false, 0, 0f);
            r.addIngredient(new ItemStack(Material.EMERALD, price));
            list.add(r);
        }
        return list;
    }

    public Villager spawn(Location loc) {
        return loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setProfession(Villager.Profession.LIBRARIAN);
            v.setVillagerLevel(5);
            v.setVillagerExperience(250);
            v.customName(Msg.item(plugin.getConfig().getString("auction.op-villager.name", "<gradient:#ffd700:#ff8c00><bold>OP Villager</bold></gradient>")));
            v.setCustomNameVisible(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.getPersistentDataContainer().set(villagerKey, PersistentDataType.BYTE, (byte) 1);
            v.setRecipes(recipes());
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (!isEgg(item)) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block target = e.getClickedBlock().getRelative(e.getBlockFace());
        if (!target.isPassable()) {
            Msg.send(e.getPlayer(), "<red>There's no room for the villager there.");
            return;
        }
        Player p = e.getPlayer();
        EquipmentSlot hand = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
        ItemStack held = p.getInventory().getItem(hand);
        if (!isEgg(held)) return;
        held.setAmount(held.getAmount() - 1);
        p.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
        spawn(target.getLocation().add(0.5, 0, 0.5));
        Msg.send(p, "<gold>Your OP Villager has arrived!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseOnEntity(PlayerInteractEntityEvent e) {
        if (isEgg(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDispense(BlockDispenseEvent e) {
        if (isEgg(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCareer(VillagerCareerChangeEvent e) {
        if (isOpVillager(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquire(VillagerAcquireTradeEvent e) {
        if (isOpVillager(e.getEntity())) e.setCancelled(true);
    }
}

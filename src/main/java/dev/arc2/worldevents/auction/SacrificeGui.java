package dev.arc2.worldevents.auction;

import dev.arc2.worldevents.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 54-slot sacrifice altar: top 45 slots are input, bottom row is buttons. */
public final class SacrificeGui implements InventoryHolder {

    public static final int INPUT_SLOTS = 45;
    public static final int CANCEL_SLOT = 45;
    public static final int INFO_SLOT = 49;
    public static final int CONFIRM_SLOT = 53;

    private final Inventory inventory;
    private final UUID owner;
    private boolean done;

    public SacrificeGui(Player owner) {
        this.owner = owner.getUniqueId();
        this.inventory = Bukkit.createInventory(this, 54, Msg.mm("<dark_red><bold>Sacrifice Altar</bold></dark_red> <dark_gray>— items → money"));
        for (int i = INPUT_SLOTS; i < 54; i++) inventory.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>Sacrifice Altar #" + i, List.of()));
        inventory.setItem(CANCEL_SLOT, button(Material.RED_CONCRETE, "<red><bold>Cancel", List.of("<gray>Returns all items.")));
        inventory.setItem(CONFIRM_SLOT, button(Material.LIME_CONCRETE, "<green><bold>SACRIFICE", List.of("<gray>Destroys the items above and", "<gray>adds their value to your balance.", "<red>This cannot be undone!")));
    }

    public UUID owner() { return owner; }
    public boolean done() { return done; }
    public void setDone() { done = true; }

    public static ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.item(s));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    public void updateInfo(double total, int refused, double balance) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Value of items: <green>" + Msg.money(total));
        lore.add("<gray>Your balance: <yellow>" + Msg.money(balance));
        if (refused > 0) lore.add("<red>" + refused + " item stack(s) are worthless and will be returned.");
        lore.add("");
        lore.add("<dark_gray>Gapples ≈ $20/stack, diamond armor $1–8 each.");
        lore.add("<dark_gray>Empty shulkers/bundles before sacrificing.");
        inventory.setItem(INFO_SLOT, button(Material.GOLD_INGOT, "<gold><bold>Total: " + Msg.money(total), lore));
    }

    public List<ItemStack> inputs() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack it = inventory.getItem(i);
            if (it != null && !it.getType().isAir()) out.add(it);
        }
        return out;
    }

    public void clearInputs() {
        for (int i = 0; i < INPUT_SLOTS; i++) inventory.setItem(i, null);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

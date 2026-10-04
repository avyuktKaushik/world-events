package dev.arc2.worldevents.gui;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small admin GUI: set each event's duration and whether it's in the random pool.
 * Left/right click = +/- 1 minute, shift = +/- 5, Q (drop) = toggle random pool, F (swap hand) = start it.
 */
public final class DurationGui implements InventoryHolder {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 31};
    private static final int RANDOM_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final WorldEventsPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, EventType> slots = new HashMap<>();

    public DurationGui(WorldEventsPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, Msg.mm("<gradient:#ff4e50:#f9d423><bold>World Events</bold></gradient> <dark_gray>— Durations"));
        render();
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    private static ItemStack item(Material m, String name, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.item(s));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        if (glint) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public void render() {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);
        EventType[] types = EventType.values();
        for (int i = 0; i < types.length && i < SLOTS.length; i++) {
            EventType t = types[i];
            slots.put(SLOTS[i], t);
            boolean enabled = plugin.events().enabled(t);
            WorldEvent active = plugin.events().get(t);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + plugin.events().description(t));
            lore.add("");
            lore.add("<gray>Duration: <yellow><bold>" + plugin.events().durationMinutes(t) + " min");
            lore.add("<gray>Random pool: " + (enabled ? "<green>enabled" : "<red>disabled"));
            if (active != null) lore.add("<green>● ACTIVE <gray>(" + Msg.time(active.remainingSeconds()) + " left)");
            lore.add("");
            lore.add("<dark_gray>Left / Right: <white>+1 / -1 min");
            lore.add("<dark_gray>Shift-Left / Shift-Right: <white>+5 / -5 min");
            lore.add("<dark_gray>Q (drop): <white>toggle random pool");
            lore.add("<dark_gray>F (swap hand): <white>start this event");
            inventory.setItem(SLOTS[i], item(t.icon(), plugin.events().rawName(t), lore, active != null));
        }
        inventory.setItem(RANDOM_SLOT, item(Material.NETHER_STAR, "<gradient:#ff4e50:#f9d423><bold>Start Random Event</bold></gradient>",
                List.of("<gray>Spins the WORLD EVENT roulette."), true));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red>Close", List.of(), false));
    }

    public void click(Player p, int slot, ClickType click) {
        if (slot == CLOSE_SLOT) {
            p.closeInventory();
            return;
        }
        if (slot == RANDOM_SLOT) {
            p.closeInventory();
            plugin.events().startRandom(p, true);
            return;
        }
        EventType t = slots.get(slot);
        if (t == null) return;
        String base = "events." + t.id() + ".";
        switch (click) {
            case DROP, CONTROL_DROP -> plugin.getConfig().set(base + "enabled", !plugin.events().enabled(t));
            case SWAP_OFFHAND -> {
                p.closeInventory();
                plugin.events().start(t, null, true, new HashMap<>(), p);
                return;
            }
            default -> {
                int delta = switch (click) {
                    case LEFT -> 1;
                    case RIGHT -> -1;
                    case SHIFT_LEFT -> 5;
                    case SHIFT_RIGHT -> -5;
                    default -> 0;
                };
                if (delta == 0) return;
                int v = Math.max(1, Math.min(1440, plugin.events().durationMinutes(t) + delta));
                plugin.getConfig().set(base + "duration-minutes", v);
            }
        }
        plugin.saveConfig();
        p.playSound(Msg.sound("minecraft:ui.button.click", 0.6f, 1.4f), net.kyori.adventure.sound.Sound.Emitter.self());
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

package dev.arc2.worldevents.gui;

import dev.arc2.worldevents.WorldEventsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    private final WorldEventsPlugin plugin;

    public GuiListener(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof DurationGui gui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("worldevents.admin")) return;
        if (e.getRawSlot() < 0 || e.getRawSlot() >= 54) return;
        gui.click(p, e.getRawSlot(), e.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof DurationGui) e.setCancelled(true);
    }
}

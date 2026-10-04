package dev.arc2.worldevents.listener;

import dev.arc2.worldevents.WorldEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Always-on listener: join/quit/respawn reconciliation, milk/totem protection, AFK tracking. */
public final class CoreListener implements Listener {

    private final WorldEventsPlugin plugin;

    public CoreListener(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.events().markActivity(p);
        plugin.events().enforce(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.events().handleJoin(p);
            plugin.rewards().deliverPending(p);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.events().handleQuit(e.getPlayer());
        plugin.events().forgetActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.events().handleRespawn(p);
        }, 1L);
    }

    /** Milk buckets and totems can never clear event effects. (Deaths are re-applied on respawn.) */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEffectRemoved(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.REMOVED && e.getAction() != EntityPotionEffectEvent.Action.CLEARED) return;
        EntityPotionEffectEvent.Cause c = e.getCause();
        if (c != EntityPotionEffectEvent.Cause.MILK && c != EntityPotionEffectEvent.Cause.TOTEM) return;
        if (plugin.events().desiredEffects(p).containsKey(e.getModifiedType())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (f.getBlockX() != t.getBlockX() || f.getBlockZ() != t.getBlockZ() || f.getBlockY() != t.getBlockY()
                || Math.abs(f.getYaw() - t.getYaw()) > 2 || Math.abs(f.getPitch() - t.getPitch()) > 2) {
            plugin.events().markActivity(e.getPlayer());
        }
    }
}

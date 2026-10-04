package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Reach is reduced from 3 to 1.5 blocks using the vanilla interaction range attributes
 * (server side, so it can't be bypassed by clients). A server-side distance check on melee hits
 * acts as a backup against reach hacks.
 */
public final class ShortReachEvent extends WorldEvent implements Listener {

    public ShortReachEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.SHORT_REACH);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        return true;
    }

    @Override
    public double entityReach(Player p) {
        return cfg().getDouble("entity-reach", 1.5);
    }

    @Override
    public double blockReach(Player p) {
        return cfg().getDouble("block-reach", 3.0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !affected(p)) return;
        if (e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        double reach = entityReach(p) + cfg().getDouble("hit-tolerance", 0.6);
        Vector eye = p.getEyeLocation().toVector();
        BoundingBox box = e.getEntity().getBoundingBox();
        double dx = Math.max(box.getMinX() - eye.getX(), Math.max(0, eye.getX() - box.getMaxX()));
        double dy = Math.max(box.getMinY() - eye.getY(), Math.max(0, eye.getY() - box.getMaxY()));
        double dz = Math.max(box.getMinZ() - eye.getZ(), Math.max(0, eye.getZ() - box.getMaxZ()));
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > reach) e.setCancelled(true);
    }
}

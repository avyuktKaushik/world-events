package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.Map;

/** Everyone receives 20 hearts. Health is clamped back down when the event ends. */
public final class TankyEvent extends WorldEvent {

    public TankyEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.TANKY);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        if (cfg().getBoolean("heal-on-start", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!affected(p) || p.isDead()) continue;
                    AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
                    if (a != null) p.setHealth(a.getValue());
                }
            }, 2L);
        }
        return true;
    }

    @Override
    public double maxHealth(Player p) {
        return Math.max(1, cfg().getDouble("hearts", 20)) * 2.0;
    }

    @Override
    public int healthPriority() {
        return 20;
    }
}

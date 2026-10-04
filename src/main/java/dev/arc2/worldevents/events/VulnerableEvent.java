package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import org.bukkit.entity.Player;

import java.util.Map;

/** Everyone is reduced to 7 hearts (max health attribute — survives relog/milk/totem/death). */
public final class VulnerableEvent extends WorldEvent {

    public VulnerableEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.VULNERABLE);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        return true;
    }

    @Override
    public double maxHealth(Player p) {
        return Math.max(1, cfg().getDouble("hearts", 7)) * 2.0;
    }

    @Override
    public int healthPriority() {
        return 10;
    }
}

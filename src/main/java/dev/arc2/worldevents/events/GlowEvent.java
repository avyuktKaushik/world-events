package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Everyone glows. Uses the entity glowing flag instead of the potion effect, so milk and totems
 * can't remove it; it's re-applied every second, on join and on respawn.
 */
public final class GlowEvent extends WorldEvent {

    public GlowEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.GLOW_AND_BEHOLD);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        return true;
    }

    @Override
    public boolean glow(Player p) {
        return true;
    }
}

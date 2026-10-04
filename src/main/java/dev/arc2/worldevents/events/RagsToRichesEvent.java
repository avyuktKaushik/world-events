package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/** Everyone receives Hero of the Village 255. */
public final class RagsToRichesEvent extends WorldEvent {

    public RagsToRichesEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.RAGS_TO_RICHES);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        return true;
    }

    @Override
    public void effects(Player p, Map<PotionEffectType, PotionEffect> out) {
        int level = Math.max(1, Math.min(255, cfg().getInt("level", 255)));
        out.put(PotionEffectType.HERO_OF_THE_VILLAGE,
                new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffect.INFINITE_DURATION, level - 1, false, false, true));
    }
}

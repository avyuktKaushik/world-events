package dev.arc2.worldevents.core;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "WORLD EVENT" announcement: event names flash rapidly in the subtitle with a click
 * on each change, slowing down until it lands on the chosen event.
 */
public final class Roulette {

    private final WorldEventsPlugin plugin;
    private final EventType target;
    private final Runnable onLand;
    private final List<EventType> pool = new ArrayList<>();
    private final int spins;
    private final String titleA;
    private final String titleB;

    public Roulette(WorldEventsPlugin plugin, EventType target, Runnable onLand) {
        this.plugin = plugin;
        this.target = target;
        this.onLand = onLand;
        FileConfiguration c = plugin.getConfig();
        this.spins = Math.max(8, c.getInt("settings.roulette.spins", 30));
        this.titleA = c.getString("settings.roulette.title", "<gradient:#ff4e50:#f9d423><bold>WORLD EVENT</bold></gradient>");
        this.titleB = c.getString("settings.roulette.title-flash", "<gradient:#f9d423:#ff4e50><bold>WORLD EVENT</bold></gradient>");
        for (EventType t : EventType.values()) if (t != target) pool.add(t);
        Collections.shuffle(pool);
    }

    public void play() {
        Msg.playAll(Msg.sound(plugin.getConfig().getString("settings.roulette.start-sound", "minecraft:block.beacon.activate"), 1f, 1.2f));
        step(0);
    }

    private void step(int i) {
        if (i >= spins) {
            land();
            return;
        }
        EventType shown = pool.isEmpty() ? target : pool.get(i % pool.size());
        Component title = Msg.mm(i % 2 == 0 ? titleA : titleB);
        Component sub = Msg.mm(plugin.events().rawName(shown));
        Msg.titleAll(title, sub, 0, 1500, 0);
        float pitch = 0.8f + (float) i / spins * 1.0f;
        Msg.playAll(Msg.sound(plugin.getConfig().getString("settings.roulette.click-sound", "minecraft:ui.button.click"), 0.8f, pitch));
        // delay grows from 1 tick to ~10 ticks (ease-out)
        double t = (double) i / spins;
        long delay = 1 + Math.round(Math.pow(t, 3) * 9);
        Bukkit.getScheduler().runTaskLater(plugin, () -> step(i + 1), delay);
    }

    private void land() {
        Component title = Msg.mm(titleA);
        Component sub = Msg.mm("<bold>» </bold>" + plugin.events().rawName(target) + "<reset><bold> «</bold>");
        Msg.titleAll(title, sub, 0, 3500, 800);
        Msg.playAll(Msg.sound(plugin.getConfig().getString("settings.roulette.land-sound", "minecraft:ui.toast.challenge_complete"), 1f, 1f));
        Msg.playAll(Msg.sound("minecraft:entity.firework_rocket.twinkle", 1f, 1f));
        onLand.run();
    }
}

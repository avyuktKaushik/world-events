package dev.arc2.worldevents.core;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Optional automation:
 * <ul>
 *   <li>auto-events — a random world event every X–Y minutes</li>
 *   <li>auto-auction — 1–2 auctions per day at a random time inside configured windows</li>
 * </ul>
 */
public final class AutoScheduler {

    private record Planned(ZonedDateTime at, ZonedDateTime windowEnd) {}

    private final WorldEventsPlugin plugin;
    private int taskId = -1;
    private long nextAutoEvent;
    private LocalDate plannedFor;
    private final List<Planned> planned = new ArrayList<>();

    public AutoScheduler(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 30, 20L * 60).getTaskId();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    public void reload() {
        scheduleNextAutoEvent();
        plannedFor = null;
        planned.clear();
    }

    private void scheduleNextAutoEvent() {
        FileConfiguration c = plugin.getConfig();
        int min = Math.max(5, c.getInt("auto-events.min-interval-minutes", 120));
        int max = Math.max(min, c.getInt("auto-events.max-interval-minutes", 240));
        nextAutoEvent = System.currentTimeMillis() + Util.range(min, max) * 60_000L;
    }

    private ZoneId zone() {
        String z = plugin.getConfig().getString("auction.auto.timezone", "");
        try {
            return z == null || z.isBlank() ? ZoneId.systemDefault() : ZoneId.of(z);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private void tick() {
        FileConfiguration c = plugin.getConfig();
        long online = Bukkit.getOnlinePlayers().stream().filter(WorldEvent::affected).count();

        // random world events
        if (c.getBoolean("auto-events.enabled", false) && System.currentTimeMillis() >= nextAutoEvent) {
            if (online >= c.getInt("auto-events.min-players", 3) && plugin.events().active().isEmpty() && !plugin.events().isRouletteRunning()) {
                plugin.events().startRandom(Bukkit.getConsoleSender(), true);
            }
            scheduleNextAutoEvent();
        }

        // daily auctions
        if (!c.getBoolean("auction.auto.enabled", true)) return;
        ZonedDateTime now = ZonedDateTime.now(zone());
        if (!now.toLocalDate().equals(plannedFor)) planDay(now);
        for (Planned pl : new ArrayList<>(planned)) {
            if (now.isBefore(pl.at())) continue;
            if (now.isAfter(pl.windowEnd())) {
                planned.remove(pl);
                continue;
            }
            if (online < c.getInt("auction.auto.min-players", 3)) continue;
            if (plugin.events().checkCanStart(EventType.AUCTION, false) != null) continue;
            planned.remove(pl);
            plugin.events().start(EventType.AUCTION, null, c.getBoolean("auction.auto.roulette", true), new HashMap<>(), Bukkit.getConsoleSender());
        }
    }

    private void planDay(ZonedDateTime now) {
        plannedFor = now.toLocalDate();
        planned.clear();
        List<String> windows = plugin.getConfig().getStringList("auction.auto.windows");
        List<Double> chances = plugin.getConfig().getDoubleList("auction.auto.window-chances");
        for (int i = 0; i < windows.size(); i++) {
            double chance = i < chances.size() ? chances.get(i) : 1.0;
            if (!Util.chance(chance)) continue;
            String[] parts = windows.get(i).split("-");
            if (parts.length != 2) continue;
            try {
                LocalTime a = LocalTime.parse(parts[0].trim());
                LocalTime b = LocalTime.parse(parts[1].trim());
                int span = Math.max(0, (b.toSecondOfDay() - a.toSecondOfDay()) / 60);
                LocalTime at = a.plusMinutes(span == 0 ? 0 : Util.rnd().nextInt(span));
                ZonedDateTime when = now.with(at);
                ZonedDateTime end = now.with(b);
                if (end.isAfter(now)) planned.add(new Planned(when, end));
            } catch (Exception e) {
                plugin.getLogger().warning("Bad auction window '" + windows.get(i) + "' (use HH:mm-HH:mm)");
            }
        }
    }
}

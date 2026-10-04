package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates are announced, a countdown gives everyone time to travel, then loot falls from
 * the sky in waves around that spot until the event ends. Chunks are kept loaded with plugin
 * tickets so the loot actually lands.
 */
public final class LootRainEvent extends WorldEvent {

    private World world;
    private int cx, cz;
    private int countdown;
    private int waveTimer;
    private final Set<Long> tickets = new HashSet<>();

    public LootRainEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.LOOT_RAIN);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        Location loc = options.get("location") instanceof Location l ? l : Util.randomRegionLocation(plugin.getConfig());
        if (loc == null || loc.getWorld() == null) return false;
        world = loc.getWorld();
        cx = loc.getBlockX();
        cz = loc.getBlockZ();
        countdown = Math.max(0, cfg().getInt("countdown-seconds", 90));
        addTickets();
        Msg.broadcast(Msg.box("gold", List.of(
                "<gold><bold>  ☔ LOOT RAIN INCOMING ☔</bold>",
                "  <yellow>Location: <white>X: <x>  Z: <z></white> <gray>(<world>)",
                "  <gray>Loot starts falling in <white><cd>s</white>. Get there first!"
        ), Placeholder.unparsed("x", String.valueOf(cx)), Placeholder.unparsed("z", String.valueOf(cz)),
                Placeholder.unparsed("world", Util.worldLabel(world)), Placeholder.unparsed("cd", String.valueOf(countdown))));
        return true;
    }

    private void addTickets() {
        int r = cfg().getInt("radius", 12) + 16;
        for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) {
            for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) {
                world.addPluginChunkTicket(x, z, plugin);
                tickets.add(((long) x << 32) | (z & 0xffffffffL));
            }
        }
    }

    @Override
    public void tickSecond() {
        if (world == null) return;
        if (countdown > 0) {
            countdown--;
            if (countdown == 60 || countdown == 30 || countdown == 10 || (countdown <= 5 && countdown > 0)) {
                Msg.broadcast("<gold>Loot Rain</gold> <gray>at <white>X: " + cx + " Z: " + cz + "</white> starts in <yellow>" + countdown + "s");
                if (countdown <= 5) Msg.playAll(Msg.sound("minecraft:block.note_block.pling", 0.7f, 1.5f));
            }
            if (countdown == 0) {
                Msg.titleAll(Msg.mm("<gold><bold>LOOT RAIN</bold>"), Msg.mm("<yellow>X: " + cx + "  Z: " + cz), 100, 2500, 500);
                Msg.playAll(Msg.sound("minecraft:entity.lightning_bolt.thunder", 0.6f, 1.2f));
            }
            return;
        }
        if (--waveTimer > 0) return;
        waveTimer = Math.max(1, cfg().getInt("wave-interval-seconds", 5));
        spawnWave();
    }

    private void spawnWave() {
        int radius = Math.max(2, cfg().getInt("radius", 12));
        int height = Math.max(5, cfg().getInt("drop-height", 25));
        int[] per = Util.parseRange(cfg().get("items-per-wave"), 5);
        int count = Util.range(per[0], per[1]);
        List<Map<?, ?>> loot = cfgMapList("loot");
        for (int i = 0; i < count; i++) {
            double ang = Util.rnd().nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(Util.rnd().nextDouble()) * radius;
            int x = (int) Math.floor(cx + Math.cos(ang) * dist);
            int z = (int) Math.floor(cz + Math.sin(ang) * dist);
            int y = Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z) + height);
            Location at = new Location(world, x + 0.5, y, z + 0.5);
            for (ItemStack it : plugin.rewards().sanitize(plugin.rewards().roll(loot, 1))) {
                world.dropItem(at, it, item -> {
                    item.setGlowing(true);
                    item.setVelocity(new Vector(0, -0.4, 0));
                    item.setPickupDelay(10);
                });
            }
            world.spawnParticle(Particle.FIREWORK, at, 6, 0.2, 0.2, 0.2, 0.02);
        }
    }

    @Override
    public void onStop(StopReason reason) {
        if (world == null) return;
        for (long key : tickets) world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
        tickets.clear();
    }

    @Override
    public Component bossBarName() {
        if (countdown > 0)
            return Msg.mm("<gold><bold>Loot Rain</bold></gold> <gray>at <white>" + cx + ", " + cz + "</white> — starts in <yellow>" + countdown + "s");
        return Msg.mm("<gold><bold>Loot Rain</bold></gold> <gray>at <white>" + cx + ", " + cz + "</white> — <white>" + Msg.time(remainingSeconds()));
    }

    @Override
    public void save(ConfigurationSection s) {
        s.set("world", world == null ? null : world.getName());
        s.set("x", cx);
        s.set("z", cz);
        s.set("countdown", countdown);
    }

    @Override
    public boolean load(ConfigurationSection s) {
        world = Bukkit.getWorld(s.getString("world", ""));
        cx = s.getInt("x");
        cz = s.getInt("z");
        countdown = s.getInt("countdown");
        return world != null;
    }

    @Override
    public void onResume() {
        addTickets();
    }
}

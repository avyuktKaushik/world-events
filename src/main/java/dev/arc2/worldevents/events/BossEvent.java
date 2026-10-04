package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Announces random coordinates (X 1–6000, Z 1–6000 by default). The admin places the boss there;
 * admins get a clickable teleport in chat. Reminders are broadcast periodically.
 */
public final class BossEvent extends WorldEvent {

    private String worldName;
    private int x, z;
    private int reminderTimer;

    public BossEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.BOSS);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public int x() { return x; }
    public int z() { return z; }

    @Override
    public boolean onStart(Map<String, Object> options) {
        Location loc = Util.randomRegionLocation(plugin.getConfig());
        worldName = loc.getWorld().getName();
        x = options.get("x") instanceof Integer i ? i : loc.getBlockX();
        z = options.get("z") instanceof Integer i ? i : loc.getBlockZ();
        reminderTimer = Math.max(60, cfg().getInt("reminder-minutes", 5) * 60);

        Msg.broadcast(Msg.box("dark_purple", List.of(
                "<dark_purple><bold>  ☠ BOSS EVENT ☠</bold>",
                "  <light_purple>A powerful boss has been summoned!",
                "  <gray>Location: <white>X: <x>  Z: <z></white> <dark_gray>(<world>)",
                "  <gold>Defeat it for OP loot!"
        ), Placeholder.unparsed("x", String.valueOf(x)), Placeholder.unparsed("z", String.valueOf(z)),
                Placeholder.unparsed("world", Util.worldLabel(world()))));
        Msg.playAll(Msg.sound("minecraft:entity.ender_dragon.growl", 0.8f, 1f));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("worldevents.admin")) continue;
            Msg.send(p, "<light_purple>Admin:</light_purple> place the boss at <white>" + x + ", " + z + "</white> "
                    + "<click:run_command:'/worldevent bosstp'><hover:show_text:'<gray>Teleport to the boss location'><yellow><u>[Teleport]</u></yellow></hover></click>");
        }
        return true;
    }

    @Override
    public void tickSecond() {
        if (--reminderTimer > 0) return;
        reminderTimer = Math.max(60, cfg().getInt("reminder-minutes", 5) * 60);
        Msg.broadcast("<dark_purple>☠ Boss Event</dark_purple> <gray>— the boss awaits at <white>X: " + x + "  Z: " + z
                + "</white> <dark_gray>(" + Util.worldLabel(world()) + ")");
    }

    @Override
    public Component bossBarName() {
        return Msg.mm("<dark_purple><bold>Boss Event</bold></dark_purple> <gray>at <white>X: " + x + "  Z: " + z + "</white> — <white>" + Msg.time(remainingSeconds()));
    }

    @Override
    public void save(ConfigurationSection s) {
        s.set("world", worldName);
        s.set("x", x);
        s.set("z", z);
    }

    @Override
    public boolean load(ConfigurationSection s) {
        worldName = s.getString("world", "world");
        x = s.getInt("x");
        z = s.getInt("z");
        reminderTimer = 60;
        return true;
    }
}

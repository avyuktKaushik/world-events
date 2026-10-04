package dev.arc2.worldevents.util;

import dev.arc2.worldevents.WorldEventsPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Small MiniMessage / sound / formatting helper. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String DEFAULT_PREFIX =
            "<dark_gray>[<gradient:#ff4e50:#f9d423><bold>World Events</bold></gradient><dark_gray>]</dark_gray> <gray>";
    private static WorldEventsPlugin plugin;

    private Msg() {}

    public static void init(WorldEventsPlugin p) {
        plugin = p;
    }

    public static Component mm(String s, TagResolver... resolvers) {
        return MM.deserialize(s == null ? "" : s, resolvers);
    }

    /** Component suitable for item names / lore (no default italics). */
    public static Component item(String s, TagResolver... resolvers) {
        return mm(s, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String stripTags(String mini) {
        return MM.stripTags(mini == null ? "" : mini);
    }

    public static String escape(String s) {
        return MM.escapeTags(s == null ? "" : s);
    }

    public static String raw(String path, String def) {
        if (plugin == null) return def;
        return plugin.getConfig().getString("messages." + path, def);
    }

    public static String prefix() {
        return raw("prefix", DEFAULT_PREFIX);
    }

    public static Component prefixed(String s, TagResolver... resolvers) {
        return mm(prefix() + s, resolvers);
    }

    public static void send(CommandSender to, String s, TagResolver... resolvers) {
        if (to != null) to.sendMessage(prefixed(s, resolvers));
    }

    public static void broadcast(Component c) {
        Bukkit.getServer().sendMessage(c);
    }

    public static void broadcast(String mini, TagResolver... resolvers) {
        broadcast(prefixed(mini, resolvers));
    }

    /** Big, noticeable multi-line chat box. */
    public static Component box(String color, List<String> lines, TagResolver... resolvers) {
        String bar = "<" + color + "><strikethrough>                                                            </strikethrough></" + color + ">";
        StringBuilder sb = new StringBuilder();
        sb.append(bar);
        for (String line : lines) sb.append("<newline>").append(line).append("<reset>");
        sb.append("<newline>").append(bar);
        return mm(sb.toString(), resolvers);
    }

    public static Sound sound(String key, float volume, float pitch) {
        Key k;
        try {
            k = Key.key(key == null ? "minecraft:ui.button.click" : key);
        } catch (Exception e) {
            k = Key.key("minecraft:ui.button.click");
        }
        return Sound.sound(k, Sound.Source.MASTER, volume, pitch);
    }

    public static void playAll(Sound s) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(s, Sound.Emitter.self());
    }

    public static void play(Player p, Sound s) {
        p.playSound(s, Sound.Emitter.self());
    }

    public static void titleAll(Component title, Component sub, long fadeInMs, long stayMs, long fadeOutMs) {
        Title t = Title.title(title, sub, Title.Times.times(Duration.ofMillis(fadeInMs), Duration.ofMillis(stayMs), Duration.ofMillis(fadeOutMs)));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(t);
    }

    public static String money(double v) {
        return String.format(Locale.US, "$%,.2f", v);
    }

    public static String time(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    public static String minutes(long minutes) {
        if (minutes >= 60 && minutes % 60 == 0) return (minutes / 60) + "h";
        if (minutes >= 60) return (minutes / 60) + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }
}

package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One player gets a bounty: red glow, 16 hearts and buffs, but their coordinates are shown to
 * everyone. Killer gets 2 enchanted gapples, 2 stacks of gapples and a Prot III diamond set.
 * <p>Anti-abuse: logging out doesn't remove the bounty (forfeit after a grace period),
 * deaths without a player killer give nothing, same-IP (alt) kills give nothing,
 * a minimum hunt time is required before the bounty can be claimed.</p>
 */
public final class HuntedEvent extends WorldEvent implements Listener {

    private static final String TEAM = "we_hunted";

    private UUID target;
    private String targetName = "?";
    private String previousTeam;
    private long offlineSince = -1;
    private int broadcastTimer;
    private boolean claimed;
    private boolean forfeited;

    public HuntedEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.THE_HUNTED);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        Player t = null;
        if (options.get("target") instanceof UUID id) t = Bukkit.getPlayer(id);
        if (t == null || !affected(t)) {
            List<Player> eligible = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) if (affected(p)) eligible.add(p);
            if (eligible.size() < Math.max(1, cfg().getInt("min-players", 2)) || eligible.isEmpty()) return false;
            t = eligible.get(Util.rnd().nextInt(eligible.size()));
        }
        target = t.getUniqueId();
        targetName = t.getName();
        joinTeam(t);
        broadcastTimer = 3;
        Player finalT = t;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!finalT.isOnline()) return;
            AttributeInstance a = finalT.getAttribute(Attribute.MAX_HEALTH);
            if (a != null && !finalT.isDead()) finalT.setHealth(a.getValue());
        }, 2L);

        Msg.broadcast(Msg.box("dark_red", List.of(
                "<dark_red><bold>  ☠ THE HUNTED ☠</bold>",
                "  <red><bold><name></bold></red> <gray>has a bounty on their head!",
                "  <gray>Their coordinates are leaked to everyone.",
                "  <gold>Kill them for: <yellow>2 Enchanted Golden Apples, 2 stacks of Golden Apples & a Prot III diamond set"
        ), Placeholder.unparsed("name", targetName)));
        Msg.play(t, Msg.sound("minecraft:entity.wither.spawn", 0.7f, 1f));
        t.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<dark_red><bold>YOU ARE HUNTED"),
                Msg.mm("<red>Survive " + Msg.minutes((endMillis - startMillis) / 60000) + " for a reward")));
        return true;
    }

    // ------------------------------------------------------------ declarative state

    @Override
    public double maxHealth(Player p) {
        return p.getUniqueId().equals(target) ? Math.max(1, cfg().getDouble("hearts", 16)) * 2.0 : -1;
    }

    @Override
    public int healthPriority() {
        return 100;
    }

    @Override
    public void effects(Player p, Map<PotionEffectType, PotionEffect> out) {
        if (!p.getUniqueId().equals(target)) return;
        for (String s : cfg().getStringList("buffs")) {
            String[] parts = s.split(":");
            PotionEffectType t = Util.effect(parts[0].trim());
            if (t == null) continue;
            int amp = 0;
            try {
                if (parts.length > 1) amp = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
            out.put(t, new PotionEffect(t, PotionEffect.INFINITE_DURATION, amp, false, false, true));
        }
    }

    @Override
    public boolean glow(Player p) {
        return p.getUniqueId().equals(target);
    }

    @Override
    public void enforce(Player p) {
        if (p.getUniqueId().equals(target)) {
            Team team = team();
            if (!team.hasEntry(p.getName())) joinTeam(p);
        } else {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM);
            if (team != null && team.hasEntry(p.getName())) team.removeEntry(p.getName());
        }
    }

    // ------------------------------------------------------------ team (red glow)

    private static Team team() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(TEAM);
        if (t == null) t = sb.registerNewTeam(TEAM);
        t.color(NamedTextColor.RED);
        t.prefix(Msg.mm("<dark_red><bold>☠</bold> "));
        return t;
    }

    private void joinTeam(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = sb.getEntryTeam(p.getName());
        if (current != null && !current.getName().equals(TEAM)) previousTeam = current.getName();
        team().addEntry(p.getName());
    }

    /** Removes a player from the hunted team (called when no hunt is active). */
    public static void cleanupTeam(WorldEventsPlugin plugin, Player p) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM);
        if (team != null && team.hasEntry(p.getName())) team.removeEntry(p.getName());
    }

    // ------------------------------------------------------------ ticking

    @Override
    public void tickSecond() {
        Player t = Bukkit.getPlayer(target);
        long now = System.currentTimeMillis();
        if (t == null || !t.isOnline()) {
            if (offlineSince < 0) offlineSince = now;
            long grace = cfg().getLong("offline-grace-minutes", 5) * 60_000L;
            if (now - offlineSince > grace) {
                forfeited = true;
                Msg.broadcast("<dark_red>☠</dark_red> <red><name></red> <gray>stayed offline too long and <red>forfeited</red> the bounty!",
                        Placeholder.unparsed("name", targetName));
                complete();
            }
            return;
        }
        offlineSince = -1;
        if (--broadcastTimer <= 0) {
            broadcastTimer = Math.max(10, cfg().getInt("coords-broadcast-seconds", 60));
            Location l = t.getLocation();
            Msg.broadcast("<dark_red>☠</dark_red> <red><name></red> <gray>is at <white>X: <x> Y: <y> Z: <z></white> <dark_gray>(<world>)",
                    Placeholder.unparsed("name", targetName),
                    Placeholder.unparsed("x", String.valueOf(l.getBlockX())),
                    Placeholder.unparsed("y", String.valueOf(l.getBlockY())),
                    Placeholder.unparsed("z", String.valueOf(l.getBlockZ())),
                    Placeholder.unparsed("world", Util.worldLabel(l.getWorld())));
        }
        if (now / 1000 % 2 == 0) t.sendActionBar(Msg.mm("<dark_red><bold>☠ YOU ARE HUNTED ☠</bold> <gray>— your location is public"));
    }

    @Override
    public Component bossBarName() {
        Player t = Bukkit.getPlayer(target);
        if (t == null) return Msg.mm("<dark_red><bold>THE HUNTED</bold> <red>" + Msg.escape(targetName) + " <gray>is <red>OFFLINE</red> — <white>" + Msg.time(remainingSeconds()));
        Location l = t.getLocation();
        return Msg.mm("<dark_red><bold>THE HUNTED</bold> <red>" + Msg.escape(targetName) + " <gray>» <white>"
                + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " <dark_gray>(" + Util.worldLabel(l.getWorld()) + ")<gray> — <white>" + Msg.time(remainingSeconds()));
    }

    // ------------------------------------------------------------ listeners

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!victim.getUniqueId().equals(target) || claimed) return;
        Player killer = victim.getKiller();
        long huntedFor = (System.currentTimeMillis() - startMillis) / 1000;
        long minHunt = cfg().getLong("min-hunt-seconds", 60);

        String deny = null;
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) deny = "died without a player killing them";
        else if (cfg().getBoolean("block-same-ip-kills", true) && sameIp(killer, victim)) deny = "was killed by an account on the same IP";
        else if (huntedFor < minHunt) deny = "was killed too early (bounty needs " + minHunt + "s)";

        claimed = true;
        if (deny != null) {
            Msg.broadcast("<dark_red>☠</dark_red> <red><name></red> <gray><why> — the bounty goes <red>unclaimed</red>.",
                    Placeholder.unparsed("name", targetName), Placeholder.unparsed("why", deny));
            complete();
            return;
        }
        plugin.rewards().give(killer.getUniqueId(), plugin.rewards().all(cfgMapList("killer-rewards")));
        Msg.broadcast(Msg.box("gold", List.of(
                "<gold><bold>  ☠ BOUNTY CLAIMED ☠</bold>",
                "  <yellow><killer></yellow> <gray>killed <red><name></red> and claimed the bounty!"
        ), Placeholder.unparsed("killer", killer.getName()), Placeholder.unparsed("name", targetName)));
        Msg.titleAll(Msg.mm("<gold><bold>BOUNTY CLAIMED"), Msg.mm("<yellow>" + Msg.escape(killer.getName()) + " <gray>killed <red>" + Msg.escape(targetName)), 100, 3000, 600);
        Msg.playAll(Msg.sound("minecraft:ui.toast.challenge_complete", 1f, 1f));
        plugin.rewards().rollJackpot(killer.getUniqueId(), cfg().getDouble("killer-jackpot-chance", 0.20));
        complete();
    }

    private static boolean sameIp(Player a, Player b) {
        InetSocketAddress x = a.getAddress(), y = b.getAddress();
        if (x == null || y == null || x.getAddress() == null || y.getAddress() == null) return false;
        return x.getAddress().equals(y.getAddress());
    }

    @Override
    public void onQuit(Player p) {
        if (!p.getUniqueId().equals(target)) return;
        Location l = p.getLocation();
        Msg.broadcast("<dark_red>☠</dark_red> <red><name></red> <gray>logged out at <white>X: <x> Y: <y> Z: <z></white>. They have <yellow><grace>m</yellow> to return or they forfeit!",
                Placeholder.unparsed("name", targetName),
                Placeholder.unparsed("x", String.valueOf(l.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(l.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(l.getBlockZ())),
                Placeholder.unparsed("grace", String.valueOf(cfg().getLong("offline-grace-minutes", 5))));
    }

    @Override
    public void onJoin(Player p) {
        if (p.getUniqueId().equals(target)) {
            Msg.broadcast("<dark_red>☠</dark_red> <red><name></red> <gray>is back online — the hunt continues!", Placeholder.unparsed("name", targetName));
        }
    }

    @Override
    public void onStop(StopReason reason) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM);
        if (team != null) {
            for (String entry : new ArrayList<>(team.getEntries())) team.removeEntry(entry);
        }
        if (previousTeam != null) {
            Team prev = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(previousTeam);
            if (prev != null) prev.addEntry(targetName);
        }
        if (reason == StopReason.EXPIRED && !claimed && !forfeited && target != null) {
            plugin.rewards().give(target, plugin.rewards().all(cfgMapList("survivor-rewards")));
            Msg.broadcast("<dark_red>☠</dark_red> <red><name></red> <gray>survived the hunt and earned the <gold>survivor reward</gold>!",
                    Placeholder.unparsed("name", targetName));
        }
    }

    @Override
    public boolean excludeFromParticipation(UUID uuid) {
        return forfeited && uuid.equals(target);
    }

    // ------------------------------------------------------------ persistence

    @Override
    public void save(ConfigurationSection s) {
        s.set("target", target == null ? null : target.toString());
        s.set("name", targetName);
        s.set("previous-team", previousTeam);
        s.set("offline-since", offlineSince);
    }

    @Override
    public boolean load(ConfigurationSection s) {
        try {
            target = UUID.fromString(s.getString("target", ""));
        } catch (IllegalArgumentException e) {
            return false;
        }
        targetName = s.getString("name", "?");
        previousTeam = s.getString("previous-team");
        offlineSince = -1; // the server was down; restart the grace period
        return true;
    }
}

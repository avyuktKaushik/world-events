package dev.arc2.worldevents.core;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.events.*;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Starts/stops events, runs the 1-second heartbeat and reconciles every player's state. */
public final class EventManager {

    public final NamespacedKey healthKey;
    public final NamespacedKey entityReachKey;
    public final NamespacedKey blockReachKey;
    public final NamespacedKey glowMarkKey;

    private final WorldEventsPlugin plugin;
    private final Map<EventType, WorldEvent> active = new EnumMap<>(EventType.class);
    private final Map<EventType, BossBar> bars = new EnumMap<>(EventType.class);
    private final Map<EventType, Map<UUID, Integer>> participation = new EnumMap<>(EventType.class);
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<PotionEffectType> managedEffects = new HashSet<>();
    private volatile SmartiePantsEvent smartie;
    private boolean rouletteRunning;
    private int taskId = -1;
    private final long enabledAt = System.currentTimeMillis();

    public EventManager(WorldEventsPlugin plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "event_health");
        this.entityReachKey = new NamespacedKey(plugin, "event_entity_reach");
        this.blockReachKey = new NamespacedKey(plugin, "event_block_reach");
        this.glowMarkKey = new NamespacedKey(plugin, "event_glow");
        reload();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    public void reload() {
        managedEffects.clear();
        managedEffects.add(PotionEffectType.HERO_OF_THE_VILLAGE);
        managedEffects.add(PotionEffectType.BLINDNESS);
        for (String s : plugin.getConfig().getStringList("events.the_hunted.buffs")) {
            PotionEffectType t = Util.effect(s.split(":")[0].trim());
            if (t != null) managedEffects.add(t);
        }
    }

    // ------------------------------------------------------------ accessors

    public Collection<WorldEvent> active() { return Collections.unmodifiableCollection(active.values()); }
    public boolean isActive(EventType t) { return active.containsKey(t); }
    public WorldEvent get(EventType t) { return active.get(t); }
    public SmartiePantsEvent smartie() { return smartie; }
    public boolean isRouletteRunning() { return rouletteRunning; }

    public String rawName(EventType t) {
        return plugin.getConfig().getString("events." + t.id() + ".display-name", t.defaultName());
    }

    public Component name(EventType t) { return Msg.mm(rawName(t)); }

    public String description(EventType t) {
        return plugin.getConfig().getString("events." + t.id() + ".description", t.defaultDescription());
    }

    public int durationMinutes(EventType t) {
        return Math.max(1, plugin.getConfig().getInt("events." + t.id() + ".duration-minutes", t.defaultMinutes()));
    }

    public boolean enabled(EventType t) {
        return plugin.getConfig().getBoolean("events." + t.id() + ".enabled", true);
    }

    public double weight(EventType t) {
        return plugin.getConfig().getDouble("events." + t.id() + ".weight", 10);
    }

    // ------------------------------------------------------------ starting

    /** @return null if the event may start, otherwise a human readable reason. */
    public String checkCanStart(EventType t, boolean ignoreRoulette) {
        if (!ignoreRoulette && rouletteRunning) return "A world event announcement is already in progress.";
        if (active.containsKey(t)) return Msg.stripTags(rawName(t)) + " is already active.";
        int max = plugin.getConfig().getInt("settings.max-concurrent-events", 1);
        if (max > 0 && active.size() >= max) return "The maximum number of concurrent events (" + max + ") is already running.";
        for (List<?> group : conflictGroups()) {
            if (!group.contains(t.id())) continue;
            for (Object o : group) {
                EventType other = EventType.fromId(String.valueOf(o));
                if (other != null && other != t && active.containsKey(other))
                    return Msg.stripTags(rawName(t)) + " conflicts with the active event " + Msg.stripTags(rawName(other)) + ".";
            }
        }
        if (t == EventType.AUCTION && plugin.auction().sessionRunning()) return "An auction is already running.";
        if (t == EventType.THE_HUNTED) {
            long eligible = Bukkit.getOnlinePlayers().stream().filter(WorldEvent::affected).count();
            if (eligible < plugin.getConfig().getInt("events.the_hunted.min-players", 2))
                return "Not enough eligible players online for The Hunted.";
        }
        return null;
    }

    private List<List<?>> conflictGroups() {
        List<List<?>> out = new ArrayList<>();
        for (Object o : plugin.getConfig().getList("settings.conflicts", List.of())) if (o instanceof List<?> l) out.add(l);
        return out;
    }

    public boolean startRandom(CommandSender sender, boolean roulette) {
        List<EventType> candidates = new ArrayList<>();
        for (EventType t : EventType.values()) if (enabled(t) && checkCanStart(t, false) == null) candidates.add(t);
        if (candidates.isEmpty()) {
            Msg.send(sender, "<red>No event can be started right now (all disabled, conflicting or already running).");
            return false;
        }
        EventType pick = Util.weighted(candidates, this::weight);
        return start(pick, null, roulette, new HashMap<>(), sender);
    }

    public boolean start(EventType t, Integer minutes, boolean roulette, Map<String, Object> options, CommandSender sender) {
        String reason = checkCanStart(t, false);
        if (reason != null) {
            Msg.send(sender, "<red>" + reason);
            return false;
        }
        if (roulette && plugin.getConfig().getBoolean("settings.roulette.enabled", true)) {
            rouletteRunning = true;
            try {
                new Roulette(plugin, t, () -> {
                    rouletteRunning = false;
                    doStart(t, minutes, options, sender, true);
                }).play();
            } catch (Exception e) {
                rouletteRunning = false;
                plugin.getLogger().log(Level.SEVERE, "Roulette failed", e);
                return doStart(t, minutes, options, sender, false);
            }
            return true;
        }
        return doStart(t, minutes, options, sender, false);
    }

    private boolean doStart(EventType t, Integer minutes, Map<String, Object> options, CommandSender sender, boolean rouletteShown) {
        String reason = checkCanStart(t, true);
        if (reason != null) {
            Msg.send(sender, "<red>" + reason);
            return false;
        }
        WorldEvent ev = create(t);
        long now = System.currentTimeMillis();
        int mins = minutes != null ? Math.max(1, minutes) : durationMinutes(t);
        ev.setTimes(now, now + mins * 60_000L);
        active.put(t, ev);
        boolean ok;
        try {
            ok = ev.onStart(options == null ? new HashMap<>() : options);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start event " + t.id(), e);
            ok = false;
        }
        if (!ok) {
            active.remove(t);
            Msg.send(sender, "<red>" + Msg.stripTags(rawName(t)) + " could not start (conditions not met — check console).");
            return false;
        }
        if (ev instanceof Listener l) Bukkit.getPluginManager().registerEvents(l, plugin);
        if (ev instanceof SmartiePantsEvent s) smartie = s;
        participation.put(t, new HashMap<>());
        announceStart(ev, mins, rouletteShown);
        showBar(ev);
        enforceAll();
        saveState();
        plugin.getLogger().info("Started world event " + t.id() + " (" + mins + "m)" + (sender != null ? " by " + sender.getName() : ""));
        return true;
    }

    private WorldEvent create(EventType t) {
        return switch (t) {
            case VULNERABLE -> new VulnerableEvent(plugin);
            case RAGS_TO_RICHES -> new RagsToRichesEvent(plugin);
            case GLOW_AND_BEHOLD -> new GlowEvent(plugin);
            case LOCATOR_BAR -> new LocatorBarEvent(plugin);
            case LOOT_RAIN -> new LootRainEvent(plugin);
            case THE_HUNTED -> new HuntedEvent(plugin);
            case LOOTING_3000 -> new Looting3000Event(plugin);
            case SHORT_REACH -> new ShortReachEvent(plugin);
            case BOSS -> new BossEvent(plugin);
            case MONEY_TREES -> new MoneyTreesEvent(plugin);
            case AUCTION -> new AuctionEvent(plugin);
            case SMARTIE_PANTS -> new SmartiePantsEvent(plugin);
            case TWISTS_AND_TURNS -> new MazeEvent(plugin);
            case TANKY -> new TankyEvent(plugin);
            case UNFORTUNATE -> new UnfortunateEvent(plugin);
        };
    }

    // ------------------------------------------------------------ stopping

    public boolean stop(EventType t, StopReason reason) {
        WorldEvent ev = active.remove(t);
        if (ev == null) return false;
        if (ev instanceof Listener l) HandlerList.unregisterAll(l);
        if (ev == smartie) smartie = null;
        try {
            ev.onStop(reason);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while stopping event " + t.id(), e);
        }
        hideBar(t);
        Map<UUID, Integer> part = participation.remove(t);
        announceEnd(ev, reason);
        boolean reward = reason == StopReason.EXPIRED || reason == StopReason.COMPLETED
                || (reason == StopReason.ADMIN && plugin.getConfig().getBoolean("settings.rewards-on-admin-stop", false));
        if (reward && part != null) giveParticipation(ev, part);
        enforceAll();
        saveState();
        return true;
    }

    public void stopAll(StopReason reason) {
        for (EventType t : new ArrayList<>(active.keySet())) stop(t, reason);
    }

    private void giveParticipation(WorldEvent ev, Map<UUID, Integer> part) {
        if (!plugin.getConfig().getBoolean("events." + ev.type().id() + ".participation-rewards", true)) return;
        // a resumed event only counts the time since this server start (participation isn't persisted)
        long elapsed = Math.max(1, (System.currentTimeMillis() - Math.max(ev.startMillis(), enabledAt)) / 1000);
        double pct = plugin.getConfig().getDouble("settings.participation.min-percent", 50) / 100.0;
        long required = (long) Math.ceil(elapsed * pct);
        List<UUID> eligible = new ArrayList<>();
        for (var e : part.entrySet()) {
            if (e.getValue() >= required && !ev.excludeFromParticipation(e.getKey())) eligible.add(e.getKey());
        }
        plugin.rewards().rollParticipation(ev.type(), eligible);
    }

    // ------------------------------------------------------------ heartbeat

    private void tick() {
        long now = System.currentTimeMillis();
        for (WorldEvent ev : new ArrayList<>(active.values())) {
            if (!active.containsKey(ev.type())) continue;
            if (ev.usesTimer() && now >= ev.endMillis()) {
                stop(ev.type(), StopReason.EXPIRED);
                continue;
            }
            try {
                ev.tickSecond();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error ticking event " + ev.type().id(), e);
            }
            if (active.containsKey(ev.type())) {
                updateBar(ev);
                trackParticipation(ev, now);
            }
        }
        enforceAll();
    }

    public void markActivity(Player p) {
        lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void forgetActivity(UUID uuid) {
        lastActivity.remove(uuid);
    }

    private void trackParticipation(WorldEvent ev, long now) {
        Map<UUID, Integer> map = participation.computeIfAbsent(ev.type(), k -> new HashMap<>());
        long afkMs = plugin.getConfig().getLong("settings.participation.afk-seconds", 180) * 1000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!WorldEvent.affected(p)) continue;
            long last = lastActivity.getOrDefault(p.getUniqueId(), now);
            if (now - last > afkMs) continue;
            map.merge(p.getUniqueId(), 1, Integer::sum);
        }
    }

    // ------------------------------------------------------------ reconciliation

    public void enforceAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                enforce(p);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not enforce event state for " + p.getName(), e);
            }
        }
    }

    public Map<PotionEffectType, PotionEffect> desiredEffects(Player p) {
        Map<PotionEffectType, PotionEffect> out = new HashMap<>();
        if (!WorldEvent.affected(p)) return out;
        for (WorldEvent ev : active.values()) {
            Map<PotionEffectType, PotionEffect> part = new HashMap<>();
            ev.effects(p, part);
            for (var e : part.entrySet()) {
                PotionEffect cur = out.get(e.getKey());
                if (cur == null || e.getValue().getAmplifier() > cur.getAmplifier()) out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** Makes the player's health/reach/effects/glow match whatever the active events require. */
    public void enforce(Player p) {
        boolean affected = WorldEvent.affected(p);
        double hp = -1, entityReach = -1, blockReach = -1;
        int pri = Integer.MIN_VALUE;
        boolean glow = false;
        if (affected) {
            for (WorldEvent ev : active.values()) {
                double h = ev.maxHealth(p);
                if (h > 0 && ev.healthPriority() > pri) {
                    hp = h;
                    pri = ev.healthPriority();
                }
                double er = ev.entityReach(p);
                if (er >= 0) entityReach = entityReach < 0 ? er : Math.min(entityReach, er);
                double br = ev.blockReach(p);
                if (br >= 0) blockReach = blockReach < 0 ? br : Math.min(blockReach, br);
                if (ev.glow(p)) glow = true;
            }
        }

        // health
        Util.applyAttribute(p, Attribute.MAX_HEALTH, healthKey, hp);
        AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null && !p.isDead() && p.getHealth() > maxHp.getValue()) p.setHealth(Math.max(0.5, maxHp.getValue()));

        // reach
        Util.applyAttribute(p, Attribute.ENTITY_INTERACTION_RANGE, entityReachKey, entityReach);
        Util.applyAttribute(p, Attribute.BLOCK_INTERACTION_RANGE, blockReachKey, blockReach);

        // potion effects
        if (!p.isDead()) {
            Map<PotionEffectType, PotionEffect> desired = desiredEffects(p);
            for (PotionEffectType t : managedEffects) {
                PotionEffect want = desired.get(t);
                PotionEffect cur = p.getPotionEffect(t);
                if (want != null) {
                    if (cur == null || cur.getAmplifier() != want.getAmplifier() || !cur.isInfinite()) {
                        if (cur != null) p.removePotionEffect(t);
                        p.addPotionEffect(want);
                    }
                } else if (cur != null && cur.isInfinite()) {
                    p.removePotionEffect(t);
                }
            }
        }

        // glowing (entity flag — milk cannot touch it)
        var pdc = p.getPersistentDataContainer();
        if (glow) {
            if (!p.isGlowing()) p.setGlowing(true);
            if (!pdc.has(glowMarkKey)) pdc.set(glowMarkKey, PersistentDataType.BYTE, (byte) 1);
        } else if (pdc.has(glowMarkKey)) {
            p.setGlowing(false);
            pdc.remove(glowMarkKey);
        }

        // team cleanup for the hunted when no hunt is running
        if (!active.containsKey(EventType.THE_HUNTED)) HuntedEvent.cleanupTeam(plugin, p);

        for (WorldEvent ev : active.values()) ev.enforce(p);
    }

    // ------------------------------------------------------------ player hooks

    public void handleJoin(Player p) {
        showBars(p);
        if (!active.containsKey(EventType.TWISTS_AND_TURNS)) MazeEvent.restoreIfPending(plugin, p);
        for (WorldEvent ev : new ArrayList<>(active.values())) {
            try {
                ev.onJoin(p);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "onJoin failed for " + ev.type().id(), e);
            }
        }
        enforce(p);
    }

    public void handleQuit(Player p) {
        for (WorldEvent ev : new ArrayList<>(active.values())) {
            try {
                ev.onQuit(p);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "onQuit failed for " + ev.type().id(), e);
            }
        }
    }

    public void handleRespawn(Player p) {
        for (WorldEvent ev : new ArrayList<>(active.values())) ev.onRespawn(p);
        enforce(p);
    }

    // ------------------------------------------------------------ announcements

    private void announceStart(WorldEvent ev, int mins, boolean rouletteShown) {
        EventType t = ev.type();
        if (!rouletteShown) {
            Msg.titleAll(Msg.mm(plugin.getConfig().getString("settings.roulette.title", "<gradient:#ff4e50:#f9d423><bold>WORLD EVENT</bold></gradient>")),
                    name(t), 200, 3500, 800);
            Msg.playAll(Msg.sound(plugin.getConfig().getString("settings.roulette.land-sound", "minecraft:ui.toast.challenge_complete"), 1f, 1f));
        }
        String dur = ev.usesTimer() ? Msg.minutes(mins) : "until finished";
        Msg.broadcast(Msg.box("gold", List.of(
                "<gradient:#ff4e50:#f9d423><bold>  ⚡ WORLD EVENT ⚡</bold></gradient>",
                "  <name>",
                "  <gray><desc>",
                "  <dark_gray>Duration: <white><dur>"
        ), Placeholder.parsed("name", rawName(t)), Placeholder.parsed("desc", description(t)), Placeholder.unparsed("dur", dur)));
    }

    private void announceEnd(WorldEvent ev, StopReason reason) {
        String why = switch (reason) {
            case EXPIRED, COMPLETED -> "has ended!";
            case ADMIN -> "was stopped by an admin.";
            case SHUTDOWN -> "was ended (server restart).";
            case RESTART -> "expired while the server was offline.";
        };
        if (reason == StopReason.RESTART) return;
        Msg.broadcast("<name> <gray><why>", Placeholder.parsed("name", rawName(ev.type())), Placeholder.unparsed("why", why));
    }

    // ------------------------------------------------------------ boss bars

    private boolean barsEnabled() {
        return plugin.getConfig().getBoolean("settings.bossbar", true);
    }

    private BossBar.Color barColor(EventType t) {
        try {
            return BossBar.Color.valueOf(plugin.getConfig().getString("events." + t.id() + ".bossbar-color", "RED").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }

    private void showBar(WorldEvent ev) {
        if (!barsEnabled()) return;
        BossBar bar = BossBar.bossBar(name(ev.type()), 1f, barColor(ev.type()), BossBar.Overlay.PROGRESS);
        bars.put(ev.type(), bar);
        updateBar(ev);
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
    }

    private void showBars(Player p) {
        for (BossBar bar : bars.values()) p.showBossBar(bar);
    }

    private void hideBar(EventType t) {
        BossBar bar = bars.remove(t);
        if (bar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    private void updateBar(WorldEvent ev) {
        BossBar bar = bars.get(ev.type());
        if (bar == null) return;
        Component custom = ev.bossBarName();
        if (custom != null) bar.name(custom);
        else bar.name(Msg.mm(rawName(ev.type()) + "<reset> <dark_gray>— <white>" + Msg.time(ev.remainingSeconds())));
        float progress = ev.bossBarProgress();
        if (progress < 0) {
            long total = Math.max(1, ev.endMillis() - ev.startMillis());
            progress = (float) Math.max(0, Math.min(1, (ev.endMillis() - System.currentTimeMillis()) / (double) total));
        }
        bar.progress(Math.max(0f, Math.min(1f, progress)));
    }

    // ------------------------------------------------------------ persistence

    public void saveState() {
        var yml = plugin.data().yml();
        yml.set("active-events", null);
        for (WorldEvent ev : active.values()) {
            if (!ev.canResume() && ev.type() != EventType.TWISTS_AND_TURNS) continue;
            ConfigurationSection s = yml.createSection("active-events." + ev.type().id());
            s.set("start", ev.startMillis());
            s.set("end", ev.endMillis());
            try {
                ev.save(s.createSection("data"));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not save state of " + ev.type().id(), e);
            }
        }
        plugin.data().save();
    }

    public void resumeFromData() {
        ConfigurationSection root = plugin.data().yml().getConfigurationSection("active-events");
        if (root == null) return;
        long now = System.currentTimeMillis();
        for (String id : root.getKeys(false)) {
            EventType t = EventType.fromId(id);
            ConfigurationSection s = root.getConfigurationSection(id);
            if (t == null || s == null) continue;
            WorldEvent ev = create(t);
            ev.setTimes(s.getLong("start"), s.getLong("end"));
            ConfigurationSection data = s.getConfigurationSection("data");
            boolean loaded;
            try {
                loaded = ev.load(data != null ? data : s.createSection("data"));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not load saved state of " + id, e);
                loaded = false;
            }
            boolean resume = loaded && ev.canResume() && (!ev.usesTimer() || ev.endMillis() > now);
            if (resume) {
                active.put(t, ev);
                if (ev instanceof Listener l) Bukkit.getPluginManager().registerEvents(l, plugin);
                participation.put(t, new HashMap<>());
                try {
                    ev.onResume();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "onResume failed for " + id, e);
                }
                showBar(ev);
                plugin.getLogger().info("Resumed world event " + id + " (" + Msg.time(ev.remainingSeconds()) + " left).");
            } else {
                try {
                    if (loaded) ev.onStop(StopReason.RESTART);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Cleanup failed for " + id, e);
                }
                plugin.getLogger().info("World event " + id + " was not resumed (expired or not resumable) and was cleaned up.");
            }
        }
        saveState();
    }

    public void shutdown() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        for (WorldEvent ev : new ArrayList<>(active.values())) {
            if (!ev.canResume()) stop(ev.type(), StopReason.SHUTDOWN);
        }
        for (WorldEvent ev : active.values()) if (ev instanceof Listener l) HandlerList.unregisterAll(l);
        for (EventType t : new ArrayList<>(bars.keySet())) hideBar(t);
        saveState();
    }
}

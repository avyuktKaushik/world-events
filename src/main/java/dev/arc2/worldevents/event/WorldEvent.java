package dev.arc2.worldevents.event;

import dev.arc2.worldevents.WorldEventsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for every world event.
 * <p>
 * Persistent effects (health, reach, potion effects, glowing) are NOT applied directly by events —
 * events only <i>declare</i> what a player should have via the hook methods, and the
 * {@link dev.arc2.worldevents.core.EventManager} reconciles every player every second, on join and on respawn.
 * That is what makes them impossible to remove with milk, totems, dying or relogging.
 * <p>
 * If a subclass implements {@link org.bukkit.event.Listener} it is registered only while the event runs.
 */
public abstract class WorldEvent {

    protected final WorldEventsPlugin plugin;
    protected final EventType type;
    protected long startMillis;
    protected long endMillis;

    protected WorldEvent(WorldEventsPlugin plugin, EventType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public EventType type() { return type; }
    public long startMillis() { return startMillis; }
    public long endMillis() { return endMillis; }

    public void setTimes(long start, long end) {
        this.startMillis = start;
        this.endMillis = end;
    }

    public long remainingSeconds() {
        return Math.max(0, (endMillis - System.currentTimeMillis()) / 1000);
    }

    protected ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("events." + type.id());
        return s != null ? s : new MemoryConfiguration();
    }

    @SuppressWarnings("unchecked")
    protected List<Map<?, ?>> cfgMapList(String path) {
        return cfg().getMapList(path);
    }

    /** Players events apply to: online, survival/adventure, without worldevents.bypass. */
    public static boolean affected(Player p) {
        if (p == null || !p.isOnline()) return false;
        if (p.hasPermission("worldevents.bypass")) return false;
        GameMode g = p.getGameMode();
        return g == GameMode.SURVIVAL || g == GameMode.ADVENTURE;
    }

    /** Finish the event on the next tick (safe to call from anywhere). */
    protected void complete() {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.events().stop(type, StopReason.COMPLETED));
    }

    // ------------------------------------------------------------ lifecycle

    /** @return false to abort the start (e.g. not enough players). */
    public abstract boolean onStart(Map<String, Object> options);

    public void onStop(StopReason reason) {}

    /** Called once per second while running. */
    public void tickSecond() {}

    /** false = the event ends itself (complete()) instead of on a timer. */
    public boolean usesTimer() { return true; }

    /** Can this event continue after a server restart? */
    public boolean canResume() { return true; }

    public void save(ConfigurationSection s) {}

    /** @return false if the saved data is unusable. */
    public boolean load(ConfigurationSection s) { return true; }

    /** Called after a successful resume from data.yml. */
    public void onResume() {}

    // ------------------------------------------------------------ declarative player state

    /** Desired max health (in HP) or -1. Highest {@link #healthPriority()} wins. */
    public double maxHealth(Player p) { return -1; }

    public int healthPriority() { return 0; }

    public void effects(Player p, Map<PotionEffectType, PotionEffect> out) {}

    public boolean glow(Player p) { return false; }

    public double entityReach(Player p) { return -1; }

    public double blockReach(Player p) { return -1; }

    /** Extra per-second enforcement (teams, gamemode...). Called for every online player. */
    public void enforce(Player p) {}

    // ------------------------------------------------------------ player hooks

    public void onJoin(Player p) {}

    public void onQuit(Player p) {}

    public void onRespawn(Player p) {}

    public boolean excludeFromParticipation(UUID uuid) { return false; }

    // ------------------------------------------------------------ boss bar

    /** Custom boss bar text, or null for "Name — mm:ss". */
    public Component bossBarName() { return null; }

    /** 0..1 or negative for time-based progress. */
    public float bossBarProgress() { return -1f; }
}

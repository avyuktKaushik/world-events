package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/** Turns the locator_bar gamerule on in every world and restores the previous value afterwards. */
public final class LocatorBarEvent extends WorldEvent {

    private final Map<String, Boolean> previous = new HashMap<>();

    public LocatorBarEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.LOCATOR_BAR);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        GameRule<Boolean> rule = Util.locatorBarRule();
        for (World w : Bukkit.getWorlds()) {
            if (rule != null) {
                Boolean prev = w.getGameRuleValue(rule);
                previous.put(w.getName(), prev != null && prev);
                w.setGameRule(rule, true);
            } else {
                previous.put(w.getName(), cfg().getBoolean("fallback-previous-value", false));
                setByCommand(w, true);
            }
        }
        return true;
    }

    @Override
    public void onResume() {
        // make sure it's still on after a restart
        GameRule<Boolean> rule = Util.locatorBarRule();
        for (World w : Bukkit.getWorlds()) {
            if (rule != null) w.setGameRule(rule, true);
            else setByCommand(w, true);
        }
    }

    @Override
    public void onStop(StopReason reason) {
        GameRule<Boolean> rule = Util.locatorBarRule();
        for (World w : Bukkit.getWorlds()) {
            boolean prev = previous.getOrDefault(w.getName(), cfg().getBoolean("fallback-previous-value", false));
            if (rule != null) w.setGameRule(rule, prev);
            else setByCommand(w, prev);
        }
    }

    private void setByCommand(World w, boolean value) {
        String cmd = "execute in " + w.getKey().asString() + " run gamerule " + cfg().getString("gamerule-name", "locator_bar") + " " + value;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    @Override
    public void save(ConfigurationSection s) {
        for (var e : previous.entrySet()) s.set("previous." + e.getKey(), e.getValue());
    }

    @Override
    public boolean load(ConfigurationSection s) {
        ConfigurationSection p = s.getConfigurationSection("previous");
        if (p != null) for (String k : p.getKeys(false)) previous.put(k, p.getBoolean(k));
        return true;
    }
}

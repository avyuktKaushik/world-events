package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Natural leaves broken by a player have a ~45% chance to drop a golden apple. Logs drop nothing extra.
 * <p>Anti-abuse: player-placed leaves (persistent=true) never drop, trees grown during the event
 * (bonemeal farms) never drop, leaf decay doesn't count, and there is a per-player cap.</p>
 */
public final class MoneyTreesEvent extends WorldEvent implements Listener {

    private final Map<UUID, Set<Long>> grown = new HashMap<>();
    private final Map<UUID, Integer> given = new HashMap<>();

    public MoneyTreesEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.MONEY_TREES);
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        return true;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        Set<Long> set = grown.computeIfAbsent(e.getWorld().getUID(), k -> new HashSet<>());
        for (BlockState s : e.getBlocks()) {
            if (Tag.LEAVES.isTagged(s.getType())) set.add(pack(s.getX(), s.getY(), s.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeafBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!Tag.LEAVES.isTagged(b.getType())) return;
        if (b.getBlockData() instanceof Leaves leaves && leaves.isPersistent()) return;
        Player p = e.getPlayer();
        if (!affected(p)) return;
        Set<Long> set = grown.get(b.getWorld().getUID());
        if (set != null && set.contains(pack(b.getX(), b.getY(), b.getZ()))) return;

        int cap = cfg().getInt("max-per-player", 64);
        int got = given.getOrDefault(p.getUniqueId(), 0);
        if (cap > 0 && got >= cap) return;
        if (!Util.chance(cfg().getDouble("drop-chance", 0.45))) return;

        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.GOLDEN_APPLE));
        given.put(p.getUniqueId(), got + 1);
        if (cap > 0 && got + 1 >= cap) {
            Msg.send(p, "<gold>You've collected the maximum of <white>" + cap + "</white> golden apples from trees this event!");
        }
    }
}

package dev.arc2.worldevents.core;

import dev.arc2.worldevents.WorldEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Remembers every ore block a player ever placed (or pushed with a piston), stored in the chunk's
 * persistent data so it survives restarts. Looting 3000 only multiplies ores that are NOT in here,
 * i.e. naturally generated ore — so placing an ore back down can never be used to dupe.
 * Tracking runs all the time, not just during the event.
 */
public final class PlacedBlockTracker implements Listener {

    private final WorldEventsPlugin plugin;
    private final NamespacedKey key;
    private final Set<Material> ores = EnumSet.noneOf(Material.class);

    public PlacedBlockTracker(WorldEventsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "placed_ores");
        reload();
    }

    public void reload() {
        ores.clear();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) continue;
            if (m.name().endsWith("_ORE")) ores.add(m);
        }
        for (String s : plugin.getConfig().getStringList("events.looting_3000.extra-ores")) {
            Material m = Material.matchMaterial(s);
            if (m != null) ores.add(m);
        }
        for (String s : plugin.getConfig().getStringList("events.looting_3000.excluded-ores")) {
            Material m = Material.matchMaterial(s);
            if (m != null) ores.remove(m);
        }
    }

    public boolean isOre(Material m) {
        return ores.contains(m);
    }

    private static int pack(Block b) {
        return (b.getX() & 15) | ((b.getZ() & 15) << 4) | ((b.getY() + 2048) << 8);
    }

    private Set<Integer> read(Chunk c) {
        int[] arr = c.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        Set<Integer> set = new LinkedHashSet<>();
        if (arr != null) for (int i : arr) set.add(i);
        return set;
    }

    private void write(Chunk c, Set<Integer> set) {
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (set.isEmpty()) {
            pdc.remove(key);
            return;
        }
        int[] arr = new int[set.size()];
        int i = 0;
        for (int v : set) arr[i++] = v;
        pdc.set(key, PersistentDataType.INTEGER_ARRAY, arr);
    }

    public boolean isPlaced(Block b) {
        return read(b.getChunk()).contains(pack(b));
    }

    public void mark(Block b) {
        Chunk c = b.getChunk();
        Set<Integer> s = read(c);
        if (s.add(pack(b))) write(c, s);
    }

    public void unmark(Block b) {
        Chunk c = b.getChunk();
        Set<Integer> s = read(c);
        if (s.remove(pack(b))) write(c, s);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isOre(e.getBlockPlaced().getType())) mark(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isOre(b.getType())) return;
        // removed next tick so the drop handler (same tick) still sees the "placed" flag
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isOre(b.getType())) unmark(b);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        markMoved(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        markMoved(e.getBlocks(), e.getDirection());
    }

    /** A piston-moved ore counts as placed (both possible destinations are marked to be safe). */
    private void markMoved(List<Block> blocks, BlockFace dir) {
        for (Block b : blocks) {
            if (!isOre(b.getType())) continue;
            mark(b.getRelative(dir));
            mark(b.getRelative(dir.getOppositeFace()));
            mark(b);
        }
    }

    public String describe() {
        return ores.size() + " ore types tracked (" + String.join(", ", ores.stream().limit(4).map(m -> m.name().toLowerCase(Locale.ROOT)).toList()) + "...)";
    }
}

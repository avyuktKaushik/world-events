package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.core.DataStore;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Twists and Turns: a fresh maze is generated (high in the sky by default), everyone gets
 * Blindness + Adventure mode and is teleported in. Dead ends contain loot chests, the first player
 * to reach the emerald exit wins.
 *
 * <p>Escape-proof: bedrock walls, a full barrier ceiling directly on top of the walls (no room to
 * jump on them), ender pearls / chorus fruit / /home /tpa etc. are blocked, elytra gliding is blocked,
 * leaving the box teleports you back, natural mob spawns are blocked inside the maze.
 * Everyone's original location and gamemode are saved to disk and restored afterwards
 * (even after a crash or if they were offline when the maze ended).</p>
 */
public final class MazeEvent extends WorldEvent implements Listener {

    private World world;
    private int ox, oy, oz;
    private int cells, pw, wallHeight, size;
    private boolean[][] open;
    private Location start;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> finished = new HashSet<>();
    private final Set<UUID> allowTeleport = new HashSet<>();
    private final Set<Long> tickets = new HashSet<>();
    private UUID winner;

    public MazeEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.TWISTS_AND_TURNS);
    }

    @Override
    public boolean canResume() {
        return false;
    }

    // ------------------------------------------------------------ start

    @Override
    public boolean onStart(Map<String, Object> options) {
        world = Bukkit.getWorld(cfg().getString("world", "world"));
        if (world == null) {
            plugin.getLogger().warning("Maze world '" + cfg().getString("world") + "' does not exist.");
            return false;
        }
        cells = Math.max(5, Math.min(40, cfg().getInt("cells", 15)));
        pw = Math.max(1, Math.min(4, cfg().getInt("path-width", 2)));
        wallHeight = Math.max(2, Math.min(6, cfg().getInt("wall-height", 3)));
        ox = cfg().getInt("origin.x", 10000);
        oy = cfg().getInt("origin.y", 290);
        oz = cfg().getInt("origin.z", 10000);
        if (oy + wallHeight + 1 >= world.getMaxHeight() || oy <= world.getMinHeight()) {
            plugin.getLogger().warning("Maze origin Y " + oy + " doesn't fit in the world height.");
            return false;
        }
        size = (cells * 2 + 1) * pw;

        generate();
        addTickets();
        build();

        int sx = ox + pw + pw / 2;
        int sz = oz + pw + pw / 2;
        start = new Location(world, sx + (pw % 2 == 1 ? 0.5 : 0.0), oy + 1, sz + (pw % 2 == 1 ? 0.5 : 0.0));

        Msg.broadcast(Msg.box("dark_green", List.of(
                "<dark_green><bold>  ✦ TWISTS AND TURNS ✦</bold>",
                "  <gray>Everyone has been thrown into a <green>maze</green> — blind!",
                "  <gray>Loot chests hide in the dead ends.",
                "  <gold>First to reach the <green>emerald exit</green> wins!"
        )));
        for (Player p : Bukkit.getOnlinePlayers()) if (affected(p)) enter(p);
        return true;
    }

    private void generate() {
        int n = cells * 2 + 1;
        open = new boolean[n][n];
        boolean[][] visited = new boolean[cells][cells];
        Deque<int[]> stack = new ArrayDeque<>();
        visited[0][0] = true;
        open[1][1] = true;
        stack.push(new int[]{0, 0});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<int[]> options = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < cells && nz < cells && !visited[nx][nz]) options.add(new int[]{nx, nz, d[0], d[1]});
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] pick = options.get(Util.rnd().nextInt(options.size()));
            open[2 * cur[0] + 1 + pick[2]][2 * cur[1] + 1 + pick[3]] = true;
            open[2 * pick[0] + 1][2 * pick[1] + 1] = true;
            visited[pick[0]][pick[1]] = true;
            stack.push(new int[]{pick[0], pick[1]});
        }
        // a few loops so it's not a single corridor
        double loops = cfg().getDouble("extra-openings", 0.06);
        for (int x = 1; x < n - 1; x++) {
            for (int z = 1; z < n - 1; z++) {
                if (open[x][z] || (x % 2 == 1 && z % 2 == 1) || (x % 2 == 0 && z % 2 == 0)) continue;
                if (Util.chance(loops)) open[x][z] = true;
            }
        }
    }

    private void addTickets() {
        for (int cx = ox >> 4; cx <= (ox + size) >> 4; cx++) {
            for (int cz = oz >> 4; cz <= (oz + size) >> 4; cz++) {
                world.addPluginChunkTicket(cx, cz, plugin);
                tickets.add(((long) cx << 32) | (cz & 0xffffffffL));
            }
        }
    }

    private Material mat(String path, Material def) {
        Material m = Material.matchMaterial(cfg().getString(path, def.name()));
        return m == null || !m.isBlock() ? def : m;
    }

    private void build() {
        Material floor = mat("floor-material", Material.BEDROCK);
        Material wall = mat("wall-material", Material.BEDROCK);
        Material ceiling = mat("ceiling-material", Material.BARRIER);
        Material exit = mat("exit-material", Material.EMERALD_BLOCK);
        int n = cells * 2 + 1;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int ux = x / pw, uz = z / pw;
                boolean isWall = !open[ux][uz];
                boolean isExit = ux == n - 2 && uz == n - 2;
                world.getBlockAt(ox + x, oy, oz + z).setType(isExit ? exit : floor, false);
                for (int y = 1; y <= wallHeight; y++) world.getBlockAt(ox + x, oy + y, oz + z).setType(isWall ? wall : Material.AIR, false);
                world.getBlockAt(ox + x, oy + wallHeight + 1, oz + z).setType(ceiling, false);
            }
        }
        // light so the maze isn't a mob spawner (blindness hides it anyway)
        placeChests(n);
    }

    private void placeChests(int n) {
        double chance = cfg().getDouble("chest-chance", 0.7);
        int[] rolls = Util.parseRange(cfg().get("rolls-per-chest"), 4);
        List<Map<?, ?>> loot = cfgMapList("chest-loot");
        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                if ((cx == 0 && cz == 0) || (cx == cells - 1 && cz == cells - 1)) continue;
                int ux = 2 * cx + 1, uz = 2 * cz + 1;
                int openN = 0;
                if (open[ux + 1][uz]) openN++;
                if (open[ux - 1][uz]) openN++;
                if (open[ux][uz + 1]) openN++;
                if (open[ux][uz - 1]) openN++;
                if (openN != 1 || !Util.chance(chance)) continue;
                Block b = world.getBlockAt(ox + ux * pw, oy + 1, oz + uz * pw);
                b.setType(Material.CHEST, false);
                if (b.getState() instanceof Chest chest) {
                    List<ItemStack> items = plugin.rewards().sanitize(plugin.rewards().roll(loot, Util.range(rolls[0], rolls[1])));
                    List<Integer> slots = new ArrayList<>();
                    for (int i = 0; i < 27; i++) slots.add(i);
                    Collections.shuffle(slots);
                    for (int i = 0; i < items.size() && i < 27; i++) chest.getSnapshotInventory().setItem(slots.get(i), items.get(i));
                    chest.update(true, false);
                }
            }
        }
    }

    // ------------------------------------------------------------ enter / leave

    private boolean inside(Location l) {
        if (l == null || world == null || !world.equals(l.getWorld())) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= ox && x < ox + size && z >= oz && z < oz + size && y >= oy && y <= oy + wallHeight + 1;
    }

    private boolean inExit(Location l) {
        if (!inside(l)) return false;
        int n = cells * 2 + 1;
        int ux = (l.getBlockX() - ox) / pw, uz = (l.getBlockZ() - oz) / pw;
        return ux == n - 2 && uz == n - 2;
    }

    private void teleport(Player p, Location to) {
        allowTeleport.add(p.getUniqueId());
        try {
            if (p.isInsideVehicle()) p.leaveVehicle();
            p.setFallDistance(0);
            p.setVelocity(new Vector());
            p.teleport(to);
        } finally {
            allowTeleport.remove(p.getUniqueId());
        }
    }

    private void enter(Player p) {
        if (finished.contains(p.getUniqueId())) return;
        if (!plugin.data().hasRestore(p.getUniqueId()))
            plugin.data().setRestore(p.getUniqueId(), p.getLocation(), p.getGameMode());
        participants.add(p.getUniqueId());
        if (p.isGliding()) p.setGliding(false);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setGameMode(GameMode.ADVENTURE);
        if (!inside(p.getLocation())) teleport(p, start);
        Msg.play(p, Msg.sound("minecraft:entity.enderman.teleport", 1f, 0.8f));
    }

    private void leave(Player p) {
        participants.remove(p.getUniqueId());
        DataStore.Restore r = plugin.data().takeRestore(p.getUniqueId());
        Location back = r != null && r.location() != null ? r.location() : p.getWorld().getSpawnLocation();
        if (r != null && r.location() == null) back = Bukkit.getWorlds().get(0).getSpawnLocation();
        teleport(p, back);
        p.setGameMode(r != null ? r.gameMode() : GameMode.SURVIVAL);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    /** Called on join when the maze is NOT running — puts people back where they were. */
    public static void restoreIfPending(WorldEventsPlugin plugin, Player p) {
        if (!plugin.data().hasRestore(p.getUniqueId())) return;
        DataStore.Restore r = plugin.data().takeRestore(p.getUniqueId());
        if (r == null) return;
        Location back = r.location() != null ? r.location() : Bukkit.getWorlds().get(0).getSpawnLocation();
        p.teleport(back);
        p.setGameMode(r.gameMode());
        PotionEffect blind = p.getPotionEffect(PotionEffectType.BLINDNESS);
        if (blind != null && blind.isInfinite()) p.removePotionEffect(PotionEffectType.BLINDNESS);
        Msg.send(p, "<gray>The maze ended while you were away — you've been returned to where you were.");
    }

    private void finish(Player p) {
        if (!participants.contains(p.getUniqueId()) || finished.contains(p.getUniqueId())) return;
        finished.add(p.getUniqueId());
        if (winner == null) {
            winner = p.getUniqueId();
            plugin.rewards().give(p.getUniqueId(), plugin.rewards().all(cfgMapList("winner-rewards")));
            Msg.broadcast(Msg.box("gold", List.of(
                    "<gold><bold>  ✦ MAZE CONQUERED ✦</bold>",
                    "  <yellow><name></yellow> <gray>found the exit first and wins the maze reward!"
            ), Placeholder.unparsed("name", p.getName())));
            Msg.playAll(Msg.sound("minecraft:ui.toast.challenge_complete", 1f, 1f));
            plugin.rewards().rollJackpot(p.getUniqueId(), cfg().getDouble("winner-jackpot-chance", 0.35));
        } else {
            plugin.rewards().give(p.getUniqueId(), plugin.rewards().all(cfgMapList("finisher-rewards")));
            Msg.broadcast("<dark_green>✦</dark_green> <yellow>" + Msg.escape(p.getName()) + "</yellow> <gray>escaped the maze! <dark_gray>(#" + finished.size() + ")");
        }
        leave(p);
        boolean anyLeft = false;
        for (UUID id : participants) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && other.isOnline()) {
                anyLeft = true;
                break;
            }
        }
        if (!anyLeft) complete();
    }

    // ------------------------------------------------------------ declarative state

    @Override
    public void effects(Player p, Map<PotionEffectType, PotionEffect> out) {
        if (participants.contains(p.getUniqueId()))
            out.put(PotionEffectType.BLINDNESS, new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    @Override
    public void enforce(Player p) {
        if (!participants.contains(p.getUniqueId()) || p.hasPermission("worldevents.bypass")) return;
        if (p.getGameMode() != GameMode.ADVENTURE && !p.isDead()) p.setGameMode(GameMode.ADVENTURE);
        if (p.getAllowFlight()) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        if (!p.isDead() && !inside(p.getLocation())) teleport(p, start);
    }

    @Override
    public void tickSecond() {
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && !p.isDead() && inExit(p.getLocation())) finish(p);
        }
    }

    @Override
    public void onJoin(Player p) {
        if (finished.contains(p.getUniqueId())) {
            restoreIfPending(plugin, p);
            return;
        }
        if (participants.contains(p.getUniqueId()) || affected(p)) enter(p);
    }

    @Override
    public Component bossBarName() {
        return Msg.mm("<dark_green><bold>Twists and Turns</bold></dark_green> <gray>— escaped: <white>" + finished.size()
                + "</white> — <white>" + Msg.time(remainingSeconds()));
    }

    // ------------------------------------------------------------ listeners

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!participants.contains(p.getUniqueId())) return;
        Location to = e.getTo();
        if (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockY() == to.getBlockY() && e.getFrom().getBlockZ() == to.getBlockZ()) return;
        if (!inside(to)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (participants.contains(p.getUniqueId()) && !inside(p.getLocation())) teleport(p, start);
            });
            return;
        }
        if (inExit(to)) Bukkit.getScheduler().runTask(plugin, () -> finish(p));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (allowTeleport.contains(p.getUniqueId())) return;
        if (participants.contains(p.getUniqueId())) {
            if (p.hasPermission("worldevents.admin") && e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) return;
            e.setCancelled(true);
            Msg.send(p, "<red>You can't teleport out of the maze!");
            return;
        }
        if (inside(e.getTo()) && !p.hasPermission("worldevents.admin")) {
            e.setCancelled(true);
            Msg.send(p, "<red>You can't teleport into the maze.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!participants.contains(p.getUniqueId()) || p.hasPermission("worldevents.admin")) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        for (String allowed : cfg().getStringList("allowed-commands")) if (allowed.equalsIgnoreCase(label)) return;
        e.setCancelled(true);
        Msg.send(p, "<red>You can't use that command inside the maze.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (cfg().getBoolean("allow-pvp", false)) return;
        if (!(e.getEntity() instanceof Player victim) || !participants.contains(victim.getUniqueId())) return;
        Entity d = e.getDamager();
        Player attacker = d instanceof Player p ? p : (d instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null);
        if (attacker != null && participants.contains(attacker.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.isGliding() && e.getEntity() instanceof Player p && participants.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM || e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) return;
        if (inside(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inside(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("worldevents.admin")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inside(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("worldevents.admin")) e.setCancelled(true);
    }

    /** Items would be lost forever when the maze is cleared, so deaths inside keep inventory. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (!participants.contains(e.getEntity().getUniqueId()) || !cfg().getBoolean("keep-inventory-on-death", true)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawnEvent(PlayerRespawnEvent e) {
        if (participants.contains(e.getPlayer().getUniqueId()) && cfg().getBoolean("respawn-in-maze", true) && start != null)
            e.setRespawnLocation(start);
    }

    // ------------------------------------------------------------ stop / cleanup

    @Override
    public void onStop(StopReason reason) {
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) leave(p);
        }
        participants.clear();
        clearRegion();
        for (long key : tickets) world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
        tickets.clear();
    }

    private void clearRegion() {
        if (world == null || size <= 0) return;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y <= wallHeight + 1; y++) {
                    Block b = world.getBlockAt(ox + x, oy + y, oz + z);
                    Material t = b.getType();
                    if (t == Material.AIR) continue;
                    if (t == Material.CHEST && b.getState() instanceof Chest chest) chest.getBlockInventory().clear();
                    b.setType(Material.AIR, false);
                }
            }
        }
        BoundingBox box = new BoundingBox(ox, oy - 1, oz, ox + size, oy + wallHeight + 3, oz + size);
        for (Entity en : world.getNearbyEntities(box)) if (!(en instanceof Player)) en.remove();
    }

    // ------------------------------------------------------------ persistence (crash cleanup only)

    @Override
    public void save(ConfigurationSection s) {
        s.set("world", world == null ? null : world.getName());
        s.set("x", ox);
        s.set("y", oy);
        s.set("z", oz);
        s.set("size", size);
        s.set("wall-height", wallHeight);
    }

    @Override
    public boolean load(ConfigurationSection s) {
        world = Bukkit.getWorld(s.getString("world", ""));
        ox = s.getInt("x");
        oy = s.getInt("y");
        oz = s.getInt("z");
        size = s.getInt("size");
        wallHeight = s.getInt("wall-height", 3);
        return world != null && size > 0;
    }
}

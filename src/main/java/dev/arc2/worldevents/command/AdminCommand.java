package dev.arc2.worldevents.command;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.auction.AuctionLot;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.events.BossEvent;
import dev.arc2.worldevents.gui.DurationGui;
import dev.arc2.worldevents.util.Msg;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * /worldevent — everything an admin needs.
 * <pre>
 * /worldevent random [noroulette]
 * /worldevent start &lt;event&gt; [minutes] [noroulette]
 * /worldevent stop &lt;event|all&gt;
 * /worldevent list | gui | reload
 * /worldevent hunted &lt;player&gt; [minutes]
 * /worldevent lootrain [here] [minutes]
 * /worldevent boss [x z]   |   /worldevent bosstp
 * /worldevent maze setorigin
 * /worldevent auction start|now [lots]|item &lt;price&gt;|text &lt;price&gt; &lt;description&gt;|skip|cancel
 * /worldevent balance &lt;player&gt; [set|add|take &lt;amount&gt;]
 * /worldevent givejackpot &lt;player&gt;
 * </pre>
 */
public final class AdminCommand implements BasicCommand {

    private static final String PERM = "worldevents.admin";
    private static final List<String> SUBS = List.of("help", "random", "start", "stop", "list", "gui", "reload",
            "hunted", "lootrain", "boss", "bosstp", "maze", "auction", "balance", "givejackpot");

    private final WorldEventsPlugin plugin;

    public AdminCommand(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender s = source.getSender();
        if (!s.hasPermission(PERM)) {
            Msg.send(s, "<red>You don't have permission.");
            return;
        }
        if (args.length == 0) {
            help(s);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "random" -> plugin.events().startRandom(s, !hasFlag(args, "noroulette"));
            case "start" -> start(s, args);
            case "stop" -> stop(s, args);
            case "list" -> list(s);
            case "gui", "config", "durations" -> {
                if (s instanceof Player p) new DurationGui(plugin).open(p);
                else Msg.send(s, "<red>Only players can open the GUI.");
            }
            case "reload" -> {
                plugin.reload();
                Msg.send(s, "<green>WorldEvents config reloaded.");
            }
            case "hunted" -> hunted(s, args);
            case "lootrain" -> lootRain(s, args);
            case "boss" -> boss(s, args);
            case "bosstp" -> bossTp(s);
            case "maze" -> maze(s, args);
            case "auction" -> auction(s, args);
            case "balance", "bal" -> balance(s, args);
            case "givejackpot" -> giveJackpot(s, args);
            default -> help(s);
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equalsIgnoreCase(flag) || a.equalsIgnoreCase("-" + flag) || a.equalsIgnoreCase("--" + flag)) return true;
        return false;
    }

    private static Integer intArg(String[] args, int index) {
        if (args.length <= index) return null;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.box("gold", List.of(
                "<gradient:#ff4e50:#f9d423><bold>  World Events — Admin</bold></gradient>",
                " <yellow>/worldevent random <gray>[noroulette]",
                " <yellow>/worldevent start <gray><event> [minutes] [noroulette]",
                " <yellow>/worldevent stop <gray><event|all>",
                " <yellow>/worldevent list <dark_gray>| <yellow>gui <dark_gray>| <yellow>reload",
                " <yellow>/worldevent hunted <gray><player> [minutes]",
                " <yellow>/worldevent lootrain <gray>[here] [minutes]",
                " <yellow>/worldevent boss <gray>[x z] <dark_gray>| <yellow>bosstp",
                " <yellow>/worldevent maze setorigin",
                " <yellow>/worldevent auction <gray>start | now [lots] | item <price> | text <price> <desc> | skip | cancel",
                " <yellow>/worldevent balance <gray><player> [set|add|take <amount>]",
                " <yellow>/worldevent givejackpot <gray><player>",
                " <dark_gray>Events: " + String.join(", ", Arrays.stream(EventType.values()).map(EventType::id).toList())
        )));
    }

    private void start(CommandSender s, String[] args) {
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /worldevent start <event> [minutes] [noroulette]");
            return;
        }
        EventType t = EventType.fromId(args[1]);
        if (t == null) {
            Msg.send(s, "<red>Unknown event '" + Msg.escape(args[1]) + "'.");
            return;
        }
        Integer minutes = intArg(args, 2);
        if (plugin.events().start(t, minutes, !hasFlag(args, "noroulette"), new HashMap<>(), s))
            Msg.send(s, "<green>Starting " + plugin.events().rawName(t) + "<green>...");
    }

    private void stop(CommandSender s, String[] args) {
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /worldevent stop <event|all>");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            plugin.events().stopAll(StopReason.ADMIN);
            if (plugin.auction().sessionRunning()) plugin.auction().cancel("Stopped by an admin.");
            Msg.send(s, "<green>All events stopped.");
            return;
        }
        EventType t = EventType.fromId(args[1]);
        if (t == null || !plugin.events().stop(t, StopReason.ADMIN)) Msg.send(s, "<red>That event isn't running.");
        else Msg.send(s, "<green>Stopped.");
    }

    private void list(CommandSender s) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>  World Events</bold>");
        for (EventType t : EventType.values()) {
            WorldEvent ev = plugin.events().get(t);
            String state = ev != null ? (ev.usesTimer() ? "<green>ACTIVE " + Msg.time(ev.remainingSeconds()) : "<green>ACTIVE") : (plugin.events().enabled(t) ? "<gray>ready" : "<red>disabled");
            lines.add(" <hover:show_text:'<gray>Click to start'><click:suggest_command:'/worldevent start " + t.id() + "'>" + plugin.events().rawName(t)
                    + "</click></hover><reset> <dark_gray>(" + t.id() + ", " + plugin.events().durationMinutes(t) + "m) " + state);
        }
        s.sendMessage(Msg.box("gold", lines));
    }

    private void hunted(CommandSender s, String[] args) {
        Map<String, Object> opts = new HashMap<>();
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Msg.send(s, "<red>Player not found.");
                return;
            }
            opts.put("target", target.getUniqueId());
        }
        plugin.events().start(EventType.THE_HUNTED, intArg(args, 2), !hasFlag(args, "noroulette"), opts, s);
    }

    private void lootRain(CommandSender s, String[] args) {
        Map<String, Object> opts = new HashMap<>();
        Integer minutes = null;
        if (args.length >= 2 && args[1].equalsIgnoreCase("here")) {
            if (!(s instanceof Player p)) {
                Msg.send(s, "<red>Only players can use 'here'.");
                return;
            }
            opts.put("location", p.getLocation());
            minutes = intArg(args, 2);
        } else {
            minutes = intArg(args, 1);
        }
        plugin.events().start(EventType.LOOT_RAIN, minutes, !hasFlag(args, "noroulette"), opts, s);
    }

    private void boss(CommandSender s, String[] args) {
        Map<String, Object> opts = new HashMap<>();
        Integer x = intArg(args, 1), z = intArg(args, 2);
        if (x != null && z != null) {
            opts.put("x", x);
            opts.put("z", z);
        }
        plugin.events().start(EventType.BOSS, null, !hasFlag(args, "noroulette"), opts, s);
    }

    private void bossTp(CommandSender s) {
        if (!(s instanceof Player p)) return;
        if (!(plugin.events().get(EventType.BOSS) instanceof BossEvent boss) || boss.world() == null) {
            Msg.send(s, "<red>No boss event is running.");
            return;
        }
        World w = boss.world();
        int y = w.getHighestBlockYAt(boss.x(), boss.z()) + 1;
        p.teleport(new Location(w, boss.x() + 0.5, y, boss.z() + 0.5));
        Msg.send(p, "<green>Teleported to the boss location.");
    }

    private void maze(CommandSender s, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("setorigin") || !(s instanceof Player p)) {
            Msg.send(s, "<red>Usage (in game): /worldevent maze setorigin <gray>— sets the maze's corner to your position");
            return;
        }
        Location l = p.getLocation();
        plugin.getConfig().set("events.twists_and_turns.world", l.getWorld().getName());
        plugin.getConfig().set("events.twists_and_turns.origin.x", l.getBlockX());
        plugin.getConfig().set("events.twists_and_turns.origin.y", l.getBlockY());
        plugin.getConfig().set("events.twists_and_turns.origin.z", l.getBlockZ());
        plugin.saveConfig();
        int cells = plugin.getConfig().getInt("events.twists_and_turns.cells", 15);
        int pw = plugin.getConfig().getInt("events.twists_and_turns.path-width", 2);
        int size = (cells * 2 + 1) * pw;
        Msg.send(s, "<green>Maze origin set. The maze will occupy <white>" + size + "×" + size + "</white> blocks from here towards +X/+Z. "
                + "<red>Everything in that area will be replaced and cleared when the maze ends!");
    }

    private void auction(CommandSender s, String[] args) {
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /worldevent auction start | now [lots] | item <price> | text <price> <description> | skip | cancel");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> plugin.events().start(EventType.AUCTION, intArg(args, 2), !hasFlag(args, "noroulette"), new HashMap<>(), s);
            case "now" -> {
                Integer lots = intArg(args, 2);
                plugin.auction().startSession(plugin.auction().generateLots(lots == null ? plugin.getConfig().getInt("auction.lots-per-auction", 3) : Math.max(1, lots)), null);
                Msg.send(s, "<green>Auction started.");
            }
            case "item" -> {
                if (!(s instanceof Player p)) {
                    Msg.send(s, "<red>Hold the item in game.");
                    return;
                }
                Double price = parsePrice(args, 2);
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (price == null || hand.getType().isAir()) {
                    Msg.send(s, "<red>Usage: hold an item and run /worldevent auction item <starting price>");
                    return;
                }
                ItemStack item = hand.clone();
                p.getInventory().setItemInMainHand(null);
                String name = "<white>" + (item.getAmount() > 1 ? item.getAmount() + "× " : "") + "</white>"
                        + (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                        ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(item.getItemMeta().displayName())
                        : "<aqua><lang:" + item.getType().translationKey() + "></aqua>");
                plugin.auction().queueLot(new AuctionLot(name, "", price, false, AuctionLot.Delivery.ITEM, item, p.getUniqueId()));
                Msg.send(s, "<green>Item queued for auction at <white>" + Msg.money(price) + "</white>. If nobody bids you get it back.");
            }
            case "text" -> {
                Double price = parsePrice(args, 2);
                if (price == null || args.length < 4) {
                    Msg.send(s, "<red>Usage: /worldevent auction text <starting price> <description (MiniMessage ok)>");
                    return;
                }
                String desc = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                plugin.auction().queueLot(new AuctionLot(desc, "", price, false, AuctionLot.Delivery.ANNOUNCE, null, null));
                Msg.send(s, "<green>Lot queued. You'll be told who to give it to.");
            }
            case "skip" -> {
                plugin.auction().skipLot();
                Msg.send(s, "<green>Current lot will close now.");
            }
            case "cancel" -> {
                plugin.auction().cancel("Cancelled by an admin.");
                plugin.events().stop(EventType.AUCTION, StopReason.ADMIN);
                Msg.send(s, "<green>Auction cancelled.");
            }
            default -> Msg.send(s, "<red>Unknown auction sub-command.");
        }
    }

    private static Double parsePrice(String[] args, int index) {
        if (args.length <= index) return null;
        try {
            double d = Double.parseDouble(args[index].replace("$", ""));
            return d > 0 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached;
    }

    private void balance(CommandSender s, String[] args) {
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /worldevent balance <player> [set|add|take <amount>]");
            return;
        }
        OfflinePlayer op = findPlayer(args[1]);
        if (op == null) {
            Msg.send(s, "<red>Player not found.");
            return;
        }
        UUID id = op.getUniqueId();
        if (args.length >= 4) {
            Double amt = parsePrice(args, 3);
            if (amt == null && !args[3].equals("0")) {
                Msg.send(s, "<red>Invalid amount.");
                return;
            }
            double a = amt == null ? 0 : amt;
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "set" -> plugin.auction().setBalance(id, a);
                case "add" -> plugin.auction().addBalance(id, a);
                case "take" -> plugin.auction().addBalance(id, -a);
                default -> {
                    Msg.send(s, "<red>Use set, add or take.");
                    return;
                }
            }
            plugin.auction().log("ADMIN " + s.getName() + " " + args[2] + " " + Msg.money(a) + " for " + args[1]);
        }
        Msg.send(s, "<yellow>" + Msg.escape(args[1]) + "</yellow>'s auction balance: <gold>" + Msg.money(plugin.auction().balance(id)));
    }

    private void giveJackpot(CommandSender s, String[] args) {
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /worldevent givejackpot <player>");
            return;
        }
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) {
            Msg.send(s, "<red>Player must be online.");
            return;
        }
        plugin.rewards().give(p.getUniqueId(), plugin.rewards().jackpot());
        Msg.send(s, "<green>Gave the jackpot to " + Msg.escape(p.getName()) + ".");
    }

    // ------------------------------------------------------------ tab completion

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(PERM)) return List.of();
        int idx = args.length == 0 ? 0 : args.length - 1;
        String cur = args.length == 0 ? "" : args[idx].toLowerCase(Locale.ROOT);
        List<String> opts = new ArrayList<>();
        if (idx == 0) opts.addAll(SUBS);
        else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "start" -> {
                    if (idx == 1) for (EventType t : EventType.values()) opts.add(t.id());
                    else if (idx == 2) opts.addAll(List.of("5", "10", "15", "30", "60"));
                    else opts.add("noroulette");
                }
                case "stop" -> {
                    if (idx == 1) {
                        opts.add("all");
                        for (WorldEvent ev : plugin.events().active()) opts.add(ev.type().id());
                    }
                }
                case "random" -> opts.add("noroulette");
                case "hunted", "balance", "bal", "givejackpot" -> {
                    if (idx == 1) for (Player p : Bukkit.getOnlinePlayers()) opts.add(p.getName());
                    else if (idx == 2 && !sub.equals("hunted") && !sub.equals("givejackpot")) opts.addAll(List.of("set", "add", "take"));
                }
                case "lootrain" -> {
                    if (idx == 1) opts.add("here");
                }
                case "maze" -> {
                    if (idx == 1) opts.add("setorigin");
                }
                case "auction" -> {
                    if (idx == 1) opts.addAll(List.of("start", "now", "item", "text", "skip", "cancel"));
                    else if (idx == 2 && (args[1].equalsIgnoreCase("item") || args[1].equalsIgnoreCase("text"))) opts.add("<price>");
                }
                default -> {
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(cur)) out.add(o);
        return out;
    }
}

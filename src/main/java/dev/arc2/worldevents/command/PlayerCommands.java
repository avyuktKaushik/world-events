package dev.arc2.worldevents.command;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** /sacrifice, /abalance, /activeevents */
public final class PlayerCommands {

    private final WorldEventsPlugin plugin;

    public PlayerCommands(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public BasicCommand sacrifice() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                CommandSender s = source.getSender();
                if (!(s instanceof Player p)) {
                    Msg.send(s, "<red>Players only.");
                    return;
                }
                if (!p.hasPermission("worldevents.sacrifice")) {
                    Msg.send(p, "<red>You don't have permission.");
                    return;
                }
                plugin.auction().openSacrifice(p);
            }
        };
    }

    public BasicCommand balance() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                CommandSender s = source.getSender();
                if (args.length >= 1 && s.hasPermission("worldevents.admin")) {
                    OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[0]);
                    if (op == null) {
                        Msg.send(s, "<red>Player not found.");
                        return;
                    }
                    Msg.send(s, "<yellow>" + Msg.escape(args[0]) + "</yellow>'s auction balance: <gold>" + Msg.money(plugin.auction().balance(op.getUniqueId())));
                    return;
                }
                if (!(s instanceof Player p)) {
                    Msg.send(s, "<red>Players only.");
                    return;
                }
                Msg.send(p, "<gray>Your auction balance: <gold>" + Msg.money(plugin.auction().balance(p.getUniqueId()))
                        + "</gold> <dark_gray>— get more with <yellow><click:run_command:'/sacrifice'>/sacrifice</click>");
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (!source.getSender().hasPermission("worldevents.admin") || args.length > 1) return List.of();
                String cur = args.length == 0 ? "" : args[0].toLowerCase();
                List<String> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(cur)) out.add(p.getName());
                return out;
            }
        };
    }

    public BasicCommand active() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                CommandSender s = source.getSender();
                if (plugin.events().active().isEmpty()) {
                    Msg.send(s, "<gray>No world event is active right now. Stay alert!");
                    return;
                }
                List<String> lines = new ArrayList<>();
                lines.add("<gradient:#ff4e50:#f9d423><bold>  Active World Events</bold></gradient>");
                for (WorldEvent ev : plugin.events().active()) {
                    String time = ev.usesTimer() ? " <dark_gray>— <white>" + Msg.time(ev.remainingSeconds()) + " left" : "";
                    lines.add("  " + plugin.events().rawName(ev.type()) + "<reset>" + time);
                    lines.add("    <gray>" + plugin.events().description(ev.type()));
                }
                s.sendMessage(Msg.box("gold", lines));
            }
        };
    }
}

package dev.arc2.worldevents.listener;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.events.SmartiePantsEvent;
import dev.arc2.worldevents.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.regex.Pattern;

/**
 * Chat input for Smartie Pants answers and auction bids. Matching messages are hidden from
 * public chat (so nobody can copy answers) and processed on the main thread.
 */
public final class ChatListener implements Listener {

    private static final Pattern INTEGER = Pattern.compile("^-?\\d{1,12}$");
    private static final Pattern MONEY = Pattern.compile("^\\$?\\d{1,9}(\\.\\d{1,2})?$");

    private final WorldEventsPlugin plugin;

    public ChatListener(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        String text = Msg.plain(e.message()).trim().replace(",", "");
        Player p = e.getPlayer();
        plugin.events().markActivity(p);

        SmartiePantsEvent smartie = plugin.events().smartie();
        if (smartie != null && smartie.acceptingAnswers() && INTEGER.matcher(text).matches()) {
            e.setCancelled(true);
            long value;
            try {
                value = Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                SmartiePantsEvent s = plugin.events().smartie();
                if (s != null && p.isOnline()) s.handleGuess(p, value);
            });
            return;
        }

        if (plugin.auction().biddingOpen() && MONEY.matcher(text).matches()) {
            e.setCancelled(true);
            double amount;
            try {
                amount = Double.parseDouble(text.replace("$", ""));
            } catch (NumberFormatException ex) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) plugin.auction().bid(p, amount);
            });
        }
    }
}

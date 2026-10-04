package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.auction.AuctionLot;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;

/**
 * Auction world event: a sacrifice window (the configurable duration) where players turn items into
 * money with /sacrifice, followed by the auction itself (lots are bid on in chat).
 */
public final class AuctionEvent extends WorldEvent {

    private boolean auctionStarted;
    private long sacrificeEnd;
    private int announceTimer;
    private boolean sessionDone;

    public AuctionEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.AUCTION);
    }

    @Override
    public boolean usesTimer() {
        return false;
    }

    @Override
    public boolean canResume() {
        return false;
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        sacrificeEnd = endMillis;
        announceTimer = 60;
        Msg.broadcast(Msg.box("yellow", List.of(
                "<gold><bold>  ⚖ AUCTION ⚖</bold>",
                "  <gray>An auction starts in <white>" + Msg.time((sacrificeEnd - startMillis) / 1000) + "</white>!",
                "  <gray>Sacrifice items with <yellow><click:run_command:'/sacrifice'><u>/sacrifice</u></click></yellow> to get money to bid with.",
                "  <gray>Check your money with <yellow>/abal</yellow>."
        )));
        return true;
    }

    @Override
    public void tickSecond() {
        if (auctionStarted) {
            if (sessionDone) complete();
            return;
        }
        long left = (sacrificeEnd - System.currentTimeMillis()) / 1000;
        if (left <= 0) {
            auctionStarted = true;
            int lots = Math.max(1, plugin.getConfig().getInt("auction.lots-per-auction", 3));
            List<AuctionLot> generated = plugin.auction().generateLots(lots);
            plugin.auction().startSession(generated, () -> sessionDone = true);
            return;
        }
        if (--announceTimer <= 0 || left == 30 || left == 10) {
            announceTimer = 60;
            Msg.broadcast("<gold>⚖ Auction</gold> <gray>starts in <white>" + Msg.time(left)
                    + "</white> — use <yellow><click:run_command:'/sacrifice'><u>/sacrifice</u></click></yellow> to get bidding money!");
        }
    }

    @Override
    public Component bossBarName() {
        if (!auctionStarted)
            return Msg.mm("<gold><bold>Auction</bold></gold> <gray>— sacrifice phase, starts in <white>" + Msg.time((sacrificeEnd - System.currentTimeMillis()) / 1000) + "</white> <dark_gray>(/sacrifice)");
        return Msg.mm("<gold><bold>Auction</bold></gold> <gray>— " + plugin.auction().statusLine());
    }

    @Override
    public float bossBarProgress() {
        if (!auctionStarted) {
            long total = Math.max(1, sacrificeEnd - startMillis);
            return (float) Math.max(0, Math.min(1, (sacrificeEnd - System.currentTimeMillis()) / (double) total));
        }
        return plugin.auction().lotProgress();
    }

    @Override
    public void onStop(StopReason reason) {
        if (auctionStarted && !sessionDone) plugin.auction().cancel("The auction event was stopped.");
    }
}

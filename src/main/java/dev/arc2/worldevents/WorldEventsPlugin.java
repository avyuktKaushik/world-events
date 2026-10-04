package dev.arc2.worldevents;

import dev.arc2.worldevents.auction.AuctionManager;
import dev.arc2.worldevents.auction.OpVillager;
import dev.arc2.worldevents.command.AdminCommand;
import dev.arc2.worldevents.command.PlayerCommands;
import dev.arc2.worldevents.core.AutoScheduler;
import dev.arc2.worldevents.core.DataStore;
import dev.arc2.worldevents.core.EventManager;
import dev.arc2.worldevents.core.PlacedBlockTracker;
import dev.arc2.worldevents.gui.GuiListener;
import dev.arc2.worldevents.listener.ChatListener;
import dev.arc2.worldevents.listener.CoreListener;
import dev.arc2.worldevents.reward.RewardManager;
import dev.arc2.worldevents.util.Msg;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class WorldEventsPlugin extends JavaPlugin {

    private static WorldEventsPlugin instance;

    private DataStore data;
    private PlacedBlockTracker tracker;
    private RewardManager rewards;
    private AuctionManager auction;
    private EventManager events;
    private AutoScheduler scheduler;
    private OpVillager opVillager;

    public static WorldEventsPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Msg.init(this);

        data = new DataStore(this);
        data.load();

        tracker = new PlacedBlockTracker(this);
        rewards = new RewardManager(this);
        opVillager = new OpVillager(this);
        auction = new AuctionManager(this);
        events = new EventManager(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(tracker, this);
        pm.registerEvents(new CoreListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(auction, this);
        pm.registerEvents(opVillager, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            AdminCommand admin = new AdminCommand(this);
            commands.register("worldevent", "Arc 2 World Events admin command", List.of("wevent", "worldevents"), admin);
            PlayerCommands pc = new PlayerCommands(this);
            commands.register("sacrifice", "Sacrifice items for auction money", List.of(), pc.sacrifice());
            commands.register("abalance", "Show your auction balance", List.of("abal", "auctionbalance"), pc.balance());
            commands.register("activeevents", "Show the active world events", List.of("wevents"), pc.active());
        });

        events.resumeFromData();

        scheduler = new AutoScheduler(this);
        scheduler.start();

        // periodic save
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            auction.saveBalances();
            data.save();
        }, 20L * 300, 20L * 300);

        getLogger().info("WorldEvents (Arc 2) enabled — " + events.active().size() + " event(s) resumed.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.stop();
        if (events != null) events.shutdown();
        if (auction != null) auction.shutdown();
        if (data != null) data.save();
    }

    public void reload() {
        reloadConfig();
        Msg.init(this);
        tracker.reload();
        rewards.reload();
        auction.reload();
        events.reload();
        scheduler.reload();
    }

    public DataStore data() { return data; }
    public PlacedBlockTracker tracker() { return tracker; }
    public RewardManager rewards() { return rewards; }
    public AuctionManager auction() { return auction; }
    public EventManager events() { return events; }
    public OpVillager opVillager() { return opVillager; }
}

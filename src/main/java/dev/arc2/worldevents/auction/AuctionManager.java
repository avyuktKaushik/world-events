package dev.arc2.worldevents.auction;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Auction money (from sacrificing items), the /sacrifice GUI, and running auction sessions:
 * lots are announced in a big chat box, players bid by typing an amount in chat,
 * you can never bid more than you have, the winner is announced in chat, no buyouts.
 */
public final class AuctionManager implements Listener {

    private final WorldEventsPlugin plugin;
    private final SacrificeValuer valuer;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, SacrificeGui> openGuis = new HashMap<>();

    // session state
    private final Deque<AuctionLot> queue = new ArrayDeque<>();
    private final List<Runnable> onSessionEnd = new ArrayList<>();
    private int taskId = -1;
    private AuctionLot current;
    private double startPrice;
    private UUID topBidder;
    private String topBidderName;
    private double topBid;
    private long lotEnd;
    private long lotDuration;
    private long nextLotAt;
    private int lotIndex;
    private int lotTotal;
    private volatile boolean biddingOpen;
    private final Map<UUID, Long> lastBid = new HashMap<>();

    public AuctionManager(WorldEventsPlugin plugin) {
        this.plugin = plugin;
        this.valuer = new SacrificeValuer(plugin);
        balances.putAll(plugin.data().loadBalances());
    }

    public void reload() {
        valuer.reload();
    }

    // ------------------------------------------------------------ balances

    public double balance(UUID id) {
        return balances.getOrDefault(id, 0.0);
    }

    public void setBalance(UUID id, double v) {
        balances.put(id, Math.max(0, Math.round(v * 100.0) / 100.0));
        saveBalances();
    }

    public void addBalance(UUID id, double v) {
        setBalance(id, balance(id) + v);
    }

    public void saveBalances() {
        plugin.data().saveBalances(balances);
        plugin.data().save();
    }

    // ------------------------------------------------------------ sacrifice GUI

    public boolean sacrificeAllowed() {
        return plugin.getConfig().getBoolean("auction.sacrifice.anytime", true)
                || plugin.events().isActive(dev.arc2.worldevents.event.EventType.AUCTION) || sessionRunning();
    }

    public void openSacrifice(Player p) {
        if (!sacrificeAllowed()) {
            Msg.send(p, "<red>You can only sacrifice items before/during an auction.");
            return;
        }
        SacrificeGui gui = new SacrificeGui(p);
        gui.updateInfo(0, 0, balance(p.getUniqueId()));
        openGuis.put(p.getUniqueId(), gui);
        p.openInventory(gui.getInventory());
    }

    private void refresh(SacrificeGui gui) {
        double total = 0;
        int refused = 0;
        for (ItemStack it : gui.inputs()) {
            double v = valuer.value(it);
            if (v <= 0) refused++;
            total += v;
        }
        gui.updateInfo(total, refused, balance(gui.owner()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof SacrificeGui gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        if (raw >= SacrificeGui.INPUT_SLOTS && raw < 54) {
            e.setCancelled(true);
            if (raw == SacrificeGui.CONFIRM_SLOT) confirm(p, gui);
            else if (raw == SacrificeGui.CANCEL_SLOT) p.closeInventory();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refresh(gui));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof SacrificeGui gui)) return;
        for (int slot : e.getRawSlots()) {
            if (slot >= SacrificeGui.INPUT_SLOTS && slot < 54) {
                e.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> refresh(gui));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof SacrificeGui gui)) return;
        openGuis.remove(gui.owner());
        if (gui.done()) return;
        if (e.getPlayer() instanceof Player p) returnItems(p, gui.inputs());
        gui.clearInputs();
    }

    private void returnItems(Player p, List<ItemStack> items) {
        if (items.isEmpty()) return;
        for (ItemStack left : p.getInventory().addItem(items.toArray(new ItemStack[0])).values())
            p.getWorld().dropItem(p.getLocation(), left, i -> i.setOwner(p.getUniqueId()));
    }

    private void confirm(Player p, SacrificeGui gui) {
        if (!sacrificeAllowed()) {
            Msg.send(p, "<red>Sacrificing isn't allowed right now.");
            p.closeInventory();
            return;
        }
        double total = 0;
        int count = 0;
        List<ItemStack> refused = new ArrayList<>();
        for (ItemStack it : gui.inputs()) {
            double v = valuer.value(it);
            if (v <= 0) refused.add(it);
            else {
                total += v;
                count += it.getAmount();
            }
        }
        if (total <= 0) {
            Msg.send(p, "<red>None of those items are worth anything.");
            return;
        }
        gui.setDone();
        gui.clearInputs();
        addBalance(p.getUniqueId(), total);
        returnItems(p, refused);
        p.closeInventory();
        Msg.send(p, "<green>You sacrificed <white>" + count + "</white> items for <gold>" + Msg.money(total)
                + "</gold>. Balance: <yellow>" + Msg.money(balance(p.getUniqueId())));
        Msg.play(p, Msg.sound("minecraft:block.beacon.power_select", 1f, 0.7f));
        log("SACRIFICE " + p.getName() + " (" + p.getUniqueId() + ") +" + Msg.money(total) + " -> " + Msg.money(balance(p.getUniqueId())));
    }

    // ------------------------------------------------------------ villager trade tagging

    /** Returns a copy of the recipe whose result carries the "villager traded" tag (or the same recipe). */
    private MerchantRecipe tagged(MerchantRecipe r) {
        ItemStack result = r.getResult();
        if (result.getType().isAir() || !valuer.isValuable(result.getType())) return r;
        if (result.hasItemMeta() && result.getItemMeta().getPersistentDataContainer().has(valuer.tradedKey())) return r;
        ItemStack copy = result.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return r;
        meta.getPersistentDataContainer().set(valuer.tradedKey(), PersistentDataType.BYTE, (byte) 1);
        copy.setItemMeta(meta);
        MerchantRecipe n = new MerchantRecipe(copy, r.getUses(), r.getMaxUses(), r.hasExperienceReward(), r.getVillagerExperience(),
                r.getPriceMultiplier(), r.getDemand(), r.getSpecialPrice());
        n.setIngredients(r.getIngredients());
        return n;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTradeAcquired(VillagerAcquireTradeEvent e) {
        e.setRecipe(tagged(e.getRecipe()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent e) {
        if (!(e.getInventory() instanceof MerchantInventory mi)) return;
        Merchant m = mi.getMerchant();
        List<MerchantRecipe> recipes = m.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe t = tagged(recipes.get(i));
            if (t != recipes.get(i)) m.setRecipe(i, t);
        }
    }

    // ------------------------------------------------------------ session control

    public boolean sessionRunning() {
        return taskId != -1;
    }

    public boolean biddingOpen() {
        return biddingOpen;
    }

    public void startSession(List<AuctionLot> lots, Runnable onEnd) {
        queue.addAll(lots);
        lotTotal += lots.size();
        if (onEnd != null) onSessionEnd.add(onEnd);
        if (taskId == -1) {
            nextLotAt = System.currentTimeMillis() + 3000;
            Msg.broadcast(Msg.box("yellow", List.of(
                    "<gold><bold>  ⚖ THE AUCTION IS STARTING ⚖</bold>",
                    "  <gray><white>" + lotTotal + "</white> lot(s) are up for grabs.",
                    "  <gray>Bid by typing an amount in chat, e.g. <yellow>150</yellow>.",
                    "  <gray>Your money: <yellow>/abal</yellow> — get more with <yellow>/sacrifice</yellow>."
            )));
            Msg.playAll(Msg.sound("minecraft:block.bell.use", 1f, 1f));
            taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
        }
    }

    /** Adds a single lot; starts a mini auction if none is running. */
    public void queueLot(AuctionLot lot) {
        startSession(List.of(lot), null);
    }

    public void cancel(String reason) {
        if (!sessionRunning()) return;
        biddingOpen = false;
        if (current != null) refund(current);
        for (AuctionLot l : queue) refund(l);
        queue.clear();
        current = null;
        Msg.broadcast("<gold>⚖</gold> <red>The auction was cancelled.</red> <gray>" + Msg.escape(reason));
        endSession();
    }

    /** Ends the current lot immediately. */
    public void skipLot() {
        if (current != null) lotEnd = System.currentTimeMillis();
    }

    private void refund(AuctionLot lot) {
        if (lot.delivery() == AuctionLot.Delivery.ITEM && lot.owner() != null && lot.item() != null)
            plugin.rewards().giveRaw(lot.owner(), List.of(lot.item()));
    }

    private void endSession() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        current = null;
        biddingOpen = false;
        lotIndex = 0;
        lotTotal = 0;
        List<Runnable> callbacks = new ArrayList<>(onSessionEnd);
        onSessionEnd.clear();
        for (Runnable r : callbacks) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (current == null) {
            if (queue.isEmpty()) {
                Msg.broadcast("<gold>⚖</gold> <gray>The auction is over. Thanks for bidding!");
                endSession();
                return;
            }
            if (now >= nextLotAt) openLot(queue.poll());
            return;
        }
        long left = (lotEnd - now + 999) / 1000;
        if (left <= 0) {
            closeLot();
            return;
        }
        if (left == 30 || left == 15 || left <= 5) {
            String bid = topBidder == null ? "<gray>No bids yet — starting at <green>" + Msg.money(startPrice) : "<gray>Top bid: <green>" + Msg.money(topBid) + " <gray>by <yellow>" + Msg.escape(topBidderName);
            Msg.broadcast("<gold>⚖</gold> " + current.name() + " <gray>— <white>" + left + "s</white> left. " + bid);
            if (left <= 5) Msg.playAll(Msg.sound("minecraft:block.note_block.hat", 1f, 1.5f));
        }
    }

    private double scaledStartPrice(AuctionLot lot) {
        double base = lot.basePrice();
        if (!lot.scale() || !plugin.getConfig().getBoolean("auction.starting-price-scaling.enabled", true)) return Math.max(1, Math.round(base));
        double reference = Math.max(1, plugin.getConfig().getDouble("auction.starting-price-scaling.reference-balance", 200));
        double strength = plugin.getConfig().getDouble("auction.starting-price-scaling.strength", 0.5);
        double maxMult = Math.max(1, plugin.getConfig().getDouble("auction.starting-price-scaling.max-multiplier", 2.5));
        List<Double> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(balance(p.getUniqueId()));
        online.sort(Comparator.reverseOrder());
        int n = Math.min(3, online.size());
        double richness = 0;
        for (int i = 0; i < n; i++) richness += online.get(i);
        richness = n == 0 ? 0 : richness / n;
        double mult = 1 + (richness / reference - 1) * strength;
        mult = Math.max(1, Math.min(maxMult, mult));
        return Math.max(1, Math.round(base * mult));
    }

    private void openLot(AuctionLot lot) {
        current = lot;
        lotIndex++;
        startPrice = scaledStartPrice(lot);
        topBidder = null;
        topBidderName = null;
        topBid = 0;
        lastBid.clear();
        lotDuration = Math.max(15, plugin.getConfig().getInt("auction.bid-seconds", 45)) * 1000L;
        lotEnd = System.currentTimeMillis() + lotDuration;
        biddingOpen = true;

        Component name = Msg.mm(lot.name());
        if (lot.delivery() == AuctionLot.Delivery.ITEM && lot.item() != null) name = name.hoverEvent(lot.item().asHoverEvent());
        else if (lot.lore() != null && !lot.lore().isBlank()) name = name.hoverEvent(HoverEvent.showText(Msg.mm(lot.lore())));

        List<String> lines = new ArrayList<>();
        lines.add("<gradient:#FFD700:#FFA500><bold>  ⚖ AUCTION — LOT " + lotIndex + "/" + Math.max(lotIndex, lotTotal) + " ⚖</bold></gradient>");
        lines.add("  <lot>");
        if (lot.lore() != null && !lot.lore().isBlank()) lines.add("  <gray>" + lot.lore());
        lines.add("  <gray>Starting price: <green><bold>" + Msg.money(startPrice) + "</bold>");
        lines.add("  <gray>Type your bid in chat! <dark_gray>(e.g. <yellow>" + (long) Math.ceil(startPrice) + "</yellow>)");
        lines.add("  <gray>Ends in <yellow>" + lotDuration / 1000 + "s</yellow> <dark_gray>• no buyouts • you can't bid more than you have");
        Msg.broadcast(Msg.box("gold", lines, Placeholder.component("lot", name)));
        Msg.titleAll(Msg.mm("<gold><bold>AUCTION"), Msg.mm(lot.name()), 200, 2500, 500);
        Msg.playAll(Msg.sound("minecraft:block.anvil.land", 0.6f, 1.4f));
    }

    private double minNextBid() {
        if (topBidder == null) return startPrice;
        double pct = plugin.getConfig().getDouble("auction.min-increment-percent", 5) / 100.0;
        double flat = plugin.getConfig().getDouble("auction.min-increment", 1);
        return Math.round((topBid + Math.max(flat, topBid * pct)) * 100.0) / 100.0;
    }

    /** Main thread. */
    public void bid(Player p, double amount) {
        if (!biddingOpen || current == null) return;
        amount = Math.round(amount * 100.0) / 100.0;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastBid.get(id);
        if (last != null && now - last < 1000) {
            Msg.send(p, "<red>Slow down — one bid per second.");
            return;
        }
        lastBid.put(id, now);
        if (id.equals(topBidder)) {
            Msg.send(p, "<yellow>You're already the highest bidder.");
            return;
        }
        double min = minNextBid();
        if (amount < min) {
            Msg.send(p, "<red>The minimum bid is <white>" + Msg.money(min) + "</white>.");
            return;
        }
        double bal = balance(id);
        if (amount > bal) {
            Msg.send(p, "<red>You only have <white>" + Msg.money(bal) + "</white>. Sacrifice items with <yellow>/sacrifice</yellow> to get more.");
            return;
        }
        topBidder = id;
        topBidderName = p.getName();
        topBid = amount;
        Msg.broadcast("<gold>⚖</gold> <yellow><name></yellow> <gray>bid <green><bold>" + Msg.money(amount) + "</bold></green> on <lot>",
                Placeholder.unparsed("name", p.getName()), Placeholder.parsed("lot", current.name()));
        Msg.playAll(Msg.sound("minecraft:entity.experience_orb.pickup", 0.7f, 1.2f));

        long antiSnipe = Math.max(0, plugin.getConfig().getInt("auction.anti-snipe-seconds", 10)) * 1000L;
        if (lotEnd - now < antiSnipe) {
            lotEnd = now + antiSnipe;
            Msg.broadcast("<gold>⚖</gold> <gray>Late bid! Time extended to <white>" + antiSnipe / 1000 + "s</white>.");
        }
    }

    private void closeLot() {
        biddingOpen = false;
        AuctionLot lot = current;
        current = null;
        nextLotAt = System.currentTimeMillis() + 5000;
        if (lot == null) return;

        if (topBidder != null && balance(topBidder) + 1.0e-6 >= topBid) {
            setBalance(topBidder, balance(topBidder) - topBid);
            Msg.broadcast(Msg.box("gold", List.of(
                    "<gradient:#FFD700:#FFA500><bold>  ⚖ SOLD! ⚖</bold></gradient>",
                    "  <lot>",
                    "  <gray>Winner: <yellow><bold><winner></bold></yellow> <gray>for <green><bold><price></bold>"
            ), Placeholder.parsed("lot", lot.name()), Placeholder.unparsed("winner", topBidderName), Placeholder.unparsed("price", Msg.money(topBid))));
            Msg.titleAll(Msg.mm("<gold><bold>SOLD!"), Msg.mm("<yellow>" + Msg.escape(topBidderName) + " <gray>won " + lot.name()), 200, 3000, 600);
            Msg.playAll(Msg.sound("minecraft:ui.toast.challenge_complete", 1f, 1f));
            deliver(lot, topBidder, topBidderName, topBid);
        } else {
            Msg.broadcast("<gold>⚖</gold> " + lot.name() + " <gray>received no valid bids and goes <red>unsold</red>.");
            refund(lot);
            log("UNSOLD " + Msg.stripTags(lot.name()));
        }
    }

    private void deliver(AuctionLot lot, UUID winner, String winnerName, double price) {
        String plainName = Msg.stripTags(lot.name());
        log("WIN " + winnerName + " (" + winner + ") won '" + plainName + "' for " + Msg.money(price) + " [" + lot.delivery() + "]");
        switch (lot.delivery()) {
            case ITEM -> {
                if (lot.item() != null) plugin.rewards().giveRaw(winner, List.of(lot.item()));
            }
            case OP_VILLAGER -> plugin.rewards().giveRaw(winner, List.of(plugin.opVillager().egg()));
            case ANNOUNCE -> {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("worldevents.admin"))
                        Msg.send(admin, "<light_purple>Admin:</light_purple> give <yellow>" + Msg.escape(winnerName) + "</yellow> → <white>" + Msg.escape(plainName) + "</white> <dark_gray>(logged in auction-winners.log)");
                }
                plugin.getLogger().info("[Auction] Give " + winnerName + " -> " + plainName);
            }
        }
    }

    // ------------------------------------------------------------ lot generation

    public List<AuctionLot> generateLots(int count) {
        ConfigurationSection pool = plugin.getConfig().getConfigurationSection("auction.lot-pool");
        List<AuctionLot> out = new ArrayList<>();
        if (pool == null) return out;
        List<String> keys = new ArrayList<>(pool.getKeys(false));
        Set<String> used = new HashSet<>();
        for (int i = 0; i < count && !keys.isEmpty(); i++) {
            List<String> candidates = new ArrayList<>();
            for (String k : keys) if (!used.contains(k)) candidates.add(k);
            if (candidates.isEmpty()) candidates = keys;
            String key = Util.weighted(candidates, k -> pool.getDouble(k + ".weight", 10));
            if (key == null) break;
            used.add(key);
            ConfigurationSection s = pool.getConfigurationSection(key);
            if (s == null) continue;
            int[] r = Util.parseRange(s.get("amount"), 1);
            int step = Math.max(1, s.getInt("amount-step", 1));
            int amount = Util.range(r[0] / step, r[1] / step) * step;
            if (amount <= 0) amount = Math.max(1, r[0]);
            String nm = s.getString("name", key);
            String display = amount > 1 ? "<white>" + amount + "×</white> " + nm : nm;
            AuctionLot.Delivery delivery;
            try {
                delivery = AuctionLot.Delivery.valueOf(s.getString("delivery", "ANNOUNCE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                delivery = AuctionLot.Delivery.ANNOUNCE;
            }
            double price = s.getDouble("price-per-unit", 10) * amount;
            out.add(new AuctionLot(display, s.getString("description", ""), price, true, delivery, null, null));
        }
        return out;
    }

    // ------------------------------------------------------------ status / misc

    public String statusLine() {
        if (current == null) return queue.isEmpty() ? "finishing..." : "next lot in a moment...";
        long left = Math.max(0, (lotEnd - System.currentTimeMillis()) / 1000);
        String bid = topBidder == null ? "start " + Msg.money(startPrice) : "top " + Msg.money(topBid) + " by " + Msg.escape(topBidderName);
        return "Lot " + lotIndex + "/" + Math.max(lotIndex, lotTotal) + ": " + current.name() + "<reset> <gray>— " + bid + " — <white>" + left + "s";
    }

    public float lotProgress() {
        if (current == null || lotDuration <= 0) return 1f;
        return (float) Math.max(0, Math.min(1, (lotEnd - System.currentTimeMillis()) / (double) lotDuration));
    }

    public String nameOf(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? id.toString() : op.getName();
    }

    public void log(String line) {
        File f = new File(plugin.getDataFolder(), "auction-winners.log");
        try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
            w.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + line);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write auction-winners.log: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (sessionRunning()) cancel("Server is restarting.");
        for (SacrificeGui gui : new ArrayList<>(openGuis.values())) {
            Player p = Bukkit.getPlayer(gui.owner());
            if (p != null && !gui.done()) {
                returnItems(p, gui.inputs());
                gui.clearInputs();
                gui.setDone();
                p.closeInventory();
            }
        }
        openGuis.clear();
        saveBalances();
    }
}

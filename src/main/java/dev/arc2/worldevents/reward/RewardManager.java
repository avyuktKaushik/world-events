package dev.arc2.worldevents.reward;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds, sanitizes and delivers rewards (works for offline players too). */
public final class RewardManager {

    private final WorldEventsPlugin plugin;

    public RewardManager(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // config is read live
    }

    public int maxProtection() {
        return Math.min(3, Math.max(0, plugin.getConfig().getInt("rewards.max-protection-level", 3)));
    }

    // ------------------------------------------------------------ building

    /** Rolls {@code rolls} weighted entries from a list of item specs. */
    public List<ItemStack> roll(List<Map<?, ?>> specs, int rolls) {
        List<ItemSpec> parsed = ItemSpec.parse(specs);
        List<ItemStack> out = new ArrayList<>();
        if (parsed.isEmpty()) return out;
        for (int i = 0; i < rolls; i++) {
            ItemSpec s = Util.weighted(parsed, ItemSpec::weight);
            if (s != null) out.addAll(s.build(plugin));
        }
        return out;
    }

    /** Builds every entry of a list (no weights) — used for fixed reward bundles. */
    public List<ItemStack> all(List<Map<?, ?>> specs) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemSpec s : ItemSpec.parse(specs)) out.addAll(s.build(plugin));
        return out;
    }

    public List<ItemStack> jackpot() {
        List<Map<?, ?>> pool = plugin.getConfig().getMapList("rewards.jackpot.items");
        if (pool.isEmpty()) return new ArrayList<>(List.of(ItemFactory.armorShulker(plugin)));
        return all(pool);
    }

    public List<ItemStack> sanitize(Collection<ItemStack> items) {
        List<ItemStack> out = new ArrayList<>();
        int max = maxProtection();
        for (ItemStack it : items) {
            ItemStack c = Sanitizer.clean(it, max);
            if (c != null) out.add(c);
        }
        return out;
    }

    // ------------------------------------------------------------ delivery

    /** Gives sanitized reward items (never netherite / prot IV). Offline players get them on join. */
    public void give(UUID uuid, Collection<ItemStack> items) {
        giveRaw(uuid, sanitize(items));
    }

    /** Gives items exactly as they are (used for admin-auctioned items). */
    public void giveRaw(UUID uuid, Collection<ItemStack> items) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) list.add(it);
        if (list.isEmpty()) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            giveNow(p, list);
        } else {
            plugin.data().addPending(uuid, list);
        }
    }

    private void giveNow(Player p, List<ItemStack> items) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack it : left.values()) {
            p.getWorld().dropItem(p.getLocation(), it, item -> {
                item.setOwner(p.getUniqueId());
                item.setPickupDelay(0);
            });
        }
        if (!left.isEmpty()) Msg.send(p, "<yellow>Your inventory was full — some rewards were dropped at your feet.");
    }

    public void deliverPending(Player p) {
        List<ItemStack> items = plugin.data().takePending(p.getUniqueId());
        if (items.isEmpty()) return;
        giveNow(p, items);
        Msg.send(p, "<green>You received <white>" + items.size() + "</white> pending reward item(s) from World Events!");
    }

    // ------------------------------------------------------------ participation

    public double jackpotChance(EventType type) {
        return plugin.getConfig().getDouble("events." + type.id() + ".reward-chance",
                plugin.getConfig().getDouble("rewards.participation.default-jackpot-chance", 0.20));
    }

    /** Every active participant gets a roll: jackpot (≈20%) or a consolation item. */
    public void rollParticipation(EventType type, Collection<UUID> participants) {
        if (participants.isEmpty()) return;
        double chance = jackpotChance(type);
        List<String> winners = new ArrayList<>();
        Map<UUID, Boolean> results = new HashMap<>();
        for (UUID uuid : participants) {
            boolean jackpot = Util.chance(chance);
            results.put(uuid, jackpot);
            if (jackpot) {
                give(uuid, jackpot());
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                winners.add(op.getName() == null ? "?" : op.getName());
            } else {
                List<Map<?, ?>> consolation = plugin.getConfig().getMapList("rewards.participation.consolation");
                if (!consolation.isEmpty()) give(uuid, roll(consolation, 1));
            }
        }
        for (var e : results.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            if (e.getValue()) {
                Msg.send(p, "<gold><bold>JACKPOT!</bold></gold> <yellow>You won the event jackpot reward!");
                Msg.play(p, Msg.sound("minecraft:ui.toast.challenge_complete", 1f, 1f));
            } else {
                Msg.send(p, "<gray>Thanks for taking part! You received a participation reward.");
            }
        }
        if (!winners.isEmpty() && plugin.getConfig().getBoolean("rewards.participation.announce-jackpots", true)) {
            Msg.broadcast("<gold>★</gold> <yellow>Jackpot winners: <white><names></white>",
                    Placeholder.unparsed("names", String.join(", ", winners)));
        }
    }

    /** Rolls the jackpot for one player (used for event winners). Returns true if won. */
    public boolean rollJackpot(UUID uuid, double chance) {
        if (!Util.chance(chance)) return false;
        give(uuid, jackpot());
        Player p = Bukkit.getPlayer(uuid);
        String name = p != null ? p.getName() : String.valueOf(Bukkit.getOfflinePlayer(uuid).getName());
        Msg.broadcast("<gold>★ <yellow><name></yellow> also hit the <gold><bold>JACKPOT</bold></gold>!", Placeholder.unparsed("name", name));
        return true;
    }
}

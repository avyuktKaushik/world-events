package dev.arc2.worldevents.events;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Golden apples, potions, shields and attacks have a 30% chance of simply not working.
 * Every failure plays a sound (and shows an action bar message).
 * Failed consumables are still used up — no free retries.
 */
public final class UnfortunateEvent extends WorldEvent implements Listener {

    private final NamespacedKey fizzleKey;
    private boolean reapplying;

    public UnfortunateEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.UNFORTUNATE);
        this.fizzleKey = new NamespacedKey(plugin, "unfortunate_fizzle");
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        return true;
    }

    private boolean roll() {
        return Util.chance(cfg().getDouble("fail-chance", 0.30));
    }

    private void fail(Player p, String what) {
        Msg.play(p, Msg.sound(cfg().getString("fail-sound", "minecraft:entity.item.break"), 1f, 0.8f));
        Msg.play(p, Msg.sound(cfg().getString("fail-sound-2", "minecraft:block.note_block.bass"), 1f, 0.5f));
        p.getWorld().playSound(Msg.sound(cfg().getString("fail-sound", "minecraft:entity.item.break"), 0.6f, 0.8f), p.getX(), p.getY(), p.getZ());
        p.sendActionBar(Msg.mm("<dark_red>✖</dark_red> <gray>Unfortunate! Your <red>" + what + "</red> didn't work."));
    }

    // ------------------------------------------------------------ golden apples & drinkable potions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!affected(p)) return;
        Material m = e.getItem().getType();
        boolean gap = m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE;
        boolean potion = m == Material.POTION;
        if (gap && !cfg().getBoolean("affect-gaps", true)) return;
        if (potion && !cfg().getBoolean("affect-potions", true)) return;
        if (!gap && !potion) return;
        if (!roll()) return;

        e.setCancelled(true);
        EquipmentSlot hand = e.getHand();
        ItemStack held = p.getInventory().getItem(hand);
        if (held.getType() == m) {
            held.setAmount(held.getAmount() - 1);
            p.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
        }
        if (potion) {
            for (ItemStack left : p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE)).values())
                p.getWorld().dropItem(p.getLocation(), left);
        }
        p.updateInventory();
        fail(p, gap ? "golden apple" : "potion");
    }

    // ------------------------------------------------------------ splash / lingering potions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrow(PlayerLaunchProjectileEvent e) {
        Material m = e.getItemStack().getType();
        if (m != Material.SPLASH_POTION && m != Material.LINGERING_POTION) return;
        Player p = e.getPlayer();
        if (!affected(p) || !cfg().getBoolean("affect-potions", true) || !roll()) return;
        e.getProjectile().getPersistentDataContainer().set(fizzleKey, PersistentDataType.BYTE, (byte) 1);
        fail(p, "potion");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (e.getPotion().getPersistentDataContainer().has(fizzleKey)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLinger(LingeringPotionSplashEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(fizzleKey)) e.setCancelled(true);
    }

    // ------------------------------------------------------------ attacks

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (reapplying || !cfg().getBoolean("affect-attacks", true)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        Player attacker = null;
        Entity d = e.getDamager();
        if (d instanceof Player p) attacker = p;
        else if (d instanceof Projectile pr && !(pr instanceof ThrownPotion)) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof Player p) attacker = p;
        }
        if (attacker == null || !affected(attacker) || !roll()) return;
        e.setCancelled(true);
        fail(attacker, "attack");
    }

    // ------------------------------------------------------------ shields

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShieldBlock(EntityDamageByEntityEvent e) {
        if (reapplying || !cfg().getBoolean("affect-shields", true)) return;
        if (!(e.getEntity() instanceof Player victim) || !affected(victim) || !victim.isBlocking()) return;
        // would the shield actually block? (attacker must be in front of the victim)
        Location src = e.getDamager().getLocation();
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity shooter && pr.getVelocity().lengthSquared() < 1.0e-4)
            src = shooter.getLocation();
        Vector toSrc = src.toVector().subtract(victim.getLocation().toVector()).setY(0);
        Vector look = victim.getLocation().getDirection().setY(0);
        if (toSrc.lengthSquared() < 1.0e-6 || look.lengthSquared() < 1.0e-6) return;
        if (toSrc.normalize().dot(look.normalize()) <= 0) return;
        if (!roll()) return;

        double raw = e.getDamage();
        DamageSource source = e.getDamageSource();
        e.setCancelled(true);
        victim.clearActiveItem();
        victim.setCooldown(Material.SHIELD, Math.max(1, cfg().getInt("shield-fail-cooldown-ticks", 20)));
        reapplying = true;
        try {
            victim.damage(raw, source);
        } finally {
            reapplying = false;
        }
        fail(victim, "shield");
    }
}

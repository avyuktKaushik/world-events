package dev.arc2.worldevents.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

/** Misc static helpers. */
public final class Util {

    private Util() {}

    public static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    public static boolean chance(double c) {
        return rnd().nextDouble() < c;
    }

    public static int range(int min, int max) {
        if (max <= min) return min;
        return min + rnd().nextInt(max - min + 1);
    }

    /** Parses "5" or "3-7" into {min,max}. */
    public static int[] parseRange(Object o, int def) {
        if (o == null) return new int[]{def, def};
        if (o instanceof Number n) return new int[]{n.intValue(), n.intValue()};
        String s = o.toString().trim();
        try {
            int dash = s.indexOf('-', 1);
            if (dash > 0) {
                int a = Integer.parseInt(s.substring(0, dash).trim());
                int b = Integer.parseInt(s.substring(dash + 1).trim());
                return new int[]{Math.min(a, b), Math.max(a, b)};
            }
            int v = Integer.parseInt(s);
            return new int[]{v, v};
        } catch (NumberFormatException e) {
            return new int[]{def, def};
        }
    }

    public static <T> T weighted(List<T> list, ToDoubleFunction<T> weight) {
        double total = 0;
        for (T t : list) total += Math.max(0, weight.applyAsDouble(t));
        if (list.isEmpty() || total <= 0) return list.isEmpty() ? null : list.get(rnd().nextInt(list.size()));
        double r = rnd().nextDouble() * total;
        for (T t : list) {
            r -= Math.max(0, weight.applyAsDouble(t));
            if (r <= 0) return t;
        }
        return list.get(list.size() - 1);
    }

    // ------------------------------------------------------------------ registries

    public static Enchantment enchantment(String key) {
        if (key == null) return null;
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT).replace("minecraft:", "")));
        } catch (Exception e) {
            return null;
        }
    }

    public static PotionEffectType effect(String key) {
        if (key == null) return null;
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)
                    .get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT).replace("minecraft:", "")));
        } catch (Exception e) {
            return null;
        }
    }

    public static PotionType potion(String key) {
        if (key == null) return null;
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION)
                    .get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT).replace("minecraft:", "")));
        } catch (Exception e) {
            return null;
        }
    }

    public static String enchantKey(Enchantment e) {
        return e.getKey().getKey();
    }

    public static boolean hasEnchant(ItemStack it, Enchantment e) {
        return it != null && e != null && !it.getType().isAir() && it.containsEnchantment(e);
    }

    // ------------------------------------------------------------------ attributes

    /**
     * Makes the attribute's final value equal {@code target} using a single ADD_NUMBER
     * modifier with our key. target &lt; 0 removes our modifier.
     */
    public static void applyAttribute(Player p, Attribute attribute, NamespacedKey key, double target) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier ours = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (key.equals(m.getKey())) {
                ours = m;
                break;
            }
        }
        if (target < 0) {
            if (ours != null) inst.removeModifier(ours);
            return;
        }
        double without = inst.getValue() - (ours != null ? ours.getAmount() : 0);
        double amount = target - without;
        if (ours != null && Math.abs(ours.getAmount() - amount) < 1.0e-3) return;
        if (ours != null) inst.removeModifier(ours);
        if (Math.abs(amount) < 1.0e-3) return;
        inst.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    // ------------------------------------------------------------------ gamerules

    /** Locator bar gamerule, looked up reflectively so a rename does not break compilation. */
    @SuppressWarnings("unchecked")
    public static GameRule<Boolean> locatorBarRule() {
        for (String name : new String[]{"LOCATOR_BAR", "LOCATORBAR"}) {
            try {
                Object o = GameRule.class.getField(name).get(null);
                if (o instanceof GameRule<?> g) return (GameRule<Boolean>) g;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ locations

    /** Random coordinates within settings.random-region. */
    public static Location randomRegionLocation(ConfigurationSection root) {
        String worldName = root.getString("settings.random-region.world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().get(0);
        int x = range(root.getInt("settings.random-region.min-x", 1), root.getInt("settings.random-region.max-x", 6000));
        int z = range(root.getInt("settings.random-region.min-z", 1), root.getInt("settings.random-region.max-z", 6000));
        return new Location(w, x + 0.5, 0, z + 0.5);
    }

    public static String worldLabel(World w) {
        if (w == null) return "?";
        return switch (w.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> "Overworld";
        };
    }

    public static Location readLocation(ConfigurationSection s) {
        if (s == null) return null;
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public static void writeLocation(ConfigurationSection s, Location l) {
        s.set("world", l.getWorld() == null ? "world" : l.getWorld().getName());
        s.set("x", l.getX());
        s.set("y", l.getY());
        s.set("z", l.getZ());
        s.set("yaw", (double) l.getYaw());
        s.set("pitch", (double) l.getPitch());
    }
}

package dev.arc2.worldevents.event;

import org.bukkit.Material;

import java.util.Locale;

public enum EventType {
    VULNERABLE("vulnerable", "<red><bold>Vulnerable</bold>", Material.FERMENTED_SPIDER_EYE, 30,
            "Everyone is reduced to <red>7 hearts</red>."),
    RAGS_TO_RICHES("rags_to_riches", "<green><bold>Rags to Riches</bold>", Material.EMERALD, 30,
            "Everyone receives <green>Hero of the Village 255</green>."),
    GLOW_AND_BEHOLD("glow_and_behold", "<yellow><bold>Glow and Behold</bold>", Material.GLOW_INK_SAC, 30,
            "Everyone is given the <yellow>Glowing</yellow> effect."),
    LOCATOR_BAR("locator_bar", "<aqua><bold>Locator Bar</bold>", Material.RECOVERY_COMPASS, 30,
            "The <aqua>Locator Bar</aqua> is activated — you can track other players."),
    LOOT_RAIN("loot_rain", "<gold><bold>Loot Rain</bold>", Material.CHEST, 6,
            "Loot rains from the sky at a <gold>specific location</gold>."),
    THE_HUNTED("the_hunted", "<dark_red><bold>THE HUNTED</bold>", Material.TARGET, 30,
            "One player receives a <red>bounty</red> and buffs, but their coordinates are leaked to everyone."),
    LOOTING_3000("looting_3000", "<light_purple><bold>Looting 3000</bold>", Material.DIAMOND_ORE, 30,
            "All natural ore and mob drops are multiplied <light_purple>4×</light_purple>."),
    SHORT_REACH("short_reach", "<gray><bold>Short Reach</bold>", Material.STICK, 30,
            "Everyone's reach is reduced from <white>3</white> to <white>1.5</white> blocks."),
    BOSS("boss", "<dark_purple><bold>Boss Event</bold>", Material.WITHER_SKELETON_SKULL, 30,
            "A powerful <dark_purple>boss</dark_purple> is summoned. Defeat it for OP loot."),
    MONEY_TREES("money_grows_on_trees", "<gold><bold>Money Grows on Trees</bold>", Material.OAK_LEAVES, 30,
            "Tree leaves drop <gold>Golden Apples</gold>."),
    AUCTION("auction", "<yellow><bold>Auction</bold>", Material.GOLD_INGOT, 5,
            "Sacrifice items with <yellow>/sacrifice</yellow>, then bid on OP loot in chat."),
    SMARTIE_PANTS("smartie_pants", "<blue><bold>Smartie Pants</bold>", Material.WRITABLE_BOOK, 10,
            "Math questions appear in chat. First correct answer wins <gold>OP loot</gold>."),
    TWISTS_AND_TURNS("twists_and_turns", "<dark_green><bold>Twists and Turns</bold>", Material.MOSSY_STONE_BRICKS, 10,
            "Blindness + Adventure Mode — you're teleported into a <dark_green>maze</dark_green> full of loot."),
    TANKY("tanky", "<red><bold>Tanky</bold>", Material.IRON_CHESTPLATE, 30,
            "Everyone receives <red>20 hearts</red>."),
    UNFORTUNATE("unfortunate", "<dark_gray><bold>Unfortunate</bold>", Material.POISONOUS_POTATO, 30,
            "Gaps, potions, shields and attacks have a <red>30%</red> chance of not working.");

    private final String id;
    private final String defaultName;
    private final Material icon;
    private final int defaultMinutes;
    private final String defaultDescription;

    EventType(String id, String defaultName, Material icon, int defaultMinutes, String defaultDescription) {
        this.id = id;
        this.defaultName = defaultName;
        this.icon = icon;
        this.defaultMinutes = defaultMinutes;
        this.defaultDescription = defaultDescription;
    }

    public String id() { return id; }
    public String defaultName() { return defaultName; }
    public Material icon() { return icon; }
    public int defaultMinutes() { return defaultMinutes; }
    public String defaultDescription() { return defaultDescription; }

    public static EventType fromId(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT).replace('-', '_');
        for (EventType t : values()) if (t.id.equals(k) || t.name().equalsIgnoreCase(k)) return t;
        // friendly aliases
        return switch (k) {
            case "hunted", "bounty" -> THE_HUNTED;
            case "trees", "money_trees" -> MONEY_TREES;
            case "maze" -> TWISTS_AND_TURNS;
            case "math", "smartie" -> SMARTIE_PANTS;
            case "glow" -> GLOW_AND_BEHOLD;
            case "locator" -> LOCATOR_BAR;
            case "looting" -> LOOTING_3000;
            case "rags" -> RAGS_TO_RICHES;
            default -> null;
        };
    }
}

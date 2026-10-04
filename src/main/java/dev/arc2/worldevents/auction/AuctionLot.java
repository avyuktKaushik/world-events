package dev.arc2.worldevents.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One thing being auctioned.
 *
 * @param name       MiniMessage display name (already includes the amount)
 * @param lore       optional MiniMessage hover/extra line
 * @param basePrice  starting price before economy scaling
 * @param scale      whether the starting price scales with how rich the bidders are
 * @param delivery   how the winner gets it
 * @param item       the item (for {@link Delivery#ITEM})
 * @param owner      admin who put the item up (gets it back if nobody bids)
 */
public record AuctionLot(String name, String lore, double basePrice, boolean scale, Delivery delivery, ItemStack item, UUID owner) {

    public enum Delivery {
        /** Only announced — an admin hands it out manually (logged to auction-winners.log). */
        ANNOUNCE,
        /** The plugin gives the item to the winner. */
        ITEM,
        /** The plugin gives the winner an OP Villager spawn item. */
        OP_VILLAGER
    }
}

package org.example.sawervatoryy.mirageAuction.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class StoredItem {

    public final int id;
    public final UUID owner;
    public ItemStack item;
    /** EXPIRED, CANCELLED, WON, PURCHASE, REMOVED, OVERFLOW */
    public final String reason;
    public final long time;

    public StoredItem(int id, UUID owner, ItemStack item, String reason, long time) {
        this.id = id;
        this.owner = owner;
        this.item = item;
        this.reason = reason;
        this.time = time;
    }
}

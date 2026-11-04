package org.example.sawervatoryy.mirageAuction.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class Lot {

    public final int id;
    public final UUID seller;
    public final String sellerName;
    public final ItemStack item;
    public final boolean bid;
    public final double price;
    /** Цена мгновенного выкупа в режиме торгов (0 = нет). */
    public final double buyout;
    public final long created;

    public double currentBid;
    public UUID bidder;
    public String bidderName;
    public int bidCount;
    public long expires;
    public int extensions;

    public Lot(int id, UUID seller, String sellerName, ItemStack item, boolean bid, double price, double buyout, long created, long expires) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.bid = bid;
        this.price = price;
        this.buyout = buyout;
        this.created = created;
        this.expires = expires;
    }

    /** Цена, по которой лот будет продан сейчас. */
    public double effectivePrice() {
        return bid && bidCount > 0 ? currentBid : price;
    }
}

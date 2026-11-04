package org.example.sawervatoryy.mirageAuction.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public final class AuctionHolder implements InventoryHolder {

    public enum Type { MAIN, CONFIRM, BID, STORAGE, MYLOTS }

    public final Type type;
    public final Session session;
    public Inventory inventory;

    /** id лота для CONFIRM / BID. */
    public int lotId;
    /** Страница для STORAGE / MYLOTS. */
    public int page;
    public int pages = 1;
    public int total;
    public String title = "";
    /** slot -> id лота (MAIN, MYLOTS) или id предмета хранилища (STORAGE). */
    public final Map<Integer, Integer> slotRef = new HashMap<>();
    /** slot -> сумма ставки (BID). */
    public final Map<Integer, Double> slotAmount = new HashMap<>();

    public BukkitTask animation;
    public boolean animating;

    public AuctionHolder(Type type, Session session) {
        this.type = type;
        this.session = session;
    }

    public void cancelAnimation() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        animating = false;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

package org.example.sawervatoryy.mirageAuction.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.example.sawervatoryy.mirageAuction.MirageAuction;
import org.example.sawervatoryy.mirageAuction.gui.AuctionHolder;
import org.example.sawervatoryy.mirageAuction.util.Text;

import java.util.List;

public final class GuiListener implements Listener {

    private final MirageAuction plugin;

    public GuiListener(MirageAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof AuctionHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (!plugin.gui().allowClick(p)) return;
        plugin.gui().click(p, h, e.getRawSlot(), e.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof AuctionHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof AuctionHolder h) h.cancelAnimation();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.gui().forget(e.getPlayer().getUniqueId());
        plugin.chatInput().cancel(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            List<String> mail = plugin.manager().popMail(p.getUniqueId());
            for (String line : mail) plugin.messages().sendRaw(p, line);
            int stored = plugin.manager().storageCount(p.getUniqueId());
            if (stored > 0) plugin.messages().send(p, "storage-notify", "count", String.valueOf(stored));
        }, 50L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!plugin.chatInput().has(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = Text.plain(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.chatInput().complete(p, text));
    }
}

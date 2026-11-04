package org.example.sawervatoryy.mirageAuction.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.example.sawervatoryy.mirageAuction.MirageAuction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Ввод значения через чат (поиск, своя ставка). Callback получает null при отмене/таймауте. */
public final class ChatInput {

    private record Pending(Consumer<String> callback, BukkitTask timeout) {}

    private final MirageAuction plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(MirageAuction plugin) {
        this.plugin = plugin;
    }

    public boolean has(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void request(Player p, Consumer<String> callback) {
        cancel(p.getUniqueId());
        long ticks = Math.max(5, plugin.configs().config().getInt("chat-input.timeout-seconds", 30)) * 20L;
        UUID id = p.getUniqueId();
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending pd = pending.remove(id);
            if (pd != null) {
                Player online = Bukkit.getPlayer(id);
                if (online != null) plugin.messages().send(online, "input-timeout");
                pd.callback().accept(null);
            }
        }, ticks);
        pending.put(id, new Pending(callback, t));
    }

    public void cancel(UUID id) {
        Pending pd = pending.remove(id);
        if (pd != null) pd.timeout().cancel();
    }

    /** Вызывается в основном потоке с текстом сообщения игрока. */
    public void complete(Player p, String text) {
        Pending pd = pending.remove(p.getUniqueId());
        if (pd == null) return;
        pd.timeout().cancel();
        String t = text == null ? "" : text.trim();
        for (String w : plugin.configs().config().getStringList("chat-input.cancel-words")) {
            if (t.equalsIgnoreCase(w)) {
                pd.callback().accept(null);
                return;
            }
        }
        pd.callback().accept(t);
    }
}

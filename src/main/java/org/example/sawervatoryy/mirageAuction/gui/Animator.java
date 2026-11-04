package org.example.sawervatoryy.mirageAuction.gui;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.example.sawervatoryy.mirageAuction.MirageAuction;
import org.example.sawervatoryy.mirageAuction.util.Sounds;
import org.example.sawervatoryy.mirageAuction.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Animator {

    private Animator() {}

    /**
     * @param mode        "open" или "switch" (секции animation.open / animation.switch)
     * @param titleFrames кадры заголовка (шаблоны) или null
     */
    public static void play(MirageAuction plugin, Player p, AuctionHolder h, ItemStack[] content, List<String> titleFrames, String mode) {
        ConfigurationSection cfg = plugin.configs().config().getConfigurationSection("animation." + mode);
        String style = cfg == null ? "WAVE" : cfg.getString("style", "WAVE");
        int period = Math.max(1, cfg == null ? 1 : cfg.getInt("step-ticks", 1));
        int perStep = Math.max(1, cfg == null ? 3 : cfg.getInt("per-step", 3));
        int titleEvery = Math.max(1, plugin.configs().config().getInt("animation.title.step-ticks", 2));
        double pMin = plugin.configs().config().getDouble("animation.reveal-sound-pitch.min", 0.7);
        double pMax = plugin.configs().config().getDouble("animation.reveal-sound-pitch.max", 2.0);

        final Inventory inv = h.inventory;
        final List<Integer> seq = order(style, content);
        final List<String> frames = titleFrames == null ? new ArrayList<>() : titleFrames;

        h.cancelAnimation();
        h.animating = true;
        h.animation = new BukkitRunnable() {
            int idx = 0;
            int frame = 0;
            int elapsed = 0;
            int nextTitleAt = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    h.animating = false;
                    cancel();
                    return;
                }
                for (int k = 0; k < perStep && idx < seq.size(); k++) {
                    int slot = seq.get(idx++);
                    inv.setItem(slot, content[slot]);
                }
                if (!seq.isEmpty()) {
                    float pitch = (float) (pMin + (pMax - pMin) * Math.min(1.0, (double) idx / seq.size()));
                    Sounds.playPitch(p, "reveal", pitch);
                }
                if (frame < frames.size() && elapsed >= nextTitleAt) {
                    InventoryView v = p.getOpenInventory();
                    if (v.getTopInventory() == inv) v.setTitle(Text.color(frames.get(frame)));
                    frame++;
                    nextTitleAt += titleEvery;
                }
                elapsed += period;
                if (idx >= seq.size() && frame >= frames.size()) {
                    h.animating = false;
                    Sounds.play(p, "reveal-done");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    /** Порядок появления слотов (только непустые) для сетки 6x9. */
    static List<Integer> order(String style, ItemStack[] content) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < content.length; i++) if (content[i] != null) all.add(i);
        switch (style.toUpperCase(Locale.ROOT)) {
            case "WAVE" -> all.sort(Comparator.comparingInt((Integer s) -> s / 9 + s % 9).thenComparingInt(s -> s));
            case "CENTER" -> all.sort(Comparator.comparingDouble((Integer s) -> {
                double dr = s / 9 - 2.5;
                double dc = s % 9 - 4.0;
                return dr * dr * 2.2 + dc * dc;
            }).thenComparingInt(s -> s));
            case "COLUMNS" -> all.sort(Comparator.comparingInt((Integer s) -> s % 9).thenComparingInt(s -> s));
            case "RANDOM" -> Collections.shuffle(all);
            case "SNAKE" -> all.sort(Comparator.comparingInt((Integer s) -> {
                int r = s / 9;
                int c = s % 9;
                return r * 9 + (r % 2 == 0 ? c : 8 - c);
            }));
            case "SPIRAL" -> {
                List<Integer> spiral = new ArrayList<>();
                int top = 0, bottom = 5, left = 0, right = 8;
                while (top <= bottom && left <= right) {
                    for (int c = left; c <= right; c++) spiral.add(top * 9 + c);
                    for (int r = top + 1; r <= bottom; r++) spiral.add(r * 9 + right);
                    if (top < bottom) for (int c = right - 1; c >= left; c--) spiral.add(bottom * 9 + c);
                    if (left < right) for (int r = bottom - 1; r > top; r--) spiral.add(r * 9 + left);
                    top++;
                    bottom--;
                    left++;
                    right--;
                }
                List<Integer> out = new ArrayList<>();
                for (int s : spiral) if (s < content.length && content[s] != null) out.add(s);
                return out;
            }
            default -> {
            }
        }
        return all;
    }

    /** Кадры «печатающегося» заголовка + финальный заголовок. */
    public static List<String> titleFrames(MirageAuction plugin, String finalTitle) {
        List<String> frames = new ArrayList<>();
        ConfigurationSection t = plugin.configs().config().getConfigurationSection("animation.title");
        if (t == null || !t.getBoolean("enabled", true)) return frames;
        String text = t.getString("text", "AUCTION");
        boolean bold = t.getBoolean("bold", true);
        for (int i = 1; i <= text.length(); i++) {
            frames.add(Text.themeGradientPrefix(text, i, bold) + (i < text.length() ? "{accent}_" : ""));
        }
        frames.add(finalTitle);
        return frames;
    }
}

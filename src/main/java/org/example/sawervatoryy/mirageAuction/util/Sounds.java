package org.example.sawervatoryy.mirageAuction.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/** Звуки из config.yml, формат: "minecraft:block.note_block.chime;1.0;1.2" (ключ;громкость;тон). */
public final class Sounds {

    private record Snd(String key, float volume, float pitch) {}

    private static final Map<String, Snd> sounds = new HashMap<>();
    private static boolean enabled = true;

    private Sounds() {}

    public static void load(ConfigurationSection sec) {
        sounds.clear();
        enabled = sec == null || sec.getBoolean("enabled", true);
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            if (!sec.isString(id)) continue;
            String raw = sec.getString(id, "");
            if (raw.isBlank() || raw.equalsIgnoreCase("none")) continue;
            String[] p = raw.split(";");
            try {
                float vol = p.length > 1 ? Float.parseFloat(p[1].trim()) : 1f;
                float pitch = p.length > 2 ? Float.parseFloat(p[2].trim()) : 1f;
                sounds.put(id, new Snd(p[0].trim(), vol, pitch));
            } catch (NumberFormatException ignored) {
                sounds.put(id, new Snd(p[0].trim(), 1f, 1f));
            }
        }
    }

    public static void play(Player p, String id) {
        playPitch(p, id, -1f);
    }

    public static void playPitch(Player p, String id, float pitchOverride) {
        if (!enabled || p == null) return;
        Snd s = sounds.get(id);
        if (s == null) return;
        p.playSound(p.getLocation(), s.key(), s.volume(), pitchOverride > 0 ? pitchOverride : s.pitch());
    }
}

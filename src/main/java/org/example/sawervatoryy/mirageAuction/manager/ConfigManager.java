package org.example.sawervatoryy.mirageAuction.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.example.sawervatoryy.mirageAuction.MirageAuction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigManager {

    private final MirageAuction plugin;
    private YamlConfiguration config;
    private YamlConfiguration messages;
    private YamlConfiguration gui;

    public ConfigManager(MirageAuction plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = read("config.yml");
        messages = read("messages.yml");
        gui = read("gui.yml");
    }

    private YamlConfiguration read(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) plugin.saveResource(name, false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // значения по умолчанию недоступны — работаем с файлом как есть
        }
        return y;
    }

    public YamlConfiguration config() {
        return config;
    }

    public YamlConfiguration messages() {
        return messages;
    }

    public YamlConfiguration gui() {
        return gui;
    }

    /** Меняет строку `theme: "..."` в config.yml, сохраняя комментарии. */
    public boolean writeActiveTheme(String name) {
        File f = new File(plugin.getDataFolder(), "config.yml");
        try {
            String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("(?m)^theme:.*$");
            Matcher m = p.matcher(text);
            String out = m.find() ? m.replaceFirst(Matcher.quoteReplacement("theme: \"" + name + "\"")) : "theme: \"" + name + "\"\n" + text;
            Files.writeString(f.toPath(), out, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить тему: " + e.getMessage());
            return false;
        }
    }
}

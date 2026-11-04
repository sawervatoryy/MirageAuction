package org.example.sawervatoryy.mirageAuction.manager;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.example.sawervatoryy.mirageAuction.MirageAuction;
import org.example.sawervatoryy.mirageAuction.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ThemeManager {

    private final MirageAuction plugin;
    private String active = "mirage";
    private ConfigurationSection current;

    public ThemeManager(MirageAuction plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ConfigurationSection themes = plugin.configs().config().getConfigurationSection("themes");
        String want = plugin.configs().config().getString("theme", "mirage");
        current = null;
        if (themes != null) {
            if (themes.isConfigurationSection(want)) {
                active = want;
                current = themes.getConfigurationSection(want);
            } else {
                for (String k : themes.getKeys(false)) {
                    if (themes.isConfigurationSection(k)) {
                        active = k;
                        current = themes.getConfigurationSection(k);
                        break;
                    }
                }
            }
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("primary", "&f");
        vars.put("secondary", "&7");
        vars.put("accent", "&#FA4249");
        vars.put("dark", "&#C80000");
        vars.put("success", "&a");
        vars.put("error", "&c");
        List<String> gradient = new ArrayList<>(List.of("#FA4249", "#C80000"));
        if (current != null) {
            for (String k : current.getKeys(false)) {
                if (current.isString(k) && !k.startsWith("pane") && !k.equals("filler")) {
                    vars.put(k, current.getString(k));
                }
            }
            List<String> g = current.getStringList("gradient");
            if (!g.isEmpty()) gradient = g;
        }
        Text.setTheme(vars, gradient);
    }

    public String active() {
        return active;
    }

    public Set<String> names() {
        ConfigurationSection themes = plugin.configs().config().getConfigurationSection("themes");
        return themes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(themes.getKeys(false));
    }

    public boolean exists(String name) {
        return names().contains(name);
    }

    public boolean set(String name) {
        if (!exists(name)) return false;
        boolean ok = plugin.configs().writeActiveTheme(name);
        plugin.configs().config().set("theme", name);
        load();
        return ok;
    }

    public Material material(String key, Material def) {
        if (current == null) return def;
        Material m = Material.matchMaterial(current.getString(key, ""));
        return m == null ? def : m;
    }

    public Material filler() {
        return material("filler", Material.BLACK_STAINED_GLASS_PANE);
    }

    public Material paneAccent() {
        return material("pane-accent", Material.RED_STAINED_GLASS_PANE);
    }

    public Material panePrimary() {
        return material("pane-primary", Material.WHITE_STAINED_GLASS_PANE);
    }
}

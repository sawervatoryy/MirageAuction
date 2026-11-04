package org.example.sawervatoryy.mirageAuction.manager;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.example.sawervatoryy.mirageAuction.model.Category;

import java.util.ArrayList;
import java.util.List;

public final class CategoryManager {

    private final List<Category> list = new ArrayList<>();
    private Category fallback;

    public void load(ConfigurationSection sec) {
        list.clear();
        fallback = null;
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(id);
            if (c == null) continue;
            Material mat = Material.matchMaterial(c.getString("material", "STONE"));
            if (mat == null) mat = Material.STONE;
            Category cat = new Category(id, c.getInt("slot", 0), mat, c.getString("name", id),
                    c.getStringList("lore"), c.getStringList("match"), c.getBoolean("fallback", false));
            list.add(cat);
            if (cat.fallback && fallback == null) fallback = cat;
        }
    }

    public List<Category> all() {
        return list;
    }

    public Category get(String id) {
        for (Category c : list) if (c.id.equalsIgnoreCase(id)) return c;
        return null;
    }

    /** Возвращает id категории предмета (первая подошедшая; иначе fallback). */
    public String categorize(ItemStack it) {
        for (Category c : list) {
            if (c.all || c.fallback) continue;
            if (c.matches(it)) return c.id;
        }
        return fallback != null ? fallback.id : "misc";
    }

    public String displayName(String id) {
        Category c = get(id);
        return c == null ? id : c.name;
    }
}

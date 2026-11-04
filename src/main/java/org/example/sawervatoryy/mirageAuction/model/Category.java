package org.example.sawervatoryy.mirageAuction.model;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

public final class Category {

    public final String id;
    public final int slot;
    public final Material material;
    public final String name;
    public final List<String> lore;
    public final List<String> match;
    public final boolean fallback;
    public final boolean all;

    public Category(String id, int slot, Material material, String name, List<String> lore, List<String> match, boolean fallback) {
        this.id = id;
        this.slot = slot;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.match = match;
        this.fallback = fallback;
        boolean a = false;
        for (String t : match) if (t.equalsIgnoreCase("@all")) a = true;
        this.all = a;
    }

    public boolean matches(ItemStack it) {
        if (all) return true;
        Material m = it.getType();
        String n = m.name();
        for (String token : match) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("@")) {
                switch (t.toLowerCase(Locale.ROOT)) {
                    case "@edible" -> {
                        if (m.isEdible()) return true;
                    }
                    case "@block" -> {
                        if (m.isBlock()) return true;
                    }
                    case "@enchanted" -> {
                        if (isEnchanted(it)) return true;
                    }
                    case "@potion" -> {
                        if (n.endsWith("POTION") || n.equals("TIPPED_ARROW")) return true;
                    }
                    default -> {
                    }
                }
                continue;
            }
            boolean suffixWild = t.startsWith("*");
            boolean prefixWild = t.endsWith("*");
            String core = t.replace("*", "").toUpperCase(Locale.ROOT);
            boolean ok;
            if (suffixWild && prefixWild) ok = n.contains(core);
            else if (prefixWild) ok = n.startsWith(core);
            else if (suffixWild) ok = n.endsWith(core);
            else ok = n.equals(core);
            if (ok) return true;
        }
        return false;
    }

    public static boolean isEnchanted(ItemStack it) {
        if (!it.getEnchantments().isEmpty()) return true;
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta esm) {
            for (Enchantment ignored : esm.getStoredEnchants().keySet()) return true;
        }
        return false;
    }
}

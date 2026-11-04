package org.example.sawervatoryy.mirageAuction.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.example.sawervatoryy.mirageAuction.util.Text;

import java.util.List;
import java.util.Map;

public final class Items {

    private Items() {}

    public static ItemStack make(Material mat, String name, List<String> lore, int amount, boolean glow) {
        ItemStack it = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        if (name != null) m.displayName(Text.component(name));
        if (lore != null && !lore.isEmpty()) m.lore(Text.components(lore));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        if (glow) m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack filler(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.displayName(Text.component("&r"));
        m.setHideTooltip(true);
        it.setItemMeta(m);
        return it;
    }

    /** Кнопка из секции gui.yml: slot, material, name, lore, amount, glow. */
    public static final class Button {
        public final int slot;
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final int amount;
        private final boolean glow;

        public Button(ConfigurationSection s, int defSlot, Material defMat) {
            if (s == null) {
                slot = defSlot;
                material = defMat;
                name = "&f";
                lore = List.of();
                amount = 1;
                glow = false;
                return;
            }
            slot = s.getInt("slot", defSlot);
            Material m = Material.matchMaterial(s.getString("material", defMat.name()));
            material = m == null ? defMat : m;
            name = s.getString("name", "&f");
            lore = s.getStringList("lore");
            amount = s.getInt("amount", 1);
            glow = s.getBoolean("glow", false);
        }

        public ItemStack build(Map<String, String> ph) {
            return make(material, Text.apply(name, ph), Text.expandLines(lore, ph, null), amount, glow);
        }

        public ItemStack build(Map<String, String> ph, boolean forceGlow) {
            return make(material, Text.apply(name, ph), Text.expandLines(lore, ph, null), amount, glow || forceGlow);
        }
    }
}

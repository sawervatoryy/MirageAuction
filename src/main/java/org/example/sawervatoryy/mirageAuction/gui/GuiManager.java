package org.example.sawervatoryy.mirageAuction.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.example.sawervatoryy.mirageAuction.MirageAuction;
import org.example.sawervatoryy.mirageAuction.manager.AuctionManager;
import org.example.sawervatoryy.mirageAuction.model.Category;
import org.example.sawervatoryy.mirageAuction.model.Lot;
import org.example.sawervatoryy.mirageAuction.model.SortMode;
import org.example.sawervatoryy.mirageAuction.model.StoredItem;
import org.example.sawervatoryy.mirageAuction.util.Money;
import org.example.sawervatoryy.mirageAuction.util.Sounds;
import org.example.sawervatoryy.mirageAuction.util.Text;
import org.example.sawervatoryy.mirageAuction.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GuiManager {

    private final MirageAuction plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public GuiManager(MirageAuction plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection g() {
        return plugin.configs().gui();
    }

    public Session session(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), k -> {
            Session s = new Session();
            s.sort = SortMode.parse(plugin.configs().config().getString("gui.default-sort", "NEWEST"), SortMode.NEWEST);
            return s;
        });
    }

    public void forget(UUID id) {
        sessions.remove(id);
        lastClick.remove(id);
    }

    public boolean allowClick(Player p) {
        long cd = plugin.configs().config().getLong("gui.click-cooldown-ms", 120);
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < cd) return false;
        lastClick.put(p.getUniqueId(), now);
        return true;
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof AuctionHolder h) {
                h.cancelAnimation();
                p.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static List<Integer> parseSlots(List<?> raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof Number n) {
                out.add(n.intValue());
                continue;
            }
            String s = String.valueOf(o).trim();
            try {
                if (s.contains("-")) {
                    String[] p = s.split("-");
                    int a = Integer.parseInt(p[0].trim());
                    int b = Integer.parseInt(p[1].trim());
                    for (int i = Math.min(a, b); i <= Math.max(a, b); i++) out.add(i);
                } else if (!s.isEmpty()) {
                    out.add(Integer.parseInt(s));
                }
            } catch (NumberFormatException ignored) {
                // неверный слот в конфиге
            }
        }
        out.removeIf(i -> i < 0 || i > 53);
        return out;
    }

    private Items.Button button(String path, int slot, Material mat) {
        return new Items.Button(g().getConfigurationSection(path), slot, mat);
    }

    private ItemStack filler() {
        return Items.filler(plugin.themes().filler());
    }

    private String categoryName(String id) {
        return Text.strip(plugin.categories().displayName(id));
    }

    private Map<String, String> lotPlaceholders(Lot l) {
        Map<String, String> ph = new HashMap<>();
        ph.put("id", String.valueOf(l.id));
        ph.put("seller", l.sellerName);
        ph.put("price", Money.format(l.price));
        ph.put("start", Money.format(l.price));
        ph.put("amount", String.valueOf(l.item.getAmount()));
        ph.put("unit_price", l.item.getAmount() > 1 ? Money.format(l.price / l.item.getAmount()) : null);
        ph.put("time", TimeUtil.format(l.expires - System.currentTimeMillis()));
        ph.put("category", categoryName(plugin.categories().categorize(l.item)));
        ph.put("bid", l.bidCount > 0 ? Money.format(l.currentBid) : g().getString("lot-lore.no-bids", "нет"));
        ph.put("bidder", l.bidCount > 0 && l.bidderName != null ? l.bidderName : "—");
        ph.put("bids", String.valueOf(l.bidCount));
        ph.put("buyout", l.buyout > 0 ? Money.format(l.buyout) : null);
        ph.put("min_bid", Money.format(plugin.manager().minNextBid(l)));
        ph.put("item", Text.itemLabel(l.item));
        return ph;
    }

    /** Предмет лота с лором из gui.yml. withHints=false — без подсказок по кликам (меню подтверждения). */
    private ItemStack lotItem(Player viewer, Lot l, boolean withHints) {
        boolean own = l.seller.equals(viewer.getUniqueId());
        List<String> hints = new ArrayList<>();
        if (withHints) {
            String key = own ? (l.bid && l.bidCount > 0 ? "own-bid" : "own") : (l.bid ? "bid" : "fixed");
            hints = g().getStringList("lot-lore.hints." + key);
        }
        List<String> lines = Text.expandLines(g().getStringList(l.bid ? "lot-lore.bid" : "lot-lore.fixed"), lotPlaceholders(l), hints);
        ItemStack it = l.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        List<net.kyori.adventure.text.Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.addAll(Text.components(lines));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private void msg(Player p, String key, String... kv) {
        plugin.messages().send(p, key, kv);
    }

    // ------------------------------------------------------------------ MAIN

    private Map<String, String> mainPlaceholders(Player p, AuctionHolder h) {
        Session s = h.session;
        int mine = plugin.manager().countLots(p.getUniqueId());
        int max = plugin.manager().maxLots(p);
        Map<String, String> ph = new HashMap<>();
        ph.put("page", String.valueOf(s.page + 1));
        ph.put("pages", String.valueOf(h.pages));
        ph.put("total", String.valueOf(h.total));
        ph.put("category", categoryName(s.category));
        ph.put("sort", g().getString("sort-names." + s.sort.name(), s.sort.name()));
        ph.put("mode", g().getString("mode-names." + s.mode.name(), s.mode.name()));
        ph.put("search", s.search == null ? g().getString("main.search-none", "не задан") : s.search);
        ph.put("balance", plugin.economy().ready() ? Money.format(plugin.economy().balance(p)) : "—");
        ph.put("my_lots", String.valueOf(mine));
        ph.put("limit", max == Integer.MAX_VALUE ? "∞" : String.valueOf(max));
        ph.put("storage", String.valueOf(plugin.manager().storageCount(p.getUniqueId())));
        ph.put("all_lots", String.valueOf(plugin.manager().totalLots()));
        return ph;
    }

    private ItemStack[] buildMain(Player p, AuctionHolder h) {
        Session s = h.session;
        ItemStack[] out = new ItemStack[54];
        List<Integer> slots = parseSlots(g().getList("main.lot-slots", List.of("9-44")));
        int per = Math.max(1, slots.size());
        List<Lot> lots = plugin.manager().query(s.category, s.mode, s.search, s.sort);
        h.total = lots.size();
        h.pages = Math.max(1, (lots.size() + per - 1) / per);
        s.page = Math.max(0, Math.min(s.page, h.pages - 1));
        h.slotRef.clear();
        int start = s.page * per;
        for (int i = 0; i < per && start + i < lots.size() && i < slots.size(); i++) {
            Lot l = lots.get(start + i);
            out[slots.get(i)] = lotItem(p, l, true);
            h.slotRef.put(slots.get(i), l.id);
        }

        Map<String, String> ph = mainPlaceholders(p, h);

        // категории + счётчики
        Map<String, Integer> counts = new HashMap<>();
        int totalActive = 0;
        long now = System.currentTimeMillis();
        for (Lot l : plugin.manager().allLots()) {
            if (l.expires <= now) continue;
            totalActive++;
            counts.merge(plugin.categories().categorize(l.item), 1, Integer::sum);
        }
        for (Category c : plugin.categories().all()) {
            if (c.slot < 0 || c.slot > 53) continue;
            boolean selected = c.id.equalsIgnoreCase(s.category);
            Map<String, String> cph = new HashMap<>(ph);
            cph.put("count", String.valueOf(c.all ? totalActive : counts.getOrDefault(c.id, 0)));
            cph.put("selected", selected ? g().getString("main.selected-text", "{success}✔ Выбрано") : null);
            cph.put("hint", selected ? null : g().getString("main.select-hint", "{accent}▸ {primary}ЛКМ {secondary}— открыть"));
            out[c.slot] = Items.make(c.material, Text.apply(c.name, cph), Text.expandLines(c.lore, cph, null), 1, selected);
        }

        // кнопки
        if (s.page > 0) out[button("main.buttons.prev", 45, Material.ARROW).slot] = button("main.buttons.prev", 45, Material.ARROW).build(ph);
        if (s.page < h.pages - 1) out[button("main.buttons.next", 53, Material.ARROW).slot] = button("main.buttons.next", 53, Material.ARROW).build(ph);
        put(out, button("main.buttons.sort", 46, Material.HOPPER), ph);
        put(out, button("main.buttons.mode", 47, Material.COMPARATOR), ph);
        put(out, button("main.buttons.my-lots", 48, Material.CHEST), ph);
        put(out, button("main.buttons.info", 49, Material.PLAYER_HEAD), ph);
        put(out, button("main.buttons.storage", 50, Material.ENDER_CHEST), ph);
        Items.Button searchBtn = button("main.buttons.search", 51, Material.OAK_SIGN);
        out[searchBtn.slot] = searchBtn.build(ph, s.search != null);
        put(out, button("main.buttons.refresh", 52, Material.CLOCK), ph);

        if (lots.isEmpty()) {
            Items.Button empty = button("main.buttons.empty", g().getInt("main.empty-slot", 22), Material.BARRIER);
            out[empty.slot] = empty.build(ph);
        }

        if (g().getBoolean("main.filler", true)) {
            ItemStack f = filler();
            for (int i = 0; i < 54; i++) {
                if (out[i] == null && !slots.contains(i)) out[i] = f;
            }
        }
        return out;
    }

    private void put(ItemStack[] out, Items.Button b, Map<String, String> ph) {
        if (b.slot >= 0 && b.slot < out.length) out[b.slot] = b.build(ph);
    }

    private String mainTitle(Player p, AuctionHolder h) {
        return Text.apply(g().getString("main.title", "{gradb:AUCTION} {secondary}» {primary}{category}"), mainPlaceholders(p, h));
    }

    public void openMain(Player p, boolean animate) {
        Session s = session(p);
        AuctionHolder h = new AuctionHolder(AuctionHolder.Type.MAIN, s);
        ItemStack[] content = buildMain(p, h);
        h.title = mainTitle(p, h);
        Inventory inv = Bukkit.createInventory(h, 54, Text.component(h.title));
        h.inventory = inv;
        boolean anim = animate && plugin.configs().config().getBoolean("animation.enabled", true);
        if (!anim) {
            for (int i = 0; i < 54; i++) inv.setItem(i, content[i]);
            p.openInventory(inv);
            Sounds.play(p, "open");
            return;
        }
        p.openInventory(inv);
        Sounds.play(p, "open");
        Animator.play(plugin, p, h, content, Animator.titleFrames(plugin, h.title), "open");
    }

    /** Перерисовка главного меню на месте. */
    private void refreshMain(Player p, AuctionHolder h, boolean animate) {
        h.cancelAnimation();
        ItemStack[] content = buildMain(p, h);
        Inventory inv = h.inventory;
        String title = mainTitle(p, h);
        if (!title.equals(h.title)) {
            h.title = title;
            InventoryView v = p.getOpenInventory();
            if (v.getTopInventory() == inv) v.setTitle(Text.color(title));
        }
        boolean anim = animate && plugin.configs().config().getBoolean("animation.enabled", true)
                && plugin.configs().config().getBoolean("animation.switch.enabled", true);
        if (anim) {
            inv.clear();
            Animator.play(plugin, p, h, content, null, "switch");
        } else {
            for (int i = 0; i < 54; i++) inv.setItem(i, content[i]);
        }
    }

    /** Периодическое обновление таймеров/цен у открытых меню. */
    public void autoRefresh() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder(false) instanceof AuctionHolder h)) continue;
            if (h.animating) continue;
            switch (h.type) {
                case MAIN -> {
                    ItemStack[] content = buildMain(p, h);
                    for (int i = 0; i < 54; i++) h.inventory.setItem(i, content[i]);
                }
                case MYLOTS, STORAGE -> renderList(p, h);
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ click dispatch

    public void click(Player p, AuctionHolder h, int slot, ClickType click) {
        switch (h.type) {
            case MAIN -> clickMain(p, h, slot, click);
            case CONFIRM -> clickConfirm(p, h, slot);
            case BID -> clickBid(p, h, slot);
            case STORAGE, MYLOTS -> clickList(p, h, slot);
        }
    }

    private void clickMain(Player p, AuctionHolder h, int slot, ClickType click) {
        Session s = h.session;
        Integer lotId = h.slotRef.get(slot);
        if (lotId != null) {
            Lot l = plugin.manager().get(lotId);
            if (l == null) {
                msg(p, "lot-not-found");
                Sounds.play(p, "error");
                refreshMain(p, h, false);
                return;
            }
            lotClick(p, h, l, click);
            return;
        }
        for (Category c : plugin.categories().all()) {
            if (c.slot == slot) {
                if (!c.id.equalsIgnoreCase(s.category)) {
                    s.category = c.id;
                    s.page = 0;
                    Sounds.play(p, "click");
                    refreshMain(p, h, true);
                }
                return;
            }
        }
        if (slot == button("main.buttons.prev", 45, Material.ARROW).slot && s.page > 0) {
            s.page--;
            Sounds.play(p, "page");
            refreshMain(p, h, false);
        } else if (slot == button("main.buttons.next", 53, Material.ARROW).slot && s.page < h.pages - 1) {
            s.page++;
            Sounds.play(p, "page");
            refreshMain(p, h, false);
        } else if (slot == button("main.buttons.sort", 46, Material.HOPPER).slot) {
            s.sort = click.isRightClick() ? s.sort.prev() : s.sort.next();
            s.page = 0;
            Sounds.play(p, "click");
            refreshMain(p, h, false);
        } else if (slot == button("main.buttons.mode", 47, Material.COMPARATOR).slot) {
            s.mode = click.isRightClick() ? s.mode.prev() : s.mode.next();
            s.page = 0;
            Sounds.play(p, "click");
            refreshMain(p, h, false);
        } else if (slot == button("main.buttons.my-lots", 48, Material.CHEST).slot) {
            Sounds.play(p, "click");
            openList(p, AuctionHolder.Type.MYLOTS, 0);
        } else if (slot == button("main.buttons.storage", 50, Material.ENDER_CHEST).slot) {
            Sounds.play(p, "click");
            openList(p, AuctionHolder.Type.STORAGE, 0);
        } else if (slot == button("main.buttons.search", 51, Material.OAK_SIGN).slot) {
            Sounds.play(p, "click");
            if (click.isRightClick() || click.isShiftClick()) {
                if (s.search != null) {
                    s.search = null;
                    s.page = 0;
                    msg(p, "search-cleared");
                    refreshMain(p, h, true);
                }
            } else {
                promptSearch(p);
            }
        } else if (slot == button("main.buttons.refresh", 52, Material.CLOCK).slot) {
            Sounds.play(p, "click");
            refreshMain(p, h, true);
        }
    }

    public void promptSearch(Player p) {
        if (!p.hasPermission("mirageauction.search")) {
            msg(p, "no-permission");
            return;
        }
        p.closeInventory();
        msg(p, "search-prompt");
        plugin.chatInput().request(p, text -> {
            if (text == null || text.isEmpty()) {
                msg(p, "input-cancelled");
            } else {
                Session s = session(p);
                s.search = text;
                s.page = 0;
            }
            if (p.isOnline()) openMain(p, true);
        });
    }

    private void lotClick(Player p, AuctionHolder h, Lot l, ClickType click) {
        boolean own = l.seller.equals(p.getUniqueId());
        if (own) {
            if (click.isRightClick() || click.isShiftClick()) {
                cancelLot(p, l);
                refreshMain(p, h, false);
            } else {
                msg(p, "lot-own");
                Sounds.play(p, "error");
            }
            return;
        }
        Sounds.play(p, "click");
        if (l.bid) openBid(p, l);
        else openConfirm(p, l);
    }

    private void cancelLot(Player p, Lot l) {
        AuctionManager.Result r = plugin.manager().cancel(p, l, false);
        if (r == AuctionManager.Result.OK) {
            msg(p, "cancelled", "item", Text.itemLabel(l.item));
            Sounds.play(p, "success");
        } else {
            report(p, r, l, 0);
        }
    }

    private boolean report(Player p, AuctionManager.Result r, Lot l, double amount) {
        if (r == AuctionManager.Result.OK) return true;
        switch (r) {
            case NOT_FOUND, WRONG_MODE -> msg(p, "lot-not-found");
            case OWN -> msg(p, "lot-own");
            case NO_MONEY -> msg(p, "no-money", "price", Money.format(amount > 0 ? amount : l == null ? 0 : l.price));
            case TOO_LOW -> msg(p, "bid-too-low", "min", Money.format(l == null ? 0 : plugin.manager().minNextBid(l)));
            case HAS_BIDS -> msg(p, "cancel-has-bids");
            case NOT_OWNER -> msg(p, "not-owner");
            case NO_ECONOMY -> msg(p, "economy-not-ready");
            default -> msg(p, "lot-not-found");
        }
        Sounds.play(p, "error");
        return false;
    }

    // ------------------------------------------------------------------ CONFIRM (покупка)

    public void openConfirm(Player p, Lot l) {
        AuctionHolder h = new AuctionHolder(AuctionHolder.Type.CONFIRM, session(p));
        h.lotId = l.id;
        Map<String, String> ph = lotPlaceholders(l);
        Inventory inv = Bukkit.createInventory(h, 27, Text.component(Text.apply(g().getString("confirm.title", "{accent}Подтверждение покупки"), ph)));
        h.inventory = inv;
        ItemStack filler = filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        inv.setItem(g().getInt("confirm.item-slot", 13), lotItem(p, l, false));
        Items.Button yes = button("confirm.buttons.yes", 11, Material.LIME_CONCRETE);
        Items.Button no = button("confirm.buttons.no", 15, Material.RED_CONCRETE);
        inv.setItem(yes.slot, yes.build(ph));
        inv.setItem(no.slot, no.build(ph));
        p.openInventory(inv);
    }

    private void clickConfirm(Player p, AuctionHolder h, int slot) {
        Items.Button yes = button("confirm.buttons.yes", 11, Material.LIME_CONCRETE);
        Items.Button no = button("confirm.buttons.no", 15, Material.RED_CONCRETE);
        if (slot == no.slot) {
            Sounds.play(p, "click");
            openMain(p, false);
        } else if (slot == yes.slot) {
            Lot l = plugin.manager().get(h.lotId);
            if (l == null) {
                msg(p, "lot-not-found");
                Sounds.play(p, "error");
                openMain(p, false);
                return;
            }
            String item = Text.itemLabel(l.item);
            double price = l.price;
            AuctionManager.Result r = plugin.manager().buy(p, l);
            if (report(p, r, l, price)) {
                msg(p, "bought", "item", item, "price", Money.format(price));
                Sounds.play(p, "success");
            }
            openMain(p, false);
        }
    }

    // ------------------------------------------------------------------ BID (торги)

    public void openBid(Player p, Lot l) {
        AuctionHolder h = new AuctionHolder(AuctionHolder.Type.BID, session(p));
        h.lotId = l.id;
        Map<String, String> ph = lotPlaceholders(l);
        Inventory inv = Bukkit.createInventory(h, 27, Text.component(Text.apply(g().getString("bid.title", "{accent}Торги {secondary}» {primary}№{id}"), ph)));
        h.inventory = inv;
        ItemStack filler = filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        inv.setItem(g().getInt("bid.item-slot", 4), lotItem(p, l, false));

        List<Integer> quickSlots = parseSlots(g().getList("bid.quick-slots", List.of(10, 11, 12)));
        List<Integer> raises = plugin.configs().config().getIntegerList("bid.quick-raises");
        if (raises.isEmpty()) raises = List.of(1, 3, 5);
        double step = plugin.manager().bidStep(l);
        for (int i = 0; i < quickSlots.size() && i < raises.size(); i++) {
            int k = Math.max(1, raises.get(i));
            double amount = l.bidCount == 0 ? l.price + step * (k - 1) : l.currentBid + step * k;
            Map<String, String> qph = new HashMap<>(ph);
            qph.put("amount", Money.format(amount));
            qph.put("step", Money.format(step));
            Items.Button quick = new Items.Button(g().getConfigurationSection("bid.buttons.quick"), quickSlots.get(i), Material.GOLD_NUGGET);
            inv.setItem(quickSlots.get(i), quick.build(qph));
            h.slotAmount.put(quickSlots.get(i), amount);
        }
        Items.Button custom = button("bid.buttons.custom", 14, Material.OAK_SIGN);
        inv.setItem(custom.slot, custom.build(ph));
        if (l.buyout > 0) {
            Items.Button buy = button("bid.buttons.buyout", 16, Material.EMERALD);
            inv.setItem(buy.slot, buy.build(ph));
        }
        Items.Button back = button("bid.buttons.back", 22, Material.ARROW);
        inv.setItem(back.slot, back.build(ph));
        p.openInventory(inv);
    }

    private void clickBid(Player p, AuctionHolder h, int slot) {
        Lot l = plugin.manager().get(h.lotId);
        if (l == null) {
            msg(p, "lot-not-found");
            Sounds.play(p, "error");
            openMain(p, false);
            return;
        }
        Items.Button custom = button("bid.buttons.custom", 14, Material.OAK_SIGN);
        Items.Button buy = button("bid.buttons.buyout", 16, Material.EMERALD);
        Items.Button back = button("bid.buttons.back", 22, Material.ARROW);
        Double quick = h.slotAmount.get(slot);
        if (quick != null) {
            doBid(p, l, quick);
        } else if (slot == custom.slot) {
            Sounds.play(p, "click");
            promptBid(p, l);
        } else if (slot == buy.slot && l.buyout > 0) {
            String item = Text.itemLabel(l.item);
            double price = l.buyout;
            AuctionManager.Result r = plugin.manager().buyoutBid(p, l);
            if (report(p, r, l, price)) {
                msg(p, "bought", "item", item, "price", Money.format(price));
                Sounds.play(p, "success");
            }
            openMain(p, false);
        } else if (slot == back.slot) {
            Sounds.play(p, "click");
            openMain(p, false);
        }
    }

    private void promptBid(Player p, Lot l) {
        p.closeInventory();
        msg(p, "bid-prompt", "min", Money.format(plugin.manager().minNextBid(l)));
        final int id = l.id;
        plugin.chatInput().request(p, text -> {
            if (!p.isOnline()) return;
            Lot cur = plugin.manager().get(id);
            if (text == null || text.isEmpty()) {
                msg(p, "input-cancelled");
                openMain(p, false);
                return;
            }
            if (cur == null) {
                msg(p, "lot-not-found");
                openMain(p, false);
                return;
            }
            double amount;
            try {
                amount = Money.parse(text);
            } catch (NumberFormatException ex) {
                msg(p, "invalid-price");
                openBid(p, cur);
                return;
            }
            doBid(p, cur, amount);
        });
    }

    private void doBid(Player p, Lot l, double amount) {
        String item = Text.itemLabel(l.item);
        AuctionManager.Result r = plugin.manager().placeBid(p, l, amount);
        if (report(p, r, l, amount)) {
            if (plugin.manager().get(l.id) == null) {
                msg(p, "bought", "item", item, "price", Money.format(l.buyout));
                Sounds.play(p, "success");
            } else {
                msg(p, "bid-placed", "item", item, "amount", Money.format(amount));
                Sounds.play(p, "bid");
            }
        }
        openMain(p, false);
    }

    // ------------------------------------------------------------------ STORAGE / MYLOTS

    public void openList(Player p, AuctionHolder.Type type, int page) {
        AuctionHolder h = new AuctionHolder(type, session(p));
        h.page = page;
        String root = type == AuctionHolder.Type.STORAGE ? "storage" : "my-lots";
        Inventory inv = Bukkit.createInventory(h, 54, Text.component(listTitle(p, h, root)));
        h.inventory = inv;
        renderList(p, h);
        p.openInventory(inv);
        Sounds.play(p, "open");
    }

    private String listTitle(Player p, AuctionHolder h, String root) {
        Map<String, String> ph = new HashMap<>();
        ph.put("page", String.valueOf(h.page + 1));
        ph.put("pages", String.valueOf(h.pages));
        ph.put("count", String.valueOf(h.total));
        return Text.apply(g().getString(root + ".title", "{accent}" + root), ph);
    }

    private void renderList(Player p, AuctionHolder h) {
        boolean storage = h.type == AuctionHolder.Type.STORAGE;
        String root = storage ? "storage" : "my-lots";
        List<Integer> slots = parseSlots(g().getList(root + ".lot-slots", List.of("0-44")));
        int per = Math.max(1, slots.size());
        List<ItemStack> items = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        if (storage) {
            long now = System.currentTimeMillis();
            for (StoredItem si : plugin.manager().storage(p.getUniqueId())) {
                ItemStack it = si.item.clone();
                ItemMeta m = it.getItemMeta();
                if (m != null) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("reason", g().getString("storage.reasons." + si.reason, si.reason));
                    ph.put("age", TimeUtil.format(now - si.time));
                    List<net.kyori.adventure.text.Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
                    lore.addAll(Text.components(Text.expandLines(g().getStringList("storage.item-lore"), ph, null)));
                    m.lore(lore);
                    it.setItemMeta(m);
                }
                items.add(it);
                ids.add(si.id);
            }
        } else {
            for (Lot l : plugin.manager().lotsOf(p.getUniqueId())) {
                items.add(lotItem(p, l, true));
                ids.add(l.id);
            }
        }
        h.total = items.size();
        h.pages = Math.max(1, (items.size() + per - 1) / per);
        h.page = Math.max(0, Math.min(h.page, h.pages - 1));
        h.slotRef.clear();
        Inventory inv = h.inventory;
        inv.clear();
        int start = h.page * per;
        for (int i = 0; i < per && start + i < items.size() && i < slots.size(); i++) {
            inv.setItem(slots.get(i), items.get(start + i));
            h.slotRef.put(slots.get(i), ids.get(start + i));
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("page", String.valueOf(h.page + 1));
        ph.put("pages", String.valueOf(h.pages));
        ph.put("count", String.valueOf(h.total));
        ph.put("balance", plugin.economy().ready() ? Money.format(plugin.economy().balance(p)) : "—");
        ItemStack filler = filler();
        for (int i = 45; i < 54; i++) if (!slots.contains(i)) inv.setItem(i, filler);
        if (h.page > 0) {
            Items.Button b = button(root + ".buttons.prev", 45, Material.ARROW);
            inv.setItem(b.slot, b.build(ph));
        }
        if (h.page < h.pages - 1) {
            Items.Button b = button(root + ".buttons.next", 53, Material.ARROW);
            inv.setItem(b.slot, b.build(ph));
        }
        Items.Button back = button(root + ".buttons.back", 49, Material.BARRIER);
        inv.setItem(back.slot, back.build(ph));
        if (storage) {
            Items.Button all = button("storage.buttons.claim-all", 47, Material.HOPPER);
            inv.setItem(all.slot, all.build(ph));
        }
        if (items.isEmpty()) {
            Items.Button empty = button(root + ".buttons.empty", 22, Material.BARRIER);
            inv.setItem(empty.slot, empty.build(ph));
        }
    }

    private void clickList(Player p, AuctionHolder h, int slot) {
        boolean storage = h.type == AuctionHolder.Type.STORAGE;
        String root = storage ? "storage" : "my-lots";
        Integer ref = h.slotRef.get(slot);
        if (ref != null) {
            if (storage) {
                for (StoredItem si : plugin.manager().storage(p.getUniqueId())) {
                    if (si.id != ref) continue;
                    String label = Text.itemLabel(si.item);
                    AuctionManager.ClaimResult r = plugin.manager().claim(p, ref);
                    if (r == AuctionManager.ClaimResult.OK) {
                        msg(p, "storage-claimed", "item", label);
                        Sounds.play(p, "claim");
                    } else {
                        msg(p, "inventory-full");
                        Sounds.play(p, "error");
                    }
                    break;
                }
            } else {
                Lot l = plugin.manager().get(ref);
                if (l == null) msg(p, "lot-not-found");
                else cancelLot(p, l);
            }
            renderList(p, h);
            return;
        }
        if (slot == button(root + ".buttons.prev", 45, Material.ARROW).slot && h.page > 0) {
            h.page--;
            Sounds.play(p, "page");
            renderList(p, h);
        } else if (slot == button(root + ".buttons.next", 53, Material.ARROW).slot && h.page < h.pages - 1) {
            h.page++;
            Sounds.play(p, "page");
            renderList(p, h);
        } else if (slot == button(root + ".buttons.back", 49, Material.BARRIER).slot) {
            Sounds.play(p, "click");
            openMain(p, false);
        } else if (storage && slot == button("storage.buttons.claim-all", 47, Material.HOPPER).slot) {
            int before = plugin.manager().storageCount(p.getUniqueId());
            int n = plugin.manager().claimAll(p);
            if (n > 0) {
                msg(p, "storage-claimed-all", "count", String.valueOf(n));
                Sounds.play(p, "claim");
            }
            if (n < before) msg(p, "inventory-full");
            renderList(p, h);
        }
    }
}

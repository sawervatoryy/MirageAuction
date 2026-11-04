package org.example.sawervatoryy.mirageAuction.manager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.example.sawervatoryy.mirageAuction.MirageAuction;
import org.example.sawervatoryy.mirageAuction.model.Lot;
import org.example.sawervatoryy.mirageAuction.model.ModeFilter;
import org.example.sawervatoryy.mirageAuction.model.SortMode;
import org.example.sawervatoryy.mirageAuction.model.StoredItem;
import org.example.sawervatoryy.mirageAuction.util.Money;
import org.example.sawervatoryy.mirageAuction.util.Text;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuctionManager {

    public enum Result { OK, NOT_FOUND, OWN, NO_MONEY, WRONG_MODE, TOO_LOW, HAS_BIDS, NOT_OWNER, NO_ECONOMY }

    public enum ListResult { OK, NO_ITEM, BLACKLISTED, LIMIT, PRICE_LOW, PRICE_HIGH, BUYOUT_LOW, NO_FEE_MONEY, NO_ECONOMY }

    public record ListOutcome(ListResult result, Lot lot, double fee) {}

    public enum ClaimResult { OK, FULL, NOT_FOUND }

    private final MirageAuction plugin;
    private final File file;
    private final Object ioLock = new Object();

    private final Map<Integer, Lot> lots = new LinkedHashMap<>();
    private final Map<UUID, List<StoredItem>> storage = new HashMap<>();
    private final Map<UUID, List<String>> mail = new HashMap<>();
    private int nextLotId = 1;
    private int nextStoreId = 1;
    private boolean dirty;
    private BukkitTask tickTask;
    private BukkitTask saveTask;

    public AuctionManager(MirageAuction plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }


    public void start() {
        load();
        int tick = Math.max(5, plugin.configs().config().getInt("storage.tick-interval-ticks", 20));
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, tick);
        long save = Math.max(10, plugin.configs().config().getInt("storage.autosave-seconds", 60)) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save(true);
        }, save, save);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
        save(false);
    }


    public Lot get(int id) {
        return lots.get(id);
    }

    public int totalLots() {
        return lots.size();
    }

    public Iterable<Lot> allLots() {
        return new ArrayList<>(lots.values());
    }

    public List<Lot> lotsOf(UUID seller) {
        List<Lot> out = new ArrayList<>();
        for (Lot l : lots.values()) if (l.seller.equals(seller)) out.add(l);
        out.sort(Comparator.comparingLong((Lot l) -> l.created).reversed());
        return out;
    }

    public int countLots(UUID seller) {
        int c = 0;
        for (Lot l : lots.values()) if (l.seller.equals(seller)) c++;
        return c;
    }

    public List<Lot> query(String category, ModeFilter mode, String search, SortMode sort) {
        CategoryManager cats = plugin.categories();
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        List<Lot> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Lot l : lots.values()) {
            if (l.expires <= now) continue;
            if (mode == ModeFilter.FIXED && l.bid) continue;
            if (mode == ModeFilter.BID && !l.bid) continue;
            if (category != null && !category.equalsIgnoreCase("all") && !cats.categorize(l.item).equalsIgnoreCase(category)) continue;
            if (!q.isEmpty() && !matchesSearch(l, q)) continue;
            out.add(l);
        }
        Comparator<Lot> cmp = switch (sort) {
            case OLDEST -> Comparator.comparingLong((Lot l) -> l.created);
            case PRICE_ASC -> Comparator.comparingDouble(Lot::effectivePrice);
            case PRICE_DESC -> Comparator.comparingDouble(Lot::effectivePrice).reversed();
            case ENDING_SOON -> Comparator.comparingLong((Lot l) -> l.expires);
            default -> Comparator.comparingLong((Lot l) -> l.created).reversed();
        };
        out.sort(cmp);
        return out;
    }

    private boolean matchesSearch(Lot l, String q) {
        if (q.startsWith("@")) {
            String name = q.substring(1);
            return !name.isEmpty() && l.sellerName.toLowerCase(Locale.ROOT).contains(name);
        }
        String qu = q.replace(' ', '_');
        ItemStack it = l.item;
        if (it.getType().getKey().getKey().contains(qu)) return true;
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null
                && Text.plain(meta.displayName()).toLowerCase(Locale.ROOT).contains(q)) return true;
        for (Enchantment e : it.getEnchantments().keySet()) {
            if (e.getKey().getKey().contains(qu)) return true;
        }
        if (meta instanceof EnchantmentStorageMeta esm) {
            for (Enchantment e : esm.getStoredEnchants().keySet()) {
                if (e.getKey().getKey().contains(qu)) return true;
            }
        }
        return false;
    }


    public double taxPercent() {
        return Math.max(0, plugin.configs().config().getDouble("economy.tax-percent", 5));
    }

    public double bidStep(Lot l) {
        double cur = l.bidCount > 0 ? l.currentBid : l.price;
        double pct = plugin.configs().config().getDouble("bid.min-step-percent", 5);
        double flat = plugin.configs().config().getDouble("bid.min-step-flat", 1);
        return Math.max(flat, Math.ceil(cur * pct / 100.0));
    }

    public double minNextBid(Lot l) {
        return l.bidCount == 0 ? l.price : l.currentBid + bidStep(l);
    }

    public int maxLots(Player p) {
        if (p.hasPermission("mirageauction.bypass.limit")) return Integer.MAX_VALUE;
        int max = plugin.configs().config().getInt("limits.default-lots", 10);
        ConfigurationSection groups = plugin.configs().config().getConfigurationSection("limits.groups");
        if (groups != null) {
            for (String g : groups.getKeys(false)) {
                if (p.hasPermission("mirageauction.limit." + g)) max = Math.max(max, groups.getInt(g));
            }
        }
        return max;
    }

    public boolean isBlacklisted(ItemStack it) {
        for (String m : plugin.configs().config().getStringList("blacklist.materials")) {
            if (it.getType().name().equalsIgnoreCase(m.trim())) return true;
        }
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        List<String> nameBad = plugin.configs().config().getStringList("blacklist.name-contains");
        if (!nameBad.isEmpty() && meta.hasDisplayName() && meta.displayName() != null) {
            String n = Text.plain(meta.displayName()).toLowerCase(Locale.ROOT);
            for (String s : nameBad) if (!s.isEmpty() && n.contains(s.toLowerCase(Locale.ROOT))) return true;
        }
        List<String> loreBad = plugin.configs().config().getStringList("blacklist.lore-contains");
        if (!loreBad.isEmpty() && meta.hasLore() && meta.lore() != null) {
            for (net.kyori.adventure.text.Component c : meta.lore()) {
                String line = Text.plain(c).toLowerCase(Locale.ROOT);
                for (String s : loreBad) if (!s.isEmpty() && line.contains(s.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }


    public ListOutcome list(Player p, boolean bid, double price, double buyout, int hours) {
        if (!plugin.ensureEconomy()) return new ListOutcome(ListResult.NO_ECONOMY, null, 0);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return new ListOutcome(ListResult.NO_ITEM, null, 0);
        if (isBlacklisted(hand)) return new ListOutcome(ListResult.BLACKLISTED, null, 0);
        if (countLots(p.getUniqueId()) >= maxLots(p)) return new ListOutcome(ListResult.LIMIT, null, 0);
        double min = plugin.configs().config().getDouble("limits.min-price", 1);
        double max = plugin.configs().config().getDouble("limits.max-price", 1_000_000_000_000d);
        if (price < min) return new ListOutcome(ListResult.PRICE_LOW, null, 0);
        if (price > max) return new ListOutcome(ListResult.PRICE_HIGH, null, 0);
        if (bid && buyout > 0 && buyout <= price) return new ListOutcome(ListResult.BUYOUT_LOW, null, 0);
        if (bid && buyout > max) return new ListOutcome(ListResult.PRICE_HIGH, null, 0);

        double feePct = plugin.configs().config().getDouble("economy.listing-fee-percent", 0);
        double fee = Math.round(price * feePct) / 100.0;
        if (fee > 0) {
            if (!plugin.economy().has(p, fee) || !plugin.economy().withdraw(p, fee)) {
                return new ListOutcome(ListResult.NO_FEE_MONEY, null, fee);
            }
        }
        ItemStack item = hand.clone();
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        long now = System.currentTimeMillis();
        Lot lot = new Lot(nextLotId++, p.getUniqueId(), p.getName(), item, bid, price, bid ? Math.max(0, buyout) : 0, now, now + hours * 3_600_000L);
        lots.put(lot.id, lot);
        dirty = true;
        return new ListOutcome(ListResult.OK, lot, fee);
    }


    public Result buy(Player buyer, Lot lot) {
        if (!plugin.ensureEconomy()) return Result.NO_ECONOMY;
        if (lot == null || lots.get(lot.id) != lot || lot.expires <= System.currentTimeMillis()) return Result.NOT_FOUND;
        if (lot.bid) return Result.WRONG_MODE;
        if (lot.seller.equals(buyer.getUniqueId())) return Result.OWN;
        double price = lot.price;
        if (!plugin.economy().has(buyer, price) || !plugin.economy().withdraw(buyer, price)) return Result.NO_MONEY;
        lots.remove(lot.id);
        dirty = true;
        payoutSeller(lot, price, buyer.getName());
        giveOrStore(buyer.getUniqueId(), buyer, lot.item, "PURCHASE", true);
        return Result.OK;
    }

    public Result placeBid(Player p, Lot lot, double amount) {
        if (!plugin.ensureEconomy()) return Result.NO_ECONOMY;
        if (lot == null || lots.get(lot.id) != lot || lot.expires <= System.currentTimeMillis()) return Result.NOT_FOUND;
        if (!lot.bid) return Result.WRONG_MODE;
        if (lot.seller.equals(p.getUniqueId())) return Result.OWN;
        if (amount + 0.0001 < minNextBid(lot)) return Result.TOO_LOW;
        if (lot.buyout > 0 && amount >= lot.buyout) return buyoutBid(p, lot);

        boolean same = p.getUniqueId().equals(lot.bidder);
        double need = same ? amount - lot.currentBid : amount;
        if (!plugin.economy().has(p, need) || !plugin.economy().withdraw(p, need)) return Result.NO_MONEY;
        if (!same && lot.bidder != null) refundBidder(lot, p.getName(), amount);

        lot.currentBid = amount;
        lot.bidder = p.getUniqueId();
        lot.bidderName = p.getName();
        lot.bidCount++;
        applyAntiSnipe(lot);
        dirty = true;
        notifyPlayer(lot.seller, plugin.messages().get("bid-received", Text.map(
                "item", Text.itemLabel(lot.item), "amount", Money.format(amount), "player", p.getName())));
        return Result.OK;
    }

    /** Мгновенный выкуп лота в режиме торгов. */
    public Result buyoutBid(Player p, Lot lot) {
        if (!plugin.ensureEconomy()) return Result.NO_ECONOMY;
        if (lot == null || lots.get(lot.id) != lot || lot.expires <= System.currentTimeMillis()) return Result.NOT_FOUND;
        if (!lot.bid || lot.buyout <= 0) return Result.WRONG_MODE;
        if (lot.seller.equals(p.getUniqueId())) return Result.OWN;
        boolean same = p.getUniqueId().equals(lot.bidder);
        double need = same ? lot.buyout - lot.currentBid : lot.buyout;
        if (!plugin.economy().has(p, need) || !plugin.economy().withdraw(p, need)) return Result.NO_MONEY;
        if (!same && lot.bidder != null) refundBidder(lot, p.getName(), lot.buyout);
        lot.currentBid = lot.buyout;
        lot.bidder = p.getUniqueId();
        lot.bidderName = p.getName();
        lot.bidCount++;
        finishBid(lot);
        return Result.OK;
    }

    private void applyAntiSnipe(Lot lot) {
        ConfigurationSection s = plugin.configs().config().getConfigurationSection("bid.anti-snipe");
        if (s == null || !s.getBoolean("enabled", true)) return;
        long window = s.getLong("window-seconds", 60) * 1000L;
        long extend = s.getLong("extend-seconds", 60) * 1000L;
        int maxExt = s.getInt("max-extensions", 10);
        if (lot.expires - System.currentTimeMillis() < window && lot.extensions < maxExt) {
            lot.expires += extend;
            lot.extensions++;
        }
    }

    private void refundBidder(Lot lot, String newBidderName, double newAmount) {
        if (lot.bidder == null) return;
        OfflinePlayer old = Bukkit.getOfflinePlayer(lot.bidder);
        plugin.economy().deposit(old, lot.currentBid);
        notifyPlayer(lot.bidder, plugin.messages().get("outbid", Text.map(
                "item", Text.itemLabel(lot.item), "amount", Money.format(lot.currentBid),
                "player", newBidderName, "new_amount", Money.format(newAmount))));
    }

    private void payoutSeller(Lot lot, double gross, String buyerName) {
        double tax = Math.round(gross * taxPercent()) / 100.0;
        double net = gross - tax;
        plugin.economy().deposit(Bukkit.getOfflinePlayer(lot.seller), net);
        notifyPlayer(lot.seller, plugin.messages().get("sold", Text.map(
                "item", Text.itemLabel(lot.item), "buyer", buyerName, "price", Money.format(gross),
                "tax", Money.format(tax), "net", Money.format(net))));
    }

    private void finishBid(Lot lot) {
        lots.remove(lot.id);
        dirty = true;
        payoutSeller(lot, lot.currentBid, lot.bidderName == null ? "?" : lot.bidderName);
        Player online = lot.bidder == null ? null : Bukkit.getPlayer(lot.bidder);
        giveOrStore(lot.bidder, online, lot.item, "WON", false);
        notifyPlayer(lot.bidder, plugin.messages().get("auction-won", Text.map(
                "item", Text.itemLabel(lot.item), "price", Money.format(lot.currentBid))));
    }


    public Result cancel(Player p, Lot lot, boolean admin) {
        if (lot == null || lots.get(lot.id) != lot) return Result.NOT_FOUND;
        if (!admin && !lot.seller.equals(p.getUniqueId())) return Result.NOT_OWNER;
        boolean allowWithBids = plugin.configs().config().getBoolean("bid.allow-cancel-with-bids", false);
        if (!admin && lot.bidCount > 0 && !allowWithBids) return Result.HAS_BIDS;
        lots.remove(lot.id);
        dirty = true;
        if (lot.bidCount > 0 && lot.bidder != null) {
            plugin.economy().deposit(Bukkit.getOfflinePlayer(lot.bidder), lot.currentBid);
            notifyPlayer(lot.bidder, plugin.messages().get("bid-refunded", Text.map(
                    "item", Text.itemLabel(lot.item), "amount", Money.format(lot.currentBid))));
        }
        Player online = Bukkit.getPlayer(lot.seller);
        giveOrStore(lot.seller, online, lot.item, admin && (p == null || !p.getUniqueId().equals(lot.seller)) ? "REMOVED" : "CANCELLED", true);
        return Result.OK;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<Lot> expired = null;
        for (Lot l : lots.values()) {
            if (l.expires <= now) {
                if (expired == null) expired = new ArrayList<>();
                expired.add(l);
            }
        }
        if (expired == null) return;
        for (Lot l : expired) {
            if (lots.get(l.id) != l) continue;
            if (l.bid && l.bidCount > 0 && l.bidder != null) {
                finishBid(l);
            } else {
                lots.remove(l.id);
                dirty = true;
                addStorage(l.seller, l.item.clone(), "EXPIRED");
                notifyPlayer(l.seller, plugin.messages().get("lot-expired", Text.map("item", Text.itemLabel(l.item))));
            }
        }
    }


    private void addStorage(UUID owner, ItemStack item, String reason) {
        if (owner == null) return;
        storage.computeIfAbsent(owner, k -> new ArrayList<>())
                .add(new StoredItem(nextStoreId++, owner, item, reason, System.currentTimeMillis()));
        dirty = true;
    }

    /** Кладёт предмет в инвентарь (если inventoryFirst и игрок онлайн), остаток — в хранилище. */
    private void giveOrStore(UUID owner, Player online, ItemStack item, String reason, boolean inventoryFirst) {
        if (owner == null) return;
        boolean direct = inventoryFirst && plugin.configs().config().getBoolean("delivery.to-inventory-first", true);
        if (direct && online != null && online.isOnline()) {
            Map<Integer, ItemStack> left = online.getInventory().addItem(item.clone());
            for (ItemStack s : left.values()) addStorage(owner, s, "OVERFLOW");
            return;
        }
        addStorage(owner, item.clone(), reason);
    }

    public List<StoredItem> storage(UUID owner) {
        List<StoredItem> list = new ArrayList<>(storage.getOrDefault(owner, List.of()));
        list.sort(Comparator.comparingLong((StoredItem s) -> s.time).reversed());
        return list;
    }

    public int storageCount(UUID owner) {
        return storage.getOrDefault(owner, List.of()).size();
    }

    public ClaimResult claim(Player p, int storedId) {
        List<StoredItem> list = storage.get(p.getUniqueId());
        if (list == null) return ClaimResult.NOT_FOUND;
        StoredItem found = null;
        for (StoredItem s : list) if (s.id == storedId) found = s;
        if (found == null) return ClaimResult.NOT_FOUND;
        Map<Integer, ItemStack> left = p.getInventory().addItem(found.item.clone());
        dirty = true;
        if (left.isEmpty()) {
            list.remove(found);
            return ClaimResult.OK;
        }
        found.item = left.values().iterator().next();
        return ClaimResult.FULL;
    }

    public int claimAll(Player p) {
        int claimed = 0;
        for (StoredItem s : storage(p.getUniqueId())) {
            if (claim(p, s.id) == ClaimResult.OK) claimed++;
            else break;
        }
        return claimed;
    }


    public void notifyPlayer(UUID uuid, String colored) {
        if (uuid == null || colored == null || colored.isBlank()) return;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            plugin.messages().sendRaw(online, colored);
            return;
        }
        List<String> list = mail.computeIfAbsent(uuid, k -> new ArrayList<>());
        if (list.size() < 50) list.add(colored);
        dirty = true;
    }

    public List<String> popMail(UUID uuid) {
        List<String> list = mail.remove(uuid);
        if (list != null) dirty = true;
        return list == null ? List.of() : list;
    }


    private static String encode(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack decode(String s) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-lot-id", nextLotId);
        y.set("next-store-id", nextStoreId);
        for (Lot l : lots.values()) {
            String p = "lots." + l.id + ".";
            y.set(p + "seller", l.seller.toString());
            y.set(p + "seller-name", l.sellerName);
            y.set(p + "item", encode(l.item));
            y.set(p + "bid", l.bid);
            y.set(p + "price", l.price);
            y.set(p + "buyout", l.buyout);
            y.set(p + "created", l.created);
            y.set(p + "expires", l.expires);
            y.set(p + "current-bid", l.currentBid);
            y.set(p + "bidder", l.bidder == null ? null : l.bidder.toString());
            y.set(p + "bidder-name", l.bidderName);
            y.set(p + "bid-count", l.bidCount);
            y.set(p + "extensions", l.extensions);
        }
        for (Map.Entry<UUID, List<StoredItem>> e : storage.entrySet()) {
            for (StoredItem s : e.getValue()) {
                String p = "storage." + e.getKey() + "." + s.id + ".";
                y.set(p + "item", encode(s.item));
                y.set(p + "reason", s.reason);
                y.set(p + "time", s.time);
            }
        }
        for (Map.Entry<UUID, List<String>> e : mail.entrySet()) {
            y.set("mail." + e.getKey(), e.getValue());
        }
        final String data = y.saveToString();
        dirty = false;
        Runnable write = () -> {
            synchronized (ioLock) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null) parent.mkdirs();
                    java.nio.file.Path tmp = file.toPath().resolveSibling("data.yml.tmp");
                    Files.writeString(tmp, data, StandardCharsets.UTF_8);
                    Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    plugin.getLogger().severe("Не удалось сохранить data.yml: " + ex.getMessage());
                }
            }
        };
        if (async && plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    private void load() {
        lots.clear();
        storage.clear();
        mail.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextLotId = y.getInt("next-lot-id", 1);
        nextStoreId = y.getInt("next-store-id", 1);
        ConfigurationSection ls = y.getConfigurationSection("lots");
        if (ls != null) {
            for (String key : ls.getKeys(false)) {
                ConfigurationSection c = ls.getConfigurationSection(key);
                if (c == null) continue;
                try {
                    int id = Integer.parseInt(key);
                    Lot l = new Lot(id, UUID.fromString(c.getString("seller", "")), c.getString("seller-name", "?"),
                            decode(c.getString("item", "")), c.getBoolean("bid"), c.getDouble("price"), c.getDouble("buyout"),
                            c.getLong("created"), c.getLong("expires"));
                    l.currentBid = c.getDouble("current-bid");
                    String b = c.getString("bidder");
                    if (b != null && !b.isEmpty()) l.bidder = UUID.fromString(b);
                    l.bidderName = c.getString("bidder-name");
                    l.bidCount = c.getInt("bid-count");
                    l.extensions = c.getInt("extensions");
                    lots.put(id, l);
                    nextLotId = Math.max(nextLotId, id + 1);
                } catch (Exception ex) {
                    plugin.getLogger().severe("Не удалось загрузить лот " + key + ": " + ex.getMessage());
                }
            }
        }
        ConfigurationSection ss = y.getConfigurationSection("storage");
        if (ss != null) {
            for (String owner : ss.getKeys(false)) {
                ConfigurationSection oc = ss.getConfigurationSection(owner);
                if (oc == null) continue;
                try {
                    UUID uuid = UUID.fromString(owner);
                    List<StoredItem> list = new ArrayList<>();
                    for (String sid : oc.getKeys(false)) {
                        ConfigurationSection c = oc.getConfigurationSection(sid);
                        if (c == null) continue;
                        int id = Integer.parseInt(sid);
                        list.add(new StoredItem(id, uuid, decode(c.getString("item", "")), c.getString("reason", "EXPIRED"), c.getLong("time")));
                        nextStoreId = Math.max(nextStoreId, id + 1);
                    }
                    storage.put(uuid, list);
                } catch (Exception ex) {
                    plugin.getLogger().severe("Не удалось загрузить хранилище " + owner + ": " + ex.getMessage());
                }
            }
        }
        ConfigurationSection ms = y.getConfigurationSection("mail");
        if (ms != null) {
            for (String owner : ms.getKeys(false)) {
                try {
                    mail.put(UUID.fromString(owner), new ArrayList<>(ms.getStringList(owner)));
                } catch (IllegalArgumentException ignored) {
                    // битый ключ
                }
            }
        }
        plugin.getLogger().info("Загружено лотов: " + lots.size());
    }
}

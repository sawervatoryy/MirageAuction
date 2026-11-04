package org.example.sawervatoryy.mirageAuction.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.example.sawervatoryy.mirageAuction.MirageAuction;
import org.example.sawervatoryy.mirageAuction.gui.AuctionHolder;
import org.example.sawervatoryy.mirageAuction.gui.Session;
import org.example.sawervatoryy.mirageAuction.manager.AuctionManager;
import org.example.sawervatoryy.mirageAuction.model.Lot;
import org.example.sawervatoryy.mirageAuction.util.Money;
import org.example.sawervatoryy.mirageAuction.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AhCommand implements TabExecutor {

    private final MirageAuction plugin;

    public AhCommand(MirageAuction plugin) {
        this.plugin = plugin;
    }

    private static String canonical(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "sell", "s", "продать" -> "sell";
            case "bid", "b", "ставка", "торги" -> "bid";
            case "search", "find", "поиск" -> "search";
            case "storage", "st", "хранилище" -> "storage";
            case "my", "lots", "мои" -> "my";
            case "reload" -> "reload";
            case "theme", "тема" -> "theme";
            case "remove", "delete" -> "remove";
            case "help", "?" -> "help";
            default -> "";
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "open" : canonical(args[0]);

        // админ-команды доступны и из консоли
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("mirageauction.admin")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "reload-done");
                return true;
            }
            case "theme" -> {
                theme(sender, args);
                return true;
            }
            case "remove" -> {
                remove(sender, args);
                return true;
            }
            case "help" -> {
                for (String line : plugin.messages().list("help")) plugin.messages().sendRaw(sender, line);
                return true;
            }
            default -> {
            }
        }

        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!p.hasPermission("mirageauction.use")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }

        switch (sub) {
            case "open" -> plugin.gui().openMain(p, true);
            case "sell" -> sell(p, args, false);
            case "bid" -> sell(p, args, true);
            case "search" -> search(p, args);
            case "storage" -> {
                if (!p.hasPermission("mirageauction.storage")) {
                    plugin.messages().send(p, "no-permission");
                    return true;
                }
                plugin.gui().openList(p, AuctionHolder.Type.STORAGE, 0);
            }
            case "my" -> plugin.gui().openList(p, AuctionHolder.Type.MYLOTS, 0);
            default -> {
                for (String line : plugin.messages().list("help")) plugin.messages().sendRaw(p, line);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ /ah sell, /ah bid

    private void sell(Player p, String[] args, boolean bid) {
        if (!p.hasPermission(bid ? "mirageauction.bid.sell" : "mirageauction.sell")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(p, bid ? "usage-bid" : "usage-sell");
            return;
        }
        double price;
        try {
            price = Money.parse(args[1]);
        } catch (NumberFormatException ex) {
            plugin.messages().send(p, "invalid-price");
            return;
        }
        double buyout = 0;
        int hoursArgIndex = 2;
        if (bid && args.length > 2) {
            try {
                buyout = Money.parse(args[2]);
            } catch (NumberFormatException ex) {
                plugin.messages().send(p, "invalid-price");
                return;
            }
            hoursArgIndex = 3;
        }
        ConfigurationSection d = plugin.configs().config().getConfigurationSection(bid ? "duration.bid" : "duration.sell");
        int defHours = d == null ? 24 : d.getInt("default-hours", 24);
        int maxHours = d == null ? 72 : d.getInt("max-hours", 72);
        int hours = defHours;
        if (args.length > hoursArgIndex) {
            try {
                hours = Integer.parseInt(args[hoursArgIndex]);
            } catch (NumberFormatException ex) {
                plugin.messages().send(p, "invalid-hours", "max", String.valueOf(maxHours));
                return;
            }
            if (hours < 1 || hours > maxHours) {
                plugin.messages().send(p, "invalid-hours", "max", String.valueOf(maxHours));
                return;
            }
        }

        AuctionManager.ListOutcome out = plugin.manager().list(p, bid, price, buyout, hours);
        switch (out.result()) {
            case OK -> {
                Lot lot = out.lot();
                String item = Text.itemLabel(lot.item);
                plugin.messages().send(p, bid ? "listed-bid" : "listed", "item", item, "price", Money.format(price),
                        "buyout", buyout > 0 ? Money.format(buyout) : "—", "hours", String.valueOf(hours), "id", String.valueOf(lot.id));
                if (out.fee() > 0) plugin.messages().send(p, "fee-paid", "fee", Money.format(out.fee()));
                broadcast(p, lot, item, price);
            }
            case NO_ITEM -> plugin.messages().send(p, "hold-item");
            case BLACKLISTED -> plugin.messages().send(p, "blacklisted");
            case LIMIT -> plugin.messages().send(p, "limit-reached", "limit", String.valueOf(plugin.manager().maxLots(p)));
            case PRICE_LOW -> plugin.messages().send(p, "price-low", "min", Money.format(plugin.configs().config().getDouble("limits.min-price", 1)));
            case PRICE_HIGH -> plugin.messages().send(p, "price-high", "max", Money.format(plugin.configs().config().getDouble("limits.max-price", 1_000_000_000_000d)));
            case BUYOUT_LOW -> plugin.messages().send(p, "buyout-low");
            case NO_FEE_MONEY -> plugin.messages().send(p, "no-money", "price", Money.format(out.fee()));
            case NO_ECONOMY -> plugin.messages().send(p, "economy-not-ready");
        }
    }

    private void broadcast(Player seller, Lot lot, String item, double price) {
        ConfigurationSection b = plugin.configs().config().getConfigurationSection("broadcast");
        if (b == null || !b.getBoolean("new-lot", false)) return;
        if (price < b.getDouble("min-price", 0)) return;
        Bukkit.broadcast(plugin.messages().component(lot.bid ? "listed-broadcast-bid" : "listed-broadcast",
                "player", seller.getName(), "item", item, "price", Money.format(price)));
    }

    // ------------------------------------------------------------------ /ah search

    private void search(Player p, String[] args) {
        if (!p.hasPermission("mirageauction.search")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(p, "usage-search");
            return;
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        Session s = plugin.gui().session(p);
        s.search = query;
        s.category = "all";
        s.page = 0;
        plugin.gui().openMain(p, true);
    }

    // ------------------------------------------------------------------ admin

    private void theme(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mirageauction.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "theme-list", "themes", String.join(", ", plugin.themes().names()), "active", plugin.themes().active());
            return;
        }
        if (!plugin.themes().exists(args[1])) {
            plugin.messages().send(sender, "theme-unknown", "themes", String.join(", ", plugin.themes().names()));
            return;
        }
        plugin.themes().set(args[1]);
        plugin.messages().send(sender, "theme-changed", "theme", args[1]);
    }

    private void remove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mirageauction.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-remove");
            return;
        }
        Lot lot;
        try {
            lot = plugin.manager().get(Integer.parseInt(args[1]));
        } catch (NumberFormatException ex) {
            lot = null;
        }
        if (lot == null) {
            plugin.messages().send(sender, "lot-not-found");
            return;
        }
        Player p = sender instanceof Player pl ? pl : null;
        plugin.manager().cancel(p, lot, true);
        plugin.messages().send(sender, "removed-admin", "id", String.valueOf(lot.id), "item", Text.itemLabel(lot.item));
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("sell", "bid", "search", "storage", "my", "help"));
            if (sender.hasPermission("mirageauction.admin")) subs.addAll(List.of("reload", "theme", "remove"));
            for (String s : subs) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        String sub = canonical(args[0]);
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell", "bid" -> {
                if (args.length == 2) {
                    for (String s : List.of("1k", "10k", "100k", "1kk", "10kk")) if (s.startsWith(cur)) out.add(s);
                } else if (args.length == 3 && sub.equals("bid")) {
                    for (String s : List.of("0", "500k", "1kk", "5kk")) if (s.startsWith(cur)) out.add(s);
                } else {
                    for (String s : List.of("6", "12", "24", "48", "72")) if (s.startsWith(cur)) out.add(s);
                }
            }
            case "search" -> {
                if (args.length == 2 && cur.length() >= 2) {
                    for (Material m : Material.values()) {
                        if (!m.isItem() || m.isLegacy()) continue;
                        String n = m.getKey().getKey();
                        if (n.startsWith(cur)) out.add(n);
                        if (out.size() >= 25) break;
                    }
                }
            }
            case "theme" -> {
                if (args.length == 2) for (String t : plugin.themes().names()) if (t.toLowerCase(Locale.ROOT).startsWith(cur)) out.add(t);
            }
            default -> {
            }
        }
        return out;
    }
}

package org.example.sawervatoryy.mirageAuction.util;

import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Парсинг цен (100k, 1kk, 1.5м, 2b ...) и красивое форматирование. */
public final class Money {

    private static final Pattern NUMBER = Pattern.compile("^(\\d+(?:\\.\\d+)?)([a-zа-яё]*)$");

    private static final Map<String, Double> suffixes = new HashMap<>();
    private static List<String> shortUnits = List.of("", "k", "kk", "kkk");
    private static boolean shortStyle = false;
    private static String symbol = "$";
    private static boolean symbolBefore = true;
    private static String groupSeparator = " ";

    private Money() {}

    public static void load(ConfigurationSection format) {
        suffixes.clear();
        shortStyle = false;
        symbol = "$";
        symbolBefore = true;
        groupSeparator = " ";
        shortUnits = List.of("", "k", "kk", "kkk");
        if (format == null) return;
        ConfigurationSection sf = format.getConfigurationSection("suffixes");
        if (sf != null) {
            for (String key : sf.getKeys(false)) {
                suffixes.put(key.toLowerCase(Locale.ROOT), sf.getDouble(key));
            }
        }
        shortStyle = "SHORT".equalsIgnoreCase(format.getString("style", "FULL"));
        symbol = format.getString("currency-symbol", "$");
        symbolBefore = !"AFTER".equalsIgnoreCase(format.getString("symbol-position", "BEFORE"));
        groupSeparator = format.getString("group-separator", " ");
        List<String> units = format.getStringList("short-units");
        if (!units.isEmpty()) shortUnits = new ArrayList<>(units);
    }

    /** @throws NumberFormatException если строка не цена */
    public static double parse(String input) {
        if (input == null) throw new NumberFormatException("empty");
        String s = input.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(",", ".");
        Matcher m = NUMBER.matcher(s);
        if (!m.matches()) throw new NumberFormatException(input);
        double base = Double.parseDouble(m.group(1));
        String suf = m.group(2);
        double mul = 1;
        if (!suf.isEmpty()) {
            Double v = suffixes.get(suf);
            if (v == null) throw new NumberFormatException(input);
            mul = v;
        }
        double res = base * mul;
        if (Double.isNaN(res) || Double.isInfinite(res)) throw new NumberFormatException(input);
        return Math.round(res * 100.0) / 100.0;
    }

    public static String format(double value) {
        String body = shortStyle ? formatShort(value) : formatFull(value);
        return symbolBefore ? symbol + body : body + symbol;
    }

    public static String formatFull(double value) {
        DecimalFormatSymbols sy = new DecimalFormatSymbols(Locale.US);
        String sep = groupSeparator.isEmpty() ? "," : groupSeparator;
        sy.setGroupingSeparator(sep.charAt(0));
        boolean whole = Math.abs(value - Math.rint(value)) < 0.005;
        DecimalFormat df = new DecimalFormat(whole ? "#,##0" : "#,##0.00", sy);
        String out = df.format(value);
        if (sep.length() > 1) out = out.replace(String.valueOf(sep.charAt(0)), sep);
        return out;
    }

    public static String formatShort(double value) {
        int idx = 0;
        double v = value;
        while (Math.abs(v) >= 1000 && idx < shortUnits.size() - 1) {
            v /= 1000.0;
            idx++;
        }
        DecimalFormat df = new DecimalFormat("0.##",
                new DecimalFormatSymbols(Locale.US));
        return df.format(v) + shortUnits.get(idx);
    }
}

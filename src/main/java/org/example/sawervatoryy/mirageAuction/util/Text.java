package org.example.sawervatoryy.mirageAuction.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Цвета и текст. Понимает: &a-&f, &l, &#RRGGBB, <gradient:#A:#B>текст</gradient>,
 * а также плейсхолдеры темы {primary} {secondary} {accent} {dark} {success} {error}
 * и {grad:текст} / {gradb:текст} (градиент активной темы).
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRAD_TAG = Pattern.compile("<gradient:#?([A-Fa-f0-9]{6}):#?([A-Fa-f0-9]{6})(:b)?>(.*?)</gradient>");
    private static final Pattern GRAD_PH = Pattern.compile("\\{(grad|gradb):([^}]*)}");
    private static final Pattern VAR = Pattern.compile("\\{([a-z_]+)}");
    private static final Pattern STRIP = Pattern.compile("§.");
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static volatile Map<String, String> themeVars = new HashMap<>();
    private static volatile List<String> themeGradient = List.of("#FFFFFF");

    private Text() {}

    public static void setTheme(Map<String, String> vars, List<String> gradient) {
        themeVars = new HashMap<>(vars);
        themeGradient = (gradient == null || gradient.isEmpty()) ? List.of("#FFFFFF") : new ArrayList<>(gradient);
    }

    /** Превращает шаблон в строку с §-кодами (включая hex). */
    public static String color(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input;
        for (Map.Entry<String, String> e : themeVars.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        s = GRAD_PH.matcher(s).replaceAll(r ->
                Matcher.quoteReplacement(gradient(r.group(2), themeGradient, r.group(1).equals("gradb"))));
        s = GRAD_TAG.matcher(s).replaceAll(r ->
                Matcher.quoteReplacement(gradient(r.group(4), List.of(r.group(1), r.group(2)), r.group(3) != null)));
        s = HEX.matcher(s).replaceAll(r -> {
            StringBuilder b = new StringBuilder("§x");
            for (char c : r.group(1).toLowerCase(Locale.ROOT).toCharArray()) b.append('§').append(c);
            return Matcher.quoteReplacement(b.toString());
        });
        char[] arr = s.toCharArray();
        for (int i = 0; i < arr.length - 1; i++) {
            if (arr[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(arr[i + 1]) > -1) {
                arr[i] = '§';
                arr[i + 1] = Character.toLowerCase(arr[i + 1]);
            }
        }
        return new String(arr);
    }

    public static Component component(String template) {
        return fromColored(color(template));
    }

    public static Component fromColored(String colored) {
        return SECTION.deserialize(colored).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String toLegacy(Component c) {
        return SECTION.serialize(c);
    }

    public static String plain(Component c) {
        return c == null ? "" : PLAIN.serialize(c);
    }

    public static String strip(String template) {
        return STRIP.matcher(color(template)).replaceAll("");
    }

    /** Градиент по списку цветов. Каждый символ получает свой &#RRGGBB. */
    public static String gradient(String text, List<String> colors, boolean bold) {
        List<int[]> rgb = new ArrayList<>();
        for (String c : colors) {
            String h = c.startsWith("#") ? c.substring(1) : c;
            try {
                rgb.add(new int[]{Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16), Integer.parseInt(h.substring(4, 6), 16)});
            } catch (Exception ignored) {
                // пропускаем неверный цвет
            }
        }
        if (rgb.isEmpty()) rgb.add(new int[]{255, 255, 255});
        int n = text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                sb.append(ch);
                continue;
            }
            double t = n <= 1 ? 0 : (double) i / (n - 1);
            int[] c = sample(rgb, t);
            sb.append(String.format("&#%02X%02X%02X", c[0], c[1], c[2]));
            if (bold) sb.append("&l");
            sb.append(ch);
        }
        return sb.toString();
    }

    private static int[] sample(List<int[]> rgb, double t) {
        if (rgb.size() == 1) return rgb.get(0);
        double scaled = t * (rgb.size() - 1);
        int idx = Math.min((int) scaled, rgb.size() - 2);
        double f = scaled - idx;
        int[] a = rgb.get(idx);
        int[] b = rgb.get(idx + 1);
        return new int[]{
                (int) Math.round(a[0] + (b[0] - a[0]) * f),
                (int) Math.round(a[1] + (b[1] - a[1]) * f),
                (int) Math.round(a[2] + (b[2] - a[2]) * f)};
    }

    /** Первые count символов градиента активной темы (эффект «печати» заголовка). Цвета считаются по полному тексту. */
    public static String themeGradientPrefix(String text, int count, boolean bold) {
        String full = gradient(text, themeGradient, bold);
        StringBuilder out = new StringBuilder();
        int visible = 0;
        int i = 0;
        while (i < full.length() && visible < count) {
            char c = full.charAt(i);
            if (c == '&' && i + 1 < full.length()) {
                if (full.charAt(i + 1) == '#') {
                    out.append(full, i, i + 8);
                    i += 8;
                    continue;
                }
                out.append(full, i, i + 2);
                i += 2;
                continue;
            }
            out.append(c);
            visible++;
            i++;
        }
        return out.toString();
    }

    /** Подстановка {key} -> value (null = пустая строка). */
    public static String apply(String template, Map<String, String> ph) {
        if (template == null) return "";
        String s = template;
        if (ph != null) {
            for (Map.Entry<String, String> e : ph.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return s;
    }

    /**
     * Разворачивает шаблон лора: строка {hints} заменяется списком hints,
     * строки с плейсхолдером, значение которого null, выбрасываются.
     */
    public static List<String> expandLines(List<String> tmpl, Map<String, String> ph, List<String> hints) {
        List<String> out = new ArrayList<>();
        for (String line : tmpl) {
            if (line.trim().equals("{hints}")) {
                if (hints != null) for (String h : hints) addLine(out, h, ph);
                continue;
            }
            addLine(out, line, ph);
        }
        return out;
    }

    private static void addLine(List<String> out, String line, Map<String, String> ph) {
        Matcher m = VAR.matcher(line);
        while (m.find()) {
            String k = m.group(1);
            if (ph.containsKey(k) && ph.get(k) == null) return;
        }
        out.add(apply(line, ph));
    }

    public static List<Component> components(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(component(l));
        return out;
    }

    public static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Читаемое имя предмета: своё название или красивое имя материала. */
    public static String itemName(ItemStack item) {
        if (item == null) return "?";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return plain(meta.displayName());
        }
        return prettify(item.getType().name());
    }

    public static String itemLabel(ItemStack item) {
        String n = itemName(item);
        return item.getAmount() > 1 ? n + " x" + item.getAmount() : n;
    }

    public static String prettify(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}

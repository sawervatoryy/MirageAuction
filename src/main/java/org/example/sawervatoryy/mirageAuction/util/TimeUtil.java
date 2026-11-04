package org.example.sawervatoryy.mirageAuction.util;

import org.bukkit.configuration.ConfigurationSection;

public final class TimeUtil {

    private static String d = "д", h = "ч", m = "м", s = "с";

    private TimeUtil() {}

    public static void load(ConfigurationSection sec) {
        d = "д";
        h = "ч";
        m = "м";
        s = "с";
        if (sec == null) return;
        d = sec.getString("d", d);
        h = sec.getString("h", h);
        m = sec.getString("m", m);
        s = sec.getString("s", s);
    }

    public static String format(long millis) {
        long sec = Math.max(0, millis / 1000);
        long days = sec / 86400;
        long hours = (sec % 86400) / 3600;
        long mins = (sec % 3600) / 60;
        long secs = sec % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(d).append(' ');
        if (hours > 0) sb.append(hours).append(h).append(' ');
        if (days == 0 && mins > 0) sb.append(mins).append(m).append(' ');
        if (days == 0 && hours == 0 && (secs > 0 || sb.length() == 0)) sb.append(secs).append(s).append(' ');
        return sb.toString().trim();
    }
}

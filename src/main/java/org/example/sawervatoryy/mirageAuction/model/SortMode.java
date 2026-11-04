package org.example.sawervatoryy.mirageAuction.model;

public enum SortMode {
    NEWEST, OLDEST, PRICE_ASC, PRICE_DESC, ENDING_SOON;

    public SortMode next() {
        SortMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public SortMode prev() {
        SortMode[] v = values();
        return v[(ordinal() + v.length - 1) % v.length];
    }

    public static SortMode parse(String s, SortMode def) {
        try {
            return valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return def;
        }
    }
}

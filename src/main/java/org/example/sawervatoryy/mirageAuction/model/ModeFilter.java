package org.example.sawervatoryy.mirageAuction.model;

public enum ModeFilter {
    ALL, FIXED, BID;

    public ModeFilter next() {
        ModeFilter[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public ModeFilter prev() {
        ModeFilter[] v = values();
        return v[(ordinal() + v.length - 1) % v.length];
    }
}

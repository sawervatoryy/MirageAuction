package org.example.sawervatoryy.mirageAuction.gui;

import org.example.sawervatoryy.mirageAuction.model.ModeFilter;
import org.example.sawervatoryy.mirageAuction.model.SortMode;

public final class Session {
    public String category = "all";
    public int page = 0;
    public SortMode sort = SortMode.NEWEST;
    public ModeFilter mode = ModeFilter.ALL;
    public String search = null;
}

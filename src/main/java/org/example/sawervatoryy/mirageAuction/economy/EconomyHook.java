package org.example.sawervatoryy.mirageAuction.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Экономика без жёсткой зависимости на этапе компиляции (через рефлексию).
 * Поддерживает Vault (любой экономический плагин) и PlayerPoints.
 */
public final class EconomyHook {

    public enum Provider { VAULT, PLAYERPOINTS, NONE }

    private Provider provider = Provider.NONE;

    // Vault
    private Object vault;
    private Method vBalance, vWithdraw, vDeposit, vSuccess;

    // PlayerPoints
    private Object points;
    private Method pLook, pTake, pGive;

    public Provider provider() {
        return provider;
    }

    public boolean ready() {
        return provider != Provider.NONE;
    }

    public boolean setup(String name) {
        provider = Provider.NONE;
        String want = name == null ? "VAULT" : name.toUpperCase();
        if (want.equals("PLAYERPOINTS")) {
            if (setupPoints()) return true;
            return setupVault();
        }
        if (setupVault()) return true;
        return setupPoints();
    }

    @SuppressWarnings("unchecked")
    private boolean setupVault() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
            Class<?> econ = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> resp = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            RegisteredServiceProvider<Object> rsp = Bukkit.getServicesManager().getRegistration((Class<Object>) econ);
            if (rsp == null) return false;
            vault = rsp.getProvider();
            vBalance = econ.getMethod("getBalance", OfflinePlayer.class);
            vWithdraw = econ.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            vDeposit = econ.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            vSuccess = resp.getMethod("transactionSuccess");
            provider = Provider.VAULT;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean setupPoints() {
        try {
            Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pp == null || !pp.isEnabled()) return false;
            points = pp.getClass().getMethod("getAPI").invoke(pp);
            pLook = points.getClass().getMethod("look", UUID.class);
            pTake = points.getClass().getMethod("take", UUID.class, int.class);
            pGive = points.getClass().getMethod("give", UUID.class, int.class);
            provider = Provider.PLAYERPOINTS;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public double balance(OfflinePlayer p) {
        try {
            return switch (provider) {
                case VAULT -> ((Number) vBalance.invoke(vault, p)).doubleValue();
                case PLAYERPOINTS -> ((Number) pLook.invoke(points, p.getUniqueId())).doubleValue();
                default -> 0;
            };
        } catch (Throwable t) {
            return 0;
        }
    }

    public boolean has(OfflinePlayer p, double amount) {
        return balance(p) + 0.0001 >= amount;
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        if (amount <= 0) return true;
        try {
            switch (provider) {
                case VAULT -> {
                    Object r = vWithdraw.invoke(vault, p, amount);
                    return (boolean) vSuccess.invoke(r);
                }
                case PLAYERPOINTS -> {
                    return (boolean) pTake.invoke(points, p.getUniqueId(), (int) Math.ceil(amount));
                }
                default -> {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        if (amount <= 0) return true;
        try {
            switch (provider) {
                case VAULT -> {
                    Object r = vDeposit.invoke(vault, p, amount);
                    return (boolean) vSuccess.invoke(r);
                }
                case PLAYERPOINTS -> {
                    return (boolean) pGive.invoke(points, p.getUniqueId(), (int) Math.floor(amount));
                }
                default -> {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }
    }
}

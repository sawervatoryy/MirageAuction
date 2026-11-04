package org.example.sawervatoryy.mirageAuction;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.sawervatoryy.mirageAuction.command.AhCommand;
import org.example.sawervatoryy.mirageAuction.economy.EconomyHook;
import org.example.sawervatoryy.mirageAuction.gui.ChatInput;
import org.example.sawervatoryy.mirageAuction.gui.GuiManager;
import org.example.sawervatoryy.mirageAuction.listener.GuiListener;
import org.example.sawervatoryy.mirageAuction.manager.AuctionManager;
import org.example.sawervatoryy.mirageAuction.manager.CategoryManager;
import org.example.sawervatoryy.mirageAuction.manager.ConfigManager;
import org.example.sawervatoryy.mirageAuction.manager.Messages;
import org.example.sawervatoryy.mirageAuction.manager.ThemeManager;
import org.example.sawervatoryy.mirageAuction.util.Money;
import org.example.sawervatoryy.mirageAuction.util.Sounds;
import org.example.sawervatoryy.mirageAuction.util.TimeUtil;

/**
 * MirageAuction — профессиональный аукцион для Paper 1.21.4+.
 * Создано студией MirageStudio. Автор: sawervatoryy. Discord: https://discord.gg/dWPenZegK
 */
public final class MirageAuction extends JavaPlugin {

    private ConfigManager configs;
    private Messages messages;
    private ThemeManager themes;
    private CategoryManager categories;
    private EconomyHook economy;
    private AuctionManager manager;
    private GuiManager gui;
    private ChatInput chatInput;

    @Override
    public void onEnable() {
        configs = new ConfigManager(this);
        configs.load();
        messages = new Messages(this);
        themes = new ThemeManager(this);
        categories = new CategoryManager();
        economy = new EconomyHook();
        chatInput = new ChatInput(this);
        manager = new AuctionManager(this);
        gui = new GuiManager(this);
        applyConfig();

        PluginCommand ah = getCommand("ah");
        if (ah != null) {
            AhCommand executor = new AhCommand(this);
            ah.setExecutor(executor);
            ah.setTabCompleter(executor);
        }
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);

        manager.start();
        long refresh = Math.max(5, configs.config().getInt("gui.auto-refresh-ticks", 20));
        Bukkit.getScheduler().runTaskTimer(this, () -> gui.autoRefresh(), refresh, refresh);

        Bukkit.getScheduler().runTask(this, () -> {
            if (!economy.setup(configs.config().getString("economy.provider", "VAULT"))) {
                getLogger().warning("Экономика не найдена! Установите Vault + экономический плагин (или PlayerPoints).");
            } else {
                getLogger().info("Экономика: " + economy.provider());
            }
        });

        banner();
    }

    @Override
    public void onDisable() {
        if (gui != null) gui.closeAll();
        if (manager != null) manager.stop();
    }

    private void banner() {
        getLogger().info("=======================================");
        getLogger().info(" MirageAuction v" + getPluginMeta().getVersion());
        getLogger().info(" Создано студией MirageStudio");
        getLogger().info(" Автор: sawervatoryy");
        getLogger().info(" Discord: https://discord.gg/dWPenZegK");
        getLogger().info("=======================================");
    }

    /** Применяет значения из конфигов (тема, формат цен, звуки, категории). */
    private void applyConfig() {
        themes.load();
        Money.load(configs.config().getConfigurationSection("format"));
        TimeUtil.load(configs.config().getConfigurationSection("format.time-units"));
        Sounds.load(configs.config().getConfigurationSection("sounds"));
        categories.load(configs.gui().getConfigurationSection("categories"));
    }

    public void reloadAll() {
        configs.load();
        applyConfig();
        economy.setup(configs.config().getString("economy.provider", "VAULT"));
    }

    /** Лениво (пере)подключает экономику; true — если готова. */
    public boolean ensureEconomy() {
        if (!economy.ready()) economy.setup(configs.config().getString("economy.provider", "VAULT"));
        return economy.ready();
    }

    public ConfigManager configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public ThemeManager themes() {
        return themes;
    }

    public CategoryManager categories() {
        return categories;
    }

    public EconomyHook economy() {
        return economy;
    }

    public AuctionManager manager() {
        return manager;
    }

    public GuiManager gui() {
        return gui;
    }

    public ChatInput chatInput() {
        return chatInput;
    }
}

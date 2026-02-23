package me.minseok.shopsystem;

import me.minseok.shopsystem.database.DatabaseManager;
import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.commands.*;
import me.minseok.shopsystem.shop.ShopGUI;
import me.minseok.shopsystem.shop.ShopManager;
import me.minseok.shopsystem.utils.MessageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ShopCore extends JavaPlugin {

    private DatabaseManager database;
    private VaultEconomy economy;
    private ShopManager shopManager;
    private ShopGUI shopGUI;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Validate configuration
        if (!validateConfiguration()) {
            getLogger().severe("Configuration validation failed! Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Server Name: " + getConfig().getString("server-name", "unknown"));

        // Initialize database
        FileConfiguration config = getConfig();
        database = new DatabaseManager(
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.database", "minecraft"),
                config.getString("database.username", "root"),
                config.getString("database.password", ""),
                getLogger());

        try {
            database.initialize();
            getLogger().info("Database initialized successfully");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize economy
        economy = new VaultEconomy(database, getLogger());

        // Register Vault economy provider
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getServicesManager().register(
                    Economy.class,
                    economy,
                    this,
                    ServicePriority.Highest);
            getLogger().info("Registered Vault economy provider");
        } else {
            getLogger().warning("Vault not found! Economy features may not work");
        }

        // Initialize shop system
        shopManager = new ShopManager(getDataFolder(), getLogger(), database, this);
        shopManager.loadConfig(getConfig());
        shopManager.loadShops();

        // Initialize MessageManager
        messageManager = new MessageManager(this);
        messageManager.reloadMessages();

        // Register messaging
        getServer().getMessenger().registerIncomingPluginChannel(this, "shopsystem:sync",
                new me.minseok.shopsystem.messaging.BackendMessageListener(this));
        getServer().getMessenger().registerOutgoingPluginChannel(this, "shopsystem:sync");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Register economy commands
        getCommand("balance").setExecutor(new BalanceCommand(economy, messageManager));
        getCommand("pay").setExecutor(new PayCommand(economy, messageManager));
        getCommand("baltop").setExecutor(new BaltopCommand(database, messageManager, this));
        getCommand("eco").setExecutor(new EcoCommand(economy, messageManager));

        shopGUI = new ShopGUI(shopManager, economy, messageManager);
        ShopCommand shopCmd = new ShopCommand(shopGUI, shopManager, messageManager);
        getCommand("shop").setExecutor(shopCmd);
        getCommand("shop").setTabCompleter(shopCmd);

        // Register listeners
        getServer().getPluginManager().registerEvents(shopGUI, this);
        getServer().getPluginManager()
                .registerEvents(new me.minseok.shopsystem.listeners.PlayerJoinListener(shopManager), this);

        // Schedule auto-refresh task
        int refreshInterval = getConfig().getInt("dynamic-pricing.auto-refresh-interval", 10);
        if (refreshInterval > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                shopManager.refreshPrices();
            }, 20L * refreshInterval, 20L * refreshInterval);
            getLogger().info("Auto-refresh task scheduled every " + refreshInterval + " seconds.");
        }

        getCommand("sell").setExecutor(new SellCommand(economy, messageManager));

        SellAllCommand sellAllCmd = new SellAllCommand(economy, shopManager, messageManager);
        SellGUICommand sellGUICmd = new SellGUICommand(economy, shopManager, messageManager);
        getCommand("sellall").setExecutor(sellAllCmd);
        getCommand("sellall").setTabCompleter(sellAllCmd);
        getCommand("sellgui").setExecutor(sellGUICmd);

        // Admin commands
        EShopCommand eshopCmd = new EShopCommand(shopManager, new File(getDataFolder(), "shops"), messageManager);
        ShopGiveCommand shopGiveCmd = new ShopGiveCommand(shopManager, messageManager);
        getCommand("eshop").setExecutor(eshopCmd);
        getCommand("eshop").setTabCompleter(eshopCmd);
        getCommand("shopgive").setExecutor(shopGiveCmd);
        getCommand("shopgive").setTabCompleter(shopGiveCmd);

        // Register listeners
        getServer().getPluginManager().registerEvents(sellGUICmd, this);

        // Register sync listener and force refresh on join
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                // Force refresh prices on join
                getServer().getScheduler().runTaskAsynchronously(ShopCore.this, () -> {
                    shopManager.refreshPrices();
                });

                if (getServer().getOnlinePlayers().size() == 1) {
                    sendSyncRequest(event.getPlayer());
                }
            }
        }, this);

        getLogger().info("ShopCore enabled successfully");
    }

    /**
     * 플러그인 설정을 검증합니다
     * 
     * @return 설정이 유효하면 true, 그렇지 않으면 false
     */
    private boolean validateConfiguration() {
        FileConfiguration config = getConfig();
        boolean isValid = true;

        // 필수 설정 검증
        String serverName = config.getString("server-name");
        if (serverName == null || serverName.isEmpty()) {
            getLogger().warning("Missing required config: server-name");
            isValid = false;
        }

        // 데이터베이스 설정 검증
        String dbHost = config.getString("database.host");
        String dbName = config.getString("database.database");
        String dbUser = config.getString("database.username");

        if (dbHost == null || dbHost.isEmpty()) {
            getLogger().warning("Missing required config: database.host");
            isValid = false;
        }
        if (dbName == null || dbName.isEmpty()) {
            getLogger().warning("Missing required config: database.database");
            isValid = false;
        }
        if (dbUser == null || dbUser.isEmpty()) {
            getLogger().warning("Missing required config: database.username");
            isValid = false;
        }

        // 동적 가격 설정 검증
        double maxMult = config.getDouble("dynamic-pricing.max-multiplier", 5.0);
        double minMult = config.getDouble("dynamic-pricing.min-multiplier", 0.2);

        if (maxMult <= minMult) {
            getLogger().warning("Invalid dynamic-pricing config: max-multiplier must be > min-multiplier");
            isValid = false;
        }

        if (isValid) {
            getLogger().info("Configuration validation passed");
        }

        return isValid;
    }

    public void requestSync() {
        if (getServer().getOnlinePlayers().isEmpty()) {
            return;
        }

        org.bukkit.entity.Player player = getServer().getOnlinePlayers().iterator().next();
        sendSyncRequest(player);
    }

    private void sendSyncRequest(org.bukkit.entity.Player player) {
        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("REQUEST_CONFIG");
        player.sendPluginMessage(this, "shopsystem:sync", out.toByteArray());
        getLogger().info("Sent config request to Velocity via " + player.getName());
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        getLogger().info("ShopCore disabled");
    }

    // ... existing methods ...

    public DatabaseManager getDatabase() {
        return database;
    }

    public VaultEconomy getEconomy() {
        return economy;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }
}

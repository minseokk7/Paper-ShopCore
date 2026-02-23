package me.minseok.shopsystem.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.minseok.shopsystem.database.DatabaseManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ShopManager {

    private final File shopsFolder;
    private final Logger logger;
    private final DatabaseManager database;
    private final Plugin plugin;
    private final Map<String, ShopCategory> categories = new HashMap<>();
    private final Map<String, ShopItem> itemCache = new HashMap<>();

    // Dynamic Pricing Configuration
    private double buyIncreaseRate = 0.05;
    private double sellDecreaseRate = 0.03;
    private double maxMultiplier = 5.0;
    private double minMultiplier = 0.2;
    private double decayRate = 0.01; // 1% per interval
    private int decayInterval = 3600; // seconds

    public ShopManager(File dataFolder, Logger logger, DatabaseManager database, Plugin plugin) {
        this.shopsFolder = new File(dataFolder, "shops");
        this.logger = logger;
        this.database = database;
        this.plugin = plugin;

        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
        }

        // Start price decay scheduler
        startPriceDecayScheduler();
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void loadConfig(FileConfiguration config) {
        if (config.contains("dynamic-pricing")) {
            buyIncreaseRate = config.getDouble("dynamic-pricing.buy-increase-rate", 0.05);
            sellDecreaseRate = config.getDouble("dynamic-pricing.sell-decrease-rate", 0.03);
            maxMultiplier = config.getDouble("dynamic-pricing.max-multiplier", 5.0);
            minMultiplier = config.getDouble("dynamic-pricing.min-multiplier", 0.2);
            decayRate = config.getDouble("dynamic-pricing.decay-rate", 0.01);
            decayInterval = config.getInt("dynamic-pricing.decay-interval", 3600);
            resetFluctuation = config.getDouble("dynamic-pricing.reset.fluctuation", 0.1);
        }
    }

    /**
     * 가격 변동률을 계산합니다 (공통 메서드)
     * 
     * @param item  아이템
     * @param isBuy true면 구매가, false면 판매가 기준
     * @return 변동률 (%)
     */
    public double calculatePriceChangePercentage(ShopItem item, boolean isBuy) {
        double base = isBuy ? item.getBaseBuyPrice() : item.getBaseSellPrice();
        double current = isBuy ? item.getBuyPrice() : item.getSellPrice();
        if (base == 0)
            return 0;
        return ((current - base) / base) * 100;
    }

    private void startPriceDecayScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                applyPriceDecay();
            }
        }.runTaskTimerAsynchronously(plugin, decayInterval * 20L, decayInterval * 20L);
        logger.log(Level.INFO, "Price decay scheduler started (interval: " + decayInterval + "s)");
    }

    private void applyPriceDecay() {
        int itemsDecayed = 0;
        for (ShopItem item : itemCache.values()) {
            if (!item.hasDynamicPricing())
                continue;

            double currentBuy = item.getBuyPrice();
            double currentSell = item.getSellPrice();
            double baseBuy = item.getBaseBuyPrice();
            double baseSell = item.getBaseSellPrice();

            boolean changed = false;

            // Decay towards base price
            if (currentBuy > baseBuy) {
                double newBuy = currentBuy * (1 - decayRate);
                if (newBuy < baseBuy)
                    newBuy = baseBuy;
                item.setBuyPrice(newBuy);
                changed = true;
            }

            if (currentSell < baseSell) {
                double newSell = currentSell * (1 + decayRate);
                if (newSell > baseSell)
                    newSell = baseSell;
                item.setSellPrice(newSell);
                changed = true;
            }

            if (changed) {
                savePriceToDatabase(item);
                itemsDecayed++;
            }
        }

        if (itemsDecayed > 0) {
            logger.log(Level.INFO, "[PriceDecay] Applied decay to " + itemsDecayed + " items");
        }
    }

    private double resetFluctuation = 0.1; // +/- 10% fluctuation

    public void resetPrices() {
        logger.info("Starting scheduled price reset...");
        int count = 0;
        java.util.Random random = new java.util.Random();

        for (ShopItem item : itemCache.values()) {
            if (!item.hasDynamicPricing())
                continue;

            resetPrice(item, random);
            count++;
        }
        logger.info("Reset prices for " + count + " items.");
    }

    public void resetPrice(ShopItem item) {
        resetPrice(item, new java.util.Random());
    }

    private void resetPrice(ShopItem item, java.util.Random random) {
        double baseBuy = item.getBaseBuyPrice();
        double baseSell = item.getBaseSellPrice();

        double newBuy = baseBuy;
        double newSell = baseSell;

        // Apply random fluctuation if enabled
        if (resetFluctuation > 0) {
            double fluctuation = 1.0 + (random.nextDouble() * 2 - 1) * resetFluctuation; // 1.0 +/- fluctuation
            newBuy *= fluctuation;
            newSell *= fluctuation;
        }

        item.setBuyPrice(newBuy);
        item.setSellPrice(newSell);

        savePriceToDatabase(item);

        // Broadcast update
        plugin.getServer().getPluginManager()
                .callEvent(new me.minseok.shopsystem.events.ShopPriceUpdateEvent(item));
        broadcastPriceUpdate(item);
    }

    private void savePriceToDatabase(ShopItem item) {
        // 비동기로 DB 저장 (메인 스레드 블로킹 방지)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            executeSavePriceToDatabase(item);
        });
    }

    private void executeSavePriceToDatabase(ShopItem item) {
        String sql = "INSERT INTO price_data (item_id, base_price, current_price, sell_price, transaction_count, last_updated) "
                + "VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP) "
                + "ON DUPLICATE KEY UPDATE current_price = ?, sell_price = ?, "
                + "transaction_count = transaction_count + 1, last_updated = CURRENT_TIMESTAMP";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getId());
            stmt.setDouble(2, item.getBaseBuyPrice());
            stmt.setDouble(3, item.getBuyPrice());
            stmt.setDouble(4, item.getSellPrice());
            stmt.setDouble(5, item.getBuyPrice());
            stmt.setDouble(6, item.getSellPrice());
            int rows = stmt.executeUpdate();
            plugin.getLogger().info("DEBUG: Saved price for " + item.getMaterial().name() + ". Rows affected: " + rows);
        } catch (SQLException e) {
            logger.warning("Failed to save price for " + item.getMaterial() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadPriceFromDatabase(ShopItem item) {
        String sql = "SELECT current_price, sell_price FROM price_data WHERE item_id = ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    item.setBuyPrice(rs.getDouble("current_price"));
                    item.setSellPrice(rs.getDouble("sell_price"));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to load price for " + item.getMaterial() + ": " + e.getMessage());
        }
    }

    public void loadShops() {
        categories.clear();
        itemCache.clear();

        File[] shopFiles = shopsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (shopFiles == null || shopFiles.length == 0) {
            logger.warning("No shop files found in " + shopsFolder.getPath());
            createDefaultShops();
            return;
        }

        for (File file : shopFiles) {
            try {
                loadShopFile(file);
            } catch (Exception e) {
                logger.warning("Failed to load shop file: " + file.getName());
                e.printStackTrace();
            }
        }

        logger.info("Loaded " + categories.size() + " shop categories");
    }

    private void loadShopFile(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        String categoryId = file.getName().replace(".yml", "").toLowerCase();
        String categoryName = config.getString("category.name", categoryId);
        String iconStr = config.getString("category.icon", "CHEST");
        int slot = config.getInt("category.slot", 0);

        Material icon = Material.getMaterial(iconStr);
        if (icon == null)
            icon = Material.CHEST;

        ShopCategory category = new ShopCategory(categoryId, categoryName, icon, slot);

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                if (itemSection == null)
                    continue;

                String materialStr = itemSection.getString("material");
                if (materialStr == null)
                    continue;

                Material material = Material.getMaterial(materialStr);
                if (material == null) {
                    logger.warning("Invalid material: " + materialStr);
                    continue;
                }

                double buyPrice = itemSection.getDouble("buy", 0);
                double sellPrice = itemSection.getDouble("sell", 0);
                boolean dynamicPricing = itemSection.getBoolean("dynamic", false);
                boolean oneTime = itemSection.getBoolean("one-time", false);

                Map<Enchantment, Integer> enchantments = new HashMap<>();
                if (itemSection.contains("enchantments")) {
                    ConfigurationSection enchSection = itemSection.getConfigurationSection("enchantments");
                    if (enchSection != null) {
                        for (String key : enchSection.getKeys(false)) {
                            @SuppressWarnings("deprecation")
                            Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase()));
                            if (ench != null) {
                                enchantments.put(ench, enchSection.getInt(key));
                            } else {
                                logger.warning("Invalid enchantment: " + key);
                            }
                        }
                    }
                }

                Double buyRate = itemSection.contains("buy-rate") ? itemSection.getDouble("buy-rate") : null;
                Double sellRate = itemSection.contains("sell-rate") ? itemSection.getDouble("sell-rate") : null;

                ShopItem item = new ShopItem(itemKey, material, buyPrice, sellPrice, dynamicPricing, oneTime,
                        enchantments,
                        buyRate, sellRate);

                // Load price from database if dynamic pricing is enabled
                if (dynamicPricing) {
                    loadPriceFromDatabase(item);
                }

                category.addItem(item);
                itemCache.put(itemKey, item);
            }
        }

        categories.put(categoryId, category);
    }

    private void createDefaultShops() {
        // Create a sample enchanting shop
        File enchantingFile = new File(shopsFolder, "enchanting.yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("category.name", "마법 부여");
        config.set("category.icon", "ENCHANTED_BOOK");
        config.set("category.slot", 10);

        config.set("items.MENDING.material", "ENCHANTED_BOOK");
        config.set("items.MENDING.buy", 5000);
        config.set("items.MENDING.sell", 1250);
        config.set("items.MENDING.dynamic", true);

        // Default enchantment
        config.set("items.MENDING.enchantments.mending", 1);

        try {
            config.save(enchantingFile);
            logger.info("Created default enchanting shop");
        } catch (Exception e) {
            logger.severe("Failed to create default shop: " + e.getMessage());
        }
    }

    public Map<String, ShopCategory> getCategories() {
        return new HashMap<>(categories);
    }

    public ShopCategory getCategory(String id) {
        return categories.get(id.toLowerCase());
    }

    public void adjustPrice(ShopItem item, boolean isBuy, int amount) {
        if (!item.hasDynamicPricing())
            return;

        double currentBuy = item.getBuyPrice();
        double currentSell = item.getSellPrice();
        double baseBuy = item.getBaseBuyPrice();
        double baseSell = item.getBaseSellPrice();

        // Determine rates (use item-specific if available, otherwise global)
        double increaseRate = (item.getBuyRate() != null) ? item.getBuyRate() : buyIncreaseRate;
        double decreaseRate = (item.getSellRate() != null) ? item.getSellRate() : sellDecreaseRate;

        // Apply rate based on amount (Exponential calculation)
        double rateFactor = isBuy ? (1 + increaseRate) : (1 - decreaseRate);
        double adjustment = Math.pow(rateFactor, amount);

        double newBuy = currentBuy * adjustment;
        double newSell = currentSell * adjustment;

        // Apply multiplier limits
        double buyMultiplier = newBuy / baseBuy;
        double sellMultiplier = newSell / baseSell;

        if (buyMultiplier > maxMultiplier) {
            newBuy = baseBuy * maxMultiplier;
        } else if (buyMultiplier < minMultiplier) {
            newBuy = baseBuy * minMultiplier;
        }

        if (sellMultiplier > maxMultiplier) {
            newSell = baseSell * maxMultiplier;
        } else if (sellMultiplier < minMultiplier) {
            newSell = baseSell * minMultiplier;
        }

        item.setBuyPrice(newBuy);
        item.setSellPrice(newSell);

        // Save to database
        savePriceToDatabase(item);

        // Fire event for local GUI update
        plugin.getServer().getPluginManager()
                .callEvent(new me.minseok.shopsystem.events.ShopPriceUpdateEvent(item));

        // Broadcast update to other servers
        broadcastPriceUpdate(item);
    }

    private void broadcastPriceUpdate(ShopItem item) {
        if (plugin.getServer().getOnlinePlayers().isEmpty()) {
            return;
        }

        org.bukkit.entity.Player player = plugin.getServer().getOnlinePlayers().iterator().next();

        // Prepare data
        com.google.common.io.ByteArrayDataOutput dataOut = com.google.common.io.ByteStreams.newDataOutput();
        dataOut.writeUTF("PRICE_UPDATE");
        dataOut.writeUTF(item.getId());
        dataOut.writeDouble(item.getBuyPrice());
        dataOut.writeDouble(item.getSellPrice());
        dataOut.writeUTF(plugin.getConfig().getString("server-name", "unknown"));

        // Send directly to Velocity on shopsystem:sync
        player.sendPluginMessage(plugin, "shopsystem:sync", dataOut.toByteArray());
        plugin.getLogger().info("Sent PRICE_UPDATE for " + item.getId() + " to Velocity via shopsystem:sync");
    }

    public void updateItemPrice(String itemId, double buyPrice, double sellPrice) {
        ShopItem item = itemCache.get(itemId);
        if (item != null) {
            item.setBuyPrice(buyPrice);
            item.setSellPrice(sellPrice);
            savePriceToDatabase(item);
            logger.info("Updated price for " + itemId + ": Buy=" + buyPrice + ", Sell=" + sellPrice);

            plugin.getServer().getPluginManager()
                    .callEvent(new me.minseok.shopsystem.events.ShopPriceUpdateEvent(item));
        } else {
            logger.warning("Item not found for price update: " + itemId);
        }
    }

    public ShopItem getItemById(String id) {
        return itemCache.get(id);
    }

    public ShopItem getShopItem(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return null;

        for (ShopCategory category : categories.values()) {
            for (ShopItem item : category.getItems()) {
                if (item.getMaterial() == stack.getType()) {
                    // Check enchantments
                    if (item.getEnchantments().isEmpty()) {
                        if (stack.getEnchantments().isEmpty()) {
                            return item;
                        }
                    } else {
                        boolean match = true;
                        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                            if (stack.getEnchantmentLevel(entry.getKey()) != entry.getValue()) {
                                match = false;
                                break;
                            }
                        }
                        if (match)
                            return item;
                    }
                }
            }
        }
        return null;
    }

    public ShopItem getItemByMaterial(Material material) {
        for (ShopItem item : itemCache.values()) {
            if (item.getMaterial() == material) {
                return item;
            }
        }
        return null;
    }

    public void reloadShops() {
        loadShops();
        logger.info("Shops reloaded successfully");
    }

    public void refreshPrices() {
        int count = 0;
        for (ShopItem item : itemCache.values()) {
            if (item.hasDynamicPricing()) {
                loadPriceFromDatabase(item);
                count++;
            }
        }
        logger.info("Refreshed prices for " + count + " items from database");
    }

    public List<Map<String, Object>> getPriceHistory(String itemId, int limit) {
        List<Map<String, Object>> history = new ArrayList<>();
        String sql = "SELECT price, reason, timestamp FROM price_history WHERE item_id = ? ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("price", rs.getDouble("price"));
                    record.put("reason", rs.getString("reason"));
                    record.put("timestamp", rs.getTimestamp("timestamp"));
                    history.add(record);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to load price history for " + itemId + ": " + e.getMessage());
        }

        return history;
    }

    public Map<String, Integer> getPopularItems(int limit) {
        Map<String, Integer> popular = new HashMap<>();
        String sql = "SELECT item_id, transaction_count FROM price_data ORDER BY transaction_count DESC LIMIT ?";

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    popular.put(rs.getString("item_id"), rs.getInt("transaction_count"));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to load popular items: " + e.getMessage());
        }

        return popular;
    }

    public static class ShopCategory {
        private final String id;
        private final String name;
        private final Material icon;
        private final int slot;
        private final List<ShopItem> items = new ArrayList<>();

        public ShopCategory(String id, String name, Material icon, int slot) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
        }

        public void addItem(ShopItem item) {
            items.add(item);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Material getIcon() {
            return icon;
        }

        public int getSlot() {
            return slot;
        }

        public List<ShopItem> getItems() {
            return new ArrayList<>(items);
        }
    }

    public static class ShopItem {
        private final String id;
        private final Material material;
        private double buyPrice;
        private double sellPrice;
        private final double baseBuyPrice;
        private final double baseSellPrice;
        private final boolean dynamicPricing;
        private final boolean oneTime;
        private final Map<Enchantment, Integer> enchantments;
        private final Double buyRate;
        private final Double sellRate;

        public ShopItem(String id, Material material, double buyPrice, double sellPrice, boolean dynamicPricing,
                boolean oneTime,
                Map<Enchantment, Integer> enchantments, Double buyRate, Double sellRate) {
            this.id = id;
            this.material = material;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.baseBuyPrice = buyPrice;
            this.baseSellPrice = sellPrice;
            this.dynamicPricing = dynamicPricing;
            this.oneTime = oneTime;
            this.enchantments = enchantments != null ? enchantments : new HashMap<>();
            this.buyRate = buyRate;
            this.sellRate = sellRate;
        }

        public String getId() {
            return id;
        }

        public Material getMaterial() {
            return material;
        }

        public double getBuyPrice() {
            return buyPrice;
        }

        public double getSellPrice() {
            return sellPrice;
        }

        public double getBaseBuyPrice() {
            return baseBuyPrice;
        }

        public double getBaseSellPrice() {
            return baseSellPrice;
        }

        public boolean hasDynamicPricing() {
            return dynamicPricing;
        }

        public boolean isOneTime() {
            return oneTime;
        }

        public Map<Enchantment, Integer> getEnchantments() {
            return enchantments;
        }

        public Double getBuyRate() {
            return buyRate;
        }

        public Double getSellRate() {
            return sellRate;
        }

        public void setBuyPrice(double price) {
            this.buyPrice = price;
        }

        public void setSellPrice(double price) {
            this.sellPrice = price;
        }
    }
}

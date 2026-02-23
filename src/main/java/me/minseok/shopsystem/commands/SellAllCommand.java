package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.shop.ShopManager;
import me.minseok.shopsystem.utils.MessageManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellAllCommand implements CommandExecutor, TabCompleter {

    private final VaultEconomy economy;
    private final ShopManager shopManager;
    private final MessageManager messageManager;

    public SellAllCommand(VaultEconomy economy, ShopManager shopManager, MessageManager messageManager) {
        this.economy = economy;
        this.shopManager = shopManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.send(sender, "general.player-only");
            return true;
        }

        Player player = (Player) sender;

        // /sellall hand - sell items in hand
        if (args.length > 0 && args[0].equalsIgnoreCase("hand")) {
            return sellHand(player);
        }

        // /sellall <item> - sell specific item
        if (args.length > 0 && !args[0].equalsIgnoreCase("inventory")) {
            return sellSpecificItem(player, args[0]);
        }

        // /sellall [inventory] - sell all items
        return sellAllItems(player);
    }

    private boolean sellHand(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            messageManager.sendCustom(player, "<red>손에 아이템이 없습니다!");
            return true;
        }

        ShopManager.ShopItem shopItem = shopManager.getShopItem(handItem);
        if (shopItem == null || shopItem.getSellPrice() <= 0) {
            messageManager.sendCustom(player, "<red>이 아이템은 판매할 수 없습니다!");
            return true;
        }

        // Check permission
        String categoryId = getCategoryForItem(shopItem);
        if (categoryId != null && !player.hasPermission("shopsystem.sellallhand." + categoryId) &&
                !player.hasPermission("shopsystem.admin")) {
            messageManager.send(player, "general.no-permission");
            return true;
        }

        // Count all matching items in inventory
        int totalAmount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(handItem)) {
                totalAmount += item.getAmount();
            }
        }

        if (totalAmount == 0) {
            messageManager.sendCustom(player, "<red>판매할 아이템이 없습니다!");
            return true;
        }

        double totalValue = shopItem.getSellPrice() * totalAmount;

        // Remove all matching items
        ItemStack toRemove = handItem.clone();
        toRemove.setAmount(totalAmount);
        player.getInventory().removeItem(toRemove);

        EconomyResponse response = economy.depositPlayer(player, totalValue);
        if (response.transactionSuccess()) {
            messageManager.sendCustom(player, "<green>✓ " + handItem.getType().name() + " x" + totalAmount + "을(를) " +
                    economy.format(totalValue) + "에 판매했습니다!");
            messageManager.sendCustom(player, "<gray>잔액: <white>" + economy.format(response.balance));

            if (shopItem.hasDynamicPricing()) {
                shopManager.adjustPrice(shopItem, false, totalAmount);
            }
        } else {
            ItemStack toAdd = handItem.clone();
            toAdd.setAmount(totalAmount);
            player.getInventory().addItem(toAdd);
            messageManager.sendCustom(player, "<red>판매 실패: " + response.errorMessage);
        }

        return true;
    }

    private boolean sellSpecificItem(Player player, String itemName) {
        // Try to find by ID first
        ShopManager.ShopItem shopItem = shopManager.getItemById(itemName.toUpperCase());

        // Fallback to Material
        if (shopItem == null) {
            Material material = Material.getMaterial(itemName.toUpperCase());
            if (material != null) {
                shopItem = shopManager.getItemByMaterial(material);
            }
        }

        if (shopItem == null || shopItem.getSellPrice() <= 0) {
            messageManager.sendCustom(player, "<red>판매할 수 없는 아이템이거나 찾을 수 없습니다: " + itemName);
            return true;
        }

        // Check permission
        String categoryId = getCategoryForItem(shopItem);
        if (categoryId != null && !player.hasPermission("shopsystem.sellallitem." + categoryId) &&
                !player.hasPermission("shopsystem.admin")) {
            messageManager.send(player, "general.no-permission");
            return true;
        }

        // Count matching items
        // Note: For specific item sell, we need to construct a template ItemStack to
        // check isSimilar
        // But since we don't have the item stack, we might have to rely on Material
        // check if it's not an enchanted book
        // Or we can try to find an item in inventory that matches the ShopItem

        // Simplified: Just check Material for now, or iterate inventory and check
        // getShopItem
        int totalAmount = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                ShopManager.ShopItem matched = shopManager.getShopItem(item);
                if (matched == shopItem) {
                    totalAmount += item.getAmount();
                    itemsToRemove.add(item);
                }
            }
        }

        if (totalAmount == 0) {
            messageManager.sendCustom(player, "<red>인벤토리에 해당 아이템이 없습니다!");
            return true;
        }

        double totalValue = shopItem.getSellPrice() * totalAmount;

        // Remove items
        for (ItemStack is : itemsToRemove) {
            player.getInventory().removeItem(is);
        }

        EconomyResponse response = economy.depositPlayer(player, totalValue);
        if (response.transactionSuccess()) {
            messageManager.sendCustom(player, "<green>✓ " + shopItem.getId() + " x" + totalAmount + "을(를) " +
                    economy.format(totalValue) + "에 판매했습니다!");
            messageManager.sendCustom(player, "<gray>잔액: <white>" + economy.format(response.balance));

            if (shopItem.hasDynamicPricing()) {
                shopManager.adjustPrice(shopItem, false, totalAmount);
            }
        } else {
            for (ItemStack is : itemsToRemove) {
                player.getInventory().addItem(is);
            }
            messageManager.sendCustom(player, "<red>판매 실패: " + response.errorMessage);
        }

        return true;
    }

    private boolean sellAllItems(Player player) {
        Map<ShopManager.ShopItem, Integer> itemsToSell = new HashMap<>();
        double totalValue = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Scan inventory for sellable items
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR)
                continue;

            ShopManager.ShopItem shopItem = shopManager.getShopItem(item);
            if (shopItem == null || shopItem.getSellPrice() <= 0)
                continue;

            // Check permission for category
            String categoryId = getCategoryForItem(shopItem);
            if (categoryId != null && !player.hasPermission("shopsystem.sellall." + categoryId) &&
                    !player.hasPermission("shopsystem.admin")) {
                continue;
            }

            itemsToSell.put(shopItem, itemsToSell.getOrDefault(shopItem, 0) + item.getAmount());
            totalValue += shopItem.getSellPrice() * item.getAmount();
            itemsToRemove.add(item);
        }

        if (itemsToSell.isEmpty()) {
            messageManager.sendCustom(player, "<red>판매할 수 있는 아이템이 없습니다!");
            return true;
        }

        // Remove items
        for (ItemStack is : itemsToRemove) {
            player.getInventory().removeItem(is);
        }

        EconomyResponse response = economy.depositPlayer(player, totalValue);
        if (response.transactionSuccess()) {
            int totalItems = itemsToSell.values().stream().mapToInt(Integer::intValue).sum();
            messageManager.sendCustom(player, "<green>✓ " + totalItems + "개 아이템을 " +
                    economy.format(totalValue) + "에 판매했습니다!");
            messageManager.sendCustom(player, "<gray>잔액: <white>" + economy.format(response.balance));

            // Adjust prices for dynamic items
            for (Map.Entry<ShopManager.ShopItem, Integer> entry : itemsToSell.entrySet()) {
                ShopManager.ShopItem shopItem = entry.getKey();
                if (shopItem.hasDynamicPricing()) {
                    shopManager.adjustPrice(shopItem, false, entry.getValue());
                }
            }
        } else {
            for (ItemStack is : itemsToRemove) {
                player.getInventory().addItem(is);
            }
            messageManager.sendCustom(player, "<red>판매 실패: " + response.errorMessage);
        }

        return true;
    }

    /**
     * Helper method to get category ID for a shop item
     */
    private String getCategoryForItem(ShopManager.ShopItem item) {
        for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
            if (category.getItems().contains(item)) {
                return category.getId();
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("hand");
            completions.add("inventory");

            // Add common materials
            for (Material material : Material.values()) {
                if (material.isItem()) {
                    completions.add(material.name().toLowerCase());
                }
            }
        }

        return completions;
    }
}

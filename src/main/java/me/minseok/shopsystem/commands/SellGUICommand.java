package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.shop.ShopManager;
import me.minseok.shopsystem.utils.MessageManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SellGUICommand implements CommandExecutor, Listener {

    private final VaultEconomy economy;
    private final ShopManager shopManager;
    private final MessageManager messageManager;

    public SellGUICommand(VaultEconomy economy, ShopManager shopManager, MessageManager messageManager) {
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
        openSellGUI(player);
        return true;
    }

    private void openSellGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§l아이템 판매");

        // Fill with sellable items from inventory
        int slot = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR)
                continue;
            if (slot >= 45)
                break; // Reserve last row for buttons

            ShopManager.ShopItem shopItem = shopManager.getShopItem(item);
            if (shopItem == null || shopItem.getSellPrice() <= 0)
                continue;

            ItemStack display = item.clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§c판매가: §f" + economy.format(shopItem.getSellPrice()));
            lore.add("§7총 가치: §f" + economy.format(shopItem.getSellPrice() * item.getAmount()));
            lore.add("");
            lore.add("§e클릭하여 판매");
            meta.setLore(lore);
            display.setItemMeta(meta);

            inv.setItem(slot++, display);
        }

        // Sell All button
        ItemStack sellAll = new ItemStack(Material.EMERALD);
        ItemMeta sellAllMeta = sellAll.getItemMeta();
        sellAllMeta.setDisplayName("§a§l모두 판매");
        List<String> sellAllLore = new ArrayList<>();
        sellAllLore.add("§7판매 가능한 모든 아이템을");
        sellAllLore.add("§7한번에 판매합니다.");
        sellAllMeta.setLore(sellAllLore);
        sellAll.setItemMeta(sellAllMeta);
        inv.setItem(49, sellAll);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c닫기");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        if (!event.getView().getTitle().equals("§c§l아이템 판매"))
            return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (clicked.getType() == Material.EMERALD) {
            // Sell all items
            player.closeInventory();
            player.performCommand("sellall");
            return;
        }

        // Sell clicked item
        ShopManager.ShopItem shopItem = shopManager.getShopItem(clicked);
        if (shopItem == null)
            return;

        // Find and remove from player inventory
        int amount = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                ShopManager.ShopItem currentItem = shopManager.getShopItem(item);
                if (currentItem == shopItem) {
                    amount += item.getAmount();
                    itemsToRemove.add(item);
                }
            }
        }

        if (amount > 0) {
            for (ItemStack is : itemsToRemove) {
                player.getInventory().removeItem(is);
            }

            double totalPrice = shopItem.getSellPrice() * amount;

            EconomyResponse response = economy.depositPlayer(player, totalPrice);
            if (response.transactionSuccess()) {
                messageManager.sendCustom(player, "<green>✓ " + shopItem.getId() + " x" + amount + "을(를) " +
                        economy.format(totalPrice) + "에 판매했습니다!");
                messageManager.sendCustom(player, "<gray>잔액: <white>" + economy.format(response.balance));

                if (shopItem.hasDynamicPricing()) {
                    shopManager.adjustPrice(shopItem, false, amount);
                }

                player.closeInventory();
            } else {
                for (ItemStack is : itemsToRemove) {
                    player.getInventory().addItem(is);
                }
                messageManager.sendCustom(player, "<red>판매 실패: " + response.errorMessage);
            }
        }
    }
}

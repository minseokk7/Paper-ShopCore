package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.shop.ShopManager;
import me.minseok.shopsystem.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopGiveCommand implements CommandExecutor, TabCompleter {

    private final ShopManager shopManager;
    private final MessageManager messageManager;

    public ShopGiveCommand(ShopManager shopManager, MessageManager messageManager) {
        this.shopManager = shopManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shopsystem.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            messageManager.sendCustom(sender, "<red>사용법: /shopgive <section> <item_id> [player] [amount]");
            return true;
        }

        String sectionId = args[0].toLowerCase();
        String itemId = args[1].toUpperCase();

        ShopManager.ShopCategory category = shopManager.getCategory(sectionId);
        if (category == null) {
            messageManager.sendCustom(sender, "<red>카테고리를 찾을 수 없습니다: " + sectionId);
            return true;
        }

        // Try to find by ID first
        ShopManager.ShopItem shopItem = shopManager.getItemById(itemId);

        // Fallback to Material if not found
        if (shopItem == null) {
            Material material = Material.getMaterial(itemId);
            if (material != null) {
                shopItem = shopManager.getItemByMaterial(material);
            }
        }

        if (shopItem == null) {
            messageManager.sendCustom(sender, "<red>상점에서 해당 아이템을 찾을 수 없습니다: " + itemId);
            return true;
        }

        // Determine target player
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                messageManager.send(sender, "general.invalid-player");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                messageManager.send(sender, "general.player-only");
                return true;
            }
            target = (Player) sender;
        }

        // Determine amount
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0 || amount > 64) {
                    messageManager.sendCustom(sender, "<red>수량은 1-64 사이여야 합니다.");
                    return true;
                }
            } catch (NumberFormatException e) {
                messageManager.sendCustom(sender, "<red>잘못된 숫자 형식입니다.");
                return true;
            }
        }

        // Create and give item
        ItemStack item = new ItemStack(shopItem.getMaterial(), amount);

        // Apply enchantments if any
        if (!shopItem.getEnchantments().isEmpty()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                org.bukkit.inventory.meta.EnchantmentStorageMeta esm = (org.bukkit.inventory.meta.EnchantmentStorageMeta) meta;
                shopItem.getEnchantments().forEach((ench, level) -> {
                    esm.addStoredEnchant(ench, level, true);
                });
            } else {
                shopItem.getEnchantments().forEach((ench, level) -> {
                    meta.addEnchant(ench, level, true);
                });
            }
            item.setItemMeta(meta);
        }

        target.getInventory().addItem(item);

        messageManager.sendCustom(sender,
                "<green>✓ " + target.getName() + "에게 " + shopItem.getId() + " x" + amount + "을(를) 지급했습니다!");
        if (!sender.equals(target)) {
            messageManager.sendCustom(target, "<green>✓ " + shopItem.getId() + " x" + amount + "을(를) 받았습니다!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Section names
            for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
                completions.add(category.getId());
            }
        } else if (args.length == 2) {
            // Materials
            for (Material material : Material.values()) {
                if (material.isItem()) {
                    completions.add(material.name().toLowerCase());
                }
            }
        } else if (args.length == 3) {
            // Online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }

        return completions;
    }
}

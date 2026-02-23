package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.shop.ShopManager;
import me.minseok.shopsystem.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EShopCommand implements CommandExecutor, TabCompleter {

    private final ShopManager shopManager;
    private final File shopsFolder;
    private final MessageManager messageManager;

    public EShopCommand(ShopManager shopManager, File shopsFolder, MessageManager messageManager) {
        this.shopManager = shopManager;
        this.shopsFolder = shopsFolder;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shopsystem.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "additem":
                return handleAddItem(sender, args);
            case "edititem":
                return handleEditItem(sender, args);
            case "deleteitem":
                return handleDeleteItem(sender, args);
            case "addhanditem":
                return handleAddHandItem(sender, args);
            case "addsection":
                return handleAddSection(sender, args);
            case "editsection":
                return handleEditSection(sender, args);
            case "deletesection":
                return handleDeleteSection(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        messageManager.sendCustom(sender, "<yellow>=== EShop Admin Commands ===");
        messageManager.sendCustom(sender, "<gray>/eshop additem <section> <material> <buy> <sell>");
        messageManager.sendCustom(sender, "<gray>/eshop edititem <section> <index> <action> <key> <value>");
        messageManager.sendCustom(sender, "<gray>/eshop deleteitem <section> <index>");
        messageManager.sendCustom(sender, "<gray>/eshop addhanditem <section> <buy> <sell>");
        messageManager.sendCustom(sender, "<gray>/eshop addsection <section> <material> <name> <slot>");
        messageManager.sendCustom(sender, "<gray>/eshop editsection <section> <action> <key> <value>");
        messageManager.sendCustom(sender, "<gray>/eshop deletesection <section>");
    }

    private boolean handleAddItem(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messageManager.sendCustom(sender, "<red>사용법: /eshop additem <section> <material> <buy> <sell>");
            return true;
        }

        String section = args[1].toLowerCase();
        String materialName = args[2].toUpperCase();

        Material material = Material.getMaterial(materialName);
        if (material == null) {
            messageManager.sendCustom(sender, "<red>잘못된 아이템 이름: " + materialName);
            return true;
        }

        try {
            double buyPrice = Double.parseDouble(args[3]);
            double sellPrice = Double.parseDouble(args[4]);

            File sectionFile = new File(shopsFolder, section + ".yml");
            if (!sectionFile.exists()) {
                messageManager.sendCustom(sender, "<red>카테고리를 찾을 수 없습니다: " + section);
                return true;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(sectionFile);
            String itemKey = "items." + materialName;

            config.set(itemKey + ".material", materialName);
            config.set(itemKey + ".buy", buyPrice);
            config.set(itemKey + ".sell", sellPrice);
            config.set(itemKey + ".dynamic", true);

            config.save(sectionFile);
            shopManager.reloadShops();

            messageManager.send(sender, "eshop.item-added");
            messageManager.sendCustom(sender, "<gray>구매가: " + buyPrice + ", 판매가: " + sellPrice);

        } catch (NumberFormatException e) {
            messageManager.send(sender, "general.invalid-amount");
        } catch (Exception e) {
            messageManager.sendCustom(sender, "<red>아이템 추가 중 오류 발생: " + e.getMessage());
        }

        return true;
    }

    private boolean handleEditItem(CommandSender sender, String[] args) {
        if (args.length < 6) {
            messageManager.sendCustom(sender, "<red>사용법: /eshop edititem <section> <material> <property> <value>");
            messageManager.sendCustom(sender, "<gray>Properties: buy, sell, dynamic");
            return true;
        }

        String section = args[1].toLowerCase();
        String materialName = args[2].toUpperCase();
        String property = args[3].toLowerCase();
        String value = args[4];

        try {
            File sectionFile = new File(shopsFolder, section + ".yml");
            if (!sectionFile.exists()) {
                messageManager.sendCustom(sender, "<red>카테고리를 찾을 수 없습니다: " + section);
                return true;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(sectionFile);
            String itemKey = "items." + materialName;

            if (!config.contains(itemKey)) {
                messageManager.sendCustom(sender, "<red>아이템을 찾을 수 없습니다: " + materialName);
                return true;
            }

            switch (property) {
                case "buy":
                case "sell":
                    config.set(itemKey + "." + property, Double.parseDouble(value));
                    break;
                case "dynamic":
                    config.set(itemKey + ".dynamic", Boolean.parseBoolean(value));
                    break;
                default:
                    messageManager.sendCustom(sender, "<red>알 수 없는 속성: " + property);
                    return true;
            }

            config.save(sectionFile);
            shopManager.reloadShops();

            messageManager.send(sender, "eshop.item-edited");

        } catch (Exception e) {
            messageManager.sendCustom(sender, "<red>아이템 수정 중 오류 발생: " + e.getMessage());
        }

        return true;
    }

    private boolean handleDeleteItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageManager.sendCustom(sender, "<red>사용법: /eshop deleteitem <section> <material>");
            return true;
        }

        String section = args[1].toLowerCase();
        String materialName = args[2].toUpperCase();

        try {
            File sectionFile = new File(shopsFolder, section + ".yml");
            if (!sectionFile.exists()) {
                messageManager.sendCustom(sender, "<red>카테고리를 찾을 수 없습니다: " + section);
                return true;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(sectionFile);
            String itemKey = "items." + materialName;

            if (!config.contains(itemKey)) {
                messageManager.sendCustom(sender, "<red>아이템을 찾을 수 없습니다: " + materialName);
                return true;
            }

            config.set(itemKey, null);
            config.save(sectionFile);
            shopManager.reloadShops();

            messageManager.send(sender, "eshop.item-deleted");

        } catch (Exception e) {
            messageManager.sendCustom(sender, "<red>아이템 삭제 중 오류 발생: " + e.getMessage());
        }

        return true;
    }

    private boolean handleAddHandItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.send(sender, "general.player-only");
            return true;
        }

        if (args.length < 4) {
            messageManager.sendCustom(sender, "<red>사용법: /eshop addhanditem <section> <buy> <sell>");
            return true;
        }

        Player player = (Player) sender;
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            messageManager.sendCustom(sender, "<red>손에 아이템이 없습니다!");
            return true;
        }

        String section = args[1].toLowerCase();
        String materialName = handItem.getType().name();

        try {
            double buyPrice = Double.parseDouble(args[2]);
            double sellPrice = Double.parseDouble(args[3]);

            File sectionFile = new File(shopsFolder, section + ".yml");
            if (!sectionFile.exists()) {
                messageManager.sendCustom(sender, "<red>카테고리를 찾을 수 없습니다: " + section);
                return true;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(sectionFile);
            String itemKey = "items." + materialName;

            config.set(itemKey + ".material", materialName);
            config.set(itemKey + ".buy", buyPrice);
            config.set(itemKey + ".sell", sellPrice);
            config.set(itemKey + ".dynamic", true);

            // TODO: Support enchantments, potion effects, etc. from hand item

            config.save(sectionFile);
            shopManager.reloadShops();

            messageManager.send(sender, "eshop.item-added");
            messageManager.sendCustom(sender, "<gray>구매가: " + buyPrice + ", 판매가: " + sellPrice);

        } catch (NumberFormatException e) {
            messageManager.send(sender, "general.invalid-amount");
        } catch (Exception e) {
            messageManager.sendCustom(sender, "<red>아이템 추가 중 오류 발생: " + e.getMessage());
        }

        return true;
    }

    private boolean handleAddSection(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messageManager.sendCustom(sender, "<red>사용법: /eshop addsection <section> <material> <name> <slot>");
            return true;
        }

        String sectionId = args[1].toLowerCase();
        String materialName = args[2].toUpperCase();
        String name = args[3];

        Material icon = Material.getMaterial(materialName);
        if (icon == null) {
            messageManager.sendCustom(sender, "<red>잘못된 아이콘 아이템: " + materialName);
            return true;
        }

        try {
            int slot = Integer.parseInt(args[4]);

            File sectionFile = new File(shopsFolder, sectionId + ".yml");
            if (sectionFile.exists()) {
                messageManager.sendCustom(sender, "<red>이미 존재하는 카테고리입니다: " + sectionId);
                return true;
            }

            FileConfiguration config = new YamlConfiguration();
            config.set("category.name", name);
            config.set("category.icon", materialName);
            config.set("category.slot", slot);
            config.createSection("items");

            config.save(sectionFile);
            shopManager.reloadShops();

            messageManager.send(sender, "eshop.section-added");
            messageManager.sendCustom(sender, "<gray>새 이름: " + name + " (ID: " + sectionId + ", 슬롯: " + slot + ")");

        } catch (NumberFormatException e) {
            messageManager.sendCustom(sender, "<red>잘못된 슬롯 번호입니다.");
        } catch (Exception e) {
            messageManager.sendCustom(sender, "<red>카테고리 추가 중 오류 발생: " + e.getMessage());
        }

        return true;
    }

    private boolean handleEditSection(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messageManager.sendCustom(sender, "<red>사용법: /eshop editsection <section> <property> <value>");
            messageManager.sendCustom(sender, "<gray>Properties: name, icon, slot");
            return true;
        }

        String section = args[1].toLowerCase();
        String property = args[2].toLowerCase();
        String value = args[3];

        try {
            File sectionFile = new File(shopsFolder, section + ".yml");
            if (!sectionFile.exists()) {
                messageManager.sendCustom(sender, "<red>카테고리를 찾을 수 없습니다: " + section);
                return true;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(sectionFile);

            switch (property) {
                case "name":
                    config.set("category.name", value);
                    break;
                case "icon":
                    Material icon = Material.getMaterial(value.toUpperCase());
                    if (icon == null) {
                        messageManager.sendCustom(sender, "<red>잘못된 아이콘 아이템: " + value);
                        return true;
                    }
                    config.set("category.icon", value.toUpperCase());
                    break;
                case "slot":
                    config.set("category.slot", Integer.parseInt(value));
                    break;
                default:
                    messageManager.sendCustom(sender, "<red>알 수 없는 속성: " + property);
                    return true;
            }

            config.save(sectionFile);
            shopManager.reloadShops();

            messageManager.send(sender, "eshop.section-edited");

        } catch (Exception e) {
            messageManager.sendCustom(sender, "<red>카테고리 수정 중 오류 발생: " + e.getMessage());
        }

        return true;
    }

    private boolean handleDeleteSection(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageManager.sendCustom(sender, "<red>사용법: /eshop deletesection <section>");
            return true;
        }

        String section = args[1].toLowerCase();

        File sectionFile = new File(shopsFolder, section + ".yml");
        if (!sectionFile.exists()) {
            messageManager.sendCustom(sender, "<red>카테고리를 찾을 수 없습니다: " + section);
            return true;
        }

        if (sectionFile.delete()) {
            shopManager.reloadShops();
            messageManager.send(sender, "eshop.section-deleted");
        } else {
            messageManager.sendCustom(sender, "<red>카테고리 삭제에 실패했습니다.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("additem");
            completions.add("edititem");
            completions.add("deleteitem");
            completions.add("addhanditem");
            completions.add("addsection");
            completions.add("editsection");
            completions.add("deletesection");
        } else if (args.length == 2) {
            // Add section names
            for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
                completions.add(category.getId());
            }
        }

        return completions;
    }
}

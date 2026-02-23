package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.shop.ShopGUI;
import me.minseok.shopsystem.shop.ShopManager;
import me.minseok.shopsystem.utils.MessageManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopGUI shopGUI;
    private final ShopManager shopManager;
    private final MessageManager messageManager;

    public ShopCommand(ShopGUI shopGUI, ShopManager shopManager, MessageManager messageManager) {
        this.shopGUI = shopGUI;
        this.shopManager = shopManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                messageManager.send(sender, "general.player-only");
                return true;
            }
            shopGUI.openMainMenu((Player) sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
            case "sreload":
                return handleReload(sender);
            case "setprice":
                return handleSetPrice(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "history":
                return handleHistory(sender, args);
            case "stats":
                return handleStats(sender);
            case "popular":
                return handlePopular(sender);
            case "search":
                return handleSearch(sender, args);
            default:
                // Try to open category directly
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    ShopManager.ShopCategory category = shopManager.getCategory(subCommand);
                    if (category != null) {
                        shopGUI.openCategoryShop(player, category, 0);
                        return true;
                    }
                }
                messageManager.sendCustom(sender, "<red>알 수 없는 명령어입니다: " + subCommand);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("shopsystem.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
            out.writeUTF("REQUEST_GLOBAL_RELOAD");
            player.sendPluginMessage(
                    me.minseok.shopsystem.ShopCore.getPlugin(me.minseok.shopsystem.ShopCore.class),
                    "shopsystem:sync", out.toByteArray());
            messageManager.sendCustom(sender, "<green>✓ 전체 서버 동기화 요청을 보냈습니다!");
        } else {
            // Console cannot send plugin messages directly without a player connection
            // usually,
            // but for now we'll just reload locally for console or warn.
            // Actually, let's just reload locally for console as fallback,
            // but ideally we want global.
            // Since we can't easily send PM from console without a player, we'll keep local
            // reload for console
            // and maybe try to find a player to send it if possible.

            if (org.bukkit.Bukkit.getOnlinePlayers().isEmpty()) {
                shopManager.reloadShops();
                messageManager.sendCustom(sender, "<green>✓ (로컬) 상점을 다시 불러왔습니다! (플레이어가 없어 전체 동기화 불가)");
            } else {
                Player player = org.bukkit.Bukkit.getOnlinePlayers().iterator().next();
                com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
                out.writeUTF("REQUEST_GLOBAL_RELOAD");
                player.sendPluginMessage(me.minseok.shopsystem.ShopCore.getPlugin(
                        me.minseok.shopsystem.ShopCore.class), "shopsystem:sync", out.toByteArray());
                messageManager.sendCustom(sender, "<green>✓ 전체 서버 동기화 요청을 보냈습니다! (대리 전송: " + player.getName() + ")");
            }
        }
        return true;
    }

    private boolean handleSetPrice(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shopsystem.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        if (args.length < 4) {
            messageManager.sendCustom(sender, "<red>사용법: /shop setprice <아이템> <구매가> <판매가>");
            return true;
        }

        String itemName = args[1].toUpperCase();
        try {
            double buyPrice = Double.parseDouble(args[2]);
            double sellPrice = Double.parseDouble(args[3]);

            shopManager.updateItemPrice(itemName, buyPrice, sellPrice);
            messageManager.sendCustom(sender,
                    "<green>✓ " + itemName + "의 가격을 설정했습니다! <gray>(구매가: " + buyPrice + ", 판매가: " + sellPrice + ")");
        } catch (NumberFormatException e) {
            messageManager.send(sender, "general.invalid-amount");
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shopsystem.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            messageManager.sendCustom(sender, "<red>사용법: /shop reset <아이템|all>");
            return true;
        }

        if (args[1].equalsIgnoreCase("all")) {
            shopManager.resetPrices();
            messageManager.sendCustom(sender, "<green>✓ 모든 가격을 초기화했습니다! <gray>(변동폭 적용됨)");
        } else {
            String itemId = args[1].toUpperCase();
            ShopManager.ShopItem item = shopManager.getItemById(itemId);
            if (item != null) {
                shopManager.resetPrice(item);
                messageManager.sendCustom(sender, "<green>✓ " + itemId + "의 가격을 초기화했습니다! <gray>(변동폭 적용됨)");
            } else {
                messageManager.sendCustom(sender, "<red>해당 아이템을 상점에서 찾을 수 없습니다.");
            }
        }
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shopsystem.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            messageManager.sendCustom(sender, "<red>사용법: /shop history <아이템>");
            return true;
        }

        String itemName = args[1].toUpperCase();
        List<Map<String, Object>> history = shopManager.getPriceHistory(itemName, 10);

        if (history.isEmpty()) {
            messageManager.sendCustom(sender, "<red>가격 변동 기록이 없습니다.");
            return true;
        }

        messageManager.sendCustom(sender, "<yellow>=== " + itemName + " 가격 변동 기록 ===");
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        for (Map<String, Object> record : history) {
            double price = (double) record.get("price");
            String reason = (String) record.get("reason");
            String timestamp = sdf.format(record.get("timestamp"));
            messageManager.sendCustom(sender,
                    "<gray>" + timestamp + " <white>" + price + " <gray>(" + (reason != null ? reason : "자동") + ")");
        }
        return true;
    }

    private boolean handleStats(CommandSender sender) {
        if (!sender.hasPermission("shopsystem.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        Map<String, Integer> popular = shopManager.getPopularItems(10);
        messageManager.sendCustom(sender, "<yellow>=== 상점 통계 ===");
        messageManager.sendCustom(sender, "<gray>총 카테고리: <white>" + shopManager.getCategories().size());
        messageManager.sendCustom(sender, "<gray>인기 아이템 TOP 10:");
        int rank = 1;
        for (Map.Entry<String, Integer> entry : popular.entrySet()) {
            messageManager.sendCustom(sender,
                    "<white>" + rank++ + ". <gray>" + entry.getKey() + " <white>(" + entry.getValue() + " 거래)");
        }
        return true;
    }

    private boolean handlePopular(CommandSender sender) {
        Map<String, Integer> popular = shopManager.getPopularItems(10);
        if (popular == null || popular.isEmpty()) {
            messageManager.sendCustom(sender, "<red>인기 아이템 데이터가 없습니다.");
            return true;
        }

        messageManager.sendCustom(sender, "<yellow>=== 인기 아이템 TOP 10 ===");
        int rank = 1;
        for (Map.Entry<String, Integer> entry : popular.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            ShopManager.ShopItem item = shopManager.getItemById(entry.getKey());
            if (item != null) {
                double priceChange = shopManager.calculatePriceChangePercentage(item, true);
                String trend = priceChange > 0 ? "<green>▲" : priceChange < 0 ? "<red>▼" : "<gray>-";
                messageManager.sendCustom(sender, "<white>" + rank++ + ". <gray>" + entry.getKey() +
                        " " + trend + " <white>" + String.format("%.1f%%", Math.abs(priceChange)));
            }
        }
        return true;
    }

    private boolean handleSearch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.send(sender, "general.player-only");
            return true;
        }

        if (args.length < 2) {
            messageManager.sendCustom(sender, "<red>사용법: /shop search <아이템명>");
            return true;
        }

        String query = args[1].toUpperCase();
        if (query == null || query.isEmpty()) {
            messageManager.sendCustom(sender, "<red>검색어가 비어있습니다.");
            return true;
        }

        List<ShopManager.ShopItem> results = new ArrayList<>();

        for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
            if (category == null || category.getItems() == null) {
                continue;
            }

            for (ShopManager.ShopItem item : category.getItems()) {
                if (item != null && item.getId() != null && item.getMaterial() != null) {
                    if (item.getId().contains(query) || item.getMaterial().name().contains(query)) {
                        results.add(item);
                    }
                }
            }
        }

        if (results.isEmpty()) {
            messageManager.sendCustom(sender, "<red>검색 결과가 없습니다.");
            return true;
        }

        messageManager.sendCustom(sender, "<yellow>=== 검색 결과: " + query + " ===");
        for (ShopManager.ShopItem item : results) {
            messageManager.sendCustom(sender,
                    "<white>- " + item.getId() + " <gray>(구매: " + item.getBuyPrice() + ", 판매: "
                            + item.getSellPrice() + ")");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("sreload");
            completions.add("setprice");
            completions.add("reset");
            completions.add("history");
            completions.add("stats");
            completions.add("popular");
            completions.add("search");

            // Add category names
            for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
                completions.add(category.getName().toLowerCase());
            }
        }

        return completions;
    }
}

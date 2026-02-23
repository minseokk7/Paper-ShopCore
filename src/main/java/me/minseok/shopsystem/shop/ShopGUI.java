package me.minseok.shopsystem.shop;

import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.utils.MessageManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopGUI implements Listener {

    private final ShopManager shopManager;
    private final VaultEconomy economy;
    private final MessageManager messageManager;
    private final Map<String, Map<Integer, Inventory>> pageCache = new HashMap<>(); // 페이지 캐싱

    public ShopGUI(ShopManager shopManager, VaultEconomy economy, MessageManager messageManager) {
        this.shopManager = shopManager;
        this.economy = economy;
        this.messageManager = messageManager;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§2§l상점");

        for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
            // Check permission
            if (!player.hasPermission("shopsystem.shop." + category.getId()) &&
                    !player.hasPermission("shopsystem.shop.*") &&
                    !player.hasPermission("shopsystem.admin")) {
                continue;
            }

            ItemStack icon = new ItemStack(category.getIcon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a§l" + category.getName());

                List<String> lore = new ArrayList<>();
                lore.add("§7아이템: " + category.getItems().size() + "개");
                lore.add("");
                lore.add("§e클릭하여 열기");
                meta.setLore(lore);

                icon.setItemMeta(meta);
            }
            inv.setItem(category.getSlot(), icon);
        }

        // Add player info at slot 45
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta headMetaTemp = playerHead.getItemMeta();
        if (headMetaTemp instanceof org.bukkit.inventory.meta.SkullMeta) {
            org.bukkit.inventory.meta.SkullMeta skullMeta = (org.bukkit.inventory.meta.SkullMeta) headMetaTemp;
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName("§6§l" + player.getName());

            List<String> headLore = new ArrayList<>();
            headLore.add("");
            headLore.add("§a💰 잔액: §f" + economy.format(economy.getBalance(player)));
            headLore.add("");
            headLore.add("§7서버 경제 시스템");
            skullMeta.setLore(headLore);

            playerHead.setItemMeta(skullMeta);
        }
        inv.setItem(45, playerHead);

        // Add close button at slot 53
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c§l닫기");
            List<String> closeLore = new ArrayList<>();
            closeLore.add("");
            closeLore.add("§7클릭하여 상점 닫기");
            closeMeta.setLore(closeLore);
            closeButton.setItemMeta(closeMeta);
        }
        inv.setItem(53, closeButton);

        // Server Selector (Compass at 49)
        ItemStack serverSelector = new ItemStack(Material.COMPASS);
        ItemMeta selectorMeta = serverSelector.getItemMeta();
        if (selectorMeta != null) {
            selectorMeta.setDisplayName("§b§l서버 이동");
            List<String> selectorLore = new ArrayList<>();
            selectorLore.add("§7클릭하여 서버 선택 메뉴 열기");
            selectorMeta.setLore(selectorLore);
            serverSelector.setItemMeta(selectorMeta);
        }
        inv.setItem(49, serverSelector);

        player.openInventory(inv);
    }

    public void openCategoryShop(Player player, ShopManager.ShopCategory category, int page) {
        // Check permission
        if (!player.hasPermission("shopsystem.shop." + category.getId()) &&
                !player.hasPermission("shopsystem.shop.*") &&
                !player.hasPermission("shopsystem.admin")) {
            messageManager.send(player, "general.no-permission");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§2§l" + category.getName() + " §8(" + (page + 1) + "페이지)");
        updateShopInventory(inv, category, page, player);
        player.openInventory(inv);
    }

    private void updateShopInventory(Inventory inv, ShopManager.ShopCategory category, int page, Player player) {
        // inv.clear() 제거: 기존 아이템 유지 후 필요한 부분만 업데이트
        // (페이지 이동시 깜빡임 현상 감소)

        List<ShopManager.ShopItem> items = category.getItems();
        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            ShopManager.ShopItem item = items.get(i);
            ItemStack display = createShopItemStack(item, 1);
            ItemMeta meta = display.getItemMeta();

            // Null Safety 체크
            if (meta == null) {
                inv.setItem(slot++, display);
                continue;
            }

            List<String> lore = new ArrayList<>();

            // Add enchantment info for enchanted books
            if (!item.getEnchantments().isEmpty()) {
                lore.add("§d§l마법 부여:");
                for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                    String enchName = getKoreanEnchantmentName(entry.getKey());
                    lore.add("§7  " + enchName + " " + getRomanNumeral(entry.getValue()));
                }
            }

            lore.add("");
            lore.add("§a구매: §f" + economy.format(item.getBuyPrice()));
            lore.add("§c판매: §f" + economy.format(item.getSellPrice()));

            if (item.hasDynamicPricing()) {
                // Calculate price change percentage - 공통 메서드 사용
                double buyChange = shopManager.calculatePriceChangePercentage(item, true);
                double sellChange = shopManager.calculatePriceChangePercentage(item, false);

                lore.add("");
                lore.add("§6📊 동적 가격 적용 중");

                if (Math.abs(buyChange) > 0.01) {
                    String buyTrend = buyChange > 0 ? "§a▲" : "§c▼";
                    lore.add(buyTrend + " §7구매가 변동: §f" + String.format("%.1f%%", Math.abs(buyChange)));
                }

                if (Math.abs(sellChange) > 0.01) {
                    String sellTrend = sellChange > 0 ? "§a▲" : "§c▼";
                    lore.add(sellTrend + " §7판매가 변동: §f" + String.format("%.1f%%", Math.abs(sellChange)));
                }
            }

            lore.add("");
            lore.add("§e좌클릭: §f1개 구매");
            lore.add("§e우클릭: §f1개 판매");
            lore.add("§eShift + 좌클릭: §f64개 구매");
            lore.add("§eShift + 우클릭: §f전체 판매");

            // Check one-time purchase asymmetrically to prevent lag
            if (item.isOneTime()) {
                shopManager.getDatabase().hasPurchased(player.getUniqueId(), item.getMaterial().name())
                        .thenAcceptAsync(purchased -> {
                            Bukkit.getScheduler().runTask(shopManager.getPlugin(), () -> {
                                if (purchased) {
                                    display.setType(Material.BARRIER);
                                    ItemMeta displayMeta = display.getItemMeta();
                                    if (displayMeta != null) {
                                        displayMeta.setDisplayName("§c§l[구매 완료] " + item.getMaterial().name());
                                        List<String> errorLore = new ArrayList<>();
                                        errorLore.add("");
                                        errorLore.add("§c이미 구매한 아이템입니다");
                                        errorLore.add("§7더 이상 구매할 수 없습니다");
                                        displayMeta.setLore(errorLore);
                                        display.setItemMeta(displayMeta);
                                    }
                                } else {
                                    ItemMeta displayMeta = display.getItemMeta();
                                    if (displayMeta != null) {
                                        List<String> currentLore = displayMeta.getLore();
                                        if (currentLore == null) {
                                            currentLore = new ArrayList<>();
                                        }
                                        currentLore.add("");
                                        currentLore.add("§bℹ 1회 한정 구매 상품입니다");
                                        displayMeta.setLore(currentLore);
                                        display.setItemMeta(displayMeta);
                                    }
                                }
                            });
                        });
            }

            if (meta != null) {
                meta.setLore(lore);
                display.setItemMeta(meta);
            }

            inv.setItem(slot++, display);
        }

        // Navigation buttons
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName("§e이전 페이지");
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(48, prev);
        }

        if (endIndex < items.size()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName("§e다음 페이지");
                next.setItemMeta(nextMeta);
            }
            inv.setItem(50, next);
        }

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c메인으로");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        if (event.getClickedInventory() == null)
            return;

        String title = event.getView().getTitle();
        if (!title.startsWith("§2§l") && !title.equals("§8§l서버 선택"))
            return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        // Main menu
        if (title.equals("§2§l상점")) {
            // Close button
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                return;
            }

            // Server Selector (Compass at 49)
            if (clicked.getType() == Material.COMPASS && event.getSlot() == 49) {
                openServerSelector(player);
                return;
            }

            for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
                if (category.getIcon() == clicked.getType()) {
                    openCategoryShop(player, category, 0);
                    return;
                }
            }
        }
        // Server Selector GUI
        else if (title.equals("§8§l서버 선택")) {
            if (event.getSlot() == 11) {
                connectToServer(player, "lobby");
            } else if (event.getSlot() == 13) {
                connectToServer(player, "survival");
            } else if (event.getSlot() == 15) {
                connectToServer(player, "creative");
            }
        }
        // Category shop
        else {
            // Find the category
            String titleStr = title.replace("§2§l", "");
            int pageIndex = titleStr.lastIndexOf(" §8(");
            String categoryName = pageIndex != -1 ? titleStr.substring(0, pageIndex) : titleStr;

            int currentPage = 0;
            if (pageIndex != -1) {
                try {
                    String pageStr = titleStr.substring(pageIndex + 4, titleStr.length() - 4); // Remove " §8(" and
                                                                                               // "페이지)"
                    currentPage = Integer.parseInt(pageStr) - 1;
                } catch (NumberFormatException ignored) {
                }
            }

            ShopManager.ShopCategory category = null;
            for (ShopManager.ShopCategory cat : shopManager.getCategories().values()) {
                if (cat.getName().equals(categoryName)) {
                    category = cat;
                    break;
                }
            }

            if (category == null)
                return;

            // Back button (Main Menu)
            if (clicked.getType() == Material.BARRIER && event.getSlot() == 49) {
                openMainMenu(player);
                return;
            }

            // Previous Page
            if (clicked.getType() == Material.ARROW && event.getSlot() == 48) {
                openCategoryShop(player, category, currentPage - 1);
                return;
            }

            // Next Page
            if (clicked.getType() == Material.ARROW && event.getSlot() == 50) {
                openCategoryShop(player, category, currentPage + 1);
                return;
            }

            // Find the item
            if (event.getSlot() >= 45)
                return; // Ignore bottom row clicks other than buttons

            int itemIndex = (currentPage * 45) + event.getSlot();
            if (itemIndex >= category.getItems().size())
                return;

            ShopManager.ShopItem shopItem = category.getItems().get(itemIndex);

            boolean isBuy = event.isLeftClick();
            final int amount = event.isShiftClick() ? 64 : 1;

            if (isBuy) {
                // Check one-time purchase
                if (shopItem.isOneTime()) {
                    shopManager.getDatabase().hasPurchased(player.getUniqueId(), shopItem.getId())
                            .thenAcceptAsync(purchased -> {
                                Bukkit.getScheduler().runTask(
                                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(ShopGUI.class), () -> {
                                            if (purchased) {
                                                messageManager.sendCustom(player, "<red>이미 구매한 아이템입니다!");
                                            } else {
                                                handlePurchase(player, shopItem, amount);
                                            }
                                        });
                            });
                } else {
                    handlePurchase(player, shopItem, amount);
                }
            } else {
                if (event.isShiftClick()) {
                    // Sell All logic
                    ItemStack template = createShopItemStack(shopItem, 1);
                    int total = 0;
                    for (ItemStack is : player.getInventory().getContents()) {
                        if (is != null && is.isSimilar(template)) {
                            total += is.getAmount();
                        }
                    }

                    if (total == 0) {
                        messageManager.sendCustom(player, "<red>판매할 아이템이 없습니다!");
                        return;
                    }
                    handleSale(player, shopItem, total);
                } else {
                    handleSale(player, shopItem, amount);
                }
            }

            // Refresh GUI to show new prices
            openCategoryShop(player, category, currentPage);
        }

    }

    public void openServerSelector(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l서버 선택");

        // Lobby (11)
        ItemStack lobbyIcon = new ItemStack(Material.COMPASS);
        ItemMeta lobbyMeta = lobbyIcon.getItemMeta();
        if (lobbyMeta != null) {
            lobbyMeta.setDisplayName("§b§l로비 서버");
            List<String> lobbyLore = new ArrayList<>();
            lobbyLore.add("§7클릭하여 로비로 이동");
            lobbyMeta.setLore(lobbyLore);
            lobbyIcon.setItemMeta(lobbyMeta);
        }
        inv.setItem(11, lobbyIcon);

        // Survival (13)
        ItemStack survivalIcon = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta survivalMeta = survivalIcon.getItemMeta();
        if (survivalMeta != null) {
            survivalMeta.setDisplayName("§a§l야생 서버");
            List<String> survivalLore = new ArrayList<>();
            survivalLore.add("§7클릭하여 야생 서버로 이동");
            survivalMeta.setLore(survivalLore);
            survivalIcon.setItemMeta(survivalMeta);
        }
        inv.setItem(13, survivalIcon);

        // Creative (15)
        ItemStack creativeIcon = new ItemStack(Material.BRICKS);
        ItemMeta creativeMeta = creativeIcon.getItemMeta();
        if (creativeMeta != null) {
            creativeMeta.setDisplayName("§d§l크리에이티브 서버");
            List<String> creativeLore = new ArrayList<>();
            creativeLore.add("§7클릭하여 크리에이티브 서버로 이동");
            creativeMeta.setLore(creativeLore);
            creativeIcon.setItemMeta(creativeMeta);
        }
        inv.setItem(15, creativeIcon);

        player.openInventory(inv);
    }

    private void connectToServer(Player player, String serverName) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(ShopGUI.class), "BungeeCord",
                    b.toByteArray());
        } catch (Exception e) {
            messageManager.sendCustom(player, "<red>서버 이동 중 오류가 발생했습니다.");
            e.printStackTrace();
        }
    }

    private void handlePurchase(Player player, ShopManager.ShopItem item, int amount) {
        double totalPrice = item.getBuyPrice() * amount;

        if (!economy.has(player, totalPrice)) {
            messageManager.sendCustom(player, "<red>잔액이 부족합니다! 필요: " + economy.format(totalPrice));
            return;
        }

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            messageManager.sendCustom(player, "<red>인벤토리에 공간이 없습니다!");
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, totalPrice);
        if (!response.transactionSuccess()) {
            messageManager.sendCustom(player, "<red>구매 실패: " + response.errorMessage);
            return;
        }

        // Give items to player
        ItemStack toAdd = createShopItemStack(item, amount);
        player.getInventory().addItem(toAdd);

        messageManager.sendCustom(player, "<green>✓ " + item.getMaterial().name() + " x" + amount + "을(를) " +
                economy.format(totalPrice) + "에 구매했습니다");
        messageManager.sendCustom(player, "<gray>잔액: <white>" + economy.format(response.balance));

        // Adjust price
        if (item.hasDynamicPricing()) {
            shopManager.adjustPrice(item, true, amount);
            messageManager.sendCustom(player, "<gold>📊 수요 증가로 가격이 상승했습니다!");
        }

        // Record one-time purchase
        if (item.isOneTime()) {
            shopManager.getDatabase().recordPurchase(player.getUniqueId(), item.getId());
        }
    }

    private void handleSale(Player player, ShopManager.ShopItem item, int amount) {
        // Check if player has enough items
        ItemStack checkItem = createShopItemStack(item, 1);
        if (!player.getInventory().containsAtLeast(checkItem, amount)) {
            messageManager.sendCustom(player, "<red>판매할 아이템이 부족합니다!");
            return;
        }

        double totalPrice = item.getSellPrice() * amount;

        // Remove items from inventory (handle large amounts)
        int leftToRemove = amount;
        while (leftToRemove > 0) {
            int toRemoveNow = Math.min(leftToRemove, 64);
            ItemStack toRemove = createShopItemStack(item, toRemoveNow);
            player.getInventory().removeItem(toRemove);
            leftToRemove -= toRemoveNow;
        }

        EconomyResponse response = economy.depositPlayer(player, totalPrice);
        if (!response.transactionSuccess()) {
            // Refund items (simplified, might lose items if inv full, but unlikely for
            // sell)
            ItemStack toAdd = createShopItemStack(item, amount);
            player.getInventory().addItem(toAdd);
            messageManager.sendCustom(player, "<red>판매 실패: " + response.errorMessage);
            return;
        }

        messageManager.sendCustom(player, "<green>✓ " + item.getMaterial().name() + " x" + amount + "을(를) " +
                economy.format(totalPrice) + "에 판매했습니다");
        messageManager.sendCustom(player, "<gray>잔액: <white>" + economy.format(response.balance));

        // Adjust price
        if (item.hasDynamicPricing()) {
            shopManager.adjustPrice(item, false, amount);
            messageManager.sendCustom(player, "<gold>📊 공급 증가로 가격이 하락했습니다!");
        }
    }

    private ItemStack createShopItemStack(ShopManager.ShopItem item, int amount) {
        ItemStack stack = new ItemStack(item.getMaterial(), amount);
        if (item.getEnchantments() != null && !item.getEnchantments().isEmpty()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (meta instanceof EnchantmentStorageMeta) {
                    EnchantmentStorageMeta esm = (EnchantmentStorageMeta) meta;
                    for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                        esm.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                    }
                } else {
                    for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                        meta.addEnchant(entry.getKey(), entry.getValue(), true);
                    }
                }
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private String getKoreanEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        switch (key.toLowerCase()) {
            case "protection":
                return "§b보호";
            case "fire_protection":
                return "§b화염 보호";
            case "feather_falling":
                return "§b가벼운 착지";
            case "blast_protection":
                return "§b폭발 보호";
            case "projectile_protection":
                return "§b발사체 보호";
            case "respiration":
                return "§b호흡";
            case "aqua_affinity":
                return "§b물갈퀴";
            case "thorns":
                return "§b가시";
            case "depth_strider":
                return "§b물갈퀴 보행";
            case "frost_walker":
                return "§b차가운 걸음";
            case "soul_speed":
                return "§b영혼 가속";
            case "sharpness":
                return "§c날카로움";
            case "smite":
                return "§c강타";
            case "bane_of_arthropods":
                return "§c살충";
            case "knockback":
                return "§c밀치기";
            case "fire_aspect":
                return "§c화염 속성";
            case "looting":
                return "§c약탈";
            case "sweeping_edge":
                return "§c휩쓸기";
            case "efficiency":
                return "§6효율";
            case "silk_touch":
                return "§6섬세한 손길";
            case "unbreaking":
                return "§6내구성";
            case "fortune":
                return "§6행운";
            case "power":
                return "§c힘";
            case "punch":
                return "§c밀어내기";
            case "flame":
                return "§c화염";
            case "infinity":
                return "§c무한";
            case "luck_of_the_sea":
                return "§b바다의 행운";
            case "lure":
                return "§b미끼";
            case "loyalty":
                return "§b충성";
            case "impaling":
                return "§c찌르기";
            case "riptide":
                return "§b급류";
            case "channeling":
                return "§b집전";
            case "multishot":
                return "§c다발 사격";
            case "quick_charge":
                return "§c빠른 장전";
            case "piercing":
                return "§c관통";
            case "mending":
                return "§a수선";
            case "vanishing_curse":
                return "§4소실 저주";
            case "binding_curse":
                return "§4귀속 저주";
            default:
                return "§7" + enchantment.getKey().getKey();
        }
    }

    private String getRomanNumeral(int number) {
        switch (number) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            case 6:
                return "VI";
            case 7:
                return "VII";
            case 8:
                return "VIII";
            case 9:
                return "IX";
            case 10:
                return "X";
            default:
                return String.valueOf(number);
        }
    }

    @EventHandler
    public void onPriceUpdate(me.minseok.shopsystem.events.ShopPriceUpdateEvent event) {
        ShopManager.ShopItem updatedItem = event.getItem();

        // Find which category this item belongs to
        ShopManager.ShopCategory category = null;
        int itemIndex = -1;

        for (ShopManager.ShopCategory cat : shopManager.getCategories().values()) {
            itemIndex = cat.getItems().indexOf(updatedItem);
            if (itemIndex != -1) {
                category = cat;
                break;
            }
        }

        if (category == null || itemIndex == -1)
            return;

        int itemsPerPage = 45;
        int itemPage = itemIndex / itemsPerPage;
        int itemSlot = itemIndex % itemsPerPage;

        // Refresh GUI for all players viewing this category and page
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory openInv = player.getOpenInventory().getTopInventory();
            if (openInv == null)
                continue;

            String title = player.getOpenInventory().getTitle();
            if (title.startsWith("§2§l" + category.getName())) {
                // Parse page number
                int page = 0;
                int pageIndex = title.lastIndexOf(" §8(");
                if (pageIndex != -1) {
                    try {
                        String pageStr = title.substring(pageIndex + 4, title.length() - 4);
                        page = Integer.parseInt(pageStr) - 1;
                    } catch (NumberFormatException ignored) {
                    }
                }

                // Only update if the player is viewing the page containing the item
                if (page == itemPage) {
                    // We need to fully reconstruct the item stack to ensure all lore/meta is
                    // updated
                    ItemStack newItem = createShopItemStack(updatedItem, 1);
                    ItemMeta meta = newItem.getItemMeta();

                    // Re-apply display name (it might be missing from createShopItemStack)
                    // Actually, createShopItemStack just creates the base item.
                    // We should probably extract the item creation logic into a reusable method
                    // or just call updateShopInventory for the whole page to be safe and
                    // consistent.
                    // Calling updateShopInventory is safer as it handles everything including
                    // one-time checks.

                    // However, updateShopInventory clears the inventory which might cause a
                    // flicker.
                    // Let's try to just update the specific slot with full logic.

                    List<String> lore = new ArrayList<>();

                    // Add enchantment info for enchanted books
                    if (!updatedItem.getEnchantments().isEmpty()) {
                        lore.add("§d§l마법 부여:");
                        for (Map.Entry<Enchantment, Integer> entry : updatedItem.getEnchantments().entrySet()) {
                            String enchName = getKoreanEnchantmentName(entry.getKey());
                            lore.add("§7  " + enchName + " " + getRomanNumeral(entry.getValue()));
                        }
                    }

                    lore.add("");
                    lore.add("§a구매: §f" + economy.format(updatedItem.getBuyPrice()));
                    lore.add("§c판매: §f" + economy.format(updatedItem.getSellPrice()));

                    if (updatedItem.hasDynamicPricing()) {
                        double buyChange = shopManager.calculatePriceChangePercentage(updatedItem, true);
                        double sellChange = shopManager.calculatePriceChangePercentage(updatedItem, false);

                        lore.add("");
                        lore.add("§6📊 동적 가격 적용 중");

                        if (Math.abs(buyChange) > 0.01) {
                            String buyTrend = buyChange > 0 ? "§a▲" : "§c▼";
                            lore.add(buyTrend + " §7구매가 변동: §f" + String.format("%.1f%%", Math.abs(buyChange)));
                        }

                        if (Math.abs(sellChange) > 0.01) {
                            String sellTrend = sellChange > 0 ? "§a▲" : "§c▼";
                            lore.add(sellTrend + " §7판매가 변동: §f" + String.format("%.1f%%", Math.abs(sellChange)));
                        }
                    }

                    lore.add("");
                    lore.add("§e좌클릭: §f1개 구매");
                    lore.add("§e우클릭: §f1개 판매");
                    lore.add("§eShift + 좌클릭: §f64개 구매");
                    lore.add("§eShift + 우클릭: §f전체 판매");

                    if (updatedItem.isOneTime()) {
                        shopManager.getDatabase().hasPurchased(player.getUniqueId(), updatedItem.getId())
                                .thenAcceptAsync(purchased -> {
                                    Bukkit.getScheduler().runTask(shopManager.getPlugin(), () -> {
                                        if (purchased) {
                                            newItem.setType(Material.BARRIER);
                                            ItemMeta newMeta = newItem.getItemMeta();
                                            if (newMeta != null) {
                                                newMeta.setDisplayName(
                                                        "§c§l[구매 완료] " + updatedItem.getMaterial().name());
                                                List<String> currentLore = newMeta.getLore();
                                                if (currentLore != null) {
                                                    currentLore.clear();
                                                    currentLore.add("");
                                                    currentLore.add("§c이미 구매한 아이템입니다");
                                                    currentLore.add("§7더 이상 구매할 수 없습니다");
                                                    newMeta.setLore(currentLore);
                                                }
                                                newItem.setItemMeta(newMeta);
                                            }
                                        } else {
                                            ItemMeta newMeta = newItem.getItemMeta();
                                            if (newMeta != null) {
                                                List<String> currentLore = newMeta.getLore();
                                                if (currentLore == null) {
                                                    currentLore = new ArrayList<>();
                                                }
                                                currentLore.add("");
                                                currentLore.add("§bℹ 1회 한정 구매 상품입니다");
                                                newMeta.setLore(currentLore);
                                                newItem.setItemMeta(newMeta);
                                            }
                                        }
                                    });
                                });
                    }

                    meta.setLore(lore);
                    newItem.setItemMeta(meta);

                    openInv.setItem(itemSlot, newItem);

                    // Force update inventory for the player (sometimes needed for immediate visual
                    // change)
                    player.updateInventory();
                }
            }
        }
    }

    /**
     * 페이지 캐시 무효화 (가격 변경시 호출)
     */
    public void invalidatePageCache(String categoryId) {
        pageCache.remove(categoryId);
    }
}

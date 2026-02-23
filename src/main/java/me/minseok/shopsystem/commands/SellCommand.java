package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.utils.MessageManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SellCommand implements CommandExecutor {

    private final VaultEconomy economy;
    private final MessageManager messageManager;

    public SellCommand(VaultEconomy economy, MessageManager messageManager) {
        this.economy = economy;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.send(sender, "general.player-only");
            return true;
        }

        Player player = (Player) sender;
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            messageManager.sendCustom(player, "<red>손에 아이템을 들고 있어야 합니다!");
            return true;
        }

        int amount = handItem.getAmount();
        String itemName = handItem.getType().name();

        // Simple sell price calculation (you can customize this)
        double basePrice = 10.0;
        double totalPrice = basePrice * amount;

        // Remove items
        player.getInventory().setItemInMainHand(null);

        // Give money
        EconomyResponse response = economy.depositPlayer(player, totalPrice);

        if (response.transactionSuccess()) {
            messageManager.sendCustom(player, "<green>✓ " + itemName + " x" + amount + "을(를) " +
                    economy.format(totalPrice) + "에 판매했습니다!");
            messageManager.sendCustom(player, "<gray>잔액: <white>" + economy.format(response.balance));
        } else {
            // Refund items if transaction failed
            player.getInventory().setItemInMainHand(handItem);
            messageManager.sendCustom(player, "<red>판매 실패: " + response.errorMessage);
        }

        return true;
    }
}

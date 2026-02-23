package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.utils.MessageManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final VaultEconomy economy;
    private final MessageManager messageManager;

    public PayCommand(VaultEconomy economy, MessageManager messageManager) {
        this.economy = economy;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.send(sender, "general.player-only");
            return true;
        }

        if (args.length != 2) {
            messageManager.sendCustom(sender, "<red>사용법: /pay <플레이어> <금액>");
            return true;
        }

        Player player = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            messageManager.sendCustom(sender, "<red>플레이어를 찾을 수 없습니다: " + args[0]);
            return true;
        }

        if (target.equals(player)) {
            messageManager.sendCustom(sender, "<red>자신에게 송금할 수 없습니다");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                messageManager.sendCustom(sender, "<red>금액은 0보다 커야 합니다");
                return true;
            }
        } catch (NumberFormatException e) {
            messageManager.sendCustom(sender, "<red>유효하지 않은 금액입니다");
            return true;
        }

        // Check if sender has enough money
        if (!economy.has(player, amount)) {
            messageManager.sendCustom(sender, "<red>잔액이 부족합니다");
            return true;
        }

        // Withdraw from sender
        EconomyResponse withdraw = economy.withdrawPlayer(player, amount);
        if (!withdraw.transactionSuccess()) {
            messageManager.sendCustom(sender, "<red>송금 실패: " + withdraw.errorMessage);
            return true;
        }

        // Deposit to target
        EconomyResponse deposit = economy.depositPlayer(target, amount);
        if (!deposit.transactionSuccess()) {
            // Refund sender
            economy.depositPlayer(player, amount);
            messageManager.sendCustom(sender, "<red>송금 실패: " + deposit.errorMessage);
            return true;
        }

        // Success messages
        messageManager.sendCustom(sender, "<green>✓ " + target.getName() + "에게 " + economy.format(amount) + "을 송금했습니다");
        messageManager.sendCustom(sender, "<gray>잔액: " + economy.format(withdraw.balance));

        messageManager.sendCustom(target,
                "<green>✓ " + player.getName() + "으로부터 " + economy.format(amount) + "을 받았습니다");
        messageManager.sendCustom(target, "<gray>잔액: " + economy.format(deposit.balance));

        return true;
    }
}

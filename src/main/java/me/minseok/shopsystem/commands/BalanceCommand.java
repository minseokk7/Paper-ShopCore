package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final VaultEconomy economy;
    private final MessageManager messageManager;

    public BalanceCommand(VaultEconomy economy, MessageManager messageManager) {
        this.economy = economy;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Check own balance
            if (!(sender instanceof Player)) {
                messageManager.send(sender, "general.player-only");
                return true;
            }

            Player player = (Player) sender;
            double balance = economy.getBalance(player);
            messageManager.sendCustom(sender, "<green>💰 잔액: <white>" + economy.format(balance));

        } else if (args.length == 1) {
            // Check other player's balance
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                messageManager.sendCustom(sender, "<red>플레이어를 찾을 수 없습니다: " + args[0]);
                return true;
            }

            double balance = economy.getBalance(target);
            messageManager.sendCustom(sender, "<green>" + target.getName() + "의 잔액: <white>" + economy.format(balance));

        } else {
            messageManager.sendCustom(sender, "<red>사용법: /balance [플레이어]");
        }

        return true;
    }
}

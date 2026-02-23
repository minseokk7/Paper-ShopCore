package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.economy.VaultEconomy;
import me.minseok.shopsystem.utils.MessageManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EcoCommand implements CommandExecutor {

    private final VaultEconomy economy;
    private final MessageManager messageManager;

    public EcoCommand(VaultEconomy economy, MessageManager messageManager) {
        this.economy = economy;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shopsystem.eco.admin")) {
            messageManager.send(sender, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            messageManager.sendCustom(sender, "<red>사용법: /eco <give|take|set|reset> <플레이어> [금액]");
            return true;
        }

        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            messageManager.sendCustom(sender, "<red>플레이어를 찾을 수 없습니다: " + args[1]);
            return true;
        }

        switch (action) {
            case "give" -> {
                if (args.length < 3) {
                    messageManager.sendCustom(sender, "<red>사용법: /eco give <플레이어> <금액>");
                    return true;
                }
                double amount = parseAmount(args[2], sender);
                if (amount <= 0)
                    return true;

                EconomyResponse response = economy.depositPlayer(target, amount);
                if (response.transactionSuccess()) {
                    messageManager.sendCustom(sender,
                            "<green>✓ " + target.getName() + "에게 " + economy.format(amount) + "을 지급했습니다");
                    messageManager.sendCustom(target, "<green>✓ " + economy.format(amount) + "을 받았습니다");
                } else {
                    messageManager.sendCustom(sender, "<red>실패: " + response.errorMessage);
                }
            }

            case "take" -> {
                if (args.length < 3) {
                    messageManager.sendCustom(sender, "<red>사용법: /eco take <플레이어> <금액>");
                    return true;
                }
                double amount = parseAmount(args[2], sender);
                if (amount <= 0)
                    return true;

                EconomyResponse response = economy.withdrawPlayer(target, amount);
                if (response.transactionSuccess()) {
                    messageManager.sendCustom(sender,
                            "<green>✓ " + target.getName() + "으로부터 " + economy.format(amount) + "을 차감했습니다");
                    messageManager.sendCustom(target, "<red>- " + economy.format(amount) + "이 차감되었습니다");
                } else {
                    messageManager.sendCustom(sender, "<red>실패: " + response.errorMessage);
                }
            }

            case "set" -> {
                if (args.length < 3) {
                    messageManager.sendCustom(sender, "<red>사용법: /eco set <플레이어> <금액>");
                    return true;
                }
                double amount = parseAmount(args[2], sender);
                if (amount < 0)
                    return true;

                double current = economy.getBalance(target);
                EconomyResponse response;

                if (amount > current) {
                    response = economy.depositPlayer(target, amount - current);
                } else {
                    response = economy.withdrawPlayer(target, current - amount);
                }

                if (response.transactionSuccess()) {
                    messageManager.sendCustom(sender,
                            "<green>✓ " + target.getName() + "의 잔액을 " + economy.format(amount) + "으로 설정했습니다");
                    messageManager.sendCustom(target, "<yellow>잔액이 " + economy.format(amount) + "으로 설정되었습니다");
                } else {
                    messageManager.sendCustom(sender, "<red>실패: " + response.errorMessage);
                }
            }

            case "reset" -> {
                double current = economy.getBalance(target);
                EconomyResponse response = economy.withdrawPlayer(target, current);

                if (response.transactionSuccess()) {
                    messageManager.sendCustom(sender, "<green>✓ " + target.getName() + "의 잔액을 초기화했습니다");
                    messageManager.sendCustom(target, "<yellow>잔액이 초기화되었습니다");
                } else {
                    messageManager.sendCustom(sender, "<red>실패: " + response.errorMessage);
                }
            }

            default -> {
                messageManager.sendCustom(sender, "<red>알 수 없는 명령어: " + action);
                messageManager.sendCustom(sender, "<red>사용 가능: give, take, set, reset");
            }
        }

        return true;
    }

    private double parseAmount(String str, CommandSender sender) {
        try {
            double amount = Double.parseDouble(str);
            if (amount < 0) {
                messageManager.sendCustom(sender, "<red>금액은 0 이상이어야 합니다");
                return -1;
            }
            return amount;
        } catch (NumberFormatException e) {
            messageManager.sendCustom(sender, "<red>유효하지 않은 금액입니다");
            return -1;
        }
    }
}

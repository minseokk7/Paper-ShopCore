package me.minseok.shopsystem.commands;

import me.minseok.shopsystem.database.DatabaseManager;
import me.minseok.shopsystem.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BaltopCommand implements CommandExecutor {

    private final DatabaseManager database;
    private final MessageManager messageManager;
    private final Plugin plugin;
    private static final int PER_PAGE = 10;

    public BaltopCommand(DatabaseManager database, MessageManager messageManager, Plugin plugin) {
        this.database = database;
        this.messageManager = messageManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int page = 1;

        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1)
                    page = 1;
            } catch (NumberFormatException e) {
                messageManager.sendCustom(sender, "<red>유효하지 않은 페이지 번호입니다");
                return true;
            }
        }

        // 비동기로 DB 조회 후 메인 스레드에서 결과 출력
        final int finalPage = page;
        CompletableFuture.supplyAsync(() -> getTopBalances(finalPage))
                .thenAccept(entries -> {
                    // 메인 스레드에서 메시지 전송
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (entries.isEmpty()) {
                            messageManager.sendCustom(sender, "<red>데이터가 없습니다");
                            return;
                        }

                        messageManager.sendCustom(sender,
                                "<yellow><bold>=== 💰 부자 순위 (" + finalPage + "페이지) ===");

                        int rank = (finalPage - 1) * PER_PAGE + 1;
                        for (BalanceEntry entry : entries) {
                            String medal = getRankMedal(rank);
                            messageManager.sendCustom(sender,
                                    "<green>" + rank + ". " + medal + entry.playerName
                                            + " <white>- <yellow>"
                                            + String.format("%.2f원", entry.balance));
                            rank++;
                        }
                    });
                });

        return true;
    }

    private String getRankMedal(int rank) {
        return switch (rank) {
            case 1 -> "🥇 ";
            case 2 -> "🥈 ";
            case 3 -> "🥉 ";
            default -> "";
        };
    }

    private List<BalanceEntry> getTopBalances(int page) {
        List<BalanceEntry> entries = new ArrayList<>();
        int offset = (page - 1) * PER_PAGE;

        String sql = """
                    SELECT b.uuid, b.balance, p.name
                    FROM player_balances b
                    LEFT JOIN (
                        SELECT DISTINCT uuid, name
                        FROM transactions
                        WHERE name IS NOT NULL
                    ) p ON b.uuid = p.uuid
                    ORDER BY b.balance DESC
                    LIMIT ? OFFSET ?
                """;

        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, PER_PAGE);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    double balance = rs.getDouble("balance");
                    String name = rs.getString("name");

                    if (name == null) {
                        name = uuid.substring(0, 8); // UUID 앞 8자리로 대체
                    }

                    entries.add(new BalanceEntry(name, balance));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("부자 순위 조회 실패: " + e.getMessage());
        }

        return entries;
    }

    private static class BalanceEntry {
        String playerName;
        double balance;

        BalanceEntry(String playerName, double balance) {
            this.playerName = playerName;
            this.balance = balance;
        }
    }
}

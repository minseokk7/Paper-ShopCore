package me.minseok.shopsystem.economy;

import me.minseok.shopsystem.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VaultEconomy implements Economy {

    private final DatabaseManager database;
    private final Logger logger;
    private final String currencyName;
    private final String currencyPlural;

    public VaultEconomy(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
        this.currencyName = "원";
        this.currencyPlural = "원";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "VelocityShopSystem";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return String.format("%,.2f%s", amount, currencyName);
    }

    @Override
    public String currencyNamePlural() {
        return currencyPlural;
    }

    @Override
    public String currencyNameSingular() {
        return currencyName;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName) {
        // Legacy support - not recommended
        return false;
    }

    private boolean hasAccount(UUID uuid) {
        if (uuid == null) {
            logger.log(Level.WARNING, "UUID is null in hasAccount");
            return false;
        }

        String sql = "SELECT 1 FROM player_balances WHERE uuid = ?";
        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check account: " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return createPlayerAccount(player.getUniqueId());
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return false; // Not supported
    }

    private boolean createPlayerAccount(UUID uuid) {
        if (uuid == null) {
            logger.log(Level.WARNING, "UUID is null in createPlayerAccount");
            return false;
        }

        String sql = "INSERT IGNORE INTO player_balances (uuid, balance) VALUES (?, 0.00)";
        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            logger.log(Level.INFO, "Player account created for uuid: " + uuid);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create account: " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName) {
        return 0;
    }

    private double getBalance(UUID uuid) {
        if (uuid == null) {
            logger.log(Level.WARNING, "UUID is null in getBalance");
            return 0;
        }

        if (!hasAccount(uuid)) {
            createPlayerAccount(uuid);
        }

        String sql = "SELECT balance FROM player_balances WHERE uuid = ?";
        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double balance = rs.getDouble("balance");
                    logger.log(Level.INFO, "Retrieved balance for uuid " + uuid + ": " + balance);
                    return balance;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get balance for uuid " + uuid + ": " + e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, double amount) {
        return false;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdrawPlayer(player.getUniqueId(), amount, "WITHDRAW");
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Use UUID");
    }

    private EconomyResponse withdrawPlayer(UUID uuid, double amount, String reason) {
        if (uuid == null) {
            logger.log(Level.WARNING, "UUID is null in withdrawPlayer");
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid UUID");
        }

        if (amount < 0) {
            logger.log(Level.WARNING, "Attempted to withdraw negative amount: " + amount + " for uuid: " + uuid);
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative");
        }

        double balance = getBalance(uuid);
        if (balance < amount) {
            logger.log(Level.INFO, "Withdrawal failed - insufficient funds. UUID: " + uuid + ", Required: " + amount
                    + ", Balance: " + balance);
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }

        String sql = "UPDATE player_balances SET balance = balance - ? WHERE uuid = ?";
        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();

            logTransaction(uuid, reason, amount, null);

            double newBalance = balance - amount;
            logger.log(Level.INFO, "Withdrawal successful. UUID: " + uuid + ", Amount: " + amount + ", Reason: "
                    + reason + ", New Balance: " + newBalance);
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to withdraw: " + e.getMessage(), e);
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, e.getMessage());
        }
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return depositPlayer(player.getUniqueId(), amount, "DEPOSIT");
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Use UUID");
    }

    private EconomyResponse depositPlayer(UUID uuid, double amount, String reason) {
        if (uuid == null) {
            logger.log(Level.WARNING, "UUID is null in depositPlayer");
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid UUID");
        }

        if (amount < 0) {
            logger.log(Level.WARNING, "Attempted to deposit negative amount: " + amount + " for uuid: " + uuid);
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative");
        }

        if (!hasAccount(uuid)) {
            createPlayerAccount(uuid);
        }

        String sql = "UPDATE player_balances SET balance = balance + ? WHERE uuid = ?";
        try (Connection conn = database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();

            logTransaction(uuid, reason, amount, null);

            double newBalance = getBalance(uuid);
            logger.log(Level.INFO, "Deposit successful. UUID: " + uuid + ", Amount: " + amount + ", Reason: " + reason
                    + ", New Balance: " + newBalance);
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to deposit: " + e.getMessage(), e);
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, e.getMessage());
        }
    }

    private void logTransaction(UUID uuid, String type, double amount, String description) {
        if (uuid == null || type == null) {
            logger.log(Level.WARNING, "UUID or type is null in logTransaction");
            return;
        }

        // 비동기로 트랜잭션 로그 기록 (메인 스레드 블로킹 방지)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO transactions (uuid, type, amount, description) VALUES (?, ?, ?, ?)";
            try (Connection conn = database.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, type);
                stmt.setDouble(3, amount);
                stmt.setString(4, description);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "트랜잭션 로그 기록 실패: " + e.getMessage(), e);
            }
        });
    }

    // Unimplemented Vault methods (world-specific, bank support, etc.)
    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return false;
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return 0;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return false;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return false;
    }

    // Bank methods - not supported
    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return unsupportedBank();
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }

    private EconomyResponse unsupportedBank() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
}

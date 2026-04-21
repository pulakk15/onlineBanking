package com.bankapp;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankingService {

    public User register(String fullName, String email, String password) throws SQLException {
        String sql = "INSERT INTO users(full_name, email, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, CryptoUtil.hash(password));

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("User registration failed.");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new User(rs.getInt(1), fullName, email);
                }
            }
        }

        throw new SQLException("User registration failed.");
    }

    public User login(String email, String password) throws SQLException {
        String sql = "SELECT id, full_name, email, password_hash FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String inputHash = CryptoUtil.hash(password);

                    if (storedHash.equals(inputHash)) {
                        return new User(
                                rs.getInt("id"),
                                rs.getString("full_name"),
                                rs.getString("email")
                        );
                    }
                }
            }
        }

        return null;
    }

    public String createAccount(int userId) throws SQLException {
        String sql = "INSERT INTO accounts(user_id, account_number, balance) VALUES (?, ?, 0.00)";

        try (Connection conn = DBConnection.getConnection()) {
            for (int i = 0; i < 5; i++) {
                String accountNumber = generateAccountNumber();

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    ps.setString(2, accountNumber);
                    ps.executeUpdate();
                    return accountNumber;
                } catch (SQLException e) {
                    if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                        continue;
                    }
                    throw e;
                }
            }
        }

        throw new SQLException("Could not create a unique account number.");
    }

    public List<String> getUserAccounts(int userId) throws SQLException {
        String sql = "SELECT account_number, balance FROM accounts WHERE user_id = ? ORDER BY created_at DESC";
        List<String> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(
                            rs.getString("account_number") + " | Balance: ₹" + rs.getBigDecimal("balance")
                    );
                }
            }
        }

        return result;
    }

    public BigDecimal getBalance(String accountNumber) throws SQLException {
        String sql = "SELECT balance FROM accounts WHERE account_number = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }

        return null;
    }

    public void deposit(String accountNumber, BigDecimal amount) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer accountId = getAccountId(conn, accountNumber);
                if (accountId == null) {
                    throw new SQLException("Account not found.");
                }

                updateBalance(conn, accountNumber, amount);

                insertTransaction(conn, null, accountId, "DEPOSIT", amount, "Cash deposit");

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void withdraw(String accountNumber, BigDecimal amount) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer accountId = getAccountId(conn, accountNumber);
                if (accountId == null) {
                    throw new SQLException("Account not found.");
                }

                boolean success = deductBalance(conn, accountNumber, amount);
                if (!success) {
                    throw new SQLException("Insufficient balance.");
                }

                insertTransaction(conn, accountId, null, "WITHDRAW", amount, "Cash withdrawal");

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void transfer(String fromAccount, String toAccount, BigDecimal amount) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        if (fromAccount.equals(toAccount)) {
            throw new IllegalArgumentException("Source and destination account cannot be the same.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer fromId = getAccountId(conn, fromAccount);
                Integer toId = getAccountId(conn, toAccount);

                if (fromId == null || toId == null) {
                    throw new SQLException("One or both accounts not found.");
                }

                boolean success = deductBalance(conn, fromAccount, amount);
                if (!success) {
                    throw new SQLException("Insufficient balance.");
                }

                updateBalance(conn, toAccount, amount);

                insertTransaction(conn, fromId, toId, "TRANSFER", amount, "Transfer");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void printTransactionHistory(String accountNumber) throws SQLException {
        String sql = """
                SELECT t.id, t.txn_type, t.amount, t.note, t.created_at,
                       fa.account_number AS from_acc,
                       ta.account_number AS to_acc
                FROM transactions t
                LEFT JOIN accounts fa ON t.from_account_id = fa.id
                LEFT JOIN accounts ta ON t.to_account_id = ta.id
                WHERE fa.account_number = ? OR ta.account_number = ?
                ORDER BY t.created_at DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber);
            ps.setString(2, accountNumber);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n--- Transaction History for " + accountNumber + " ---");
                boolean found = false;

                while (rs.next()) {
                    found = true;
                    System.out.println(
                            rs.getInt("id") + " | " +
                            rs.getString("txn_type") + " | ₹" +
                            rs.getBigDecimal("amount") + " | " +
                            rs.getString("note") + " | " +
                            rs.getTimestamp("created_at")
                    );
                }

                if (!found) {
                    System.out.println("No transactions found.");
                }
            }
        }
    }

    private Integer getAccountId(Connection conn, String accountNumber) throws SQLException {
        String sql = "SELECT id FROM accounts WHERE account_number = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        return null;
    }

    private void updateBalance(Connection conn, String accountNumber, BigDecimal amount) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, accountNumber);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Account not found.");
            }
        }
    }

    private boolean deductBalance(Connection conn, String accountNumber, BigDecimal amount) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, accountNumber);
            ps.setBigDecimal(3, amount);

            return ps.executeUpdate() > 0;
        }
    }

    private void insertTransaction(Connection conn,
                                   Integer fromId,
                                   Integer toId,
                                   String type,
                                   BigDecimal amount,
                                   String note) throws SQLException {
        String sql = """
                INSERT INTO transactions(from_account_id, to_account_id, txn_type, amount, note)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (fromId == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, fromId);

            if (toId == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, toId);

            ps.setString(3, type);
            ps.setBigDecimal(4, amount);
            ps.setString(5, note);

            ps.executeUpdate();
        }
    }

    private String generateAccountNumber() {
        long n = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 9_000_000_000L) + 1_000_000_000L;
        return String.valueOf(n);
    }
}
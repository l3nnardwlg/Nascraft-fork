package me.bounser.nascraft.database.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

public class WebSessions {

    public static void saveWebSession(Connection connection, String sessionHash, UUID playerUuid, LocalDateTime createdAt, LocalDateTime lastActivity, LocalDateTime expiresAt) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "INSERT INTO web_sessions (session_hash, player_uuid, created_at, last_activity, expires_at) VALUES (?, ?, ?, ?, ?)")) {
            prep.setString(1, sessionHash);
            prep.setString(2, playerUuid.toString());
            prep.setString(3, createdAt.toString());
            prep.setString(4, lastActivity.toString());
            prep.setString(5, expiresAt.toString());
            prep.executeUpdate();
        }
    }

    public static void updateWebSessionActivity(Connection connection, String sessionHash, LocalDateTime lastActivity) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "UPDATE web_sessions SET last_activity = ? WHERE session_hash = ?")) {
            prep.setString(1, lastActivity.toString());
            prep.setString(2, sessionHash);
            prep.executeUpdate();
        }
    }

    public static void deleteWebSession(Connection connection, String sessionHash) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM web_sessions WHERE session_hash = ?")) {
            prep.setString(1, sessionHash);
            prep.executeUpdate();
        }
    }

    public static UUID getWebSessionPlayerUUID(Connection connection, String sessionHash) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT player_uuid, expires_at FROM web_sessions WHERE session_hash = ?")) {
            prep.setString(1, sessionHash);
            try (ResultSet rs = prep.executeQuery()) {
                if (rs.next()) {
                    String expiresAtStr = rs.getString("expires_at");
                    try {
                        LocalDateTime expiresAt = LocalDateTime.parse(expiresAtStr);
                        if (LocalDateTime.now().isAfter(expiresAt)) {
                            // Expired session, delete it!
                            deleteWebSession(connection, sessionHash);
                            return null;
                        }
                    } catch (Exception ignored) {}
                    return UUID.fromString(rs.getString("player_uuid"));
                }
            }
        }
        return null;
    }

    public static boolean hasWebSession(Connection connection, String sessionHash) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT 1 FROM web_sessions WHERE session_hash = ?")) {
            prep.setString(1, sessionHash);
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void purgeExpiredWebSessions(Connection connection) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM web_sessions WHERE expires_at < ?")) {
            prep.setString(1, LocalDateTime.now().toString());
            prep.executeUpdate();
        }
    }
}

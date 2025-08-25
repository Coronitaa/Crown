// database/DatabaseManager.java
package cp.corona.database;

import cp.corona.crown.Crown;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.Date; // Keep this import
import java.util.logging.Level;

/**
 * Manages database operations, including softbans, mutes, and punishment history.
 * Supports SQLite and MySQL.
 */
public class DatabaseManager {
    private final Crown plugin;
    private final String dbURL;
    private final String dbUsername;
    private final String dbPassword;
    private final String dbType;

    public DatabaseManager(Crown plugin) {
        this.plugin = plugin;
        this.dbType = plugin.getConfigManager().getDatabaseType();
        String dbName = plugin.getConfigManager().getDatabaseName();
        String dbAddress = plugin.getConfigManager().getDatabaseAddress();
        String dbPort = plugin.getConfigManager().getDatabasePort();
        this.dbUsername = plugin.getConfigManager().getDatabaseUsername();
        this.dbPassword = plugin.getConfigManager().getDatabasePassword();

        if ("mysql".equalsIgnoreCase(dbType)) {
            this.dbURL = String.format("jdbc:mysql://%s:%s/%s?autoReconnect=true&useSSL=false", dbAddress, dbPort, dbName);
        } else {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, dbName + ".db");
            this.dbURL = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        }
        initializeDatabase();
        startExpiryCheckTask();
        startMuteExpiryCheckTask(); // Task for mutes
    }

    private Connection getConnection() throws SQLException {
        if ("mysql".equalsIgnoreCase(dbType)) {
            return DriverManager.getConnection(dbURL, this.dbUsername, this.dbPassword);
        } else {
            return DriverManager.getConnection(dbURL);
        }
    }

    private void initializeDatabase() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            String autoIncrementKeyword = "mysql".equalsIgnoreCase(dbType) ? "AUTO_INCREMENT" : "AUTOINCREMENT";

            // Softbans Table
            String createSoftbansTableSQL = "CREATE TABLE IF NOT EXISTS softbans (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "endTime BIGINT NOT NULL," +
                    "reason TEXT)";
            statement.execute(createSoftbansTableSQL);

            // Mutes Table
            String createMutesTableSQL = "CREATE TABLE IF NOT EXISTS mutes (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "endTime BIGINT NOT NULL," +
                    "reason TEXT)";
            statement.execute(createMutesTableSQL);

            // History Table
            String createHistoryTableSQL = "CREATE TABLE IF NOT EXISTS punishment_history (" +
                    "id INTEGER PRIMARY KEY " + autoIncrementKeyword + "," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "punishment_type VARCHAR(50) NOT NULL," +
                    "reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "punisher_name VARCHAR(255)," +
                    "punishment_time BIGINT DEFAULT 0," +
                    "duration_string VARCHAR(50) DEFAULT 'permanent')";
            statement.execute(createHistoryTableSQL);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database!", e);
        }
    }

    private void startExpiryCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM softbans WHERE endTime <= ? AND endTime != ?")) {
                long currentTime = System.currentTimeMillis();
                ps.setLong(1, currentTime);
                ps.setLong(2, Long.MAX_VALUE);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error checking for expired soft bans.", e);
            }
        }, 0L, 6000L);
    }

    private void startMuteExpiryCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM mutes WHERE endTime <= ? AND endTime != ?")) {
                long currentTime = System.currentTimeMillis();
                ps.setLong(1, currentTime);
                ps.setLong(2, Long.MAX_VALUE);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error checking for expired mutes.", e);
            }
        }, 0L, 6000L);
    }

    public void softBanPlayer(UUID uuid, long endTime, String reason, String punisherName) {
        long currentEndTime = getSoftBanEndTime(uuid);
        long finalEndTime = (endTime == Long.MAX_VALUE || currentEndTime <= System.currentTimeMillis() || currentEndTime == Long.MAX_VALUE)
                ? endTime
                : currentEndTime + (endTime - System.currentTimeMillis());

        String durationString = (finalEndTime == Long.MAX_VALUE)
                ? plugin.getConfigManager().getMessage("placeholders.permanent_time_display")
                : TimeUtils.formatTime((int)((finalEndTime - System.currentTimeMillis()) / 1000), plugin.getConfigManager());

        String sql = "mysql".equalsIgnoreCase(dbType) ? "REPLACE INTO softbans (uuid, endTime, reason) VALUES (?, ?, ?)" : "INSERT OR REPLACE INTO softbans (uuid, endTime, reason) VALUES (?, ?, ?)";

        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, finalEndTime);
            ps.setString(3, reason);
            ps.executeUpdate();
            logPunishment(uuid, "softban", reason, punisherName, finalEndTime, durationString);

            Player targetPlayer = Bukkit.getPlayer(uuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned", "{time}", durationString, "{reason}", reason);
                targetPlayer.sendMessage(softbanMessage);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database operation failed while softbanning player!", e);
        }
    }

    public void unSoftBanPlayer(UUID uuid, String punisherName) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM softbans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            if (ps.executeUpdate() > 0) {
                logPunishment(uuid, "unsoftban", "Softban Removed", punisherName, 0L, "N/A");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not un-soft ban player!", e);
        }
    }

    public boolean isSoftBanned(UUID uuid) {
        return getPunishmentEndTime("softbans", uuid) > System.currentTimeMillis();
    }

    public boolean isMuted(UUID uuid) {
        return getPunishmentEndTime("mutes", uuid) > System.currentTimeMillis();
    }

    public String getSoftBanReason(UUID uuid) {
        return getPunishmentReason("softbans", uuid);
    }

    public long getSoftBanEndTime(UUID uuid) {
        return getPunishmentEndTime("softbans", uuid);
    }

    public void mutePlayer(UUID uuid, long endTime, String reason, String punisherName) {
        long currentEndTime = getMuteEndTime(uuid);
        long finalEndTime = (endTime == Long.MAX_VALUE || currentEndTime <= System.currentTimeMillis() || currentEndTime == Long.MAX_VALUE)
                ? endTime
                : currentEndTime + (endTime - System.currentTimeMillis());

        String durationString = (finalEndTime == Long.MAX_VALUE)
                ? plugin.getConfigManager().getMessage("placeholders.permanent_time_display")
                : TimeUtils.formatTime((int)((finalEndTime - System.currentTimeMillis()) / 1000), plugin.getConfigManager());

        String sql = "mysql".equalsIgnoreCase(dbType) ? "REPLACE INTO mutes (uuid, endTime, reason) VALUES (?, ?, ?)" : "INSERT OR REPLACE INTO mutes (uuid, endTime, reason) VALUES (?, ?, ?)";

        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, finalEndTime);
            ps.setString(3, reason);
            ps.executeUpdate();
            logPunishment(uuid, "mute", reason, punisherName, finalEndTime, durationString);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database operation failed while muting player!", e);
        }
    }

    public void unmutePlayer(UUID uuid, String punisherName) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM mutes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            if (ps.executeUpdate() > 0) {
                logPunishment(uuid, "unmute", "Mute Removed", punisherName, 0L, "N/A");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not unmute player!", e);
        }
    }

    public long getMuteEndTime(UUID uuid) {
        return getPunishmentEndTime("mutes", uuid);
    }

    private String getPunishmentReason(String table, UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT reason FROM " + table + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("reason");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error getting punishment reason!", e);
        }
        return null;
    }

    private long getPunishmentEndTime(String table, UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT endTime FROM " + table + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("endTime");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error getting punishment end time!", e);
        }
        return 0;
    }

    public void logPunishment(UUID playerUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString) {
        String sql = "INSERT INTO punishment_history (player_uuid, punishment_type, reason, punisher_name, punishment_time, duration_string) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentType);
            ps.setString(3, reason);
            ps.setString(4, punisherName);
            ps.setLong(5, punishmentEndTime);
            ps.setString(6, durationString);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error logging punishment!", e);
        }
    }

    public List<PunishmentEntry> getPunishmentHistory(UUID playerUUID, int page, int entriesPerPage) {
        List<PunishmentEntry> history = new ArrayList<>();
        int offset = (page - 1) * entriesPerPage;
        String sql = "SELECT punishment_type, reason, timestamp, punisher_name, punishment_time, duration_string FROM punishment_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, entriesPerPage);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new PunishmentEntry(
                            rs.getString("punishment_type"),
                            rs.getString("reason"),
                            rs.getTimestamp("timestamp"),
                            rs.getString("punisher_name"),
                            rs.getLong("punishment_time"),
                            rs.getString("duration_string")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving punishment history!", e);
        }
        return history;
    }

    public int getPunishmentHistoryCount(UUID playerUUID) {
        int count = 0;
        String sql = "SELECT COUNT(*) AS total FROM punishment_history WHERE player_uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error counting punishment history!", e);
        }
        return count;
    }

    public HashMap<String, Integer> getPunishmentCounts(UUID playerUUID) {
        HashMap<String, Integer> counts = new HashMap<>();
        String sql = "SELECT punishment_type, COUNT(*) as count FROM punishment_history WHERE player_uuid = ? GROUP BY punishment_type";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString("punishment_type"), rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving punishment counts for UUID: " + playerUUID, e);
        }
        return counts;
    }

    public static class PunishmentEntry {
        private final String type;
        private final String reason;
        private final Timestamp timestamp;
        private final String punisherName;
        private final long punishmentTime;
        private final String durationString;

        public PunishmentEntry(String type, String reason, Timestamp timestamp, String punisherName, long punishmentTime, String durationString) {
            this.type = type;
            this.reason = reason;
            this.timestamp = timestamp;
            this.punisherName = punisherName;
            this.punishmentTime = punishmentTime;
            this.durationString = durationString;
        }

        public String getType() { return type; }
        public String getReason() { return reason; }
        public Timestamp getTimestamp() { return timestamp; }
        public String getPunisherName() { return punisherName; }
        public long getPunishmentTime() { return punishmentTime; }
        public String getDurationString() { return durationString; }
    }
}
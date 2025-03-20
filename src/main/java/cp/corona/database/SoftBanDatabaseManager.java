package cp.corona.database;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages the softban database operations, including storing, retrieving,
 * and checking softban status and expiry. Supports SQLite and MySQL.
 * Also handles punishment history logging.
 */
public class SoftBanDatabaseManager {
    private final CrownPunishments plugin;
    private final String dbURL;

    /**
     * Constructor for SoftBanDatabaseManager.
     * Initializes database connection based on configuration and sets up tables.
     *
     * @param plugin Instance of the main plugin class.
     */
    public SoftBanDatabaseManager(CrownPunishments plugin) {
        this.plugin = plugin;
        String dbType = plugin.getConfigManager().getDatabaseType();
        String dbName = plugin.getConfigManager().getDatabaseName();
        String dbAddress = plugin.getConfigManager().getDatabaseAddress();
        String dbPort = plugin.getConfigManager().getDatabasePort();
        String dbUsername = plugin.getConfigManager().getDatabaseUsername();
        String dbPassword = plugin.getConfigManager().getDatabasePassword();

        if ("mysql".equalsIgnoreCase(dbType)) {
            this.dbURL = String.format("jdbc:mysql://%s:%s/%s?autoReconnect=true&useSSL=false", dbAddress, dbPort, dbName);
        } else {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File dbFile = new File(dataFolder, dbName + ".db");
            this.dbURL = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        }
        initializeDatabase();
        startExpiryCheckTask();
    }

    /**
     * Gets a database connection.
     * @return Connection object.
     * @throws SQLException if connection fails.
     */
    private Connection getConnection() throws SQLException {
        String dbType = plugin.getConfigManager().getDatabaseType();
        if ("mysql".equalsIgnoreCase(dbType)) {
            return DriverManager.getConnection(dbURL,
                    plugin.getConfigManager().getDatabaseUsername(),
                    plugin.getConfigManager().getDatabasePassword());
        } else {
            return DriverManager.getConnection(dbURL);
        }
    }

    /**
     * Initializes the database, creating necessary tables if they do not exist.
     */
    private void initializeDatabase() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS softbans (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "endTime BIGINT NOT NULL," +
                    "reason TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS punishment_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "punishment_type VARCHAR(50) NOT NULL," +
                    "reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "punisher_name VARCHAR(255)," +
                    "punishment_time BIGINT DEFAULT 0," +
                    "duration_string VARCHAR(50) DEFAULT 'permanent')");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database!", e);
        }
    }

    /**
     * Starts a repeating task to check for and remove expired softbans.
     */
    private void startExpiryCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                removeExpiredSoftBans();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error checking for expired soft bans.", e);
            }
        }, 0L, 20L * 60 * 5); // Check every 5 minutes
    }

    /**
     * Removes softbans that have expired from the database.
     * @throws SQLException if database operation fails.
     */
    private void removeExpiredSoftBans() throws SQLException {
        long currentTime = System.currentTimeMillis();
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM softbans WHERE endTime <= ? AND endTime != ?")) {
            ps.setLong(1, currentTime);
            ps.setLong(2, Long.MAX_VALUE); // Don't delete permanent softbans
            ps.executeUpdate();
        }
    }

    /**
     * Softbans a player, adding or updating their softban information in the database.
     * Extends existing softbans if applicable, unless a permanent softban is being set.
     *
     * @param uuid         UUID of the player to softban.
     * @param endTime      Timestamp of when the softban ends.
     * @param reason       Reason for the softban.
     * @param punisherName Name of the punisher, can be console or player name.
     */
    public void softBanPlayer(UUID uuid, long endTime, String reason, String punisherName) {
        long currentEndTime = getSoftBanEndTime(uuid);
        long finalEndTime = endTime;
        String logMessage;
        String durationString = TimeUtils.formatTime((int) ((endTime - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
        if (endTime == Long.MAX_VALUE) {
            durationString = "permanent";
        }

        // Logic for permanent softbans: Overwrite existing time, do not extend
        if (endTime == Long.MAX_VALUE) {
            finalEndTime = Long.MAX_VALUE;
            logMessage = "Setting PERMANENT softban";
        } else if (currentEndTime > System.currentTimeMillis() && currentEndTime != Long.MAX_VALUE) {
            finalEndTime = currentEndTime + (endTime - System.currentTimeMillis());
            logMessage = "Extending existing softban";
        } else {
            logMessage = "Adding new softban";
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO,
                    logMessage + " - UUID: " + uuid +
                            " | EndTime: " + (finalEndTime == Long.MAX_VALUE ? "PERMANENT" : new Date(finalEndTime)) +
                            " | Reason: " + reason);
        }

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT OR REPLACE INTO softbans (uuid, endTime, reason) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, finalEndTime);
            ps.setString(3, reason);
            ps.executeUpdate();

            // Send softban received message to the player
            Player targetPlayer = Bukkit.getPlayer(uuid);
            if (targetPlayer != null) {
                String formattedTime = (endTime == Long.MAX_VALUE) ? plugin.getConfigManager().getMessage("messages.permanent_time_display") : TimeUtils.formatTime((int) ((endTime - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned",
                        "{time}", formattedTime,
                        "{reason}", reason);
                targetPlayer.sendMessage(softbanMessage);
            }
            logPunishment(uuid, "softban", reason, punisherName, finalEndTime, durationString);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database operation failed!", e);
        }
    }

    /**
     * Un-softbans a player by removing their softban entry from the database.
     * @param uuid UUID of the player to un-softban.
     * @param punisherName Name of the punisher, can be console or player name.
     */
    public void unSoftBanPlayer(UUID uuid, String punisherName) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM softbans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            logPunishment(uuid, "unsoftban", "Softban Removed", punisherName, 0L, "permanent");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not un-soft ban player!", e);
        }
    }

    /**
     * Checks if a player is currently softbanned.
     * @param uuid UUID of the player to check.
     * @return true if the player is softbanned, false otherwise.
     */
    public boolean isSoftBanned(UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT endTime FROM softbans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long endTime = rs.getLong("endTime");
                return endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error!", e);
        }
        return false;
    }

    /**
     * Gets the reason for a player's softban.
     * @param uuid UUID of the player.
     * @return Reason for the softban, or null if not softbanned.
     */
    public String getSoftBanReason(UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT reason FROM softbans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("reason");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error!", e);
        }
        return null;
    }

    /**
     * Gets the end time of a player's softban.
     * @param uuid UUID of the player.
     * @return End time of the softban as a long timestamp, or 0 if not softbanned.
     */
    public long getSoftBanEndTime(UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT endTime FROM softbans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("endTime");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error!", e);
        }
        return 0;
    }

    /**
     * Logs a punishment to the punishment history database with duration.
     * @param playerUUID      UUID of the punished player.
     * @param punishmentType  Type of punishment (ban, mute, softban, kick, warn, unsoftban, unban, unmute).
     * @param reason          Reason for the punishment.
     * @param punisherName    Name of the punisher.
     * @param punishmentEndTime End time of the punishment (0 for permanent or not applicable).
     * @param durationString  The duration string for logging purposes (e.g., "1d", "permanent").
     */
    public void logPunishment(UUID playerUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO punishment_history (player_uuid, punishment_type, reason, punisher_name, punishment_time, duration_string) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentType);
            ps.setString(3, reason);
            ps.setString(4, punisherName);
            ps.setLong(5, punishmentEndTime);
            ps.setString(6, durationString);
            ps.executeUpdate();
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "[SoftBanDatabaseManager] Logging punishment: UUID=" + playerUUID + ", Type=" + punishmentType + ", Reason=" + reason + ", Punisher=" + punisherName + ", EndTime=" + punishmentEndTime + ", DurationString=" + durationString);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error logging punishment!", e);
        }
    }

    /**
     * Retrieves the punishment history for a player, paginated and limited to a certain number of entries per page.
     * Includes softbans in history retrieval.
     *
     * @param playerUUID     UUID of the player.
     * @param page           Page number to retrieve (starting from 1).
     * @param entriesPerPage Number of entries to display per page.
     * @return List of punishment history entries for the requested page.
     */
    public List<PunishmentEntry> getPunishmentHistory(UUID playerUUID, int page, int entriesPerPage) {
        List<PunishmentEntry> history = new ArrayList<>();
        int offset = (page - 1) * entriesPerPage; // Calculate offset for pagination
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT punishment_type, reason, timestamp, punisher_name, punishment_time, duration_string FROM punishment_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, entriesPerPage);
            ps.setInt(3, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String type = rs.getString("punishment_type");
                String reason = rs.getString("reason");
                Timestamp timestamp = rs.getTimestamp("timestamp");
                String punisherName = rs.getString("punisher_name");
                long punishmentTime = rs.getLong("punishment_time");
                String durationString = rs.getString("duration_string");
                history.add(new PunishmentEntry(type, reason, timestamp, punisherName, punishmentTime, durationString));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving punishment history!", e);
        }
        return history;
    }

    /**
     * Retrieves the total count of punishment history entries for a specific player.
     *
     * @param playerUUID UUID of the player.
     * @return The total count of punishment history entries.
     */
    public int getPunishmentHistoryCount(UUID playerUUID) {
        int count = 0;
        // Use a SQL COUNT query to get the total number of punishment history entries
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) AS total FROM punishment_history WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                count = rs.getInt("total");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error counting punishment history!", e);
        }
        return count;
    }

    /**
     * Inner class to represent a punishment history entry.
     */
    public static class PunishmentEntry {
        private final String type;
        private final String reason;
        private final Timestamp timestamp;
        private final String punisherName;
        private final long punishmentTime;
        private final String durationString;

        /**
         * Constructor for PunishmentEntry.
         *
         * @param type            Type of punishment.
         * @param reason          Reason for the punishment.
         * @param timestamp       Timestamp of when the punishment was applied.
         * @param punisherName    Name of the punisher.
         * @param punishmentTime  End time of the punishment (0 for permanent or not applicable).
         * @param durationString  String representation of the duration.
         */
        public PunishmentEntry(String type, String reason, Timestamp timestamp, String punisherName, long punishmentTime, String durationString) {
            this.type = type;
            this.reason = reason;
            this.timestamp = timestamp;
            this.punisherName = punisherName;
            this.punishmentTime = punishmentTime;
            this.durationString = durationString;
        }

        public String getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }

        public Timestamp getTimestamp() {
            return timestamp;
        }

        public String getPunisherName() {
            return punisherName;
        }

        public long getPunishmentTime() {
            return punishmentTime;
        }

        public String getDurationString() {
            return durationString;
        }
    }
}

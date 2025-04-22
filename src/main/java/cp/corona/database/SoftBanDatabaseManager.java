// database/SoftBanDatabaseManager.java
package cp.corona.database;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.Date; // Keep this import
import java.util.logging.Level;

/**
 * Manages the softban database operations, including storing, retrieving,
 * and checking softban status and expiry. Supports SQLite and MySQL.
 * Also handles punishment history logging.
 */
public class SoftBanDatabaseManager {
    private final CrownPunishments plugin;
    private final String dbURL;
    private final String dbUsername; // Store credentials for MySQL
    private final String dbPassword; // Store credentials for MySQL
    private final String dbType; // Store db type

    /**
     * Constructor for SoftBanDatabaseManager.
     * Initializes database connection based on configuration and sets up tables.
     *
     * @param plugin Instance of the main plugin class.
     */
    public SoftBanDatabaseManager(CrownPunishments plugin) {
        this.plugin = plugin;
        // Retrieve database configuration from MainConfigManager
        this.dbType = plugin.getConfigManager().getDatabaseType();
        String dbName = plugin.getConfigManager().getDatabaseName();
        String dbAddress = plugin.getConfigManager().getDatabaseAddress();
        String dbPort = plugin.getConfigManager().getDatabasePort();
        this.dbUsername = plugin.getConfigManager().getDatabaseUsername(); // Store for later use
        this.dbPassword = plugin.getConfigManager().getDatabasePassword(); // Store for later use

        // Construct database URL based on database type (MySQL or SQLite)
        if ("mysql".equalsIgnoreCase(dbType)) {
            // Use credentials directly here for MySQL URL formation
            this.dbURL = String.format("jdbc:mysql://%s:%s/%s?autoReconnect=true&useSSL=false", dbAddress, dbPort, dbName);
        } else { // Default to SQLite
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                boolean created = dataFolder.mkdirs();
                if (!created && plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().warning("[SoftBanDB] Failed to create plugin data folder.");
                }
            }
            File dbFile = new File(dataFolder, dbName + ".db");
            this.dbURL = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        }
        initializeDatabase(); // Initialize database tables
        startExpiryCheckTask(); // Start task to periodically check for expired softbans
    }

    /**
     * Gets a database connection. Handles both SQLite and MySQL.
     * @return Connection object.
     * @throws SQLException if connection fails.
     */
    private Connection getConnection() throws SQLException {
        // Get connection based on database type, including MySQL credentials if needed
        if ("mysql".equalsIgnoreCase(dbType)) {
            // Use stored credentials for MySQL connection
            return DriverManager.getConnection(dbURL, this.dbUsername, this.dbPassword);
        } else { // SQLite connection
            return DriverManager.getConnection(dbURL);
        }
    }

    /**
     * Initializes the database, creating necessary tables if they do not exist.
     */
    private void initializeDatabase() {
        // Use try-with-resources for automatic closing of Connection and Statement
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            // Determine AUTOINCREMENT keyword based on DB type
            String autoIncrementKeyword = "mysql".equalsIgnoreCase(dbType) ? "AUTO_INCREMENT" : "AUTOINCREMENT";

            // SQL to create softbans table if it doesn't exist
            String createSoftbansTableSQL = "CREATE TABLE IF NOT EXISTS softbans (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "endTime BIGINT NOT NULL," +
                    "reason TEXT)";
            statement.execute(createSoftbansTableSQL);

            // SQL to create punishment_history table if it doesn't exist
            String createHistoryTableSQL = "CREATE TABLE IF NOT EXISTS punishment_history (" +
                    "id INTEGER PRIMARY KEY " + autoIncrementKeyword + "," + // Use dynamic keyword
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "punishment_type VARCHAR(50) NOT NULL," +
                    "reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "punisher_name VARCHAR(255)," +
                    "punishment_time BIGINT DEFAULT 0," +
                    "duration_string VARCHAR(50) DEFAULT 'permanent')";
            statement.execute(createHistoryTableSQL);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database! Check configuration and permissions.", e);
        }
    }

    /**
     * Starts a repeating asynchronous task to check for and remove expired softbans.
     */
    private void startExpiryCheckTask() {
        // Run task asynchronously every 5 minutes (6000 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (Connection connection = getConnection();
                 // Delete entries where endTime is past AND endTime is not the permanent marker (Long.MAX_VALUE)
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM softbans WHERE endTime <= ? AND endTime != ?")) {

                long currentTime = System.currentTimeMillis();
                ps.setLong(1, currentTime);
                ps.setLong(2, Long.MAX_VALUE); // Exclude permanent softbans

                int deletedRows = ps.executeUpdate();
                if (deletedRows > 0 && plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[SoftBanDB] Removed " + deletedRows + " expired softban(s).");
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error checking for expired soft bans.", e);
            }
            // Initial delay 0, repeat every 5 minutes (20 ticks/sec * 60 sec/min * 5 min)
        }, 0L, 6000L);
    }

    // removeExpiredSoftBans is now integrated into startExpiryCheckTask

    /**
     * Softbans a player, adding or updating their softban information in the database.
     * Extends existing temporary softbans if applicable. Replaces any existing ban if setting a permanent one.
     * Logs the action to punishment history.
     *
     * @param uuid         UUID of the player to softban.
     * @param endTime      Timestamp (in milliseconds) of when the softban ends. Use Long.MAX_VALUE for permanent.
     * @param reason       Reason for the softban.
     * @param punisherName Name of the punisher (player name or "Console").
     */
    public void softBanPlayer(UUID uuid, long endTime, String reason, String punisherName) {
        long currentEndTime = getSoftBanEndTime(uuid); // Check existing softban end time
        long finalEndTime = endTime;
        String logMessage;
        String durationString; // Will store the display string for the duration

        // Determine the final end time and log message based on new and existing times
        if (endTime == Long.MAX_VALUE) {
            finalEndTime = Long.MAX_VALUE; // Ensure it's permanent
            // Get the configured display text for permanent duration
            durationString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display"); // CORRECTED
            logMessage = "Setting PERMANENT softban";
        } else if (currentEndTime > System.currentTimeMillis() && currentEndTime != Long.MAX_VALUE) {
            // If there's an existing *temporary* ban, extend it
            finalEndTime = currentEndTime + (endTime - System.currentTimeMillis());
            // Calculate the total duration in seconds for the extended ban for logging
            int totalDurationSeconds = (int)((finalEndTime - System.currentTimeMillis()) / 1000);
            durationString = TimeUtils.formatTime(totalDurationSeconds, plugin.getConfigManager()); // Use formatTime for display string
            logMessage = "Extending existing softban";
        } else {
            // New ban or replacing an expired/permanent one
            int durationSeconds = (int)((endTime - System.currentTimeMillis()) / 1000);
            durationString = TimeUtils.formatTime(durationSeconds, plugin.getConfigManager()); // Use formatTime for display string
            logMessage = "Adding new softban";
        }

        // Debug logging for the softban action
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO,
                    "[SoftBanDB] " + logMessage + " - UUID: " + uuid +
                            " | EndTime: " + (finalEndTime == Long.MAX_VALUE ? "PERMANENT" : new Date(finalEndTime)) +
                            " | Reason: " + reason);
        }

        // Use try-with-resources for automatic closing of Connection and PreparedStatement
        String sql = "INSERT OR REPLACE INTO softbans (uuid, endTime, reason) VALUES (?, ?, ?)"; // SQLite syntax
        if ("mysql".equalsIgnoreCase(dbType)) {
            sql = "REPLACE INTO softbans (uuid, endTime, reason) VALUES (?, ?, ?)"; // MySQL syntax
        }

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setLong(2, finalEndTime);
            ps.setString(3, reason != null ? reason : ""); // Ensure reason is not null
            ps.executeUpdate();

            // Log the punishment AFTER successfully updating the database
            logPunishment(uuid, "softban", reason, punisherName, finalEndTime, durationString);

            // Send softban message to the player if they are online
            Player targetPlayer = Bukkit.getPlayer(uuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                // Use the calculated durationString for the message
                String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned",
                        "{time}", durationString,
                        "{reason}", reason != null ? reason : "N/A"); // Use reason or "N/A"
                targetPlayer.sendMessage(softbanMessage);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database operation failed while softbanning player!", e);
        }
    }


    /**
     * Un-softbans a player by removing their entry from the softbans table.
     * Logs the action to punishment history.
     *
     * @param uuid         UUID of the player to un-softban.
     * @param punisherName Name of the person performing the un-softban.
     */
    public void unSoftBanPlayer(UUID uuid, String punisherName) {
        // Use try-with-resources for automatic closing
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM softbans WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                // Log the un-softban action only if a row was actually deleted
                // Use "N/A" or similar for duration/time as it's an unban action
                logPunishment(uuid, "unsoftban", "Softban Removed", punisherName, 0L, "N/A");
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[SoftBanDB] Successfully un-softbanned UUID: " + uuid);
                }
            } else {
                // Optionally log if no softban was found to remove
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[SoftBanDB] No active softban found to remove for UUID: " + uuid);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not un-soft ban player!", e);
        }
    }

    /**
     * Checks if a player is currently softbanned (has an entry with endTime in the future or permanent).
     * @param uuid UUID of the player to check.
     * @return true if the player is softbanned, false otherwise.
     */
    public boolean isSoftBanned(UUID uuid) {
        // Use try-with-resources
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT endTime FROM softbans WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long endTime = rs.getLong("endTime");
                    // Player is softbanned if endTime is permanent OR endTime is in the future
                    return endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error checking softban status!", e);
        }
        return false; // Player not found or error occurred
    }

    /**
     * Gets the reason for a player's current softban.
     * @param uuid UUID of the player.
     * @return Reason string, or null if not softbanned or reason not set.
     */
    public String getSoftBanReason(UUID uuid) {
        // Use try-with-resources
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT reason FROM softbans WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Also check if the ban is still active before returning reason
                    if (isSoftBanned(uuid)) {
                        return rs.getString("reason");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error getting softban reason!", e);
        }
        return null; // No active softban found or error occurred
    }

    /**
     * Gets the end time (timestamp in milliseconds) of a player's current softban.
     * @param uuid UUID of the player.
     * @return End time timestamp, Long.MAX_VALUE for permanent, or 0 if not softbanned.
     */
    public long getSoftBanEndTime(UUID uuid) {
        // Use try-with-resources
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT endTime FROM softbans WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long endTime = rs.getLong("endTime");
                    // Check if the ban is actually still active
                    if (endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis()) {
                        return endTime;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error getting softban end time!", e);
        }
        return 0; // No active softban found or error occurred
    }

    /**
     * Logs a punishment action to the punishment_history table.
     *
     * @param playerUUID        UUID of the punished player.
     * @param punishmentType    Type of action (e.g., "ban", "mute", "unsoftban", "freeze").
     * @param reason            Reason for the action.
     * @param punisherName      Name of the executor (player name or "Console").
     * @param punishmentEndTime End time timestamp (ms) for temporary punishments, Long.MAX_VALUE for permanent, 0 for instant actions (kick, warn, unban).
     * @param durationString    The user-friendly string representation of the duration (e.g., "1d", "Permanent", "N/A").
     */
    public void logPunishment(UUID playerUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString) {
        // Use try-with-resources
        String sql = "INSERT INTO punishment_history (player_uuid, punishment_type, reason, punisher_name, punishment_time, duration_string) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentType);
            ps.setString(3, reason != null ? reason : ""); // Ensure reason is not null
            ps.setString(4, punisherName != null ? punisherName : "Unknown"); // Ensure punisher name is not null
            ps.setLong(5, punishmentEndTime);
            ps.setString(6, durationString != null ? durationString : "N/A"); // Ensure duration string is not null

            ps.executeUpdate();

            // Debug logging for punishment logging confirmation
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "[SoftBanDB] Logged punishment: UUID=" + playerUUID + ", Type=" + punishmentType + ", Reason=" + reason + ", Punisher=" + punisherName + ", EndTime=" + punishmentEndTime + ", DurationString=" + durationString);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error logging punishment!", e);
        }
    }

    /**
     * Retrieves a paginated list of punishment history entries for a specific player.
     * Ordered by timestamp descending (most recent first).
     *
     * @param playerUUID     UUID of the player whose history is requested.
     * @param page           Page number to retrieve (starting from 1).
     * @param entriesPerPage Number of history entries to display per page.
     * @return List of PunishmentEntry objects for the requested page.
     */
    public List<PunishmentEntry> getPunishmentHistory(UUID playerUUID, int page, int entriesPerPage) {
        List<PunishmentEntry> history = new ArrayList<>();
        // Calculate the offset based on page number and entries per page
        int offset = (page - 1) * entriesPerPage;
        if (offset < 0) offset = 0; // Ensure offset is not negative

        // Use try-with-resources
        String sql = "SELECT punishment_type, reason, timestamp, punisher_name, punishment_time, duration_string FROM punishment_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, playerUUID.toString());
            ps.setInt(2, entriesPerPage);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                // Process each row in the result set
                while (rs.next()) {
                    String type = rs.getString("punishment_type");
                    String reason = rs.getString("reason");
                    Timestamp timestamp = rs.getTimestamp("timestamp");
                    String punisherName = rs.getString("punisher_name");
                    long punishmentTime = rs.getLong("punishment_time");
                    String durationString = rs.getString("duration_string");
                    // Create a new PunishmentEntry and add it to the list
                    history.add(new PunishmentEntry(type, reason, timestamp, punisherName, punishmentTime, durationString));
                }
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
        // Use try-with-resources
        String sql = "SELECT COUNT(*) AS total FROM punishment_history WHERE player_uuid = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

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

    /**
     * Retrieves the counts of each distinct punishment type for a player efficiently using GROUP BY.
     *
     * @param playerUUID UUID of the player.
     * @return A HashMap where keys are punishment types (String) and values are their counts (Integer).
     *         Returns an empty map in case of error.
     */
    public HashMap<String, Integer> getPunishmentCounts(UUID playerUUID) {
        HashMap<String, Integer> counts = new HashMap<>();
        // Use try-with-resources
        String sql = "SELECT punishment_type, COUNT(*) as count FROM punishment_history WHERE player_uuid = ? GROUP BY punishment_type";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Put the punishment type and its count into the map
                    counts.put(rs.getString("punishment_type"), rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving punishment counts for UUID: " + playerUUID, e);
            // Return empty map in case of error to prevent issues in calling code
            return new HashMap<>();
        }
        return counts;
    }

    /**
     * Inner class to represent a single entry in the punishment history.
     * Provides getters for accessing the details of the punishment record.
     */
    public static class PunishmentEntry {
        private final String type;
        private final String reason;
        private final Timestamp timestamp;
        private final String punisherName;
        private final long punishmentTime; // End time or 0/Long.MAX_VALUE
        private final String durationString; // User-friendly duration

        /**
         * Constructor for PunishmentEntry.
         *
         * @param type            Type of punishment/action.
         * @param reason          Reason for the action.
         * @param timestamp       Timestamp when the action occurred.
         * @param punisherName    Name of the executor.
         * @param punishmentTime  End time (ms) for temporary punishments, specific values for others.
         * @param durationString  User-friendly duration string.
         */
        public PunishmentEntry(String type, String reason, Timestamp timestamp, String punisherName, long punishmentTime, String durationString) {
            this.type = type;
            this.reason = reason;
            this.timestamp = timestamp;
            this.punisherName = punisherName;
            this.punishmentTime = punishmentTime;
            this.durationString = durationString;
        }

        // --- Getters ---
        public String getType() { return type; }
        public String getReason() { return reason; }
        public Timestamp getTimestamp() { return timestamp; }
        public String getPunisherName() { return punisherName; }
        public long getPunishmentTime() { return punishmentTime; }
        public String getDurationString() { return durationString; }
    }
}
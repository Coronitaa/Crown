// src/main/java/cp/corona/database/DatabaseManager.java
package cp.corona.database;

import cp.corona.config.WarnLevel;
import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date; // Keep this import
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

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
        startWarningExpiryCheckTask(); // New task for internal warnings
    }

    public Connection getConnection() throws SQLException {
        if ("mysql".equalsIgnoreCase(dbType)) {
            return DriverManager.getConnection(dbURL, this.dbUsername, this.dbPassword);
        } else {
            return DriverManager.getConnection(dbURL);
        }
    }

    private void initializeDatabase() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            String createSoftbansTableSQL = "CREATE TABLE IF NOT EXISTS softbans (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "endTime BIGINT NOT NULL," +
                    "reason TEXT)";
            statement.execute(createSoftbansTableSQL);

            String createMutesTableSQL = "CREATE TABLE IF NOT EXISTS mutes (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "endTime BIGINT NOT NULL," +
                    "reason TEXT)";
            statement.execute(createMutesTableSQL);

            String createHistoryTableSQL = "CREATE TABLE IF NOT EXISTS punishment_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "punishment_id VARCHAR(8) NOT NULL UNIQUE," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "punishment_type VARCHAR(50) NOT NULL," +
                    "reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "punisher_name VARCHAR(255)," +
                    "punishment_time BIGINT DEFAULT 0," +
                    "duration_string VARCHAR(50) DEFAULT 'permanent'," +
                    "active BOOLEAN DEFAULT 1," +
                    "removed_by_name VARCHAR(255)," +
                    "removed_at DATETIME," +
                    "removed_reason TEXT," +
                    "by_ip BOOLEAN DEFAULT 0)";

            if ("sqlite".equalsIgnoreCase(dbType)) {
                createHistoryTableSQL = "CREATE TABLE IF NOT EXISTS punishment_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "punishment_id VARCHAR(8) NOT NULL UNIQUE," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "punishment_type VARCHAR(50) NOT NULL," +
                        "reason TEXT," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "punisher_name VARCHAR(255)," +
                        "punishment_time BIGINT DEFAULT 0," +
                        "duration_string VARCHAR(50) DEFAULT 'permanent'," +
                        "active BOOLEAN DEFAULT 1," +
                        "removed_by_name VARCHAR(255)," +
                        "removed_at DATETIME," +
                        "removed_reason TEXT," +
                        "by_ip BOOLEAN DEFAULT 0)";
            }
            statement.execute(createHistoryTableSQL);

            String createPlayerInfoTableSQL = "CREATE TABLE IF NOT EXISTS player_info (" +
                    "punishment_id VARCHAR(8) PRIMARY KEY," +
                    "ip VARCHAR(45)," +
                    "location VARCHAR(255)," +
                    "gamemode VARCHAR(50)," +
                    "health DOUBLE," +
                    "hunger INT," +
                    "exp_level INT," +
                    "playtime BIGINT," +
                    "ping INT," +
                    "first_joined BIGINT," +
                    "last_joined BIGINT," +
                    "FOREIGN KEY(punishment_id) REFERENCES punishment_history(punishment_id))";

            if ("sqlite".equalsIgnoreCase(dbType)) {
                createPlayerInfoTableSQL = "CREATE TABLE IF NOT EXISTS player_info (" +
                        "punishment_id VARCHAR(8) PRIMARY KEY," +
                        "ip VARCHAR(45)," +
                        "location VARCHAR(255)," +
                        "gamemode VARCHAR(50)," +
                        "health DOUBLE," +
                        "hunger INT," +
                        "exp_level INT," +
                        "playtime BIGINT," +
                        "ping INT," +
                        "first_joined BIGINT," +
                        "last_joined BIGINT," +
                        "FOREIGN KEY(punishment_id) REFERENCES punishment_history(punishment_id))";
            }
            statement.execute(createPlayerInfoTableSQL);

            String createPlayerChatHistoryTableSQL = "CREATE TABLE IF NOT EXISTS player_chat_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "message TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
            if ("sqlite".equalsIgnoreCase(dbType)) {
                createPlayerChatHistoryTableSQL = "CREATE TABLE IF NOT EXISTS player_chat_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "message TEXT," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
            }
            statement.execute(createPlayerChatHistoryTableSQL);

            String createActiveWarningsTableSQL = "CREATE TABLE IF NOT EXISTS active_warnings (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "punishment_id VARCHAR(8) NOT NULL," +
                    "warn_level INT NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT NOT NULL," +
                    "is_paused BOOLEAN DEFAULT 0," +
                    "remaining_time_on_pause BIGINT DEFAULT 0," +
                    "FOREIGN KEY(punishment_id) REFERENCES punishment_history(punishment_id))";

            if ("sqlite".equalsIgnoreCase(dbType)) {
                createActiveWarningsTableSQL = "CREATE TABLE IF NOT EXISTS active_warnings (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "punishment_id VARCHAR(8) NOT NULL," +
                        "warn_level INT NOT NULL," +
                        "start_time BIGINT NOT NULL," +
                        "end_time BIGINT NOT NULL," +
                        "is_paused BOOLEAN DEFAULT 0," +
                        "remaining_time_on_pause BIGINT DEFAULT 0," +
                        "FOREIGN KEY(punishment_id) REFERENCES punishment_history(punishment_id))";
            }
            statement.execute(createActiveWarningsTableSQL);


            updateTableStructure(connection);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database!", e);
        }
    }

    private void updateTableStructure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!columnExists(connection, "punishment_history", "active")) {
                statement.execute("ALTER TABLE punishment_history ADD COLUMN active BOOLEAN DEFAULT 1");
            }
            if (!columnExists(connection, "punishment_history", "removed_by_name")) {
                statement.execute("ALTER TABLE punishment_history ADD COLUMN removed_by_name VARCHAR(255)");
            }
            if (!columnExists(connection, "punishment_history", "removed_at")) {
                statement.execute("ALTER TABLE punishment_history ADD COLUMN removed_at DATETIME");
            }
            if (!columnExists(connection, "punishment_history", "removed_reason")) {
                statement.execute("ALTER TABLE punishment_history ADD COLUMN removed_reason TEXT");
            }
            if (!columnExists(connection, "punishment_history", "by_ip")) {
                statement.execute("ALTER TABLE punishment_history ADD COLUMN by_ip BOOLEAN DEFAULT 0");
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private void startWarningExpiryCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<ActiveWarningEntry> expiredWarnings = new ArrayList<>();
                String sql = "SELECT * FROM active_warnings WHERE end_time <= ? AND end_time != -1 AND is_paused = 0";
                try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, System.currentTimeMillis());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        expiredWarnings.add(new ActiveWarningEntry(
                                rs.getInt("id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("punishment_id"),
                                rs.getInt("warn_level"),
                                rs.getLong("end_time"),
                                rs.getBoolean("is_paused"),
                                rs.getLong("remaining_time_on_pause")
                        ));
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error fetching expired warnings", e);
                }

                if (!expiredWarnings.isEmpty()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (ActiveWarningEntry warning : expiredWarnings) {
                                handleWarningExpiration(warning);
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 10, 20L * 30); // Check every 30 seconds
    }

    private void handleWarningExpiration(ActiveWarningEntry warning) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(warning.getPlayerUUID());
        WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(warning.getWarnLevel());
        if (levelConfig != null) {
            List<MenuItem.ClickActionData> actions = levelConfig.getOnExpireActions();
            if (plugin.getMenuListener() != null) {
                plugin.getMenuListener().executeHookActions(Bukkit.getConsoleSender(), target, "warn-expire", "N/A", "Expired", true, actions);
            }
        }
        removeActiveWarning(warning.getPlayerUUID(), warning.getPunishmentId(), "System", "Expired");
    }

    public void addActiveWarning(UUID playerUUID, String punishmentId, int warnLevel, long endTime) {
        if ("incremental".equals(plugin.getConfigManager().getWarnExpirationMode())) {
            pauseLatestActiveWarning(playerUUID);
        }

        String sql = "INSERT INTO active_warnings (player_uuid, punishment_id, warn_level, start_time, end_time) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentId);
            ps.setInt(3, warnLevel);
            ps.setLong(4, System.currentTimeMillis());
            ps.setLong(5, endTime);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not add active warning for " + playerUUID, e);
        }
    }

    public void removeActiveWarning(UUID playerUUID, String punishmentId, String removerName, String reason) {
        String sql = "DELETE FROM active_warnings WHERE punishment_id = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            if (ps.executeUpdate() > 0) {
                updatePunishmentAsRemoved(punishmentId, removerName, reason);
                if ("incremental".equals(plugin.getConfigManager().getWarnExpirationMode())) {
                    resumeLatestPausedWarning(playerUUID);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not remove active warning for punishmentId: " + punishmentId, e);
        }
    }

    public int getActiveWarningCount(UUID playerUUID) {
        String sql = "SELECT COUNT(*) FROM active_warnings WHERE player_uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not count active warnings for " + playerUUID, e);
        }
        return 0;
    }

    public ActiveWarningEntry getLatestActiveWarning(UUID playerUUID) {
        String sql = "SELECT * FROM active_warnings WHERE player_uuid = ? AND is_paused = 0 ORDER BY start_time DESC LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new ActiveWarningEntry(
                        rs.getInt("id"),
                        playerUUID,
                        rs.getString("punishment_id"),
                        rs.getInt("warn_level"),
                        rs.getLong("end_time"),
                        false, 0
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get latest active warning for " + playerUUID, e);
        }
        return null;
    }

    private void pauseLatestActiveWarning(UUID playerUUID) {
        ActiveWarningEntry latest = getLatestActiveWarning(playerUUID);
        if (latest == null) return;

        WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(latest.getWarnLevel());
        if (levelConfig != null && plugin.getMenuListener() != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerUUID);
            plugin.getMenuListener().executeHookActions(Bukkit.getConsoleSender(), target, "warn-pause", "N/A", "Warning Paused", true, levelConfig.getOnExpireActions());
        }

        long remainingTime = latest.getEndTime() == -1 ? -1 : latest.getEndTime() - System.currentTimeMillis();
        if (remainingTime <= 0 && latest.getEndTime() != -1) return;

        String sql = "UPDATE active_warnings SET is_paused = 1, remaining_time_on_pause = ? WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, remainingTime);
            ps.setInt(2, latest.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not pause warning for " + playerUUID, e);
        }
    }

    private void resumeLatestPausedWarning(UUID playerUUID) {
        String sqlSelect = "SELECT * FROM active_warnings WHERE player_uuid = ? AND is_paused = 1 ORDER BY start_time DESC LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement psSelect = connection.prepareStatement(sqlSelect)) {
            psSelect.setString(1, playerUUID.toString());
            ResultSet rs = psSelect.executeQuery();

            if (rs.next()) {
                ActiveWarningEntry warningToResume = new ActiveWarningEntry(
                        rs.getInt("id"), playerUUID, rs.getString("punishment_id"),
                        rs.getInt("warn_level"), rs.getLong("end_time"), true,
                        rs.getLong("remaining_time_on_pause")
                );

                long newEndTime = (warningToResume.getRemainingTimeOnPause() == -1) ? -1 : System.currentTimeMillis() + warningToResume.getRemainingTimeOnPause();

                String sqlUpdate = "UPDATE active_warnings SET is_paused = 0, end_time = ?, remaining_time_on_pause = 0 WHERE id = ?";
                try (PreparedStatement psUpdate = connection.prepareStatement(sqlUpdate)) {
                    psUpdate.setLong(1, newEndTime);
                    psUpdate.setInt(2, warningToResume.getId());
                    psUpdate.executeUpdate();
                }

                WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(warningToResume.getWarnLevel());
                if (levelConfig != null && plugin.getMenuListener() != null) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(playerUUID);
                    plugin.getMenuListener().executeHookActions(Bukkit.getConsoleSender(), target, "warn-resume", "N/A", "Warning Resumed", false, levelConfig.getOnWarnActions());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not resume warning for " + playerUUID, e);
        }
    }

    public List<ActiveWarningEntry> getAllActiveAndPausedWarnings(UUID playerUUID) {
        List<ActiveWarningEntry> warnings = new ArrayList<>();
        String sql = "SELECT * FROM active_warnings WHERE player_uuid = ? ORDER BY start_time DESC";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                warnings.add(new ActiveWarningEntry(
                        rs.getInt("id"),
                        playerUUID,
                        rs.getString("punishment_id"),
                        rs.getInt("warn_level"),
                        rs.getLong("end_time"),
                        rs.getBoolean("is_paused"),
                        rs.getLong("remaining_time_on_pause")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get all active/paused warnings for " + playerUUID, e);
        }
        return warnings;
    }

    public ActiveWarningEntry getActiveWarningByPunishmentId(String punishmentId) {
        String sql = "SELECT * FROM active_warnings WHERE punishment_id = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new ActiveWarningEntry(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("punishment_id"),
                        rs.getInt("warn_level"),
                        rs.getLong("end_time"),
                        rs.getBoolean("is_paused"),
                        rs.getLong("remaining_time_on_pause")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get active warning by punishment ID: " + punishmentId, e);
        }
        return null;
    }

    // ... (El resto del archivo, que ya te proporcioné anteriormente, se mantiene igual)

    public List<PunishmentEntry> getAllActivePunishments(UUID playerUUID, String playerIP) {
        List<PunishmentEntry> activePunishments = new ArrayList<>();
        String sql = "SELECT * FROM punishment_history WHERE (player_uuid = ? OR (by_ip = 1 AND punishment_id IN (SELECT punishment_id FROM player_info WHERE ip = ?))) AND active = 1 AND (punishment_time > ? OR punishment_time = ?)";

        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, playerIP);
            ps.setLong(3, System.currentTimeMillis());
            ps.setLong(4, Long.MAX_VALUE);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    activePunishments.add(new PunishmentEntry(
                            rs.getString("punishment_id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("punishment_type"),
                            rs.getString("reason"),
                            rs.getTimestamp("timestamp"),
                            rs.getString("punisher_name"),
                            rs.getLong("punishment_time"),
                            rs.getString("duration_string"),
                            rs.getBoolean("active"),
                            rs.getString("removed_by_name"),
                            rs.getTimestamp("removed_at"),
                            rs.getString("removed_reason"),
                            rs.getBoolean("by_ip")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving all active punishments!", e);
        }
        return activePunishments;
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

    public String softBanPlayer(UUID uuid, long endTime, String reason, String punisherName, boolean byIp) {
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
            String punishmentId = logPunishment(uuid, "softban", reason, punisherName, finalEndTime, durationString, byIp);

            if (finalEndTime != Long.MAX_VALUE) {
                scheduleExpiryNotification(uuid, finalEndTime, "softban", punishmentId);
            }

            Player targetPlayer = Bukkit.getPlayer(uuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned", "{time}", durationString, "{reason}", reason, "{punishment_id}", punishmentId);
                targetPlayer.sendMessage(softbanMessage);
            }
            return punishmentId;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database operation failed while softbanning player!", e);
        }
        return null;
    }

    public String unSoftBanPlayer(UUID uuid, String punisherName, String reason) {
        String activePunishmentId = getLatestActivePunishmentId(uuid, "softban");
        if (activePunishmentId == null) {
            return null; // No active softban to remove
        }
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM softbans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            if (ps.executeUpdate() > 0) {
                updatePunishmentAsRemoved(activePunishmentId, punisherName, reason);
                sendRemovalNotification(uuid, "softban");
                return activePunishmentId;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not un-soft ban player!", e);
        }
        return null;
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

    public String mutePlayer(UUID uuid, long endTime, String reason, String punisherName, boolean byIp) {
        long currentEndTime = getMuteEndTime(uuid);
        long finalEndTime = (endTime == Long.MAX_VALUE || currentEndTime <= System.currentTimeMillis() || currentEndTime == Long.MAX_VALUE)
                ? endTime
                : currentEndTime + (endTime - System.currentTimeMillis());

        String sql = "mysql".equalsIgnoreCase(dbType) ? "REPLACE INTO mutes (uuid, endTime, reason) VALUES (?, ?, ?)" : "INSERT OR REPLACE INTO mutes (uuid, endTime, reason) VALUES (?, ?, ?)";

        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, finalEndTime);
            ps.setString(3, reason);
            ps.executeUpdate();
            String durationString = (finalEndTime == Long.MAX_VALUE)
                    ? plugin.getConfigManager().getMessage("placeholders.permanent_time_display")
                    : TimeUtils.formatTime((int) ((finalEndTime - System.currentTimeMillis()) / 1000), plugin.getConfigManager());

            String punishmentId = logPunishment(uuid, "mute", reason, punisherName, finalEndTime, durationString, byIp);

            if (finalEndTime != Long.MAX_VALUE) {
                scheduleExpiryNotification(uuid, finalEndTime, "mute", punishmentId);
            }

            return punishmentId;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database operation failed while muting player!", e);
        }
        return null;
    }

    public String unmutePlayer(UUID uuid, String punisherName, String reason) {
        String activePunishmentId = getLatestActivePunishmentId(uuid, "mute");
        if (activePunishmentId == null) {
            return null; // No active mute to remove
        }
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM mutes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            if (ps.executeUpdate() > 0) {
                updatePunishmentAsRemoved(activePunishmentId, punisherName, reason);
                sendRemovalNotification(uuid, "mute");
                return activePunishmentId;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not unmute player!", e);
        }
        return null;
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

    public String logPunishment(UUID playerUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp) {
        if (!"warn".equalsIgnoreCase(punishmentType)) {
            PunishmentEntry activePunishment = getLatestActivePunishment(playerUUID, punishmentType);
            if (activePunishment != null && (activePunishment.getEndTime() > System.currentTimeMillis() || activePunishment.getEndTime() == Long.MAX_VALUE)) {
                updatePunishmentAsRemoved(activePunishment.getPunishmentId(), "System", "Superseded by new punishment.");
            }
        }

        String punishmentId = generatePunishmentId();
        String sql = "INSERT INTO punishment_history (punishment_id, player_uuid, punishment_type, reason, punisher_name, punishment_time, duration_string, active, by_ip) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            ps.setString(2, playerUUID.toString());
            ps.setString(3, punishmentType);
            ps.setString(4, reason);
            ps.setString(5, punisherName);
            ps.setLong(6, punishmentEndTime);
            ps.setString(7, durationString);
            ps.setBoolean(8, true);
            ps.setBoolean(9, byIp);
            ps.executeUpdate();

            Player targetPlayer = Bukkit.getPlayer(playerUUID);
            if (targetPlayer != null) {
                logPlayerInfo(punishmentId, targetPlayer);
            } else {
                logPlayerInfo(punishmentId, playerUUID, getLastKnownIp(playerUUID));
            }

            return punishmentId;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error logging punishment!", e);
        }
        return null;
    }

    public void logPlayerInfo(String punishmentId, Player player) {
        logPlayerInfo(punishmentId, player.getUniqueId(), player.getAddress().getAddress().getHostAddress());
    }

    private void logPlayerInfo(String punishmentId, UUID playerUUID, String ipAddress) {
        String sql = "INSERT INTO player_info (punishment_id, ip, location, gamemode, health, hunger, exp_level, playtime, ping, first_joined, last_joined) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            Player player = Bukkit.getPlayer(playerUUID);
            ps.setString(1, punishmentId);
            ps.setString(2, ipAddress);
            if (player != null) {
                ps.setString(3, player.getWorld().getName() + "," + player.getLocation().getX() + "," + player.getLocation().getY() + "," + player.getLocation().getZ());
                ps.setString(4, player.getGameMode().toString());
                ps.setDouble(5, player.getHealth());
                ps.setInt(6, player.getFoodLevel());
                ps.setInt(7, player.getLevel());
                ps.setLong(8, player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE));
                ps.setInt(9, player.getPing());
                ps.setLong(10, player.getFirstPlayed());
                ps.setLong(11, player.getLastPlayed());
            } else {
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.VARCHAR);
                ps.setNull(5, Types.DOUBLE);
                ps.setNull(6, Types.INTEGER);
                ps.setNull(7, Types.INTEGER);
                ps.setNull(8, Types.BIGINT);
                ps.setNull(9, Types.INTEGER);
                ps.setLong(10, Bukkit.getOfflinePlayer(playerUUID).getFirstPlayed());
                ps.setLong(11, Bukkit.getOfflinePlayer(playerUUID).getLastPlayed());
            }


            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error logging player info!", e);
        }
    }


    public PlayerInfo getPlayerInfo(String punishmentId) {
        String sql = "SELECT * FROM player_info WHERE punishment_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerInfo(
                            rs.getString("punishment_id"),
                            rs.getString("ip"),
                            rs.getString("location"),
                            rs.getString("gamemode"),
                            rs.getDouble("health"),
                            rs.getInt("hunger"),
                            rs.getInt("exp_level"),
                            rs.getLong("playtime"),
                            rs.getInt("ping"),
                            rs.getLong("first_joined"),
                            rs.getLong("last_joined")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving player info!", e);
        }
        return null;
    }

    public List<PunishmentEntry> getPunishmentHistory(UUID playerUUID, int page, int entriesPerPage) {
        List<PunishmentEntry> history = new ArrayList<>();
        int offset = (page - 1) * entriesPerPage;
        String sql = "SELECT * FROM punishment_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, entriesPerPage);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new PunishmentEntry(
                            rs.getString("punishment_id"),
                            playerUUID,
                            rs.getString("punishment_type"),
                            rs.getString("reason"),
                            rs.getTimestamp("timestamp"),
                            rs.getString("punisher_name"),
                            rs.getLong("punishment_time"),
                            rs.getString("duration_string"),
                            rs.getBoolean("active"),
                            rs.getString("removed_by_name"),
                            rs.getTimestamp("removed_at"),
                            rs.getString("removed_reason"),
                            rs.getBoolean("by_ip")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving punishment history!", e);
        }
        return history;
    }
    public PunishmentEntry getPunishmentById(String punishmentId) {
        String sql = "SELECT * FROM punishment_history WHERE punishment_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PunishmentEntry(
                            rs.getString("punishment_id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("punishment_type"),
                            rs.getString("reason"),
                            rs.getTimestamp("timestamp"),
                            rs.getString("punisher_name"),
                            rs.getLong("punishment_time"),
                            rs.getString("duration_string"),
                            rs.getBoolean("active"),
                            rs.getString("removed_by_name"),
                            rs.getTimestamp("removed_at"),
                            rs.getString("removed_reason"),
                            rs.getBoolean("by_ip")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving punishment by ID!", e);
        }
        return null;
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

    private String generatePunishmentId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder idBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            idBuilder.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return idBuilder.toString();
    }

    public String getLatestPunishmentId(UUID playerUUID, String punishmentType) {
        String sql = "SELECT punishment_id FROM punishment_history WHERE player_uuid = ? AND punishment_type = ? ORDER BY timestamp DESC LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("punishment_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving latest punishment ID!", e);
        }
        return null;
    }
    public String getLatestActivePunishmentId(UUID playerUUID, String punishmentType) {
        String sql = "SELECT punishment_id FROM punishment_history WHERE player_uuid = ? AND punishment_type = ? AND active = 1 ORDER BY timestamp DESC LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("punishment_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving latest active punishment ID!", e);
        }
        return null;
    }

    public PunishmentEntry getLatestActivePunishment(UUID playerUUID, String punishmentType) {
        String sql = "SELECT * FROM punishment_history WHERE player_uuid = ? AND punishment_type = ? AND active = 1 ORDER BY timestamp DESC LIMIT 1";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PunishmentEntry(
                            rs.getString("punishment_id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("punishment_type"),
                            rs.getString("reason"),
                            rs.getTimestamp("timestamp"),
                            rs.getString("punisher_name"),
                            rs.getLong("punishment_time"),
                            rs.getString("duration_string"),
                            rs.getBoolean("active"),
                            rs.getString("removed_by_name"),
                            rs.getTimestamp("removed_at"),
                            rs.getString("removed_reason"),
                            rs.getBoolean("by_ip")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving latest active punishment!", e);
        }
        return null;
    }

    public String getLastKnownIp(UUID playerUUID) {
        String sql = "SELECT ip FROM player_info pi JOIN punishment_history ph ON pi.punishment_id = ph.punishment_id WHERE ph.player_uuid = ? ORDER BY ph.timestamp DESC LIMIT 1";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ip");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving last known IP!", e);
        }
        return null;
    }

    public PunishmentEntry getLatestActivePunishmentByIp(String ip, String punishmentType) {
        String sql = "SELECT ph.* FROM punishment_history ph " +
                "JOIN player_info pi ON ph.punishment_id = pi.punishment_id " +
                "WHERE pi.ip = ? AND ph.punishment_type = ? AND ph.active = 1 " +
                "ORDER BY ph.timestamp DESC LIMIT 1";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, punishmentType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PunishmentEntry(
                            rs.getString("punishment_id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("punishment_type"),
                            rs.getString("reason"),
                            rs.getTimestamp("timestamp"),
                            rs.getString("punisher_name"),
                            rs.getLong("punishment_time"),
                            rs.getString("duration_string"),
                            rs.getBoolean("active"),
                            rs.getString("removed_by_name"),
                            rs.getTimestamp("removed_at"),
                            rs.getString("removed_reason"),
                            rs.getBoolean("by_ip")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving latest active punishment by IP!", e);
        }
        return null;
    }
    public boolean updatePunishmentAsRemoved(String punishmentId, String removedByName, String removedReason) {
        String sql = "UPDATE punishment_history SET active = 0, removed_by_name = ?, removed_reason = ?, removed_at = ? WHERE punishment_id = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, removedByName);
            ps.setString(2, removedReason);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, punishmentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update punishment status", e);
            return false;
        }
    }

    private void scheduleExpiryNotification(UUID uuid, long endTime, String punishmentType, String punishmentId) {
        long durationMillis = endTime - System.currentTimeMillis();
        if (durationMillis <= 0) return; // Expired instantly or in the past
        long durationTicks = durationMillis / 50; // Convert millis to server ticks

        new BukkitRunnable() {
            @Override
            public void run() {
                // This task runs ASYNC when the punishment should expire
                String table = punishmentType.equals("mute") ? "mutes" : "softbans";
                long currentEndTimeInDb = getPunishmentEndTime(table, uuid); // DB call, safe in async

                // If 0 (no longer punished) or times don't match (superseded by new punishment)
                if (currentEndTimeInDb == 0 || currentEndTimeInDb != endTime) {
                    return; // Do nothing, it was manually removed or changed
                }

                boolean removed = false;
                try (Connection connection = getConnection();
                     PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    if (ps.executeUpdate() > 0) {
                        removed = true;
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not remove expired " + punishmentType, e);
                }

                if (removed) {
                    // Mark as inactive in history (also async)
                    updatePunishmentAsRemoved(punishmentId, "System", "Expired");

                    // Schedule the sync task for notification
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                            if (offlinePlayer.isOnline()) {
                                Player player = offlinePlayer.getPlayer();
                                if (player != null) {
                                    String messageKey = "messages." + punishmentType + "_expired";
                                    String message = plugin.getConfigManager().getMessage(messageKey);
                                    player.sendMessage(MessageUtils.getColorMessage(message));

                                    String soundName = plugin.getConfigManager().getSoundName("punishment_expired");
                                    if (soundName != null && !soundName.isEmpty()) {
                                        try {
                                            Sound sound = Sound.valueOf(soundName.toUpperCase());
                                            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                                        } catch (IllegalArgumentException e) {
                                            plugin.getLogger().warning("Invalid sound name configured for punishment_expired: " + soundName);
                                        }
                                    }
                                }
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskLaterAsynchronously(plugin, durationTicks);
    }

    private void sendRemovalNotification(UUID uuid, String punishmentType) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.isOnline()) {
            Player player = offlinePlayer.getPlayer();
            if (player != null) {
                String messageKey = "messages." + (punishmentType.equals("mute") ? "unmute_notification" : "unsoftban_notification");
                String message = plugin.getConfigManager().getMessage(messageKey);
                player.sendMessage(MessageUtils.getColorMessage(message));

                String soundName = plugin.getConfigManager().getSoundName("punishment_removed");
                if (soundName != null && !soundName.isEmpty()) {
                    try {
                        Sound sound = Sound.valueOf(soundName.toUpperCase());
                        player.playSound(player.getLocation(), sound, 1.0f, 1.2f); // Slightly higher pitch
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound name configured for punishment_removed: " + soundName);
                    }
                }
            }
        }
    }

    public List<String> getChatHistory(UUID playerUUID, int limit) {
        List<String> chatHistory = new ArrayList<>();
        String sql = "SELECT message, timestamp FROM player_chat_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chatHistory.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(rs.getTimestamp("timestamp")) + " - " + rs.getString("message"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving chat history!", e);
        }
        return chatHistory;
    }

    public List<String> getPlayersByIp(String ip) {
        List<String> players = new ArrayList<>();
        String sql = "SELECT DISTINCT player_uuid FROM punishment_history WHERE punishment_id IN (SELECT punishment_id FROM player_info WHERE ip = ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    players.add(Bukkit.getOfflinePlayer(playerUUID).getName());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving players by IP!", e);
        }
        return players;
    }


    public static class PunishmentEntry {
        private final String punishmentId;
        private final UUID playerUUID;
        private final String type;
        private final String reason;
        private final Timestamp timestamp;
        private final String punisherName;
        private final long punishmentTime;
        private final String durationString;
        private String status;
        private final boolean active;
        private final String removedByName;
        private final Timestamp removedAt;
        private final String removedReason;
        private final boolean byIp;

        public PunishmentEntry(String punishmentId, UUID playerUUID, String type, String reason, Timestamp timestamp, String punisherName, long punishmentTime, String durationString, boolean active, String removedByName, Timestamp removedAt, String removedReason, boolean byIp) {
            this.punishmentId = punishmentId;
            this.playerUUID = playerUUID;
            this.type = type;
            this.reason = reason;
            this.timestamp = timestamp;
            this.punisherName = punisherName;
            this.punishmentTime = punishmentTime;
            this.durationString = durationString;
            this.active = active;
            this.removedByName = removedByName;
            this.removedAt = removedAt;
            this.removedReason = removedReason;
            this.byIp = byIp;
        }

        public String getPunishmentId() { return punishmentId; }
        public String getType() { return type; }
        public String getReason() { return reason; }
        public Timestamp getTimestamp() { return timestamp; }
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPunisherName() { return punisherName; }
        public long getPunishmentTime() { return punishmentTime; }
        public String getDurationString() { return durationString; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getEndTime() { return punishmentTime; }
        public boolean isActive() { return active; }
        public String getRemovedByName() { return removedByName; }
        public Timestamp getRemovedAt() { return removedAt; }
        public String getRemovedReason() { return removedReason; }
        public boolean wasByIp() { return byIp; }
    }
    public static class PlayerInfo {
        private final String punishmentId;
        private final String ip;
        private final String location;
        private final String gamemode;
        private final double health;
        private final int hunger;
        private final int expLevel;
        private final long playtime;
        private final int ping;
        private final long firstJoined;
        private final long lastJoined;


        public PlayerInfo(String punishmentId, String ip, String location, String gamemode, double health, int hunger, int expLevel, long playtime, int ping, long firstJoined, long lastJoined) {
            this.punishmentId = punishmentId;
            this.ip = ip;
            this.location = location;
            this.gamemode = gamemode;
            this.health = health;
            this.hunger = hunger;
            this.expLevel = expLevel;
            this.playtime = playtime;
            this.ping = ping;
            this.firstJoined = firstJoined;
            this.lastJoined = lastJoined;
        }

        public String getPunishmentId() {
            return punishmentId;
        }

        public String getIp() {
            return ip;
        }

        public String getLocation() {
            return location;
        }

        public String getGamemode() {
            return gamemode;
        }

        public double getHealth() {
            return health;
        }

        public int getHunger() {
            return hunger;
        }

        public int getExpLevel() {
            return expLevel;
        }

        public long getPlaytime() {
            return playtime;
        }

        public int getPing() {
            return ping;
        }

        public long getFirstJoined() {
            return firstJoined;
        }

        public long getLastJoined() {
            return lastJoined;
        }
    }
}
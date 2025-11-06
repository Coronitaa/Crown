package cp.corona.database;

import cp.corona.config.WarnLevel;
import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.BanList;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DatabaseManager {
    private final Crown plugin;
    private final String dbURL;
    private final String dbUsername;
    private final String dbPassword;
    private final String dbType;
    private static final String COMMAND_DELIMITER = ";;";

    public DatabaseManager(Crown plugin) {
        this.plugin = plugin;
        this.dbType = plugin.getConfigManager().getDatabaseType();

        // Manually load the SQLite driver to prevent "No suitable driver found" errors.
        if ("sqlite".equalsIgnoreCase(dbType)) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found! The plugin will not be able to connect to the database.", e);
            }
        }

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

        CompletableFuture.runAsync(this::initializeDatabase)
                .thenRun(() -> {
                    startExpiryCheckTask();
                    startMuteExpiryCheckTask();
                    startWarningExpiryCheckTask();
                    startBanExpiryCheckTask(); // ADDED
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to initialize database!", ex);
                    return null;
                });
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
                    "reason TEXT," +
                    "custom_commands TEXT)";
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
                    "by_ip BOOLEAN DEFAULT 0," +
                    "warn_level INT DEFAULT 0)";

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
                        "by_ip BOOLEAN DEFAULT 0," +
                        "warn_level INT DEFAULT 0)";
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
                    "associated_punishment_ids TEXT," +
                    "paused_associated_punishments TEXT," +
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
                        "associated_punishment_ids TEXT," +
                        "paused_associated_punishments TEXT," +
                        "FOREIGN KEY(punishment_id) REFERENCES punishment_history(punishment_id))";
            }
            statement.execute(createActiveWarningsTableSQL);

            String createPlayerLastStateTableSQL = "CREATE TABLE IF NOT EXISTS player_last_state (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "last_seen BIGINT NOT NULL," +
                    "ip VARCHAR(45)," +
                    "location VARCHAR(255)," +
                    "world VARCHAR(255))";
            statement.execute(createPlayerLastStateTableSQL);


            updateTableStructure(connection);

        } catch (SQLException e) {
            throw new RuntimeException("Could not initialize database!", e);
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
            if (!columnExists(connection, "punishment_history", "warn_level")) {
                statement.execute("ALTER TABLE punishment_history ADD COLUMN warn_level INT DEFAULT 0");
            }
            if (!columnExists(connection, "softbans", "custom_commands")) {
                statement.execute("ALTER TABLE softbans ADD COLUMN custom_commands TEXT");
            }
            if (!columnExists(connection, "active_warnings", "associated_punishment_ids")) {
                statement.execute("ALTER TABLE active_warnings ADD COLUMN associated_punishment_ids TEXT");
            }
            if (!columnExists(connection, "active_warnings", "paused_associated_punishments")) { // NEW
                statement.execute("ALTER TABLE active_warnings ADD COLUMN paused_associated_punishments TEXT");
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    // ASYNC EXECUTION METHODS START HERE

    public CompletableFuture<String> executePunishmentAsync(UUID targetUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp, List<String> customCommands) {
        return executePunishmentAsync(targetUUID, punishmentType, reason, punisherName, punishmentEndTime, durationString, byIp, customCommands, 0);
    }

    public CompletableFuture<String> executePunishmentAsync(UUID targetUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp, List<String> customCommands, int warnLevel) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                // Deactivate previous similar punishment if necessary, only for internal punishments
                boolean isInternal = plugin.getConfigManager().isPunishmentInternal(punishmentType);
                if (isInternal && !"warn".equalsIgnoreCase(punishmentType)) {
                    PunishmentEntry activePunishment = getLatestActivePunishment(connection, targetUUID, punishmentType);
                    if (activePunishment != null && (activePunishment.getEndTime() > System.currentTimeMillis() || activePunishment.getEndTime() == Long.MAX_VALUE)) {
                        updatePunishmentAsRemoved(connection, activePunishment.getPunishmentId(), "System", "Superseded by new punishment.");
                    }
                }

                String punishmentId = logPunishment(connection, targetUUID, punishmentType, reason, punisherName, punishmentEndTime, durationString, byIp, warnLevel);

                // Handle specific punishment table updates for internal punishments
                if (isInternal) {
                    switch (punishmentType.toLowerCase()) {
                        case "softban":
                            softBanPlayer(connection, targetUUID, punishmentEndTime, reason, customCommands);
                            scheduleExpiryNotification(targetUUID, punishmentEndTime, "softban", punishmentId);
                            break;
                        case "mute":
                            mutePlayer(connection, targetUUID, punishmentEndTime, reason);
                            scheduleExpiryNotification(targetUUID, punishmentEndTime, "mute", punishmentId);
                            break;
                    }
                }
                return punishmentId;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Async punishment execution failed!", e);
                throw new RuntimeException(e);
            }
        });
    }


    public CompletableFuture<String> executeUnpunishmentAsync(UUID targetUUID, String punishmentType, String punisherName, String reason, String punishmentIdToUpdate) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String punishmentId = punishmentIdToUpdate;
                if (punishmentId == null) {
                    punishmentId = getLatestActivePunishmentId(connection, targetUUID, punishmentType);
                } else {
                    // Failsafe check: if an ID is provided, verify it's active before proceeding.
                    PunishmentEntry entry = getPunishmentById(punishmentId);
                    if (entry == null || !entry.isActive()) {
                        return null; // The provided punishment ID is not active.
                    }
                }

                if (punishmentId == null) {
                    return null; // No active punishment to remove
                }

                updatePunishmentAsRemoved(connection, punishmentId, punisherName, reason);

                // Handle specific table cleanups
                boolean isInternal = plugin.getConfigManager().isPunishmentInternal(punishmentType);
                if (isInternal) {
                    switch (punishmentType.toLowerCase()) {
                        case "softban":
                            unSoftBanPlayer(connection, targetUUID);
                            break;
                        case "mute":
                            unmutePlayer(connection, targetUUID);
                            break;
                        case "warn":
                            // Warn removal logic is more complex and handled separately
                            break;
                    }
                }
                return punishmentId;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Async unpunishment execution failed!", e);
                throw new RuntimeException(e);
            }
        });
    }

    // ASYNC EXECUTION METHODS END

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
                                rs.getLong("remaining_time_on_pause"),
                                rs.getString("associated_punishment_ids")
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
                plugin.getMenuListener().executeHookActions(Bukkit.getConsoleSender(), target, "warn-expire", "N/A", "Expired", true, actions, warning);
            }
        }
        removeActiveWarning(warning.getPlayerUUID(), warning.getPunishmentId(), "System", "Expired");
    }

    public CompletableFuture<Void> addActiveWarning(UUID playerUUID, String punishmentId, int warnLevel, long endTime) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection()) {
                String mode = plugin.getConfigManager().getWarnExpirationMode();
                if ("unique".equals(mode)) {
                    ActiveWarningEntry latest = getLatestActiveWarning(connection, playerUUID);
                    if (latest != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(latest.getWarnLevel());
                            if (levelConfig != null && plugin.getMenuListener() != null) {
                                plugin.getMenuListener().executeHookActions(Bukkit.getConsoleSender(), Bukkit.getOfflinePlayer(playerUUID), "warn-expire", "N/A", "Superseded", true, levelConfig.getOnExpireActions(), latest);
                            }
                        });
                        removeActiveWarning(connection, playerUUID, latest.getPunishmentId(), "System", "Superseded by new warning.");
                    }
                } else if ("incremental".equals(mode)) {
                    pauseLatestActiveWarning(connection, playerUUID);
                }

                String sql = "INSERT INTO active_warnings (player_uuid, punishment_id, warn_level, start_time, end_time) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerUUID.toString());
                    ps.setString(2, punishmentId);
                    ps.setInt(3, warnLevel);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.setLong(5, endTime);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add active warning for " + playerUUID, e);
                throw new RuntimeException(e);
            }
        });
    }


    public void removeActiveWarning(UUID playerUUID, String punishmentId, String removerName, String reason) {
        CompletableFuture.runAsync(() -> {
            try(Connection connection = getConnection()){
                removeActiveWarning(connection, playerUUID, punishmentId, removerName, reason);
            } catch(SQLException e){
                plugin.getLogger().log(Level.SEVERE, "Could not remove active warning for punishmentId: " + punishmentId, e);
            }
        });
    }

    private void removeActiveWarning(Connection connection, UUID playerUUID, String punishmentId, String removerName, String reason) throws SQLException {
        ActiveWarningEntry warningToRemove = getActiveWarningByPunishmentId(punishmentId);
        if (warningToRemove != null && warningToRemove.getAssociatedPunishmentIds() != null) {
            for (String pair : warningToRemove.getAssociatedPunishmentIds().split(";")) {
                if (pair.contains(":")) {
                    String associatedId = pair.split(":")[1];
                    updatePunishmentAsRemoved(connection, associatedId, removerName, "Associated warning removed.");
                }
            }
        }

        String sql = "DELETE FROM active_warnings WHERE punishment_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            if (ps.executeUpdate() > 0) {
                updatePunishmentAsRemoved(connection, punishmentId, removerName, reason);
                if ("incremental".equals(plugin.getConfigManager().getWarnExpirationMode())) {
                    resumeLatestPausedWarning(connection, playerUUID);
                }
            }
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
        try (Connection connection = getConnection()) {
            return getLatestActiveWarning(connection, playerUUID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get latest active warning for " + playerUUID, e);
        }
        return null;
    }

    private ActiveWarningEntry getLatestActiveWarning(Connection connection, UUID playerUUID) throws SQLException {
        String sql = "SELECT * FROM active_warnings WHERE player_uuid = ? AND is_paused = 0 ORDER BY start_time DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new ActiveWarningEntry(
                        rs.getInt("id"),
                        playerUUID,
                        rs.getString("punishment_id"),
                        rs.getInt("warn_level"),
                        rs.getLong("end_time"),
                        false, 0,
                        rs.getString("associated_punishment_ids")
                );
            }
        }
        return null;
    }

    private void pauseLatestActiveWarning(Connection connection, UUID playerUUID) throws SQLException {
        ActiveWarningEntry latest = getLatestActiveWarning(connection, playerUUID);
        if (latest == null) return;

        pauseAssociatedPunishments(connection, latest);

        long remainingTime = latest.getEndTime() == -1 ? -1 : latest.getEndTime() - System.currentTimeMillis();
        if (remainingTime <= 0 && latest.getEndTime() != -1) {
            // If already expired, just handle expiration
            Bukkit.getScheduler().runTask(plugin, () -> handleWarningExpiration(latest));
            return;
        }

        String sql = "UPDATE active_warnings SET is_paused = 1, remaining_time_on_pause = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, remainingTime);
            ps.setInt(2, latest.getId());
            ps.executeUpdate();
        }
    }

    private void resumeLatestPausedWarning(Connection connection, UUID playerUUID) throws SQLException {
        String sqlSelect = "SELECT * FROM active_warnings WHERE player_uuid = ? AND is_paused = 1 ORDER BY start_time DESC LIMIT 1";
        try (PreparedStatement psSelect = connection.prepareStatement(sqlSelect)) {
            psSelect.setString(1, playerUUID.toString());
            ResultSet rs = psSelect.executeQuery();

            if (rs.next()) {
                ActiveWarningEntry warningToResume = new ActiveWarningEntry(
                        rs.getInt("id"), playerUUID, rs.getString("punishment_id"),
                        rs.getInt("warn_level"), rs.getLong("end_time"), true,
                        rs.getLong("remaining_time_on_pause"),
                        rs.getString("associated_punishment_ids")
                );

                long newEndTime = (warningToResume.getRemainingTimeOnPause() == -1) ? -1 : System.currentTimeMillis() + warningToResume.getRemainingTimeOnPause();

                String sqlUpdate = "UPDATE active_warnings SET is_paused = 0, end_time = ?, remaining_time_on_pause = 0 WHERE id = ?";
                try (PreparedStatement psUpdate = connection.prepareStatement(sqlUpdate)) {
                    psUpdate.setLong(1, newEndTime);
                    psUpdate.setInt(2, warningToResume.getId());
                    psUpdate.executeUpdate();
                }

                resumeAssociatedPunishments(connection, warningToResume);
            }
        }
    }

    private void pauseAssociatedPunishments(Connection connection, ActiveWarningEntry warning) throws SQLException {
        String associatedIds = warning.getAssociatedPunishmentIds();
        if (associatedIds == null || associatedIds.isEmpty()) return;

        for (String pair : associatedIds.split(";")) {
            String[] parts = pair.split(":");
            if (parts.length != 2) continue;
            String type = parts[0];
            String punishmentId = parts[1];

            updatePunishmentAsRemoved(connection, punishmentId, "System", "Paused by new warning");

            switch (type.toLowerCase()) {
                case "mute":
                    unmutePlayer(connection, warning.getPlayerUUID());
                    break;
                case "softban":
                    unSoftBanPlayer(connection, warning.getPlayerUUID());
                    break;
                case "ban":
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(warning.getPlayerUUID());
                        if (target.getName() != null && Bukkit.getBanList(BanList.Type.NAME).isBanned(target.getName())) {
                            Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
                        }
                    });
                    break;
            }
        }
    }

    private void reactivatePunishment(Connection connection, String punishmentId, long newEndTime, long remainingMillis) throws SQLException {
        String durationString = (remainingMillis == -1 || newEndTime == Long.MAX_VALUE) ?
                plugin.getConfigManager().getMessage("placeholders.permanent_time_display") :
                TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());

        String sql = "UPDATE punishment_history SET active = 1, punishment_time = ?, duration_string = ?, removed_by_name = NULL, removed_reason = NULL, removed_at = NULL WHERE punishment_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, newEndTime);
            ps.setString(2, durationString);
            ps.setString(3, punishmentId);
            ps.executeUpdate();
        }
    }

    private void resumeAssociatedPunishments(Connection connection, ActiveWarningEntry warning) throws SQLException {
        String associatedIds = warning.getAssociatedPunishmentIds();
        if (associatedIds == null || associatedIds.isEmpty()) return;

        OfflinePlayer target = Bukkit.getOfflinePlayer(warning.getPlayerUUID());
        String punisherName = "Console";
        long remainingMillis = warning.getRemainingTimeOnPause();
        long newEndTime = (remainingMillis == -1) ? Long.MAX_VALUE : System.currentTimeMillis() + remainingMillis;

        for (String pair : associatedIds.split(";")) {
            String[] parts = pair.split(":");
            if (parts.length != 2) continue;
            String type = parts[0];
            String originalPunishmentId = parts[1];

            PunishmentEntry originalPunishment = getPunishmentById(originalPunishmentId);
            if (originalPunishment == null || !"Paused by new warning".equals(originalPunishment.getRemovedReason())) {
                continue;
            }

            reactivatePunishment(connection, originalPunishmentId, newEndTime, remainingMillis);

            Bukkit.getScheduler().runTask(plugin, () -> {
                PunishmentEntry resumedPunishment = getPunishmentById(originalPunishmentId);
                if (resumedPunishment == null) return;

                plugin.getMenuListener().executeHookActions(Bukkit.getConsoleSender(), target, type, resumedPunishment.getDurationString(), resumedPunishment.getReason(), false, Collections.emptyList());

                switch (type.toLowerCase()) {
                    case "mute":
                        try (Connection conn = getConnection()) {
                            mutePlayer(conn, target.getUniqueId(), newEndTime, resumedPunishment.getReason());
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.SEVERE, "Failed to re-apply mute on resume", e);
                        }
                        if (target.isOnline()) {
                            String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}", resumedPunishment.getDurationString(), "{reason}", resumedPunishment.getReason(), "{punishment_id}", originalPunishmentId);
                            target.getPlayer().sendMessage(MessageUtils.getColorMessage(muteMessage));
                        }
                        break;
                    case "softban":
                        try (Connection conn = getConnection()) {
                            WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(warning.getWarnLevel());
                            List<String> customCommands = (levelConfig != null) ? levelConfig.getSoftbanBlockedCommands() : null;
                            softBanPlayer(conn, target.getUniqueId(), newEndTime, resumedPunishment.getReason(), customCommands);
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.SEVERE, "Failed to re-apply softban on resume", e);
                        }
                        break;
                    case "ban":
                        Date expiration = (newEndTime == Long.MAX_VALUE) ? null : new Date(newEndTime);
                        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), resumedPunishment.getReason(), expiration, punisherName);
                        if (target.isOnline()) {
                            String kickMessage = MessageUtils.getKickMessage(plugin.getConfigManager().getBanScreen(), resumedPunishment.getReason(), resumedPunishment.getDurationString(), originalPunishmentId, expiration, plugin.getConfigManager());
                            target.getPlayer().kickPlayer(kickMessage);
                        }
                        break;
                }
            });
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
                        rs.getLong("remaining_time_on_pause"),
                        rs.getString("associated_punishment_ids")
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
                        rs.getLong("remaining_time_on_pause"),
                        rs.getString("associated_punishment_ids")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get active warning by punishment ID: " + punishmentId, e);
        }
        return null;
    }

    public void addAssociatedPunishmentId(String warningPunishmentId, String associatedPunishmentType, String associatedPunishmentId) {
        CompletableFuture.runAsync(() -> {
            String sqlGet = "SELECT associated_punishment_ids FROM active_warnings WHERE punishment_id = ?";
            String sqlUpdate = "UPDATE active_warnings SET associated_punishment_ids = ? WHERE punishment_id = ?";
            try (Connection connection = getConnection();
                 PreparedStatement psGet = connection.prepareStatement(sqlGet)) {
                psGet.setString(1, warningPunishmentId);
                ResultSet rs = psGet.executeQuery();
                String currentIds = "";
                if (rs.next()) {
                    currentIds = rs.getString("associated_punishment_ids");
                }

                String newEntry = associatedPunishmentType + ":" + associatedPunishmentId;
                String updatedIds = (currentIds == null || currentIds.isEmpty()) ? newEntry : currentIds + ";" + newEntry;

                try (PreparedStatement psUpdate = connection.prepareStatement(sqlUpdate)) {
                    psUpdate.setString(1, updatedIds);
                    psUpdate.setString(2, warningPunishmentId);
                    psUpdate.executeUpdate();
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add associated punishment ID for warning " + warningPunishmentId, e);
            }
        });
    }

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
                            rs.getBoolean("by_ip"),
                            rs.getInt("warn_level")
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

    // New task to check for expired internal bans and update their status in punishment_history
    private void startBanExpiryCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String sql = "UPDATE punishment_history SET active = 0, removed_by_name = 'System', removed_reason = 'Expired', removed_at = ? WHERE active = 1 AND punishment_type = 'ban' AND punishment_time <= ? AND punishment_time != ?";
                try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                    long currentTime = System.currentTimeMillis();
                    ps.setTimestamp(1, new Timestamp(currentTime));
                    ps.setLong(2, currentTime);
                    ps.setLong(3, Long.MAX_VALUE); // Do not expire permanent bans

                    int updatedRows = ps.executeUpdate();
                    if (updatedRows > 0 && plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("[DatabaseManager] Marked " + updatedRows + " expired ban(s) as inactive.");
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error checking for expired internal bans", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60 * 5, 20L * 60 * 5); // Check every 5 minutes
    }


    public String softBanPlayer(UUID uuid, long endTime, String reason, String punisherName, boolean byIp) {
        return executePunishmentAsync(uuid, "softban", reason, punisherName, endTime, "...", byIp, null).join();
    }

    public String softBanPlayer(UUID uuid, long endTime, String reason, String punisherName, boolean byIp, List<String> customCommands) {
        return executePunishmentAsync(uuid, "softban", reason, punisherName, endTime, "...", byIp, customCommands).join();
    }

    private void softBanPlayer(Connection connection, UUID uuid, long endTime, String reason, List<String> customCommands) throws SQLException {
        long currentEndTime = getSoftBanEndTime(connection, uuid);
        long finalEndTime = (endTime == Long.MAX_VALUE || currentEndTime <= System.currentTimeMillis() || currentEndTime == Long.MAX_VALUE)
                ? endTime
                : currentEndTime + (endTime - System.currentTimeMillis());

        String sql = "mysql".equalsIgnoreCase(dbType) ?
                "REPLACE INTO softbans (uuid, endTime, reason, custom_commands) VALUES (?, ?, ?, ?)" :
                "INSERT OR REPLACE INTO softbans (uuid, endTime, reason, custom_commands) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, finalEndTime);
            ps.setString(3, reason);
            if (customCommands != null && !customCommands.isEmpty()) {
                ps.setString(4, String.join(COMMAND_DELIMITER, customCommands));
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.executeUpdate();
        }
    }


    public String unSoftBanPlayer(UUID uuid, String punisherName, String reason) {
        return executeUnpunishmentAsync(uuid, "softban", punisherName, reason, null).join();
    }

    private void unSoftBanPlayer(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM softbans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            if (ps.executeUpdate() > 0) {
                sendRemovalNotification(uuid, "softban");
            }
        }
    }

    public boolean isSoftBanned(UUID uuid) {
        return getPunishmentEndTime("softbans", uuid) > System.currentTimeMillis();
    }

    public List<String> getActiveSoftbanCustomCommands(UUID uuid) {
        String sql = "SELECT custom_commands FROM softbans WHERE uuid = ? AND endTime > ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String commands = rs.getString("custom_commands");
                    if (commands != null && !commands.isEmpty()) {
                        return Arrays.asList(commands.split(COMMAND_DELIMITER));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error getting custom softban commands!", e);
        }
        return null;
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

    private long getSoftBanEndTime(Connection connection, UUID uuid) throws SQLException {
        return getPunishmentEndTime(connection, "softbans", uuid);
    }


    public String mutePlayer(UUID uuid, long endTime, String reason, String punisherName, boolean byIp) {
        return executePunishmentAsync(uuid, "mute", reason, punisherName, endTime, "...", byIp, null).join();
    }

    private void mutePlayer(Connection connection, UUID uuid, long endTime, String reason) throws SQLException {
        long currentEndTime = getMuteEndTime(connection, uuid);
        long finalEndTime = (endTime == Long.MAX_VALUE || currentEndTime <= System.currentTimeMillis() || currentEndTime == Long.MAX_VALUE)
                ? endTime
                : currentEndTime + (endTime - System.currentTimeMillis());

        String sql = "mysql".equalsIgnoreCase(dbType) ? "REPLACE INTO mutes (uuid, endTime, reason) VALUES (?, ?, ?)" : "INSERT OR REPLACE INTO mutes (uuid, endTime, reason) VALUES (?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, finalEndTime);
            ps.setString(3, reason);
            ps.executeUpdate();
        }
    }


    public String unmutePlayer(UUID uuid, String punisherName, String reason) {
        return executeUnpunishmentAsync(uuid, "mute", punisherName, reason, null).join();
    }

    private void unmutePlayer(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM mutes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            if (ps.executeUpdate() > 0) {
                sendRemovalNotification(uuid, "mute");
            }
        }
    }


    public long getMuteEndTime(UUID uuid) {
        return getPunishmentEndTime("mutes", uuid);
    }

    private long getMuteEndTime(Connection connection, UUID uuid) throws SQLException {
        return getPunishmentEndTime(connection, "mutes", uuid);
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
        try (Connection connection = getConnection()) {
            return getPunishmentEndTime(connection, table, uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error getting punishment end time!", e);
        }
        return 0;
    }

    private long getPunishmentEndTime(Connection connection, String table, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT endTime FROM " + table + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("endTime");
                }
            }
        }
        return 0;
    }


    public String logPunishment(UUID playerUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp) {
        return logPunishment(playerUUID, punishmentType, reason, punisherName, punishmentEndTime, durationString, byIp, 0);
    }

    public String logPunishment(UUID playerUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp, int warnLevel) {
        try (Connection connection = getConnection()) {
            return logPunishment(connection, playerUUID, punishmentType, reason, punisherName, punishmentEndTime, durationString, byIp, warnLevel);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error logging punishment!", e);
            return null;
        }
    }

    private String logPunishment(Connection connection, UUID playerUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp, int warnLevel) throws SQLException {
        String punishmentId = generatePunishmentId();
        String sql = "INSERT INTO punishment_history (punishment_id, player_uuid, punishment_type, reason, punisher_name, punishment_time, duration_string, active, by_ip, warn_level) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            ps.setString(2, playerUUID.toString());
            ps.setString(3, punishmentType);
            ps.setString(4, reason);
            ps.setString(5, punisherName);
            ps.setLong(6, punishmentEndTime);
            ps.setString(7, durationString);
            ps.setBoolean(8, true);
            ps.setBoolean(9, byIp);
            ps.setInt(10, warnLevel);
            ps.executeUpdate();

            return punishmentId;
        }
    }


    public void logPlayerInfo(String punishmentId, Player player) {
        String ipAddress = (player != null && player.getAddress() != null) ? player.getAddress().getAddress().getHostAddress() : null;
        logPlayerInfoAsync(punishmentId, player, ipAddress);
    }

    public void logPlayerInfoAsync(String punishmentId, OfflinePlayer target, String ipAddress) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_info (punishment_id, ip, location, gamemode, health, hunger, exp_level, playtime, ping, first_joined, last_joined) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {

                Player onlineTarget = target.isOnline() ? target.getPlayer() : null;

                ps.setString(1, punishmentId);
                ps.setString(2, ipAddress);

                if (onlineTarget != null) {
                    ps.setString(3, onlineTarget.getWorld().getName() + "," + onlineTarget.getLocation().getX() + "," + onlineTarget.getLocation().getY() + "," + onlineTarget.getLocation().getZ());
                    ps.setString(4, onlineTarget.getGameMode().toString());
                    ps.setDouble(5, onlineTarget.getHealth());
                    ps.setInt(6, onlineTarget.getFoodLevel());
                    ps.setInt(7, onlineTarget.getLevel());
                    ps.setLong(8, onlineTarget.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE));
                    ps.setInt(9, onlineTarget.getPing());
                } else {
                    ps.setNull(3, Types.VARCHAR);
                    ps.setNull(4, Types.VARCHAR);
                    ps.setNull(5, Types.DOUBLE);
                    ps.setNull(6, Types.INTEGER);
                    ps.setNull(7, Types.INTEGER);
                    ps.setNull(8, Types.BIGINT);
                    ps.setNull(9, Types.INTEGER);
                }

                ps.setLong(10, target.getFirstPlayed());
                ps.setLong(11, target.getLastPlayed());

                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database error logging player info for punishment ID: " + punishmentId, e);
            }
        });
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
                            rs.getBoolean("by_ip"),
                            rs.getInt("warn_level")
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
                            rs.getBoolean("by_ip"),
                            rs.getInt("warn_level")
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
        String sql = "SELECT punishment_type, COUNT(*) as count FROM punishment_history WHERE player_uuid = ? AND (active = 1 OR (removed_by_name = 'System' AND removed_reason = 'Expired')) GROUP BY punishment_type";
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

    public HashMap<String, Integer> getActivePunishmentCounts(UUID playerUUID) {
        HashMap<String, Integer> counts = new HashMap<>();
        // Get counts for non-warn punishments from punishment_history
        String historySql = "SELECT punishment_type, COUNT(*) as count FROM punishment_history WHERE player_uuid = ? AND active = 1 AND punishment_type != 'warn' GROUP BY punishment_type";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(historySql)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString("punishment_type"), rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving active punishment counts for UUID: " + playerUUID, e);
        }

        // Get count for warns from active_warnings (includes paused as active)
        counts.put("warn", getActiveWarningCount(playerUUID));
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

    public String getLatestActivePunishmentId(UUID playerUUID, String punishmentType) {
        try(Connection connection = getConnection()) {
            return getLatestActivePunishmentId(connection, playerUUID, punishmentType);
        } catch(SQLException e){
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving latest active punishment ID!", e);
        }
        return null;
    }
    private String getLatestActivePunishmentId(Connection connection, UUID playerUUID, String punishmentType) throws SQLException {
        String sql = "SELECT punishment_id FROM punishment_history WHERE player_uuid = ? AND punishment_type = ? AND active = 1 ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, punishmentType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("punishment_id");
                }
            }
        }
        return null;
    }

    public PunishmentEntry getLatestActivePunishment(UUID playerUUID, String punishmentType) {
        try(Connection connection = getConnection()){
            return getLatestActivePunishment(connection, playerUUID, punishmentType);
        } catch(SQLException e){
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving latest active punishment!", e);
        }
        return null;
    }

    private PunishmentEntry getLatestActivePunishment(Connection connection, UUID playerUUID, String punishmentType) throws SQLException {
        String sql = "SELECT * FROM punishment_history WHERE player_uuid = ? AND punishment_type = ? AND active = 1 ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
                            rs.getBoolean("by_ip"),
                            rs.getInt("warn_level")
                    );
                }
            }
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
                            rs.getBoolean("by_ip"),
                            rs.getInt("warn_level")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving latest active punishment by IP!", e);
        }
        return null;
    }

    public boolean updatePunishmentAsRemoved(String punishmentId, String removedByName, String removedReason) {
        try(Connection connection = getConnection()){
            return updatePunishmentAsRemoved(connection, punishmentId, removedByName, removedReason);
        } catch (SQLException e){
            plugin.getLogger().log(Level.SEVERE, "Failed to update punishment status", e);
            return false;
        }
    }
    private boolean updatePunishmentAsRemoved(Connection connection, String punishmentId, String removedByName, String removedReason) throws SQLException {
        String sql = "UPDATE punishment_history SET active = 0, removed_by_name = ?, removed_reason = ?, removed_at = ? WHERE punishment_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, removedByName);
            ps.setString(2, removedReason);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, punishmentId);
            return ps.executeUpdate() > 0;
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
        Bukkit.getScheduler().runTask(plugin, () -> {
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
        });
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
        String sql = "SELECT DISTINCT ph.player_uuid FROM punishment_history ph JOIN player_info pi ON ph.punishment_id = pi.punishment_id WHERE pi.ip = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    String name = Bukkit.getOfflinePlayer(playerUUID).getName();
                    if (name != null && !players.contains(name)) {
                        players.add(name);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving players by IP!", e);
        }
        return players;
    }

    public List<String> getAllActivePunishmentIds() {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT punishment_id FROM punishment_history WHERE active = 1";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add("#" + rs.getString("punishment_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving all active punishment IDs!", e);
        }
        return ids;
    }

    public void updatePlayerLastState(Player player) {
        CompletableFuture.runAsync(() -> {
            String sql = "mysql".equalsIgnoreCase(dbType) ?
                    "INSERT INTO player_last_state (uuid, last_seen, ip, location, world) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen), ip = VALUES(ip), location = VALUES(location), world = VALUES(world)" :
                    "INSERT OR REPLACE INTO player_last_state (uuid, last_seen, ip, location, world) VALUES (?, ?, ?, ?, ?)";

            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, player.getAddress().getAddress().getHostAddress());
                ps.setString(4, String.format("%d, %d, %d", player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()));
                ps.setString(5, player.getLocation().getWorld().getName());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update player last state for " + player.getName(), e);
            }
        });
    }

    public PlayerLastState getPlayerLastState(UUID uuid) {
        String sql = "SELECT * FROM player_last_state WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerLastState(
                        uuid,
                        rs.getLong("last_seen"),
                        rs.getString("ip"),
                        rs.getString("location"),
                        rs.getString("world")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve player last state for " + uuid.toString(), e);
        }
        return null;
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
        private final int warnLevel;

        public PunishmentEntry(String punishmentId, UUID playerUUID, String type, String reason, Timestamp timestamp, String punisherName, long punishmentTime, String durationString, boolean active, String removedByName, Timestamp removedAt, String removedReason, boolean byIp, int warnLevel) {
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
            this.warnLevel = warnLevel;
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
        public int getWarnLevel() { return warnLevel; }
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
    public static class PlayerLastState {
        private final UUID uuid;
        private final long lastSeen;
        private final String ip;
        private final String location;
        private final String world;

        public PlayerLastState(UUID uuid, long lastSeen, String ip, String location, String world) {
            this.uuid = uuid;
            this.lastSeen = lastSeen;
            this.ip = ip;
            this.location = location;
            this.world = world;
        }

        public UUID getUuid() { return uuid; }
        public long getLastSeen() { return lastSeen; }
        public String getIp() { return ip; }
        public String getLocation() { return location; }
        public String getWorld() { return world; }
    }
}
package cp.corona.database;

import cp.corona.config.WarnLevel;
import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import cp.corona.report.ReportStatus;
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
    private static final String FAVORITE_TOOLS_DELIMITER = ",";

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
                    startBanExpiryCheckTask();
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

            String createAuditLogTableSQL = "CREATE TABLE IF NOT EXISTS operator_audit_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "target_uuid VARCHAR(36) NOT NULL," +
                    "executor_uuid VARCHAR(36) NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "action_type VARCHAR(255) NOT NULL," +
                    "details TEXT)";
            if ("sqlite".equalsIgnoreCase(dbType)) {
                createAuditLogTableSQL = "CREATE TABLE IF NOT EXISTS operator_audit_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "target_uuid VARCHAR(36) NOT NULL," +
                        "executor_uuid VARCHAR(36) NOT NULL," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "action_type VARCHAR(255) NOT NULL," +
                        "details TEXT)";
            }
            statement.execute(createAuditLogTableSQL);

            String createReportsTableSQL = "CREATE TABLE IF NOT EXISTS reports (" +
                    "report_id VARCHAR(12) PRIMARY KEY," +
                    "requester_uuid VARCHAR(36) NOT NULL," +
                    "target_uuid VARCHAR(36)," +
                    "target_name VARCHAR(255) NOT NULL," +
                    "report_type VARCHAR(50) NOT NULL," +
                    "category VARCHAR(255) NOT NULL," +
                    "reason VARCHAR(255) NOT NULL," +
                    "details TEXT," +
                    "status VARCHAR(50) NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "moderator_uuid VARCHAR(36)," +
                    "collected_data TEXT," +
                    "resolved_at DATETIME," +
                    "resolver_uuid VARCHAR(36))";
            if ("sqlite".equalsIgnoreCase(dbType)) {
                createReportsTableSQL = "CREATE TABLE IF NOT EXISTS reports (" +
                        "report_id VARCHAR(12) PRIMARY KEY," +
                        "requester_uuid VARCHAR(36) NOT NULL," +
                        "target_uuid VARCHAR(36)," +
                        "target_name VARCHAR(255) NOT NULL," +
                        "report_type VARCHAR(50) NOT NULL," +
                        "category VARCHAR(255) NOT NULL," +
                        "reason VARCHAR(255) NOT NULL," +
                        "details TEXT," +
                        "status VARCHAR(50) NOT NULL," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "moderator_uuid VARCHAR(36)," +
                        "collected_data TEXT," +
                        "resolved_at DATETIME," +
                        "resolver_uuid VARCHAR(36))";
            }
            statement.execute(createReportsTableSQL);

            // Added 'silent' and 'favorite_tools' column
            String createModPrefsTableSQL = "CREATE TABLE IF NOT EXISTS moderator_preferences (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "interactions BOOLEAN DEFAULT 0," +
                    "container_spy BOOLEAN DEFAULT 1," +
                    "fly_enabled BOOLEAN DEFAULT 1," +
                    "mod_on_join BOOLEAN DEFAULT 0," +
                    "silent BOOLEAN DEFAULT 0," +
                    "favorite_tools TEXT," +
                    "walk_speed FLOAT DEFAULT 1.0," +
                    "fly_speed FLOAT DEFAULT 1.0," +
                    "jump_multiplier FLOAT DEFAULT 1.0," +
                    "night_vision BOOLEAN DEFAULT 0," +
                    "glowing_enabled BOOLEAN DEFAULT 0)";
            statement.execute(createModPrefsTableSQL);

            // NEW: Confiscated Items Table
            String createConfiscatedItemsSQL = "CREATE TABLE IF NOT EXISTS confiscated_items (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "item_data TEXT NOT NULL," +
                    "confiscated_at BIGINT NOT NULL," +
                    "confiscated_by VARCHAR(36)," +
                    "original_type VARCHAR(50))";
            
            if ("sqlite".equalsIgnoreCase(dbType)) {
                createConfiscatedItemsSQL = "CREATE TABLE IF NOT EXISTS confiscated_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "item_data TEXT NOT NULL," +
                        "confiscated_at BIGINT NOT NULL," +
                        "confiscated_by VARCHAR(36)," +
                        "original_type VARCHAR(50))";
            }
            statement.execute(createConfiscatedItemsSQL);

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
            if (!columnExists(connection, "active_warnings", "paused_associated_punishments")) {
                statement.execute("ALTER TABLE active_warnings ADD COLUMN paused_associated_punishments TEXT");
            }
            if (!columnExists(connection, "reports", "resolved_at")) {
                statement.execute("ALTER TABLE reports ADD COLUMN resolved_at DATETIME");
            }
            if (!columnExists(connection, "reports", "resolver_uuid")) {
                statement.execute("ALTER TABLE reports ADD COLUMN resolver_uuid VARCHAR(36)");
            }

            if (!columnExists(connection, "moderator_preferences", "fly_enabled")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN fly_enabled BOOLEAN DEFAULT 1");
            }
            if (!columnExists(connection, "moderator_preferences", "mod_on_join")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN mod_on_join BOOLEAN DEFAULT 0");
            }
            if (!columnExists(connection, "moderator_preferences", "silent")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN silent BOOLEAN DEFAULT 0");
            }
            if (!columnExists(connection, "moderator_preferences", "favorite_tools")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN favorite_tools TEXT");
            }
            if (!columnExists(connection, "moderator_preferences", "walk_speed")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN walk_speed FLOAT DEFAULT 1.0");
            }
            if (!columnExists(connection, "moderator_preferences", "fly_speed")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN fly_speed FLOAT DEFAULT 1.0");
            }
            if (!columnExists(connection, "moderator_preferences", "jump_multiplier")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN jump_multiplier FLOAT DEFAULT 1.0");
            }
            if (!columnExists(connection, "moderator_preferences", "night_vision")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN night_vision BOOLEAN DEFAULT 0");
            }
            if (!columnExists(connection, "moderator_preferences", "glowing_enabled")) {
                statement.execute("ALTER TABLE moderator_preferences ADD COLUMN glowing_enabled BOOLEAN DEFAULT 0");
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    // --- NEW: Confiscated Items Methods ---

    public CompletableFuture<Void> addConfiscatedItem(String itemData, UUID confiscator, String containerType) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO confiscated_items (item_data, confiscated_at, confiscated_by, original_type) VALUES (?, ?, ?, ?)";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, itemData);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, confiscator != null ? confiscator.toString() : "Console");
                ps.setString(4, containerType);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error adding confiscated item", e);
            }
        });
    }

    public CompletableFuture<Boolean> removeConfiscatedItem(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM confiscated_items WHERE id = ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error removing confiscated item " + id, e);
                return false;
            }
        });
    }

    public CompletableFuture<Void> clearConfiscatedItems(UUID ownerUUID) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM confiscated_items WHERE confiscated_by = ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, ownerUUID.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error clearing confiscated items for " + ownerUUID, e);
            }
        });
    }

    public CompletableFuture<List<ConfiscatedItemEntry>> getConfiscatedItems(UUID ownerUUID, int page, int itemsPerPage) {
        return CompletableFuture.supplyAsync(() -> {
            List<ConfiscatedItemEntry> items = new ArrayList<>();
            int offset = (page - 1) * itemsPerPage;
            // Filter by confiscated_by (which acts as the locker owner)
            String sql = "SELECT * FROM confiscated_items WHERE confiscated_by = ? ORDER BY confiscated_at DESC LIMIT ? OFFSET ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, ownerUUID.toString());
                ps.setInt(2, itemsPerPage);
                ps.setInt(3, offset);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(new ConfiscatedItemEntry(
                            rs.getInt("id"),
                            rs.getString("item_data"),
                            rs.getLong("confiscated_at"),
                            rs.getString("confiscated_by"),
                            rs.getString("original_type")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error fetching confiscated items for " + ownerUUID, e);
            }
            return items;
        });
    }

    public CompletableFuture<Integer> countConfiscatedItems(UUID ownerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM confiscated_items WHERE confiscated_by = ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, ownerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error counting confiscated items for " + ownerUUID, e);
            }
            return 0;
        });
    }

    // NEW: Get all items (Global Locker)
    public CompletableFuture<List<ConfiscatedItemEntry>> getAllConfiscatedItems(int page, int itemsPerPage) {
        return CompletableFuture.supplyAsync(() -> {
            List<ConfiscatedItemEntry> items = new ArrayList<>();
            int offset = (page - 1) * itemsPerPage;
            String sql = "SELECT * FROM confiscated_items ORDER BY confiscated_at DESC LIMIT ? OFFSET ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, itemsPerPage);
                ps.setInt(2, offset);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(new ConfiscatedItemEntry(
                            rs.getInt("id"),
                            rs.getString("item_data"),
                            rs.getLong("confiscated_at"),
                            rs.getString("confiscated_by"),
                            rs.getString("original_type")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error fetching all confiscated items", e);
            }
            return items;
        });
    }

    // NEW: Count all items (Global Locker)
    public CompletableFuture<Integer> countAllConfiscatedItems() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM confiscated_items";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error counting all confiscated items", e);
            }
            return 0;
        });
    }

    public static class ConfiscatedItemEntry {
        private final int id;
        private final String itemData;
        private final long confiscatedAt;
        private final String confiscatedBy;
        private final String originalType;

        public ConfiscatedItemEntry(int id, String itemData, long confiscatedAt, String confiscatedBy, String originalType) {
            this.id = id;
            this.itemData = itemData;
            this.confiscatedAt = confiscatedAt;
            this.confiscatedBy = confiscatedBy;
            this.originalType = originalType;
        }

        public int getId() { return id; }
        public String getItemData() { return itemData; }
        public long getConfiscatedAt() { return confiscatedAt; }
        public String getConfiscatedBy() { return confiscatedBy; }
        public String getOriginalType() { return originalType; }
    }

    // NEW: Methods for Moderator Preferences with 'silent' and 'favorite_tools' support

    public CompletableFuture<Void> saveModPreferences(UUID uuid, boolean interactions, boolean containerSpy, boolean flyEnabled, boolean modOnJoin, boolean silent, List<String> favoriteTools, float walkSpeed, float flySpeed, float jumpMultiplier, boolean nightVision, boolean glowingEnabled) {
        return CompletableFuture.runAsync(() -> {
            String sql = "mysql".equalsIgnoreCase(dbType) ?
                    "INSERT INTO moderator_preferences (uuid, interactions, container_spy, fly_enabled, mod_on_join, silent, favorite_tools, walk_speed, fly_speed, jump_multiplier, night_vision, glowing_enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE interactions = VALUES(interactions), container_spy = VALUES(container_spy), fly_enabled = VALUES(fly_enabled), mod_on_join = VALUES(mod_on_join), silent = VALUES(silent), favorite_tools = VALUES(favorite_tools), walk_speed = VALUES(walk_speed), fly_speed = VALUES(fly_speed), jump_multiplier = VALUES(jump_multiplier), night_vision = VALUES(night_vision), glowing_enabled = VALUES(glowing_enabled)" :
                    "INSERT OR REPLACE INTO moderator_preferences (uuid, interactions, container_spy, fly_enabled, mod_on_join, silent, favorite_tools, walk_speed, fly_speed, jump_multiplier, night_vision, glowing_enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, interactions);
                ps.setBoolean(3, containerSpy);
                ps.setBoolean(4, flyEnabled);
                ps.setBoolean(5, modOnJoin);
                ps.setBoolean(6, silent);
                ps.setString(7, String.join(FAVORITE_TOOLS_DELIMITER, favoriteTools));
                ps.setFloat(8, walkSpeed);
                ps.setFloat(9, flySpeed);
                ps.setFloat(10, jumpMultiplier);
                ps.setBoolean(11, nightVision);
                ps.setBoolean(12, glowingEnabled);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save moderator preferences for " + uuid, e);
            }
        });
    }
    
    // Overload for backward compatibility if needed, but better to update calls
    public CompletableFuture<Void> saveModPreferences(UUID uuid, boolean interactions, boolean containerSpy, boolean flyEnabled, boolean modOnJoin, boolean silent, List<String> favoriteTools, float walkSpeed, float flySpeed, float jumpMultiplier, boolean nightVision) {
        return saveModPreferences(uuid, interactions, containerSpy, flyEnabled, modOnJoin, silent, favoriteTools, walkSpeed, flySpeed, jumpMultiplier, nightVision, false);
    }

    public CompletableFuture<ModPreferences> getModPreferences(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM moderator_preferences WHERE uuid = ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String favToolsRaw = rs.getString("favorite_tools");
                        List<String> favoriteTools;
                        
                        if (favToolsRaw == null) {
                            favoriteTools = null; // Indicates never set (use defaults)
                        } else if (favToolsRaw.isEmpty()) {
                            favoriteTools = new ArrayList<>(); // Indicates explicitly empty
                        } else {
                            favoriteTools = new ArrayList<>(Arrays.asList(favToolsRaw.split(FAVORITE_TOOLS_DELIMITER)));
                        }

                        float walkSpeed = 1.0f;
                        float flySpeed = 1.0f;
                        float jumpMultiplier = 1.0f;
                        boolean nightVision = false;
                        boolean glowingEnabled = false;
                        try {
                            walkSpeed = rs.getFloat("walk_speed");
                            flySpeed = rs.getFloat("fly_speed");
                            jumpMultiplier = rs.getFloat("jump_multiplier");
                            nightVision = rs.getBoolean("night_vision");
                            glowingEnabled = rs.getBoolean("glowing_enabled");
                        } catch (SQLException ignored) {
                            // Columns might not exist yet
                        }
                        
                        if (walkSpeed == 0.0f) walkSpeed = 1.0f;
                        if (flySpeed == 0.0f) flySpeed = 1.0f;
                        if (jumpMultiplier == 0.0f) jumpMultiplier = 1.0f;

                        return new ModPreferences(
                                rs.getBoolean("interactions"),
                                rs.getBoolean("container_spy"),
                                rs.getBoolean("fly_enabled"),
                                rs.getBoolean("mod_on_join"),
                                rs.getBoolean("silent"),
                                favoriteTools,
                                walkSpeed,
                                flySpeed,
                                jumpMultiplier,
                                nightVision,
                                glowingEnabled
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve moderator preferences for " + uuid, e);
            }
            // Default values
            return new ModPreferences(false, true, true, false, false, null, 1.0f, 1.0f, 1.0f, false, false);
        });
    }

    // Helper class for data transport
    public static class ModPreferences {
        private final boolean interactions;
        private final boolean containerSpy;
        private final boolean flyEnabled;
        private final boolean modOnJoin;
        private final boolean silent;
        private final List<String> favoriteTools;
        private final float walkSpeed;
        private final float flySpeed;
        private final float jumpMultiplier;
        private final boolean nightVision;
        private final boolean glowingEnabled;

        public ModPreferences(boolean interactions, boolean containerSpy, boolean flyEnabled, boolean modOnJoin, boolean silent, List<String> favoriteTools) {
            this(interactions, containerSpy, flyEnabled, modOnJoin, silent, favoriteTools, 1.0f, 1.0f, 1.0f, false, false);
        }

        public ModPreferences(boolean interactions, boolean containerSpy, boolean flyEnabled, boolean modOnJoin, boolean silent, List<String> favoriteTools, float walkSpeed, float flySpeed, float jumpMultiplier, boolean nightVision, boolean glowingEnabled) {
            this.interactions = interactions;
            this.containerSpy = containerSpy;
            this.flyEnabled = flyEnabled;
            this.modOnJoin = modOnJoin;
            this.silent = silent;
            this.favoriteTools = favoriteTools;
            this.walkSpeed = walkSpeed;
            this.flySpeed = flySpeed;
            this.jumpMultiplier = jumpMultiplier;
            this.nightVision = nightVision;
            this.glowingEnabled = glowingEnabled;
        }

        public boolean isInteractions() { return interactions; }
        public boolean isContainerSpy() { return containerSpy; }
        public boolean isFlyEnabled() { return flyEnabled; }
        public boolean isModOnJoin() { return modOnJoin; }
        public boolean isSilent() { return silent; }
        public List<String> getFavoriteTools() { return favoriteTools; }
        public float getWalkSpeed() { return walkSpeed; }
        public float getFlySpeed() { return flySpeed; }
        public float getJumpMultiplier() { return jumpMultiplier; }
        public boolean isNightVision() { return nightVision; }
        public boolean isGlowingEnabled() { return glowingEnabled; }
    }

    // ... (rest of the class remains identical, just ensuring all other methods are kept)
    // NEW: Methods for Operator Audit Log
    public void logOperatorAction(UUID targetUUID, UUID executorUUID, String actionType, String details) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO operator_audit_log (target_uuid, executor_uuid, action_type, details) VALUES (?, ?, ?, ?)";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, targetUUID.toString());
                ps.setString(2, executorUUID.toString());
                ps.setString(3, actionType);
                ps.setString(4, details);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not log operator action for target " + targetUUID, e);
            }
        });
    }

    public CompletableFuture<List<AuditLogEntry>> getOperatorActions(UUID targetUUID) {
        return CompletableFuture.supplyAsync(() -> {
            List<AuditLogEntry> logEntries = new ArrayList<>();
            String sql = "SELECT * FROM operator_audit_log WHERE target_uuid = ? ORDER BY timestamp DESC";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, targetUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        logEntries.add(new AuditLogEntry(
                                rs.getInt("id"),
                                UUID.fromString(rs.getString("target_uuid")),
                                UUID.fromString(rs.getString("executor_uuid")),
                                rs.getTimestamp("timestamp"),
                                rs.getString("action_type"),
                                rs.getString("details")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve operator actions for target " + targetUUID, e);
            }
            return logEntries;
        });
    }

    public CompletableFuture<String> executePunishmentAsync(UUID targetUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp, List<String> customCommands) {
        return executePunishmentAsync(targetUUID, punishmentType, reason, punisherName, punishmentEndTime, durationString, byIp, customCommands, 0);
    }

    public CompletableFuture<String> executePunishmentAsync(UUID targetUUID, String punishmentType, String reason, String punisherName, long punishmentEndTime, String durationString, boolean byIp, List<String> customCommands, int warnLevel) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                boolean isInternal = plugin.getConfigManager().isPunishmentInternal(punishmentType);
                if (isInternal && !"warn".equalsIgnoreCase(punishmentType)) {
                    PunishmentEntry activePunishment = getLatestActivePunishment(connection, targetUUID, punishmentType);
                    if (activePunishment != null && (activePunishment.getEndTime() > System.currentTimeMillis() || activePunishment.getEndTime() == Long.MAX_VALUE)) {
                        updatePunishmentAsRemoved(connection, activePunishment.getPunishmentId(), "System", "Superseded by new punishment.");
                    }
                }

                String punishmentId = logPunishment(connection, targetUUID, punishmentType, reason, punisherName, punishmentEndTime, durationString, byIp, warnLevel);

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
                    PunishmentEntry entry = getPunishmentById(punishmentId);
                    if (entry == null || !entry.isActive()) {
                        return null;
                    }
                }

                if (punishmentId == null) {
                    return null;
                }

                updatePunishmentAsRemoved(connection, punishmentId, punisherName, reason);

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
        }.runTaskTimerAsynchronously(plugin, 20L * 10, 20L * 30);
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
        ActiveWarningEntry warningToRemove = getActiveWarningByPunishmentId(connection, punishmentId);

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
                    if (warningToRemove != null && !warningToRemove.isPaused()) {
                        resumeLatestPausedWarning(connection, playerUUID);
                    }
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
            Bukkit.getScheduler().runTask(plugin, () -> handleWarningExpiration(latest));
            return;
        }

        String sql = "UPDATE active_warnings SET is_paused = 1, remaining_time_on_pause = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, remainingTime);
            ps.setInt(2, latest.getId());
            ps.executeUpdate();
        }

        updatePunishmentAsRemoved(connection, latest.getPunishmentId(), "System", "Paused by new warning");
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

                long remainingMillis = warningToResume.getRemainingTimeOnPause();
                long newEndTime = (remainingMillis == -1) ? -1 : System.currentTimeMillis() + remainingMillis;

                String sqlUpdate = "UPDATE active_warnings SET is_paused = 0, end_time = ?, remaining_time_on_pause = 0 WHERE id = ?";
                try (PreparedStatement psUpdate = connection.prepareStatement(sqlUpdate)) {
                    psUpdate.setLong(1, newEndTime);
                    psUpdate.setInt(2, warningToResume.getId());
                    psUpdate.executeUpdate();
                }

                reactivatePunishment(connection, warningToResume.getPunishmentId(), newEndTime, remainingMillis);
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
        try (Connection connection = getConnection()) {
            return getActiveWarningByPunishmentId(connection, punishmentId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not get active warning by punishment ID: " + punishmentId, e);
        }
        return null;
    }

    private ActiveWarningEntry getActiveWarningByPunishmentId(Connection connection, String punishmentId) throws SQLException {
        String sql = "SELECT * FROM active_warnings WHERE punishment_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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

    private void runExpiryTask(String tableName, String cacheName) {
        List<UUID> expiredUuids = new ArrayList<>();
        String selectSql = "SELECT uuid FROM " + tableName + " WHERE endTime <= ? AND endTime != ?";

        try (Connection connection = getConnection(); PreparedStatement psSelect = connection.prepareStatement(selectSql)) {
            long currentTime = System.currentTimeMillis();
            psSelect.setLong(1, currentTime);
            psSelect.setLong(2, Long.MAX_VALUE);
            ResultSet rs = psSelect.executeQuery();
            while (rs.next()) {
                expiredUuids.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error selecting expired entries from " + tableName, e);
            return;
        }

        if (!expiredUuids.isEmpty()) {
            String deleteSql = "DELETE FROM " + tableName + " WHERE uuid = ?";
            try (Connection connection = getConnection(); PreparedStatement psDelete = connection.prepareStatement(deleteSql)) {
                for (UUID uuid : expiredUuids) {
                    psDelete.setString(1, uuid.toString());
                    psDelete.addBatch();
                }
                psDelete.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error deleting expired entries from " + tableName, e);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UUID uuid : expiredUuids) {
                    if ("softbans".equals(tableName)) {
                        plugin.getSoftBannedPlayersCache().remove(uuid);
                        plugin.getSoftbannedCommandsCache().remove(uuid);
                    } else if ("mutes".equals(tableName)) {
                        plugin.getMutedPlayersCache().remove(uuid);
                    }
                }
            });
        }
    }

    private void startExpiryCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> runExpiryTask("softbans", "softban"), 0L, 6000L);
    }

    private void startMuteExpiryCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> runExpiryTask("mutes", "mute"), 0L, 6000L);
    }

    private void startBanExpiryCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String sql = "UPDATE punishment_history SET active = 0, removed_by_name = 'System', removed_reason = 'Expired', removed_at = ? WHERE active = 1 AND punishment_type = 'ban' AND punishment_time <= ? AND punishment_time != ?";
                try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                    long currentTime = System.currentTimeMillis();
                    ps.setTimestamp(1, new Timestamp(currentTime));
                    ps.setLong(2, currentTime);
                    ps.setLong(3, Long.MAX_VALUE);

                    int updatedRows = ps.executeUpdate();
                    if (updatedRows > 0 && plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("[DatabaseManager] Marked " + updatedRows + " expired ban(s) as inactive.");
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error checking for expired internal bans", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60 * 5, 20L * 60 * 5);
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
        String sql = "SELECT ip FROM player_last_state WHERE uuid = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ip");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error retrieving last known IP from player_last_state!", e);
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
        if (durationMillis <= 0) return;
        long durationTicks = durationMillis / 50;

        new BukkitRunnable() {
            @Override
            public void run() {
                PunishmentEntry entry = getPunishmentById(punishmentId);
                if (entry == null || !entry.isActive()) {
                    return;
                }

                String table = punishmentType.equals("mute") ? "mutes" : "softbans";
                long currentEndTimeInDb = getPunishmentEndTime(table, uuid);
                if (currentEndTimeInDb == 0 || currentEndTimeInDb != endTime) {
                    return;
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
                    updatePunishmentAsRemoved(punishmentId, "System", "Expired");

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (entry.wasByIp()) {
                                PlayerInfo pInfo = getPlayerInfo(punishmentId);
                                if (pInfo != null && pInfo.getIp() != null) {
                                    plugin.getPunishmentListener().applyIpExpiryToOnlinePlayers(punishmentType, pInfo.getIp());
                                }
                            } else {
                                sendRemovalNotification(uuid, punishmentType, true);
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskLaterAsynchronously(plugin, durationTicks);
    }

    private void sendRemovalNotification(UUID uuid, String punishmentType) {
        sendRemovalNotification(uuid, punishmentType, false);
    }

    private void sendRemovalNotification(UUID uuid, String punishmentType, boolean isExpiry) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if ("mute".equalsIgnoreCase(punishmentType)) {
                plugin.getMutedPlayersCache().remove(uuid);
            } else if ("softban".equalsIgnoreCase(punishmentType)) {
                plugin.getSoftBannedPlayersCache().remove(uuid);
                plugin.getSoftbannedCommandsCache().remove(uuid);
            }

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.isOnline()) {
                Player player = offlinePlayer.getPlayer();
                if (player != null) {
                    String messageKey = isExpiry ? "messages." + punishmentType + "_expired" :
                            "messages." + (punishmentType.equals("mute") ? "unmute_notification" : "unsoftban_notification");
                    String message = plugin.getConfigManager().getMessage(messageKey);
                    player.sendMessage(MessageUtils.getColorMessage(message));

                    String soundKey = isExpiry ? "punishment_expired" : "punishment_removed";
                    plugin.playSound(player, soundKey);
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

    private String generateReportId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder idBuilder;
        do {
            idBuilder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                idBuilder.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
            }
        } while (reportIdExists(idBuilder.toString()));
        return idBuilder.toString();
    }

    private boolean reportIdExists(String reportId) {
        String sql = "SELECT 1 FROM reports WHERE report_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check for report ID existence.", e);
            return true;
        }
    }

    public CompletableFuture<String> createReport(UUID requesterUUID, UUID targetUUID, String targetName, String reportType, String category, String reason, String details, String collectedData) {
        return CompletableFuture.supplyAsync(() -> {
            String reportId = generateReportId();
            String sql = "INSERT INTO reports (report_id, requester_uuid, target_uuid, target_name, report_type, category, reason, details, status, collected_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, reportId);
                ps.setString(2, requesterUUID.toString());
                ps.setString(3, targetUUID != null ? targetUUID.toString() : null);
                ps.setString(4, targetName);
                ps.setString(5, reportType);
                ps.setString(6, category);
                ps.setString(7, reason);
                ps.setString(8, details);
                ps.setString(9, ReportStatus.PENDING.name());
                ps.setString(10, collectedData);
                ps.executeUpdate();
                return reportId;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create report.", e);
                return null;
            }
        });
    }

    public CompletableFuture<ReportEntry> getReportById(String reportId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM reports WHERE report_id = ?";
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, reportId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        try {
                            return new ReportEntry(rs);
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.SEVERE, "Error constructing ReportEntry from ResultSet.", e);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve report by ID: " + reportId, e);
            }
            return null;
        });
    }

    public CompletableFuture<List<ReportEntry>> getReports(int page, int entriesPerPage, ReportStatus filterStatus, String filterName, boolean filterAsRequester, UUID assignedTo, String reportType) {
        return CompletableFuture.supplyAsync(() -> {
            List<ReportEntry> reports = new ArrayList<>();
            int offset = (page - 1) * entriesPerPage;

            StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM reports ");
            List<Object> params = new ArrayList<>();
            boolean whereAdded = false;

            if (assignedTo != null) {
                sqlBuilder.append("WHERE moderator_uuid = ? ");
                params.add(assignedTo.toString());
                whereAdded = true;
            }

            if (reportType != null) {
                sqlBuilder.append(whereAdded ? "AND " : "WHERE ");
                sqlBuilder.append("report_type = ? ");
                params.add(reportType);
                whereAdded = true;
            }

            if (filterStatus != null) {
                sqlBuilder.append(whereAdded ? "AND " : "WHERE ");
                sqlBuilder.append("status = ? ");
                params.add(filterStatus.name());
                whereAdded = true;
            }
            if (filterName != null && !filterName.isEmpty()) {
                sqlBuilder.append(whereAdded ? "AND " : "WHERE ");
                String nameToFilter = filterName;
                if (filterAsRequester) {
                    sqlBuilder.append("requester_uuid = ? ");
                    OfflinePlayer p = Bukkit.getOfflinePlayer(nameToFilter);
                    params.add(p.getUniqueId().toString());
                } else {
                    sqlBuilder.append("target_name LIKE ? ");
                    params.add("%" + nameToFilter + "%");
                }
            }

            sqlBuilder.append("ORDER BY timestamp DESC LIMIT ? OFFSET ?");
            params.add(entriesPerPage);
            params.add(offset);

            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sqlBuilder.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        reports.add(new ReportEntry(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve reports.", e);
            }
            return reports;
        });
    }

    public CompletableFuture<Integer> countReports(ReportStatus filterStatus, String filterName, boolean filterAsRequester, UUID assignedTo, String reportType) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sqlBuilder = new StringBuilder("SELECT COUNT(*) FROM reports ");
            List<Object> params = new ArrayList<>();
            boolean whereAdded = false;

            if (assignedTo != null) {
                sqlBuilder.append("WHERE moderator_uuid = ? ");
                params.add(assignedTo.toString());
                whereAdded = true;
            }

            if (reportType != null) {
                sqlBuilder.append(whereAdded ? "AND " : "WHERE ");
                sqlBuilder.append("report_type = ? ");
                params.add(reportType);
                whereAdded = true;
            }

            if (filterStatus != null) {
                sqlBuilder.append(whereAdded ? "AND " : "WHERE ");
                sqlBuilder.append("status = ? ");
                params.add(filterStatus.name());
                whereAdded = true;
            }
            if (filterName != null && !filterName.isEmpty()) {
                sqlBuilder.append(whereAdded ? "AND " : "WHERE ");
                String nameToFilter = filterName;
                if (filterAsRequester) {
                    sqlBuilder.append("requester_uuid = ? ");
                    OfflinePlayer p = Bukkit.getOfflinePlayer(nameToFilter);
                    params.add(p.getUniqueId().toString());
                } else {
                    sqlBuilder.append("target_name LIKE ? ");
                    params.add("%" + nameToFilter + "%");
                }
            }

            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sqlBuilder.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not count reports.", e);
            }
            return 0;
        });
    }

    public CompletableFuture<Boolean> updateReportStatus(String reportId, ReportStatus status, UUID moderatorUUID) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isResolution = (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED);
            String sql = "UPDATE reports SET status = ?, moderator_uuid = ?" +
                    (isResolution ? ", resolved_at = CURRENT_TIMESTAMP, resolver_uuid = ?" : "") +
                    " WHERE report_id = ?";
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, status.name());
                ps.setString(2, moderatorUUID != null ? moderatorUUID.toString() : null);
                if (isResolution) {
                    ps.setString(3, moderatorUUID != null ? moderatorUUID.toString() : null);
                    ps.setString(4, reportId);
                } else {
                    ps.setString(3, reportId);
                }
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update report status for ID: " + reportId, e);
                return false;
            }
        });
    }

    public CompletableFuture<Integer> countReportsAsTarget(UUID targetUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM reports WHERE target_uuid = ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, targetUUID.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not count reports as target for " + targetUUID, e);
            }
            return 0;
        });
    }

    public CompletableFuture<Integer> countReportsAsRequester(UUID requesterUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM reports WHERE requester_uuid = ?";
            try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, requesterUUID.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not count reports as requester for " + requesterUUID, e);
            }
            return 0;
        });
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

    public static class AuditLogEntry {
        private final int id;
        private final UUID targetUUID;
        private final UUID executorUUID;
        private final Timestamp timestamp;
        private final String actionType;
        private final String details;

        public AuditLogEntry(int id, UUID targetUUID, UUID executorUUID, Timestamp timestamp, String actionType, String details) {
            this.id = id;
            this.targetUUID = targetUUID;
            this.executorUUID = executorUUID;
            this.timestamp = timestamp;
            this.actionType = actionType;
            this.details = details;
        }

        public int getId() { return id; }
        public UUID getTargetUUID() { return targetUUID; }
        public UUID getExecutorUUID() { return executorUUID; }
        public Timestamp getTimestamp() { return timestamp; }
        public String getActionType() { return actionType; }
        public String getDetails() { return details; }
    }

    public static class ReportEntry {
        private final String reportId;
        private final UUID requesterUUID;
        private final UUID targetUUID;
        private final String targetName;
        private final String reportType;
        private final String category;
        private final String reason;
        private final String details;
        private final ReportStatus status;
        private final Timestamp timestamp;
        private final UUID moderatorUUID;
        private final String collectedData;
        private final Timestamp resolvedAt;
        private final UUID resolverUUID;

        public ReportEntry(ResultSet rs) throws SQLException {
            this.reportId = rs.getString("report_id");
            this.requesterUUID = UUID.fromString(rs.getString("requester_uuid"));
            String targetUUIDString = rs.getString("target_uuid");
            this.targetUUID = (targetUUIDString != null) ? UUID.fromString(targetUUIDString) : null;
            this.targetName = rs.getString("target_name");
            this.reportType = rs.getString("report_type");
            this.category = rs.getString("category");
            this.reason = rs.getString("reason");
            this.details = rs.getString("details");
            this.status = ReportStatus.valueOf(rs.getString("status"));
            this.timestamp = rs.getTimestamp("timestamp");
            String modUUIDString = rs.getString("moderator_uuid");
            this.moderatorUUID = (modUUIDString != null) ? UUID.fromString(modUUIDString) : null;
            this.collectedData = rs.getString("collected_data");
            this.resolvedAt = rs.getTimestamp("resolved_at");
            String resolverUUIDString = rs.getString("resolver_uuid");
            this.resolverUUID = (resolverUUIDString != null) ? UUID.fromString(resolverUUIDString) : null;
        }

        public String getReportId() { return reportId; }
        public UUID getRequesterUUID() { return requesterUUID; }
        public UUID getTargetUUID() { return targetUUID; }
        public String getTargetName() { return targetName; }
        public String getReportType() { return reportType; }
        public String getCategory() { return category; }
        public String getReason() { return reason; }
        public String getDetails() { return details; }
        public ReportStatus getStatus() { return status; }
        public Timestamp getTimestamp() { return timestamp; }
        public UUID getModeratorUUID() { return moderatorUUID; }
        public String getCollectedData() { return collectedData; }
        public Timestamp getResolvedAt() { return resolvedAt; }
        public UUID getResolverUUID() { return resolverUUID; }
    }
}

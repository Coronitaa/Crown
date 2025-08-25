// Crown.java
package cp.corona.crown;

import cp.corona.Metrics;
import cp.corona.commands.MainCommand;
import cp.corona.config.MainConfigManager;
import cp.corona.database.DatabaseManager;
import cp.corona.listeners.CommandBlockerListener;
import cp.corona.listeners.FreezeListener;
import cp.corona.listeners.MenuListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Main plugin class for Crown.
 * Initializes and manages plugin components, commands, and listeners.
 */
public final class Crown extends JavaPlugin {
    private final String version = getDescription().getVersion();
    private MainConfigManager configManager;
    private DatabaseManager databaseManager;
    private boolean placeholderAPIEnabled;
    private final HashMap<UUID, Boolean> pluginFrozenPlayers = new HashMap<>();

    private MenuListener menuListener;
    private FreezeListener freezeListener;

    /**
     * Called when the plugin is enabled.
     * Initializes configuration, database, commands, and event listeners, and PlaceholderAPI.
     */
    @Override
    public void onEnable() {
        // Initialize configuration manager
        this.configManager = new MainConfigManager(this);
        // Initialize database manager, setting up database connection and tables
        this.databaseManager = new DatabaseManager(this);

        // Check if PlaceholderAPI is installed and enabled - Now done in MainConfigManager
        this.placeholderAPIEnabled = configManager.isPlaceholderAPIEnabled(); // Get status from config manager

        if (placeholderAPIEnabled) { // Register placeholders only if PlaceholderAPI is enabled
            configManager.registerPlaceholders(); // Register PlaceholderAPI placeholders via ConfigManager - [CORRECTED CALL]
        }

        this.menuListener = new MenuListener(this);


        registerCommands(); // Register command handlers
        registerEvents();   // Register event listeners

        // Send plugin enabled message to console, using config for messages and prefix
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                configManager.getMessage("messages.plugin_enabled") + " Version: " + version));
        // Log debug mode status if enabled in config
        if (configManager.isDebugEnabled()) {
            getLogger().log(Level.INFO, "[Crown] Debug mode is enabled.");
        }
        // Log PlaceholderAPI status
        getLogger().log(Level.INFO, "[Crown] PlaceholderAPI integration is " + (isPlaceholderAPIEnabled() ? "enabled" : "disabled") + "."); // Log PAPI status

        // Initialize bStats metrics (plugin ID: 25939)
        try {
            Metrics metrics = new Metrics(this, 25939);
            getLogger().log(Level.INFO, "[Crown] bStats metrics initialized successfully.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[Crown] Failed to initialize bStats metrics:", e);
        }
    }


    /**
     * Called when the plugin is disabled.
     * Logs plugin disable message to console.
     */
    @Override
    public void onDisable() {
        // Send plugin disabled message to console, using config for messages and prefix
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                configManager.getMessage("messages.plugin_disabled")));
    }

    /**
     * Registers plugin commands with detailed logging for debugging command registration.
     */
    public void registerCommands() {
        MainCommand mainCommand = new MainCommand(this); // Instantiate main command handler

        // Register 'crown' command
        if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Attempting to get command: 'crown'");
        PluginCommand crownCommand = getCommand("crown");
        if (crownCommand != null) {
            if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Command 'crown' found. Setting executor and tab completer.");
            crownCommand.setExecutor(mainCommand); // Set command executor
            crownCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            if (configManager.isDebugEnabled()) getLogger().warning("[COMMAND DEBUG] Command 'crown' is NULL! Registration FAILED. Check plugin.yml for 'crown' command definition.");
        }

        // Register 'punish' command alias - for direct /punish usage
        if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Attempting to get command: 'punish'");
        PluginCommand punishCommand = getCommand("punish");
        if (punishCommand != null) {
            if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Command 'punish' found. Setting executor and tab completer.");
            punishCommand.setExecutor(mainCommand); // Set command executor
            punishCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            if (configManager.isDebugEnabled()) getLogger().warning("[COMMAND DEBUG] Command 'punish' is NULL! Registration FAILED. Check plugin.yml for 'punish' command definition.");
        }

        // Register 'kick' command - though might be handled internally now, keep logging for troubleshooting
        if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Attempting to get command: 'kick'");
        PluginCommand kickCommand = getCommand("kick");
        if (kickCommand != null) {
            if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Command 'kick' found. Setting executor and tab completer.");
            kickCommand.setExecutor(mainCommand); // Set command executor (though MainCommand might not directly handle /kick anymore)
            kickCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            if (configManager.isDebugEnabled()) getLogger().warning("[COMMAND DEBUG] Command 'kick' is NULL! Registration FAILED. Check plugin.yml for 'kick' command definition.");
        }

        // Register 'warn' command - same as kick, keep logging for consistency
        if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Attempting to get command: 'warn'");
        PluginCommand warnCommand = getCommand("warn");
        if (warnCommand != null) {
            if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Command 'warn' found. Setting executor and tab completer.");
            warnCommand.setExecutor(mainCommand); // Set executor
            warnCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            if (configManager.isDebugEnabled()) getLogger().warning("[COMMAND DEBUG] Command 'warn' is NULL! Registration FAILED. Check plugin.yml for 'warn' command definition.");
        }

        // Register 'unpunish' command - TOP LEVEL COMMAND, SEPARATE FROM CROWN SUBCOMMAND
        if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Attempting to get command: 'unpunish'");
        PluginCommand unpunishCommand = getCommand("unpunish");
        if (unpunishCommand != null) {
            if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Command 'unpunish' found. Setting executor and tab completer.");
            unpunishCommand.setExecutor(mainCommand); // Set executor
            unpunishCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            if (configManager.isDebugEnabled()) getLogger().warning("[COMMAND DEBUG] Command 'unpunish' is NULL! Registration FAILED. Check plugin.yml for 'unpunish' command definition.");
        }

        // Register 'softban' command - TOP LEVEL COMMAND, mirroring /crown punish softban
        if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Attempting to get command: 'softban'");
        PluginCommand softbanCommand = getCommand("softban");
        if (softbanCommand != null) {
            if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Command 'softban' found. Setting executor and tab completer.");
            softbanCommand.setExecutor(mainCommand); // Set executor
            softbanCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            if (configManager.isDebugEnabled()) getLogger().warning("[COMMAND DEBUG] Command 'softban' is NULL! Registration FAILED. Check plugin.yml for 'softban' command definition.");
        }

        // Register 'freeze' command - TOP LEVEL COMMAND - NEW
        if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Attempting to get command: 'freeze'");
        PluginCommand freezeCommand = getCommand("freeze");
        if (freezeCommand != null) {
            if (configManager.isDebugEnabled()) getLogger().info("[COMMAND DEBUG] Command 'freeze' found. Setting executor and tab completer.");
            freezeCommand.setExecutor(mainCommand); // Set executor
            freezeCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            if (configManager.isDebugEnabled()) getLogger().warning("[COMMAND DEBUG] Command 'freeze' is NULL! Registration FAILED. Check plugin.yml for 'freeze' command definition.");
        }
    }

    /**
     * Registers plugin event listeners.
     */
    public void registerEvents() {
        getServer().getPluginManager().registerEvents(new MenuListener(this), this); // Register MenuListener for menu interactions
        getServer().getPluginManager().registerEvents(new CommandBlockerListener(this), this); // Register CommandBlockerListener for softban command blocking
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this); // Register FreezeListener for freeze punishment - NEW
        freezeListener = new FreezeListener(this); // Initialize FreezeListener - NEW: Initialize and store FreezeListener
    }

    /**
     * Gets the MenuListener instance. - NEW
     *
     * @return MenuListener instance.
     */
    public MenuListener getMenuListener() { // NEW: getMenuListener method
        return menuListener;
    }

    /**
     * Gets the FreezeListener instance. - NEW
     *
     * @return FreezeListener instance.
     */
    public FreezeListener getFreezeListener() {
        return freezeListener;
    }

    /**
     * Gets the MainConfigManager instance.
     *
     * @return MainConfigManager instance.
     */
    public MainConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the DatabaseManager instance.
     *
     * @return DatabaseManager instance.
     */
    public DatabaseManager getSoftBanDatabaseManager() {
        return databaseManager;
    }

    /**
     * Checks if PlaceholderAPI is enabled.
     *
     * @return true if PlaceholderAPI is enabled, false otherwise.
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    /**
     * Gets the HashMap of frozen players. - NEW
     *
     * @return HashMap containing frozen players' UUIDs and their frozen status.
     */
    public HashMap<UUID, Boolean> getPluginFrozenPlayers() {
        return pluginFrozenPlayers;
    }
}
// CrownPunishments.java
package cp.corona.crownpunishments;

import cp.corona.commands.MainCommand;
import cp.corona.config.MainConfigManager;
import cp.corona.database.SoftBanDatabaseManager;
import cp.corona.listeners.CommandBlockerListener;
import cp.corona.listeners.MenuListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main plugin class for CrownPunishments.
 * Initializes and manages plugin components, commands, and listeners.
 */
public final class CrownPunishments extends JavaPlugin {
    private final String version = getDescription().getVersion();
    private MainConfigManager configManager;
    private SoftBanDatabaseManager softBanDatabaseManager; // Database Manager
    private boolean placeholderAPIEnabled;

    /**
     * Called when the plugin is enabled.
     * Initializes configuration, database, commands, and event listeners.
     */
    @Override
    public void onEnable() {
        // Initialize configuration manager
        this.configManager = new MainConfigManager(this);
        // Initialize database manager, setting up database connection and tables
        this.softBanDatabaseManager = new SoftBanDatabaseManager(this);
        // Check if PlaceholderAPI is installed and enabled
        placeholderAPIEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        registerCommands(); // Register command handlers
        registerEvents();   // Register event listeners

        // Send plugin enabled message to console, using config for messages and prefix
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                configManager.getMessage("messages.plugin_enabled") + " Version: " + version));
        // Log debug mode status if enabled in config
        if (configManager.isDebugEnabled()) {
            getLogger().log(Level.INFO, "[CrownPunishments] Debug mode is enabled.");
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
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'crown'");
        PluginCommand crownCommand = getCommand("crown");
        if (crownCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'crown' found. Setting executor and tab completer.");
            crownCommand.setExecutor(mainCommand); // Set command executor
            crownCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'crown' is NULL! Registration FAILED. Check plugin.yml for 'crown' command definition.");
        }

        // Register 'punish' command alias - for direct /punish usage
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'punish'");
        PluginCommand punishCommand = getCommand("punish");
        if (punishCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'punish' found. Setting executor and tab completer.");
            punishCommand.setExecutor(mainCommand); // Set command executor
            punishCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'punish' is NULL! Registration FAILED. Check plugin.yml for 'punish' command definition.");
        }

        // Register 'kick' command - though might be handled internally now, keep logging for troubleshooting
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'kick'");
        PluginCommand kickCommand = getCommand("kick");
        if (kickCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'kick' found. Setting executor and tab completer.");
            kickCommand.setExecutor(mainCommand); // Set command executor (though MainCommand might not directly handle /kick anymore)
            kickCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'kick' is NULL! Registration FAILED. Check plugin.yml for 'kick' command definition.");
        }

        // Register 'warn' command - same as kick, keep logging for consistency
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'warn'");
        PluginCommand warnCommand = getCommand("warn");
        if (warnCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'warn' found. Setting executor and tab completer.");
            warnCommand.setExecutor(mainCommand); // Set executor
            warnCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'warn' is NULL! Registration FAILED. Check plugin.yml for 'warn' command definition.");
        }

        // Register 'crownpunishments' command alias - for full command name usage
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'crownpunishments'");
        PluginCommand crownPunishmentsCommand = getCommand("crownpunishments");
        if (crownPunishmentsCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'crownpunishments' found. Setting executor and tab completer.");
            crownPunishmentsCommand.setExecutor(mainCommand); // Set executor
            crownPunishmentsCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'crownpunishments' is NULL! Registration FAILED. Check plugin.yml for 'crownpunishments' command definition.");
        }

        // Register 'unpunish' command - TOP LEVEL COMMAND, SEPARATE FROM CROWN SUBCOMMAND
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'unpunish'");
        PluginCommand unpunishCommand = getCommand("unpunish");
        if (unpunishCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'unpunish' found. Setting executor and tab completer.");
            unpunishCommand.setExecutor(mainCommand); // Set executor
            unpunishCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'unpunish' is NULL! Registration FAILED. Check plugin.yml for 'unpunish' command definition.");
        }

        // Register 'softban' command - TOP LEVEL COMMAND, mirroring /crown punish softban
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'softban'");
        PluginCommand softbanCommand = getCommand("softban");
        if (softbanCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'softban' found. Setting executor and tab completer.");
            softbanCommand.setExecutor(mainCommand); // Set executor
            softbanCommand.setTabCompleter(mainCommand); // Set tab completer
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'softban' is NULL! Registration FAILED. Check plugin.yml for 'softban' command definition.");
        }
    }

    /**
     * Registers plugin event listeners.
     */
    public void registerEvents() {
        getServer().getPluginManager().registerEvents(new MenuListener(this), this); // Register MenuListener for menu interactions
        getServer().getPluginManager().registerEvents(new CommandBlockerListener(this), this); // Register CommandBlockerListener for softban command blocking
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
     * Gets the SoftBanDatabaseManager instance.
     *
     * @return SoftBanDatabaseManager instance.
     */
    public SoftBanDatabaseManager getSoftBanDatabaseManager() {
        return softBanDatabaseManager;
    }

    /**
     * Checks if PlaceholderAPI is enabled.
     *
     * @return true if PlaceholderAPI is enabled, false otherwise.
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }
}
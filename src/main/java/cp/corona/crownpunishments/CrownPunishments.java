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
        this.configManager = new MainConfigManager(this);
        this.softBanDatabaseManager = new SoftBanDatabaseManager(this); // Initialize database manager
        placeholderAPIEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        registerCommands();
        registerEvents();

        // Using configManager to get prefix and messages
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                configManager.getMessage("messages.plugin_enabled") + " Version: " + version));
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
        // Using configManager to get prefix and messages
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                configManager.getMessage("messages.plugin_disabled")));
    }

    /**
     * Registers plugin commands with detailed logging for debugging.
     */
    public void registerCommands() {
        MainCommand mainCommand = new MainCommand(this);

        // Debug registration for 'crown' command
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'crown'");
        PluginCommand crownCommand = getCommand("crown");
        if (crownCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'crown' found. Setting executor and tab completer.");
            crownCommand.setExecutor(mainCommand);
            crownCommand.setTabCompleter(mainCommand);
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'crown' is NULL! Registration FAILED. Check plugin.yml for 'crown' command definition.");
        }

        // Debug registration for 'punish' command
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'punish'");
        PluginCommand punishCommand = getCommand("punish");
        if (punishCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'punish' found. Setting executor and tab completer.");
            punishCommand.setExecutor(mainCommand);
            punishCommand.setTabCompleter(mainCommand);
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'punish' is NULL! Registration FAILED. Check plugin.yml for 'punish' command definition.");
        }

        // Debug registration for 'kick' command (even though you might not be using it standalone now, keep logging for troubleshoot)
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'kick'");
        PluginCommand kickCommand = getCommand("kick");
        if (kickCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'kick' found. Setting executor and tab completer.");
            kickCommand.setExecutor(mainCommand);
            kickCommand.setTabCompleter(mainCommand);
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'kick' is NULL! Registration FAILED. Check plugin.yml for 'kick' command definition.");
        }

        // Debug registration for 'warn' command (same as kick, keep logging)
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'warn'");
        PluginCommand warnCommand = getCommand("warn");
        if (warnCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'warn' found. Setting executor and tab completer.");
            warnCommand.setExecutor(mainCommand);
            warnCommand.setTabCompleter(mainCommand);
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'warn' is NULL! Registration FAILED. Check plugin.yml for 'warn' command definition.");
        }

        // Debug registration for 'crownpunishments' command (alias)
        getLogger().info("[COMMAND DEBUG] Attempting to get command: 'crownpunishments'");
        PluginCommand crownPunishmentsCommand = getCommand("crownpunishments");
        if (crownPunishmentsCommand != null) {
            getLogger().info("[COMMAND DEBUG] Command 'crownpunishments' found. Setting executor and tab completer.");
            crownPunishmentsCommand.setExecutor(mainCommand);
            crownPunishmentsCommand.setTabCompleter(mainCommand);
        } else {
            getLogger().warning("[COMMAND DEBUG] Command 'crownpunishments' is NULL! Registration FAILED. Check plugin.yml for 'crownpunishments' command definition.");
        }
    }

    /**
     * Registers plugin event listeners.
     */
    public void registerEvents() {
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockerListener(this), this); // Register CommandBlockerListener
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
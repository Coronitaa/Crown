// Crown.java
package cp.corona.crown;

import cp.corona.Metrics;
import cp.corona.commands.MainCommand;
import cp.corona.config.MainConfigManager;
import cp.corona.database.DatabaseManager;
import cp.corona.listeners.CommandBlockerListener;
import cp.corona.listeners.FreezeListener;
import cp.corona.listeners.MenuListener;
import cp.corona.listeners.PlayerChatListener; // New import
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public final class Crown extends JavaPlugin {
    private final String version = getDescription().getVersion();
    private MainConfigManager configManager;
    private DatabaseManager databaseManager;
    private boolean placeholderAPIEnabled;
    private final HashMap<UUID, Boolean> pluginFrozenPlayers = new HashMap<>();

    private MenuListener menuListener;
    private FreezeListener freezeListener;

    @Override
    public void onEnable() {
        this.configManager = new MainConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.placeholderAPIEnabled = configManager.isPlaceholderAPIEnabled();

        if (placeholderAPIEnabled) {
            configManager.registerPlaceholders();
        }

        this.menuListener = new MenuListener(this);

        registerCommands();
        registerEvents();

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                configManager.getMessage("messages.plugin_enabled") + " Version: " + version));
        if (configManager.isDebugEnabled()) {
            getLogger().log(Level.INFO, "[Crown] Debug mode is enabled.");
        }
        getLogger().log(Level.INFO, "[Crown] PlaceholderAPI integration is " + (isPlaceholderAPIEnabled() ? "enabled" : "disabled") + ".");

        try {
            new Metrics(this, 25939);
            getLogger().log(Level.INFO, "[Crown] bStats metrics initialized successfully.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[Crown] Failed to initialize bStats metrics:", e);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                configManager.getMessage("messages.plugin_disabled")));
    }

    public void registerCommands() {
        MainCommand mainCommand = new MainCommand(this);
        getCommand("crown").setExecutor(mainCommand);
        getCommand("crown").setTabCompleter(mainCommand);
        getCommand("punish").setExecutor(mainCommand);
        getCommand("punish").setTabCompleter(mainCommand);
        getCommand("unpunish").setExecutor(mainCommand);
        getCommand("unpunish").setTabCompleter(mainCommand);
        getCommand("softban").setExecutor(mainCommand);
        getCommand("softban").setTabCompleter(mainCommand);
        getCommand("freeze").setExecutor(mainCommand);
        getCommand("freeze").setTabCompleter(mainCommand);
        getCommand("ban").setExecutor(mainCommand);
        getCommand("ban").setTabCompleter(mainCommand);
        getCommand("kick").setExecutor(mainCommand);
        getCommand("kick").setTabCompleter(mainCommand);
        getCommand("mute").setExecutor(mainCommand);
        getCommand("mute").setTabCompleter(mainCommand);
        getCommand("warn").setExecutor(mainCommand);
        getCommand("warn").setTabCompleter(mainCommand);
    }

    public void registerEvents() {
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockerListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this); // Register new listener
        freezeListener = new FreezeListener(this);
    }

    public MenuListener getMenuListener() {
        return menuListener;
    }

    public FreezeListener getFreezeListener() {
        return freezeListener;
    }

    public MainConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getSoftBanDatabaseManager() {
        return databaseManager;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    public HashMap<UUID, Boolean> getPluginFrozenPlayers() {
        return pluginFrozenPlayers;
    }
}
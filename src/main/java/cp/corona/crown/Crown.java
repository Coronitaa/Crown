// src/main/java/cp/corona/crown/Crown.java
package cp.corona.crown;

import cp.corona.Metrics;
import cp.corona.commands.MainCommand;
import cp.corona.config.MainConfigManager;
import cp.corona.database.DatabaseManager;
import cp.corona.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
    private final Set<String> registeredCommands = new HashSet<>();

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
        // Register all commands and their aliases
        registerCommand("crown", mainCommand);
        registerCommand("punish", mainCommand);
        registerCommand("unpunish", mainCommand);
        registerCommand("check", mainCommand);
        registerCommand("softban", mainCommand);
        registerCommand("freeze", mainCommand);
        registerCommand("ban", mainCommand);
        registerCommand("kick", mainCommand);
        registerCommand("mute", mainCommand);
        registerCommand("warn", mainCommand);
        registerCommand("unban", mainCommand);
        registerCommand("unwarn", mainCommand);
        registerCommand("unmute", mainCommand);
        registerCommand("unfreeze", mainCommand);
        registerCommand("unsoftban", mainCommand);
    }

    private void registerCommand(String commandName, MainCommand executor) {
        PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            registeredCommands.add(commandName.toLowerCase());
            if (command.getAliases() != null) {
                for (String alias : command.getAliases()) {
                    registeredCommands.add(alias.toLowerCase());
                }
            }
        }
    }


    public void registerEvents() {
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockerListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new MuteListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PunishmentListener(this), this);
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

    public Set<String> getRegisteredCommands() {
        return registeredCommands;
    }
}
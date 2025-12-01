// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\crown\Crown.java
package cp.corona.crown;

import cp.corona.Metrics;
import cp.corona.commands.MainCommand;
import cp.corona.config.MainConfigManager;
import cp.corona.database.DatabaseManager;
import cp.corona.moderator.ModeratorStateUpdateTask;
import cp.corona.report.ReportBookManager;
import cp.corona.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import cp.corona.commands.ModeratorCommand; // ADDED
import cp.corona.moderator.ModeratorModeListener; // ADDED
import cp.corona.moderator.ModeratorModeManager;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class Crown extends JavaPlugin {
    private final String version = getDescription().getVersion();
    private MainConfigManager configManager;
    private DatabaseManager databaseManager;
    private boolean placeholderAPIEnabled;
    private ModeratorModeManager moderatorModeManager;
    private BukkitTask moderatorStateUpdateTask;
    private ReportBookManager reportBookManager;
    private final Map<UUID, Boolean> pluginFrozenPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mutedPlayersCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> softBannedPlayersCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> softbannedCommandsCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> playerReportTimestamps = new ConcurrentHashMap<>();


    private MenuListener menuListener;
    private FreezeListener freezeListener;
    private PunishmentListener punishmentListener;
    private final Set<String> registeredCommands = new HashSet<>();

    @Override
    public void onEnable() {
        this.configManager = new MainConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.reportBookManager = new ReportBookManager(this);
        this.placeholderAPIEnabled = configManager.isPlaceholderAPIEnabled();

        if (placeholderAPIEnabled) {
            configManager.registerPlaceholders();
        }

        this.menuListener = new MenuListener(this);
        this.freezeListener = new FreezeListener(this);
        this.punishmentListener = new PunishmentListener(this);
        this.moderatorModeManager = new ModeratorModeManager(this);
        this.moderatorStateUpdateTask = new ModeratorStateUpdateTask(this).runTaskTimerAsynchronously(this, 0L, 40L);


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
        // ADDED: Stop the task
        if (this.moderatorStateUpdateTask != null && !this.moderatorStateUpdateTask.isCancelled()) {
            this.moderatorStateUpdateTask.cancel();
        }

        if (configManager != null) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    configManager.getMessage("messages.plugin_disabled")));
        }
    }

    public void registerCommands() {
        MainCommand mainCommand = new MainCommand(this);
        // Register all commands and their aliases
        registerCommand("crown", mainCommand);
        registerCommand("punish", mainCommand);
        registerCommand("unpunish", mainCommand);
        registerCommand("check", mainCommand);
        registerCommand("report", mainCommand);
        registerCommand("reports", mainCommand);
        registerCommand("history", mainCommand);
        registerCommand("profile", mainCommand);
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
        PluginCommand modCommand = getCommand("mod");
        if (modCommand != null) {
            ModeratorCommand moderatorExecutor = new ModeratorCommand(this);
            modCommand.setExecutor(moderatorExecutor);
        }
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
        getServer().getPluginManager().registerEvents(this.menuListener, this);
        getServer().getPluginManager().registerEvents(new CommandBlockerListener(this), this);
        getServer().getPluginManager().registerEvents(this.freezeListener, this);
        getServer().getPluginManager().registerEvents(new MuteListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(this.punishmentListener, this);
        getServer().getPluginManager().registerEvents(new ModeratorModeListener(this), this);
    }

    public void playSound(Player player, String soundKey) {
        String soundName = configManager.getSoundName(soundKey);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid sound configured for key '" + soundKey + "': " + soundName);
            }
        }
    }

    public MenuListener getMenuListener() {
        return menuListener;
    }

    public FreezeListener getFreezeListener() {
        return freezeListener;
    }

    public PunishmentListener getPunishmentListener() {
        return punishmentListener;
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

    public Map<UUID, Boolean> getPluginFrozenPlayers() {
        return pluginFrozenPlayers;
    }
    public ModeratorModeManager getModeratorModeManager() {
        return moderatorModeManager;
    }
    public Map<UUID, Long> getMutedPlayersCache() {
        return mutedPlayersCache;
    }

    public Map<UUID, Long> getSoftBannedPlayersCache() {
        return softBannedPlayersCache;
    }

    public Map<UUID, List<String>> getSoftbannedCommandsCache() {
        return softbannedCommandsCache;
    }
    public ReportBookManager getReportBookManager() { // ADDED
        return reportBookManager;
    }
    public Set<String> getRegisteredCommands() {
        return registeredCommands;
    }

    public Map<UUID, List<Long>> getPlayerReportTimestamps() {
        return playerReportTimestamps;
    }
}
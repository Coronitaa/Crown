// MainConfigManager.java
package cp.corona.config;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
import cp.corona.menus.actions.ClickAction;
import cp.corona.utils.ColorUtils;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Manages all plugin configurations, including messages, plugin settings, and menu configurations.
 * Provides methods to load, reload, and access configuration values, as well as process placeholders.
 *
 * Updated to load multiple click actions for menu items and handle console commands.
 */
public class MainConfigManager {
    private final CustomConfig messagesConfig;
    private final CustomConfig pluginConfig;
    private final CustomConfig punishMenuConfig;
    private final CustomConfig punishDetailsMenuConfig;
    private final CustomConfig timeSelectorMenuConfig;
    private final CustomConfig historyMenuConfig; // New config for history menu

    private final CrownPunishments plugin;
    private final String defaultTimeUnit; // Store default time unit
    private boolean debugEnabled; // Debugging flag
    private CrownPunishmentsPlaceholders placeholders; // PlaceholderAPI Expansion

    /**
     * Constructor for MainConfigManager.
     * Initializes and registers custom configuration files.
     *
     * @param plugin Instance of the main plugin class.
     */
    public MainConfigManager(CrownPunishments plugin) {
        this.plugin = plugin;

        // Load configurations from separated files
        messagesConfig = new CustomConfig("messages.yml", null, plugin, false);
        pluginConfig = new CustomConfig("config.yml", null, plugin, false);
        punishMenuConfig = new CustomConfig("punish_menu.yml", "menus", plugin, false);
        punishDetailsMenuConfig = new CustomConfig("punish_details_menu.yml", "menus", plugin, false);
        timeSelectorMenuConfig = new CustomConfig("time_selector_menu.yml", "menus", plugin, false);
        historyMenuConfig = new CustomConfig("history_menu.yml", "menus", plugin, false); // Load history menu config

        messagesConfig.registerConfig();
        pluginConfig.registerConfig();
        punishMenuConfig.registerConfig();
        punishDetailsMenuConfig.registerConfig();
        timeSelectorMenuConfig.registerConfig();
        historyMenuConfig.registerConfig(); // Register history menu config

        loadConfig();
        this.defaultTimeUnit = getTimeUnit("default"); // Load default time unit on startup
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false); // Load debug config
        registerPlaceholders(); // Register PlaceholderAPI placeholders
    }

    /**
     * Loads configuration from files.
     */
    public void loadConfig() {
        messagesConfig.reloadConfig();
        pluginConfig.reloadConfig();
        punishMenuConfig.reloadConfig();
        punishDetailsMenuConfig.reloadConfig();
        timeSelectorMenuConfig.reloadConfig();
        historyMenuConfig.reloadConfig(); // Reload history menu config
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false); // Reload debug config
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Configurations reloaded and debug mode is " + (isDebugEnabled() ? "enabled" : "disabled"));
        }
        if (placeholders != null) {
            placeholders.unregister(); // Unregister old placeholders
            placeholders.register();   // Re-register placeholders after config reload
        }
    }

    /**
     * Checks if debug mode is enabled.
     *
     * @return true if debug mode is enabled, false otherwise.
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Registers custom placeholders for PlaceholderAPI.
     */
    private void registerPlaceholders() {
        if (plugin.isPlaceholderAPIEnabled()) {
            if (placeholders == null) {
                placeholders = new CrownPunishmentsPlaceholders();
            }
            boolean registered = placeholders.register(); // Capture registration result
            if (registered) {
                plugin.getLogger().log(Level.INFO, "[MainConfigManager] PlaceholderAPI placeholders registered successfully.");
            } else {
                plugin.getLogger().log(Level.WARNING, "[MainConfigManager] PlaceholderAPI placeholders failed to register. Check for errors.");
            }
        } else {
            plugin.getLogger().log(Level.WARNING, "[MainConfigManager] PlaceholderAPI not found, placeholders not registered.");
        }
    }


    /**
     * Processes placeholders in a given text with enhanced color code support.
     *
     * @param text   The text to process.
     * @param target The target player, can be null.
     * @return The processed text with placeholders replaced and colors formatted.
     */
    public String processPlaceholders(String text, OfflinePlayer target) {
        String prefix = pluginConfig.getConfig().getString("prefix", "&8[&6C&cP&8] &r");
        text = MessageUtils.getColorMessage(text).replace("{prefix}", prefix);

        if (target == null) return text;

        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        text = text
                .replace("{target}", targetName)
                .replace("{target_online}", target.isOnline() ? "Yes" : "No")
                .replace("{target_ip}", onlineTarget != null && onlineTarget.getAddress() != null ?
                        onlineTarget.getAddress().getHostString() : "-")
                .replace("{target_coords}", onlineTarget != null ?
                        String.format("%d, %d, %d",
                                onlineTarget.getLocation().getBlockX(),
                                onlineTarget.getLocation().getBlockY(),
                                onlineTarget.getLocation().getBlockZ()) : "-")
                .replace("{target_world}", onlineTarget != null ?
                        onlineTarget.getWorld().getName() : "-");

        // Softban Placeholders
        boolean isSoftBanned = plugin.getSoftBanDatabaseManager().isSoftBanned(target.getUniqueId());
        String softbanStatus = isSoftBanned ? "&cSoftBanned" : "&aNot SoftBanned";
        text = text.replace("{target_softban_status}", ColorUtils.translateRGBColors(softbanStatus)); // Apply ColorUtils here

        if (isSoftBanned) {
            long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(target.getUniqueId());
            String remainingTimeFormatted;
            if (endTime == Long.MAX_VALUE) {
                remainingTimeFormatted = getMessage("messages.permanent_time_display");
            } else {
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                remainingTimeFormatted = TimeUtils.formatTime(remainingSeconds, this);
            }
            text = text.replace("{target_softban_remaining_time}", ColorUtils.translateRGBColors(remainingTimeFormatted)); // Apply ColorUtils here

        } else {
            text = text.replace("{target_softban_remaining_time}", "N/A");
        }

        if (plugin.isPlaceholderAPIEnabled() && target.isOnline()) {
            text = PlaceholderAPI.setPlaceholders(target.getPlayer(), text);
        }

        return text;
    }

    /**
     * Gets a message from messages.yml and processes placeholders.
     *
     * @param path       Path to the message in messages.yml.
     * @param replacements Placeholders to replace in the message.
     * @return The processed message.
     */
    public String getMessage(String path, String... replacements) {
        String message = messagesConfig.getConfig().getString(path, "");
        if (message == null) return ""; // Handle null message from config
        message = processPlaceholders(message, null);

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break;
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        return message;
    }

    //<editor-fold desc="Menu Item Getters">

    /**
     * Gets menu text from punish_menu.yml and processes placeholders.
     *
     * @param path   Path to the menu text.
     * @param target The target player for placeholders.
     * @return The processed menu text.
     */
    public String getMenuText(String path, OfflinePlayer target) {
        String text = punishMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return ""; // Handle null menu text from config
        return processPlaceholders(text, target);
    }

    /**
     * Gets details menu text from punish_details_menu.yml and processes placeholders.
     *
     * @param path            Path to the details menu text.
     * @param target          The target player for placeholders.
     * @param punishmentType  The type of punishment (ban, mute, softban, kick, warn).
     * @return The processed details menu text.
     */
    public String getDetailsMenuText(String path, OfflinePlayer target, String punishmentType) {
        String text = punishDetailsMenuConfig.getConfig().getString("menu.punish_details." + punishmentType + "." + path, "");
        if (text == null) return ""; // Handle null detail menu text from config
        return processPlaceholders(text, target);
    }

    /**
     * Gets history menu text from history_menu.yml and processes placeholders.
     *
     * @param path   Path to the history menu text.
     * @param target The target player for placeholders.
     * @return The processed history menu text.
     */
    public String getHistoryMenuText(String path, OfflinePlayer target) {
        String text = historyMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return ""; // Handle null history menu text from config
        return processPlaceholders(text, target);
    }


    /**
     * Gets a MenuItem from punish_menu.yml.
     *
     * @param itemKey Item key in the config.
     * @return The MenuItem.
     */
    public MenuItem getPunishMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(punishMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    /**
     * Gets a details MenuItem from punish_details_menu.yml.
     *
     * @param punishmentType The type of punishment (ban, mute, softban, kick, warn).
     * @param itemKey       Item key in the config.
     * @return The MenuItem.
     */
    public MenuItem getDetailsMenuItemConfig(String punishmentType, String itemKey) {
        return loadMenuItemFromConfig(punishDetailsMenuConfig.getConfig(), "menu.punish_details." + punishmentType + ".items." + itemKey);
    }

    /**
     * Gets a time option MenuItem from time_selector_menu.yml.
     *
     * @param itemKey Item key in the config.
     * @return The MenuItem.
     */
    public MenuItem getTimeOptionMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_options." + itemKey);
    }

    /**
     * Gets a time selector MenuItem from time_selector_menu.yml.
     *
     * @param itemKey Item key in the config.
     * @return The MenuItem.
     */
    public MenuItem getTimeSelectorMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_selector_items." + itemKey);
    }

    /**
     * Gets a history MenuItem from history_menu.yml.
     *
     * @param itemKey Item key in the config.
     * @return The MenuItem.
     */
    public MenuItem getHistoryMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(historyMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    /**
     * Gets time selector menu title from time_selector_menu.yml and processes placeholders.
     *
     * @param target The target player for placeholders.
     * @return The processed time selector menu title.
     */
    public String getTimeSelectorMenuTitle(OfflinePlayer target) {
        String title = timeSelectorMenuConfig.getConfig().getString("menu.time_selector_title", "");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }

    /**
     * Gets history menu title from history_menu.yml and processes placeholders.
     *
     * @param target The target player for placeholders.
     * @return The processed history menu title.
     */
    public String getHistoryMenuTitle(OfflinePlayer target) {
        String title = historyMenuConfig.getConfig().getString("menu.title", "");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }


    //</editor-fold>

    //<editor-fold desc="Menu Item Loading">

    /**
     * Loads a MenuItem from configuration.
     *
     * @param config     The FileConfiguration to load from.
     * @param configPath The path to the item configuration.
     * @return The loaded MenuItem.
     */
    private MenuItem loadMenuItemFromConfig(FileConfiguration config, String configPath) {
        MenuItem menuItem = new MenuItem();
        menuItem.setMaterial(config.getString(configPath + ".material", "STONE"));
        menuItem.setName(config.getString(configPath + ".name"));
        menuItem.setLore(config.getStringList(configPath + ".lore"));
        menuItem.setPlayerHeadValue(config.getString(configPath + ".player_head_value"));
        menuItem.setPlayerHeadName(config.getString(configPath + ".player_head"));
        if (config.isInt(configPath + ".custom_model_data")) {
            menuItem.setCustomModelData(config.getInt(configPath + ".custom_model_data"));
        }
        if (config.isInt(configPath + ".quantity")) {
            menuItem.setQuantity(config.getInt(configPath + ".quantity"));
        }
        List<Integer> slots = parseSlots(config.getString(configPath + ".slot"));
        menuItem.setSlots(slots);
        menuItem.setClickSound(config.getString(configPath + ".click_sound"));
        if (config.isDouble(configPath + ".click_volume")) {
            menuItem.setClickVolume( (float) config.getDouble(configPath + ".click_volume"));
        }
        if (config.isDouble(configPath + ".click_pitch")) {
            menuItem.setClickPitch((float) config.getDouble(configPath + ".click_pitch"));
        }

        // Load Click Actions for left click - Updated to load multiple actions
        List<String> leftClickActionConfigs = config.getStringList(configPath + ".left_click_actions");
        if (leftClickActionConfigs != null && !leftClickActionConfigs.isEmpty()) {
            List<MenuItem.ClickActionData> leftClickActions = leftClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .collect(Collectors.toList());
            menuItem.setLeftClickActions(leftClickActions);
        }

        // Load Click Actions for right click - Updated to load multiple actions
        List<String> rightClickActionConfigs = config.getStringList(configPath + ".right_click_actions");
        if (rightClickActionConfigs != null && !rightClickActionConfigs.isEmpty()) {
            List<MenuItem.ClickActionData> rightClickActions = rightClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .collect(Collectors.toList());
            menuItem.setRightClickActions(rightClickActions);
        }

        return menuItem;
    }


    /**
     * Parses a slot string that can be a single number, a range (e.g., "10-15"), or multiple ranges/numbers comma-separated (e.g., "1,3,5-10").
     * Supports multiple background items by splitting configuration and processing each part.
     *
     * @param slotConfig The slot configuration string, can contain comma-separated values and ranges.
     * @return A list of slots.
     */
    private List<Integer> parseSlots(String slotConfig) {
        List<Integer> slots = new ArrayList<>();
        if (slotConfig == null) return slots;

        String[] parts = slotConfig.split(","); // Split by comma to handle multiple ranges/slots
        for (String part : parts) {
            part = part.trim(); // Trim each part to remove leading/trailing spaces
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0]);
                        int end = Integer.parseInt(range[1]);
                        for (int i = start; i <= end; i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid slot range: " + part + " in " + slotConfig);
                    }
                } else {
                    plugin.getLogger().warning("Invalid slot range format: " + part + " in " + slotConfig);
                }
            } else {
                try {
                    slots.add(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid slot number: " + part + " in " + slotConfig);
                }
            }
        }
        return slots;
    }


    //</editor-fold>


    /**
     * Gets details menu item lore from punish_details_menu.yml and processes placeholders with replacements.
     *
     * @param punishmentType The type of punishment (ban, mute, softban, kick, warn).
     * @param itemKey       Item key in the config.
     * @param target        The target player for placeholders.
     * @param replacements  Placeholders to replace in the lore.
     * @return The processed details menu item lore.
     */
    public List<String> getDetailsMenuItemLore(String punishmentType, String itemKey, OfflinePlayer target, String... replacements) {
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getDetailsMenuItemLore called for punishmentType=" + punishmentType + ", itemKey=" + itemKey + ", target=" + target.getName() + ", replacements=" + Arrays.toString(replacements)); //Added logging
        }
        List<String> lore = new ArrayList<>();
        List<String> configLore = punishDetailsMenuConfig.getConfig().getStringList("menu.punish_details." + punishmentType + ".items." + itemKey + ".lore");
        if (configLore == null) {
            plugin.getLogger().warning("[MainConfigManager] Lore config list is null for path: menu.punish_details." + punishmentType + ".items." + itemKey + ".lore");
            return lore; // Return empty lore to avoid NullPointerException
        }
        for (String line : configLore) {
            String processedLine = processPlaceholders(line, target);
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                processedLine = processedLine.replace(replacements[i], replacements[i + 1]);
            }
            lore.add(processedLine);
        }
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed lore: " + lore); // Added logging for processed lore
        }
        return lore;
    }

    /**
     * Gets history menu item lore from history_menu.yml and processes placeholders with replacements.
     *
     * @param itemKey       Item key in the config.
     * @param target        The target player for placeholders.
     * @param replacements  Placeholders to replace in the lore.
     * @return The processed history menu item lore.
     */
    public List<String> getHistoryMenuItemLore(String itemKey, OfflinePlayer target, String... replacements) {
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getHistoryMenuItemLore called for itemKey=" + itemKey + ", target=" + target.getName() + ", replacements=" + Arrays.toString(replacements));
        }
        List<String> lore = new ArrayList<>();
        List<String> configLore = historyMenuConfig.getConfig().getStringList("menu.items." + itemKey + ".lore");
        if (configLore == null) {
            plugin.getLogger().warning("[MainConfigManager] Lore config list is null for path: menu.items." + itemKey + ".lore");
            return lore; // Return empty lore to avoid NullPointerException
        }
        for (String line : configLore) {
            String processedLine = processPlaceholders(line, target);
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                processedLine = processedLine.replace(replacements[i], replacements[i + 1]);
            }
            lore.add(processedLine);
        }
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed history lore: " + lore);
        }
        return lore;
    }


    //</editor-fold>

    //<editor-fold desc="Command Getters">

    /**
     * Gets the ban command from config.yml.
     *
     * @return The ban command string.
     */
    public String getBanCommand() {
        return pluginConfig.getConfig().getString("commands.ban_command", "ban {target} {time} {reason}");
    }

    /**
     * Gets the mute command from config.yml.
     *
     * @return The mute command string.
     */
    public String getMuteCommand() {
        return pluginConfig.getConfig().getString("commands.mute_command", "mute {target} {time} {reason}");
    }

    /**
     * Gets the unban command from config.yml.
     *
     * @return The unban command string.
     */
    public String getUnbanCommand() {
        return pluginConfig.getConfig().getString("commands.unban_command", "pardon {target}");
    }

    /**
     * Gets the unwarn command from config.yml.
     *
     * @return The unmute command string.
     */
    public String getUnwarnCommand() {
        return pluginConfig.getConfig().getString("commands.unwarn_command", "");
    }

    /**
     * Gets the unmute command from config.yml.
     *
     * @return The unmute command string.
     */
    public String getUnmuteCommand() {
        return pluginConfig.getConfig().getString("commands.unmute_command", "unmute {target}");
    }


    /**
     * Gets the warn command from config.yml.
     *
     * @return The warn command string.
     */
    public String getWarnCommand() {
        return pluginConfig.getConfig().getString("commands.warn_command", "warn {target} {reason}");
    }

    /**
     * Gets the kick command from config.yml.
     *
     * @return The kick command string.
     */
    public String getKickCommand() {
        return pluginConfig.getConfig().getString("commands.kick_command", "kick {target} {reason}");
    }

    /**
     * Gets the soft ban command from config.yml (though softban is managed internally).
     *
     * @return The soft ban command string.
     */
    public String getSoftBanCommand() {
        return pluginConfig.getConfig().getString("commands.softban_command", ""); // Softban is handled internally
    }
    //</editor-fold>

    //<editor-fold desc="Time Option Getters">

    /**
     * Gets the list of time options keys from time_selector_menu.yml.
     *
     * @return List of time option keys.
     */
    public List<String> getTimeOptions() {
        Set<String> keys = timeSelectorMenuConfig.getConfig().getConfigurationSection("menu.time_options").getKeys(false);
        return new ArrayList<>(keys);
    }

    /**
     * Gets the value of a specific time option from time_selector_menu.yml.
     *
     * @return The time option value string.
     */
    public String getTimeOptionValue(String timeOptionKey) {
        return timeSelectorMenuConfig.getConfig().getString("menu.time_options." + timeOptionKey + ".value", "");
    }
    //</editor-fold>

    //<editor-fold desc="Sound Getters">

    /**
     * Gets sound name from config.yml by key.
     *
     * @param soundKey Sound key in the config.
     * @return The sound name string.
     */
    public String getSoundName(String soundKey) {
        return pluginConfig.getConfig().getString("sounds." + soundKey, "");
    }
    //</editor-fold>

    //<editor-fold desc="Time Unit Getters">

    /**
     * Gets the time unit from config.yml by key.
     *
     * @param unitKey Unit key in the config.
     * @return The time unit string.
     */
    public String getTimeUnit(String unitKey) {
        return pluginConfig.getConfig().getString("time_units." + unitKey, "");
    }

    /**
     * Gets the time unit for hours from config.yml.
     *
     * @return The hours time unit string.
     */
    public String getHoursTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.hours", "h");
    }

    /**
     * Gets the time unit for minutes from config.yml.
     *
     * @return The minutes time unit string.
     */
    public String getMinutesTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.minutes", "m");
    }

    /**
     * Gets the time unit for seconds from config.yml.
     *
     * @return The seconds time unit string.
     */
    public String getSecondsTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.seconds", "s");
    }

    /**
     * Gets the time unit for day from config.yml.
     *
     * @return The day time unit string.
     */
    public String getDayTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.day", "d");
    }

    /**
     * Gets the time unit for year from config.yml.
     *
     * @return The years time unit string.
     */
    public String getYearsTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.years", "y");
    }

    /**
     * Gets the default time unit.
     *
     * @return The default time unit string.
     */
    public String getDefaultTimeUnit() {
        return this.defaultTimeUnit;
    }
    //</editor-fold>

    //<editor-fold desc="Softban Configuration Getters">

    /**
     * Gets the blocked commands list from config.yml.
     *
     * @return List of blocked commands.
     */
    public List<String> getBlockedCommands() {
        return pluginConfig.getConfig().getStringList("softban.blocked_commands");
    }
    //</editor-fold>

    //<editor-fold desc="Database Configuration Getters">

    /**
     * Gets the database type from config.yml.
     *
     * @return The database type string.
     */
    public String getDatabaseType() {
        return pluginConfig.getConfig().getString("database.type", "sqlite"); // Default to sqlite
    }

    /**
     * Gets the database name from config.yml.
     *
     * @return The database name string.
     */
    public String getDatabaseName() {
        return pluginConfig.getConfig().getString("database.name", "crownpunishments");
    }

    /**
     * Gets the database address from config.yml.
     *
     * @return The database address string.
     */
    public String getDatabaseAddress() {
        return pluginConfig.getConfig().getString("database.address", "localhost");
    }

    /**
     * Gets the database port from config.yml.
     *
     * @return The database port string.
     */
    public String getDatabasePort() {
        return pluginConfig.getConfig().getString("database.port", "3306");
    }

    /**
     * Gets the database username from config.yml.
     *
     * @return The database username string.
     */
    public String getDatabaseUsername() {
        return pluginConfig.getConfig().getString("database.username", "username");
    }

    /**
     * Gets the database password from config.yml.
     *
     * @return The database password string.
     */
    public String getDatabasePassword() {
        return pluginConfig.getConfig().getString("database.password", "password");
    }
    //</editor-fold>

    /**
     * Inner class to handle PlaceholderAPI placeholders with enhanced logging.
     */
    private class CrownPunishmentsPlaceholders extends PlaceholderExpansion {

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "crownpunishments";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Corona";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean register(){
            boolean registered = super.register();
            if(registered) {
                plugin.getLogger().info("[PlaceholderAPI DEBUG] CrownPunishments placeholders REGISTERED with PlaceholderAPI.");
            } else {
                plugin.getLogger().warning("[PlaceholderAPI DEBUG] CrownPunishments placeholders FAILED to register with PlaceholderAPI.");
            }
            return registered;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null) {
                return null;
            }
            plugin.getLogger().info("[PlaceholderAPI DEBUG] onRequest called for player: " + player.getName() + ", params: " + params);
            if (params.equalsIgnoreCase("is_softbanned")) {
                boolean isSoftBanned = plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId());
                plugin.getLogger().info("[PlaceholderAPI DEBUG] is_softbanned parameter detected. Returning: " + isSoftBanned);
                return String.valueOf(isSoftBanned);
            }
            if (params.equalsIgnoreCase("softban_time_left")) {
                long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(player.getUniqueId());
                if (endTime == 0 || !plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId())) {
                    plugin.getLogger().info("[PlaceholderAPI DEBUG] softban_time_left parameter detected, not softbanned or endTime=0. Returning: N/A");
                    return "N/A";
                }
                if (endTime == Long.MAX_VALUE) {
                    plugin.getLogger().info("[PlaceholderAPI DEBUG] softban_time_left parameter detected, permanent softban. Returning: permanent_time_display message");
                    return getMessage("messages.permanent_time_display");
                }
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                String formattedTime = TimeUtils.formatTime(remainingSeconds, MainConfigManager.this);
                plugin.getLogger().info("[PlaceholderAPI DEBUG] softban_time_left parameter detected. Returning formatted time: " + formattedTime);
                return formattedTime;
            }
            plugin.getLogger().warning("[PlaceholderAPI DEBUG] Unknown parameter in onRequest: " + params);
            return null;
        }
    }
}
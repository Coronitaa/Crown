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
import org.bukkit.configuration.ConfigurationSection;
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
 * Manages all plugin configurations, including messages, plugin settings, and menu configurations.
 * Provides methods to load, reload, and access configuration values, as well as process placeholders.
 *
 * Updated to load multiple click actions for menu items and handle console commands.
 * Now also manages PlaceholderAPI integration and placeholder registration.
 *
 * **MODIFIED:**
 * - Implemented dynamic loading of menu items from configuration files.
 * - Updated methods to retrieve MenuItem configurations dynamically.
 * - **FIXED:** Placeholder replacement for punishment counts in menus.
 */
public class MainConfigManager {
    private final CustomConfig messagesConfig;
    private final CustomConfig pluginConfig;
    private final CustomConfig punishMenuConfig;
    private final CustomConfig punishDetailsMenuConfig;
    private final CustomConfig timeSelectorMenuConfig;
    private final CustomConfig historyMenuConfig;

    private final CrownPunishments plugin;
    private final String defaultTimeUnit;
    private boolean debugEnabled;
    private boolean placeholderAPIEnabled;
    private CrownPunishmentsPlaceholders placeholders;


    public MainConfigManager(CrownPunishments plugin) {
        this.plugin = plugin;


        messagesConfig = new CustomConfig("messages.yml", null, plugin, false);
        pluginConfig = new CustomConfig("config.yml", null, plugin, false);
        punishMenuConfig = new CustomConfig("punish_menu.yml", "menus", plugin, false);
        punishDetailsMenuConfig = new CustomConfig("punish_details_menu.yml", "menus", plugin, false);
        timeSelectorMenuConfig = new CustomConfig("time_selector_menu.yml", "menus", plugin, false);
        historyMenuConfig = new CustomConfig("history_menu.yml", "menus", plugin, false);

        messagesConfig.registerConfig();
        pluginConfig.registerConfig();
        punishMenuConfig.registerConfig();
        punishDetailsMenuConfig.registerConfig();
        timeSelectorMenuConfig.registerConfig();
        historyMenuConfig.registerConfig();

        loadConfig();
        this.defaultTimeUnit = getTimeUnit("default");
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false);


        this.placeholderAPIEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        registerPlaceholders();
    }


    public void loadConfig() {
        messagesConfig.reloadConfig();
        pluginConfig.reloadConfig();
        punishMenuConfig.reloadConfig();
        punishDetailsMenuConfig.reloadConfig();
        timeSelectorMenuConfig.reloadConfig();
        historyMenuConfig.reloadConfig();
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false);
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Configurations reloaded and debug mode is " + (isDebugEnabled() ? "enabled" : "disabled"));
            // Debug log to check if punish_menu.yml is loaded and menu.items section exists
            FileConfiguration punishMenuCfg = punishMenuConfig.getConfig();
            if (punishMenuCfg != null) {
                ConfigurationSection itemsSection = punishMenuCfg.getConfigurationSection("menu.items");
                if (itemsSection != null) {
                    if (isDebugEnabled()) plugin.getLogger().log(Level.INFO, "[MainConfigManager] Debug: 'menu.items' section found in punish_menu.yml");
                } else {
                    plugin.getLogger().warning("[MainConfigManager] WARNING: 'menu.items' section NOT found in punish_menu.yml!");
                }
            } else {
                plugin.getLogger().warning("[MainConfigManager] WARNING: punish_menu.yml FileConfiguration is NULL! Check file loading.");
            }
        }
        if (placeholders != null && placeholderAPIEnabled) {
            placeholders.unregister();
            placeholders.register();
        }
    }


    public boolean isDebugEnabled() {
        return debugEnabled;
    }


    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }



    public void registerPlaceholders() {
        if (placeholderAPIEnabled) {
            if (placeholders == null) {
                placeholders = new CrownPunishmentsPlaceholders(plugin);
            }
            boolean registered = placeholders.register();
            if (registered) {
                plugin.getLogger().log(Level.INFO, "[MainConfigManager] PlaceholderAPI placeholders registered successfully.");
            } else {
                plugin.getLogger().log(Level.WARNING, "[MainConfigManager] PlaceholderAPI placeholders failed to register. Check for errors.");
            }
        } else {
            plugin.getLogger().log(Level.WARNING, "[MainConfigManager] PlaceholderAPI not found, placeholders not registered.");
        }
    }



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


        boolean isSoftBanned = plugin.getSoftBanDatabaseManager().isSoftBanned(target.getUniqueId());
        String softbanStatus = isSoftBanned ? "&cSoftBanned" : "&aNot SoftBanned";
        text = text.replace("{target_softban_status}", ColorUtils.translateRGBColors(softbanStatus));

        if (isSoftBanned) {
            long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(target.getUniqueId());
            String remainingTimeFormatted;
            if (endTime == Long.MAX_VALUE) {
                remainingTimeFormatted = getMessage("messages.permanent_time_display");
            } else {
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                remainingTimeFormatted = TimeUtils.formatTime(remainingSeconds, this);
            }
            text = text.replace("{target_softban_remaining_time}", ColorUtils.translateRGBColors(remainingTimeFormatted));

        } else {
            text = text.replace("{target_softban_remaining_time}", "N/A");
        }

        // Get punishment counts and replace placeholders - FIX: Placeholder replacement for counts
        HashMap<String, Integer> counts = plugin.getSoftBanDatabaseManager().getPunishmentCounts(target.getUniqueId());
        text = text.replace("{ban_count}", String.valueOf(counts.getOrDefault("ban", 0)));
        text = text.replace("{mute_count}", String.valueOf(counts.getOrDefault("mute", 0)));
        text = text.replace("{kick_count}", String.valueOf(counts.getOrDefault("kick", 0)));
        text = text.replace("{softban_count}", String.valueOf(counts.getOrDefault("softban", 0)));
        text = text.replace("{warn_count}", String.valueOf(counts.getOrDefault("warn", 0)));
        text = text.replace("{freeze_count}", String.valueOf(counts.getOrDefault("freeze", 0)));


        if (plugin.isPlaceholderAPIEnabled() && target.isOnline()) {
            text = PlaceholderAPI.setPlaceholders(target.getPlayer(), text);
        }
        return text;
    }


    public String getMessage(String path, String... replacements) {
        String message = messagesConfig.getConfig().getString(path, "");
        if (message == null) return "";
        message = processPlaceholders(message, null);

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break;
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        return message;
    }


    //<editor-fold desc="Menu Item Getters">

    public String getMenuText(String path, OfflinePlayer target) {
        String text = punishMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }


    public String getDetailsMenuText(String path, OfflinePlayer target, String punishmentType) {
        String text = punishDetailsMenuConfig.getConfig().getString("menu.punish_details." + punishmentType + "." + path, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }


    public String getHistoryMenuText(String path, OfflinePlayer target) {
        String text = historyMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }


    /**
     * Gets punish menu item configuration from punish_menu.yml by item key.
     * @param itemKey Item key in the config.
     * @return The loaded MenuItem or null if not found.
     */
    public MenuItem getPunishMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(punishMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    /**
     * Gets details menu item configuration from punish_details_menu.yml.
     * Includes debug logging to check config path and loaded item.
     * @param punishmentType The type of punishment (ban, mute, softban, kick, warn, freeze).
     * @param itemKey       Item key in the config.
     * @return The loaded MenuItem or null if not found.
     */
    public MenuItem getDetailsMenuItemConfig(String punishmentType, String itemKey) {
        String configPath = "menu.punish_details." + punishmentType + ".items." + itemKey;
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getDetailsMenuItemConfig - Trying to load config from path: " + configPath); //Debug log for config path
        }
        MenuItem menuItem = loadMenuItemFromConfig(punishDetailsMenuConfig.getConfig(), configPath);
        if (menuItem == null && isDebugEnabled()) {
            plugin.getLogger().log(Level.WARNING, "[MainConfigManager] getDetailsMenuItemConfig - No item found at path: " + configPath); //Warning log if item is null in debug mode
        }
        return menuItem;
    }


    public MenuItem getTimeOptionMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_options." + itemKey);
    }


    public MenuItem getTimeSelectorMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_selector_items." + itemKey);
    }


    public MenuItem getHistoryMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(historyMenuConfig.getConfig(), "menu.items." + itemKey);
    }


    public String getTimeSelectorMenuTitle(OfflinePlayer target) {
        String title = timeSelectorMenuConfig.getConfig().getString("menu.time_selector_title", "");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }


    public String getHistoryMenuTitle(OfflinePlayer target) {
        String title = historyMenuConfig.getConfig().getString("menu.title", "");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }


    //</editor-fold>

    //<editor-fold desc="Menu Config Getters">
    public CustomConfig getPunishMenuConfig() { // Getter for PunishMenuConfig - NEW
        return punishMenuConfig;
    }

    public CustomConfig getPunishDetailsMenuConfig() { // Getter for PunishDetailsMenuConfig - NEW
        return punishDetailsMenuConfig;
    }

    public CustomConfig getTimeSelectorMenuConfig() { // Getter for TimeSelectorMenuConfig - NEW
        return timeSelectorMenuConfig;
    }

    public CustomConfig getHistoryMenuConfig() { // Getter for HistoryMenuConfig - NEW
        return historyMenuConfig;
    }


    //</editor-fold>

    //<editor-fold desc="Menu Item Loading">

    /**
     * Loads a MenuItem from configuration.
     * Includes debug logging to trace item loading and property setting.
     *
     * @param config     The FileConfiguration to load from.
     * @param configPath The path to the item configuration.
     * @return The loaded MenuItem.
     */
    private MenuItem loadMenuItemFromConfig(FileConfiguration config, String configPath) {
        if (config == null || configPath == null) {
            if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] loadMenuItemFromConfig - Null config or configPath, cannot load item.");
            return null;
        }

        MenuItem menuItem = new MenuItem();
        if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Loading item from path: " + configPath);

        String materialStr = config.getString(configPath + ".material", "STONE");
        menuItem.setMaterial(materialStr);
        if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Material set to: " + materialStr);

        String name = config.getString(configPath + ".name");
        menuItem.setName(name);
        if (isDebugEnabled() && name != null) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Name set to: " + name);

        List<String> lore = config.getStringList(configPath + ".lore");
        menuItem.setLore(lore);
        if (isDebugEnabled() && lore != null) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Lore lines loaded: " + lore.size());

        // Unified player head loading - using 'player_head' field - MODIFIED
        String playerHeadConfig = config.getString(configPath + ".player_head");
        menuItem.setPlayerHead(playerHeadConfig); // Use setPlayerHead instead of setPlayerHeadName/Value - MODIFIED
        if (isDebugEnabled() && playerHeadConfig != null) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - PlayerHead set to: " + playerHeadConfig);


        if (config.isInt(configPath + ".custom_model_data")) {
            int cmd = config.getInt(configPath + ".custom_model_data");
            menuItem.setCustomModelData(cmd);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - CustomModelData set to: " + cmd);
        }

        if (config.isInt(configPath + ".quantity")) {
            int qty = config.getInt(configPath + ".quantity");
            menuItem.setQuantity(qty);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Quantity set to: " + qty);
        }

        List<Integer> slots = parseSlots(config.getString(configPath + ".slot"));
        menuItem.setSlots(slots);
        if (isDebugEnabled() && slots != null) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Slots parsed and set: " + slots);


        // Load Click Actions for left click - Updated to load multiple actions
        List<String> leftClickActionConfigs = config.getStringList(configPath + ".left_click_actions");
        if (leftClickActionConfigs != null && !leftClickActionConfigs.isEmpty()) {
            List<MenuItem.ClickActionData> leftClickActions = leftClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .collect(Collectors.toList());
            menuItem.setLeftClickActions(leftClickActions);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Left-click actions loaded: " + leftClickActions.size());
        }

        // Load Click Actions for right click - Updated to load multiple actions
        List<String> rightClickActionConfigs = config.getStringList(configPath + ".right_click_actions");
        if (rightClickActionConfigs != null && !rightClickActionConfigs.isEmpty()) {
            List<MenuItem.ClickActionData> rightClickActions = rightClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .collect(Collectors.toList());
            menuItem.setRightClickActions(rightClickActions);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Right-click actions loaded: " + rightClickActions.size());
        }

        if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - MenuItem loaded successfully from path: " + configPath);
        return menuItem;
    }


    /**
     * Loads menu open actions from configuration.
     *
     * @param config     The FileConfiguration to load from.
     * @param configPath The path to the open_actions configuration.
     * @return A list of ClickActionData for menu open actions.
     */
    public List<MenuItem.ClickActionData> loadMenuOpenActions(FileConfiguration config, String configPath) {
        List<String> openActionConfigs = config.getStringList(configPath + ".open_actions");
        if (openActionConfigs != null && !openActionConfigs.isEmpty()) {
            return openActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList(); // Return empty list if no open_actions configured
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
                        if (isDebugEnabled()) {
                            plugin.getLogger().warning("Invalid slot range: " + part + " in " + slotConfig);
                        }
                    }
                } else {
                    if (isDebugEnabled()) {
                        plugin.getLogger().warning("Invalid slot range format: " + part + " in " + slotConfig);
                    }
                }
            } else {
                try {
                    slots.add(Integer.parseInt(part));
                } catch (NumberFormatException e) {
//                    if (isDebugEnabled()) {
//                    plugin.getLogger().warning("Invalid slot number: " + part + " in " + slotConfig);
//                   }
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
            String processedLine = processPlaceholders(line, target); // DEBUG - Placeholder processing is happening here
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) {
                    break; // prevent index out of bounds
                }
                String replacementValue = replacements[i + 1]; // Get replacement value
                if (replacementValue == null) {
                    replacementValue = "N/A"; // Default value if replacement is null - NEW: Handle null replacement
                    if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + replacements[i] + " in history menu lore. Using 'N/A'."); // Log warning - NEW
                }
                processedLine = processedLine.replace(replacements[i], replacementValue);
            }
            lore.add(processedLine);
        }
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed history lore: " + lore);
        }
        return lore;
    }


    /**
     * Gets punish menu item lore from punish_menu.yml and processes placeholders with replacements.
     *
     * @param itemKey       Item key in the config.
     * @param target        The target player for placeholders.
     * @param replacements  Placeholders to replace in the lore.
     * @return The processed punish menu item lore.
     */
    public List<String> getPunishMenuItemLore(String itemKey, OfflinePlayer target, String... replacements) {
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getPunishMenuItemLore called for itemKey=" + itemKey + ", target=" + target.getName() + ", replacements=" + Arrays.toString(replacements));
        }
        List<String> lore = new ArrayList<>();
        List<String> configLore = punishMenuConfig.getConfig().getStringList("menu.items." + itemKey + ".lore");
        if (configLore == null) {
            plugin.getLogger().warning("[MainConfigManager] Lore config list is null for path: menu.items." + itemKey + ".lore");
            return lore; // Return empty lore to avoid NullPointerException
        }
        for (String line : configLore) {
            String processedLine = processPlaceholders(line, target); // DEBUG - Placeholder processing is happening here
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) {
                    break; // prevent index out of bounds
                }
                String replacementValue = replacements[i + 1]; // Get replacement value
                if (replacementValue == null) {
                    replacementValue = "N/A"; // Default value if replacement is null - NEW: Handle null replacement
                    if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + replacements[i] + " in punish menu lore. Using 'N/A'."); // Log warning - NEW
                }
                processedLine = processedLine.replace(replacements[i], replacementValue);
            }
            lore.add(processedLine);
        }
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed punish menu lore: " + lore);
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
     * Gets the interval for freeze actions from config.yml. - NEW
     *
     * @return The interval in ticks for freeze actions.
     */
    public int getFreezeActionsInterval() {
        return pluginConfig.getConfig().getInt("freeze.freeze_actions.interval", 40); // Default to 40 ticks (2 seconds) if not configured - NEW
    }

    /**
     * Loads the list of freeze actions from config.yml. - NEW
     *
     * @return List of MenuItem.ClickActionData for freeze actions.
     */
    public List<MenuItem.ClickActionData> loadFreezeActions() {
        List<String> actionConfigs = pluginConfig.getConfig().getStringList("freeze.freeze_actions.actions"); // Get list of action strings - NEW
        if (actionConfigs != null && !actionConfigs.isEmpty()) {
            return actionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList(); // Return empty list if no freeze actions configured - NEW
    }

    /**
     * Gets the CustomConfig instance for the main plugin configuration (config.yml).
     *
     * @return The CustomConfig instance for pluginConfig.
     */
    public CustomConfig getPluginConfig() {
        return pluginConfig;
    }

    /**
     * Inner class to handle PlaceholderAPI placeholders.
     */
    private class CrownPunishmentsPlaceholders extends PlaceholderExpansion {

        private final CrownPunishments plugin;

        /**
         * Constructor for CrownPunishmentsPlaceholders.
         * @param plugin Main plugin instance.
         */
        public CrownPunishmentsPlaceholders(CrownPunishments plugin) {
            this.plugin = plugin;
        }

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
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null) {
                return null;
            }

            if (params.equalsIgnoreCase("is_softbanned")) {
                return String.valueOf(plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId()));
            }

            if (params.equalsIgnoreCase("softban_time_left")) {
                long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(player.getUniqueId());
                if (endTime == 0) {
                    return "N/A"; // Or any default value if not soft-banned
                }
                if (endTime == Long.MAX_VALUE) {
                    return getMessage("messages.permanent_time_display");
                }
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                return TimeUtils.formatTime(remainingSeconds, MainConfigManager.this);
            }
            if (params.equalsIgnoreCase("is_frozen")) { // Placeholder to check if player is frozen - NEW
                return String.valueOf(plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())); // Return freeze status - NEW
            }
            return null;
        }
    }
}
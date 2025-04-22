// MainConfigManager.java
package cp.corona.config;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.ColorUtils; // Keep this import
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable; // Added for clarity in inner class

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
 * - **REFACTORED:** Made 'display_yes', 'display_no', 'set', 'not_set', 'permanent_time_display'
 *   symbols/text configurable via a top-level 'placeholders' section in messages.yml.
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


        // Load configuration files
        messagesConfig = new CustomConfig("messages.yml", null, plugin, false);
        pluginConfig = new CustomConfig("config.yml", null, plugin, false);
        punishMenuConfig = new CustomConfig("punish_menu.yml", "menus", plugin, false);
        punishDetailsMenuConfig = new CustomConfig("punish_details_menu.yml", "menus", plugin, false);
        timeSelectorMenuConfig = new CustomConfig("time_selector_menu.yml", "menus", plugin, false);
        historyMenuConfig = new CustomConfig("history_menu.yml", "menus", plugin, false);

        // Register and load configs
        messagesConfig.registerConfig();
        pluginConfig.registerConfig();
        punishMenuConfig.registerConfig();
        punishDetailsMenuConfig.registerConfig();
        timeSelectorMenuConfig.registerConfig();
        historyMenuConfig.registerConfig();


        loadConfig(); // Initial load
        this.defaultTimeUnit = getTimeUnit("default"); // Ensure this is after loadConfig
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false); // Ensure this is after loadConfig


        this.placeholderAPIEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        registerPlaceholders(); // Register PAPI placeholders
    }


    /**
     * Loads or reloads all configuration files.
     * Updates debug status and re-registers PAPI placeholders if necessary.
     */
    public void loadConfig() {
        messagesConfig.reloadConfig();
        pluginConfig.reloadConfig();
        punishMenuConfig.reloadConfig();
        punishDetailsMenuConfig.reloadConfig();
        timeSelectorMenuConfig.reloadConfig();
        historyMenuConfig.reloadConfig();
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false);

        // Debug logging for config reload
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
        // Re-register Placeholders if PAPI is enabled
        if (placeholders != null && placeholderAPIEnabled) {
            // Check if already registered to avoid errors on reload if PAPI hook fails initially
            if (placeholders.isRegistered()) {
                placeholders.unregister();
            }
            // Attempt to register again
            placeholders.register();
        }
    }


    /**
     * Checks if debug logging is enabled in config.yml.
     * @return true if debug logging is enabled, false otherwise.
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }


    /**
     * Checks if the PlaceholderAPI plugin is loaded and enabled on the server.
     * @return true if PlaceholderAPI is enabled, false otherwise.
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }


    /**
     * Registers the custom PlaceholderAPI expansion for this plugin.
     * Logs success or failure messages.
     */
    public void registerPlaceholders() {
        if (placeholderAPIEnabled) {
            if (placeholders == null) {
                placeholders = new CrownPunishmentsPlaceholders(plugin);
            }
            // Check if it's not already registered before attempting to register
            if (!placeholders.isRegistered()) {
                boolean registered = placeholders.register();
                if (registered) {
                    plugin.getLogger().log(Level.INFO, "[MainConfigManager] PlaceholderAPI placeholders registered successfully.");
                } else {
                    plugin.getLogger().log(Level.WARNING, "[MainConfigManager] PlaceholderAPI placeholders failed to register. Check for errors or conflicts.");
                }
            } else {
                if (isDebugEnabled()) plugin.getLogger().log(Level.INFO, "[MainConfigManager] PlaceholderAPI placeholders already registered.");
            }
        } else {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] PlaceholderAPI not found, placeholders will not be registered.");
        }
    }


    /**
     * Processes placeholders in a given text string for a specific target player.
     * Replaces standard placeholders like {target}, {prefix}, {time}, etc.,
     * and custom placeholders loaded from the database or configuration.
     * Uses PlaceholderAPI if available and the target is online.
     *
     * **REFACTORED:** Loads 'display_yes' and 'display_no' from the top-level 'placeholders'
     * section in messages.yml. Uses the correct path for 'permanent_time_display'.
     *
     * @param text   The text string containing potential placeholders.
     * @param target The OfflinePlayer context for the placeholders (can be null).
     * @return The text string with all applicable placeholders replaced.
     */
    public String processPlaceholders(String text, OfflinePlayer target) {
        // Get the plugin prefix defined in config.yml
        String prefix = pluginConfig.getConfig().getString("prefix", "&8[&6C&cP&8] &r");
        // Apply prefix and initial color translation
        text = MessageUtils.getColorMessage(text).replace("{prefix}", prefix);

        // Return early if no target player context is provided
        if (target == null) return text;

        // --- Load Customizable Display Symbols ---
        // Get the visual representation for 'yes'/'true' from the TOP-LEVEL 'placeholders' section.
        String displayYes = messagesConfig.getConfig().getString("placeholders.display_yes", "&a✔"); // CORRECTED PATH
        displayYes = MessageUtils.getColorMessage(displayYes); // Apply color codes

        // Get the visual representation for 'no'/'false' from the TOP-LEVEL 'placeholders' section.
        String displayNo = messagesConfig.getConfig().getString("placeholders.display_no", "&c❌");   // CORRECTED PATH
        displayNo = MessageUtils.getColorMessage(displayNo); // Apply color codes
        // --- End Load Customizable Display Symbols ---

        // Get online status and target details
        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        // Replace standard player information placeholders
        text = text
                .replace("{target}", targetName)
                // Use the loaded displayYes/displayNo symbols for online status
                .replace("{target_online}", target.isOnline() ? displayYes : displayNo) // Uses corrected variables
                .replace("{target_ip}", onlineTarget != null && onlineTarget.getAddress() != null ?
                        onlineTarget.getAddress().getHostString() : "-") // Use "-" if IP is unavailable
                .replace("{target_coords}", onlineTarget != null ?
                        String.format("%d, %d, %d", // Format coordinates nicely
                                onlineTarget.getLocation().getBlockX(),
                                onlineTarget.getLocation().getBlockY(),
                                onlineTarget.getLocation().getBlockZ()) : "-") // Use "-" if coords are unavailable
                .replace("{target_world}", onlineTarget != null ?
                        onlineTarget.getWorld().getName() : "-"); // Use "-" if world is unavailable

        // --- Softban Placeholders ---
        boolean isSoftBanned = plugin.getSoftBanDatabaseManager().isSoftBanned(target.getUniqueId());
        // Use the loaded displayYes/displayNo symbols for softban status
        String softbanStatus = isSoftBanned ? displayYes : displayNo; // Uses corrected variables
        text = text.replace("{target_softban_status}", softbanStatus); // Replace the status placeholder

        // Replace remaining time placeholder only if softbanned
        if (isSoftBanned) {
            long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(target.getUniqueId());
            String remainingTimeFormatted;
            if (endTime == Long.MAX_VALUE) {
                // Use the configured display text for permanent duration from the TOP-LEVEL 'placeholders' section
                remainingTimeFormatted = getMessage("placeholders.permanent_time_display"); // CORRECTED PATH
            } else {
                // Calculate and format remaining time
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                remainingTimeFormatted = TimeUtils.formatTime(remainingSeconds, this);
            }
            // Replace the remaining time placeholder (color codes applied by formatTime/getMessage)
            text = text.replace("{target_softban_remaining_time}", remainingTimeFormatted);
        } else {
            // If not softbanned, replace with "N/A" or similar
            text = text.replace("{target_softban_remaining_time}", "N/A");
        }
        // --- End Softban Placeholders ---

        // --- Punishment Count Placeholders ---
        // Get punishment counts efficiently from the database
        HashMap<String, Integer> counts = plugin.getSoftBanDatabaseManager().getPunishmentCounts(target.getUniqueId());
        text = text.replace("{ban_count}", String.valueOf(counts.getOrDefault("ban", 0)));
        text = text.replace("{mute_count}", String.valueOf(counts.getOrDefault("mute", 0)));
        text = text.replace("{kick_count}", String.valueOf(counts.getOrDefault("kick", 0)));
        text = text.replace("{softban_count}", String.valueOf(counts.getOrDefault("softban", 0)));
        text = text.replace("{warn_count}", String.valueOf(counts.getOrDefault("warn", 0)));
        text = text.replace("{freeze_count}", String.valueOf(counts.getOrDefault("freeze", 0)));
        // Calculate and replace total punishment count
        int totalPunishments = counts.values().stream().mapToInt(Integer::intValue).sum();
        text = text.replace("{punish_count}", String.valueOf(totalPunishments));
        // --- End Punishment Count Placeholders ---


        // --- PlaceholderAPI Integration ---
        // If PAPI is enabled and the target player is online, process PAPI placeholders
        if (plugin.isPlaceholderAPIEnabled() && target.isOnline()) {
            text = PlaceholderAPI.setPlaceholders(target.getPlayer(), text);
        }
        // --- End PlaceholderAPI Integration ---

        return text; // Return the fully processed text
    }


    /**
     * Gets a message string from messages.yml (from either 'messages' or 'placeholders' section),
     * processes placeholders, and applies replacements.
     *
     * @param path         The configuration path to the string (e.g., "messages.plugin_enabled", "placeholders.set").
     * @param replacements Varargs of placeholder-value pairs to replace (e.g., "{placeholder}", "value").
     * @return The processed and formatted message string, or an empty string if the path is invalid.
     */
    public String getMessage(String path, String... replacements) {
        // Retrieve the raw message string from the configuration using the provided path
        String message = messagesConfig.getConfig().getString(path, ""); // Default to empty string if path not found
        if (message == null || message.isEmpty()) return ""; // Return empty if message is null or empty

        // Process standard placeholders (like {prefix}) first, without target context
        // Note: processPlaceholders now correctly reads display_yes/no from placeholders section
        message = processPlaceholders(message, null);

        // Apply the provided key-value replacements
        for (int i = 0; i < replacements.length; i += 2) {
            // Ensure there's a corresponding value for the key
            if (i + 1 >= replacements.length) break;
            // Replace the placeholder key (replacements[i]) with its value (replacements[i+1]), handle null value
            String replacementValue = replacements[i + 1] != null ? replacements[i + 1] : "";
            message = message.replace(replacements[i], replacementValue);
        }

        // Return the fully processed message (color codes are handled by processPlaceholders/getColorMessage)
        return message;
    }


    //<editor-fold desc="Menu Text Getters">

    /**
     * Gets text specifically for the main Punish Menu, processing placeholders for the target player.
     *
     * @param path   The relative path within the 'menu.' section of punish_menu.yml.
     * @param target The target player context.
     * @return The processed text string.
     */
    public String getMenuText(String path, OfflinePlayer target) {
        String text = punishMenuConfig.getConfig().getString("menu." + path, ""); // Get text from punish_menu.yml
        if (text == null) return "";
        return processPlaceholders(text, target); // Process placeholders with target context
    }

    /**
     * Gets text specifically for the Punish Details Menu, processing placeholders for the target player and punishment type.
     *
     * @param path           The relative path within the 'menu.punish_details.{punishmentType}.' section of punish_details_menu.yml.
     * @param target         The target player context.
     * @param punishmentType The type of punishment (used to find the correct config section).
     * @return The processed text string.
     */
    public String getDetailsMenuText(String path, OfflinePlayer target, String punishmentType) {
        // Construct the full path including the punishment type
        String fullPath = "menu.punish_details." + punishmentType + "." + path;
        String text = punishDetailsMenuConfig.getConfig().getString(fullPath, ""); // Get text from punish_details_menu.yml
        if (text == null) return "";
        return processPlaceholders(text, target); // Process placeholders with target context
    }

    /**
     * Gets text specifically for the History Menu, processing placeholders for the target player.
     *
     * @param path   The relative path within the 'menu.' section of history_menu.yml.
     * @param target The target player context.
     * @return The processed text string.
     */
    public String getHistoryMenuText(String path, OfflinePlayer target) {
        String text = historyMenuConfig.getConfig().getString("menu." + path, ""); // Get text from history_menu.yml
        if (text == null) return "";
        return processPlaceholders(text, target); // Process placeholders with target context
    }
    //</editor-fold>

    //<editor-fold desc="Menu Item Configuration Getters">

    /**
     * Gets the MenuItem configuration for a specific item key in the main Punish Menu.
     *
     * @param itemKey The unique key identifying the item in punish_menu.yml (e.g., "ban", "info").
     * @return The loaded MenuItem object, or null if the key is not found.
     */
    public MenuItem getPunishMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(punishMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    /**
     * Gets the MenuItem configuration for a specific item key within a specific punishment type's Details Menu.
     * Includes debug logging to trace config path and loading status.
     *
     * @param punishmentType The type of punishment (e.g., "ban", "mute").
     * @param itemKey        The unique key identifying the item in punish_details_menu.yml (e.g., "set_time", "confirm_punish").
     * @return The loaded MenuItem object, or null if the key or section is not found.
     */
    public MenuItem getDetailsMenuItemConfig(String punishmentType, String itemKey) {
        String configPath = "menu.punish_details." + punishmentType + ".items." + itemKey;
        // Debug log: Indicate which config path is being attempted
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getDetailsMenuItemConfig - Loading config from path: " + configPath);
        }
        MenuItem menuItem = loadMenuItemFromConfig(punishDetailsMenuConfig.getConfig(), configPath);
        // Debug log: Warn if no item was found at the specified path
        if (menuItem == null && isDebugEnabled()) {
            // Avoid logging for placeholder items that might not exist for every type
            if (!itemKey.equals(PunishDetailsMenu.UNSOFTBAN_BUTTON_KEY) && !itemKey.equals(PunishDetailsMenu.UNFREEZE_BUTTON_KEY)) {
                plugin.getLogger().log(Level.WARNING, "[MainConfigManager] getDetailsMenuItemConfig - No item found at path: " + configPath);
            }
        }
        return menuItem;
    }

    /**
     * Gets the MenuItem configuration for a specific time option item in the Time Selector Menu.
     * (Note: Seems unused based on current YAML, might be legacy or for future use).
     *
     * @param itemKey The unique key identifying the time option item.
     * @return The loaded MenuItem object, or null if not found.
     */
    public MenuItem getTimeOptionMenuItemConfig(String itemKey) {
        // Path points to predefined time options (e.g., 1h, 1d buttons if they existed)
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_options." + itemKey);
    }

    /**
     * Gets the MenuItem configuration for a specific control item in the Time Selector Menu (e.g., adjust buttons, display).
     *
     * @param itemKey The unique key identifying the item in time_selector_menu.yml (e.g., "plus_1_day", "time_display").
     * @return The loaded MenuItem object, or null if not found.
     */
    public MenuItem getTimeSelectorMenuItemConfig(String itemKey) {
        // Path points to the main items in the time selector (adjust buttons, display, custom, permanent)
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_selector_items." + itemKey);
    }

    /**
     * Gets the MenuItem configuration for a specific item key in the History Menu (e.g., navigation, entries).
     *
     * @param itemKey The unique key identifying the item in history_menu.yml (e.g., "back_button", "history_entry").
     * @return The loaded MenuItem object, or null if not found.
     */
    public MenuItem getHistoryMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(historyMenuConfig.getConfig(), "menu.items." + itemKey);
    }
    //</editor-fold>

    //<editor-fold desc="Menu Title Getters">
    /**
     * Gets the title for the Time Selector Menu, processing placeholders.
     *
     * @param target The target player context for placeholders.
     * @return The processed menu title string.
     */
    public String getTimeSelectorMenuTitle(OfflinePlayer target) {
        String title = timeSelectorMenuConfig.getConfig().getString("menu.time_selector_title", "&9&lSelect Punishment Time"); // Added default title
        if (title == null) return "";
        return processPlaceholders(title, target); // Process placeholders for the title
    }

    /**
     * Gets the title for the History Menu, processing placeholders.
     *
     * @param target The target player context for placeholders.
     * @return The processed menu title string.
     */
    public String getHistoryMenuTitle(OfflinePlayer target) {
        String title = historyMenuConfig.getConfig().getString("menu.title", "&7&lPunishment History"); // Added default title
        if (title == null) return "";
        return processPlaceholders(title, target); // Process placeholders for the title
    }
    //</editor-fold>

    //<editor-fold desc="Menu Config Getters">

    /** Gets the CustomConfig instance for punish_menu.yml. */
    public CustomConfig getPunishMenuConfig() {
        return punishMenuConfig;
    }

    /** Gets the CustomConfig instance for punish_details_menu.yml. */
    public CustomConfig getPunishDetailsMenuConfig() {
        return punishDetailsMenuConfig;
    }

    /** Gets the CustomConfig instance for time_selector_menu.yml. */
    public CustomConfig getTimeSelectorMenuConfig() {
        return timeSelectorMenuConfig;
    }

    /** Gets the CustomConfig instance for history_menu.yml. */
    public CustomConfig getHistoryMenuConfig() {
        return historyMenuConfig;
    }
    //</editor-fold>

    //<editor-fold desc="Menu Item Loading Logic">

    /**
     * Central method to load a MenuItem object from a specific path in a given FileConfiguration.
     * Populates the MenuItem fields (material, name, lore, slots, actions, etc.) based on the config.
     * Includes detailed debug logging for tracing the loading process.
     *
     * @param config     The FileConfiguration object to read from.
     * @param configPath The exact path to the item's configuration section within the YAML file.
     * @return A populated MenuItem object, or null if the config or path is invalid or section doesn't exist.
     */
    private MenuItem loadMenuItemFromConfig(FileConfiguration config, String configPath) {
        // Basic validation: Ensure config and path are provided
        if (config == null || configPath == null) {
            if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] loadMenuItemFromConfig - Cannot load item: Null config or configPath provided.");
            return null;
        }

        // Check if the configuration section actually exists at the given path
        if (!config.isConfigurationSection(configPath)) {
            // If debug is enabled, log that the specific path was not found. This helps pinpoint config errors.
            // Avoid logging for dynamic history entries to reduce spam.
            if (isDebugEnabled() && !configPath.contains("history_entry")) {
                plugin.getLogger().warning("[MainConfigManager] loadMenuItemFromConfig - Configuration section not found at path: " + configPath);
            }
            return null; // Return null because the item definition doesn't exist
        }


        MenuItem menuItem = new MenuItem(); // Create a new MenuItem instance
        if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Loading item from path: " + configPath);

        // Load Material (String, handled by MenuItem internally)
        String materialStr = config.getString(configPath + ".material", "STONE"); // Default to STONE
        menuItem.setMaterial(materialStr);
        if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Material set to: " + materialStr);

        // Load Display Name (String)
        String name = config.getString(configPath + ".name");
        menuItem.setName(name); // Can be null if not set
        if (isDebugEnabled() && name != null) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Name set to: " + name);

        // Load Lore (List of Strings)
        List<String> lore = config.getStringList(configPath + ".lore");
        menuItem.setLore(lore); // Can be empty list if not set
        if (isDebugEnabled() && lore != null && !lore.isEmpty()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Lore lines loaded: " + lore.size());

        // Load Player Head (String - username or URL, handled by MenuItem)
        String playerHeadConfig = config.getString(configPath + ".player_head");
        menuItem.setPlayerHead(playerHeadConfig); // Can be null
        if (isDebugEnabled() && playerHeadConfig != null) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - PlayerHead set to: " + playerHeadConfig);

        // Load Custom Model Data (Integer)
        if (config.contains(configPath + ".custom_model_data") && config.isInt(configPath + ".custom_model_data")) {
            int cmd = config.getInt(configPath + ".custom_model_data");
            menuItem.setCustomModelData(cmd);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - CustomModelData set to: " + cmd);
        }

        // Load Quantity (Integer)
        if (config.contains(configPath + ".quantity") && config.isInt(configPath + ".quantity")) {
            int qty = config.getInt(configPath + ".quantity", 1); // Default to 1
            menuItem.setQuantity(Math.max(1, qty)); // Ensure quantity is at least 1
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Quantity set to: " + menuItem.getQuantity());
        } else {
            menuItem.setQuantity(1); // Ensure default quantity if not specified
        }

        // Load Slots (String - parsed into List<Integer>)
        List<Integer> slots = parseSlots(config.getString(configPath + ".slot"));
        menuItem.setSlots(slots); // Can be empty list
        if (isDebugEnabled() && slots != null && !slots.isEmpty()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Slots parsed and set: " + slots);


        // --- Load Click Actions ---
        // Load Left Click Actions (List of Strings, parsed into List<ClickActionData>)
        List<String> leftClickActionConfigs = config.getStringList(configPath + ".left_click_actions");
        if (!leftClickActionConfigs.isEmpty()) { // Check if the list is not null and not empty
            List<MenuItem.ClickActionData> leftClickActions = leftClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString) // Convert each string to ClickActionData
                    .filter(Objects::nonNull) // Filter out potential nulls from parsing errors
                    .collect(Collectors.toList());
            menuItem.setLeftClickActions(leftClickActions);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Left-click actions loaded: " + leftClickActions.size());
        } else {
            menuItem.setLeftClickActions(Collections.emptyList()); // Ensure it's an empty list, not null
        }

        // Load Right Click Actions (List of Strings, parsed into List<ClickActionData>)
        List<String> rightClickActionConfigs = config.getStringList(configPath + ".right_click_actions");
        if (!rightClickActionConfigs.isEmpty()) { // Check if the list is not null and not empty
            List<MenuItem.ClickActionData> rightClickActions = rightClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString) // Convert each string to ClickActionData
                    .filter(Objects::nonNull) // Filter out potential nulls from parsing errors
                    .collect(Collectors.toList());
            menuItem.setRightClickActions(rightClickActions);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Right-click actions loaded: " + rightClickActions.size());
        } else {
            menuItem.setRightClickActions(Collections.emptyList()); // Ensure it's an empty list, not null
        }
        // --- End Load Click Actions ---

        if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - MenuItem loaded successfully from path: " + configPath);
        return menuItem; // Return the populated MenuItem
    }


    /**
     * Loads menu open actions from a specific configuration path.
     * These actions are typically triggered when a menu is opened by a player.
     *
     * @param config     The FileConfiguration to load from.
     * @param configPath The path to the 'open_actions' list within the configuration (e.g., "menu.open_actions").
     * @return A List of ClickActionData representing the actions to perform on menu open, or an empty list if none are configured.
     */
    public List<MenuItem.ClickActionData> loadMenuOpenActions(FileConfiguration config, String configPath) {
        // Retrieve the list of action strings from the configuration path + ".open_actions"
        List<String> openActionConfigs = config.getStringList(configPath + ".open_actions");

        // Check if the list is not null and not empty
        if (openActionConfigs != null && !openActionConfigs.isEmpty()) {
            // Convert the list of action strings into a list of ClickActionData objects
            return openActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString) // Use the fromConfigString method in ClickActionData
                    .filter(Objects::nonNull) // Filter out potential nulls from parsing errors
                    .collect(Collectors.toList()); // Collect the results into a list
        }
        // Return an empty list if no open_actions are defined or the list is empty
        return Collections.emptyList();
    }


    /**
     * Parses a slot configuration string into a list of integer slot numbers.
     * The input string can contain single numbers, ranges (e.g., "10-15"), or comma-separated combinations (e.g., "1,3,5-10,20").
     * Useful for defining where background items or other items should be placed.
     *
     * @param slotConfig The slot configuration string from the YAML file.
     * @return A List of integers representing the individual slot numbers. Returns an empty list if input is null or invalid.
     */
    private List<Integer> parseSlots(String slotConfig) {
        List<Integer> slots = new ArrayList<>();
        if (slotConfig == null || slotConfig.trim().isEmpty()) {
            return slots; // Return empty list if input is null or empty
        }

        // Split the configuration string by commas to handle multiple parts
        String[] parts = slotConfig.split(",");
        for (String part : parts) {
            part = part.trim(); // Remove leading/trailing whitespace from each part
            if (part.isEmpty()) continue; // Skip empty parts

            // Check if the part represents a range (contains a hyphen)
            if (part.contains("-")) {
                String[] range = part.split("-", 2); // Split into two parts at the hyphen
                if (range.length == 2) {
                    try {
                        // Parse the start and end of the range
                        int start = Integer.parseInt(range[0].trim()); // Trim parts of range too
                        int end = Integer.parseInt(range[1].trim());
                        // Ensure start is not greater than end
                        if (start > end) {
                            if (isDebugEnabled()) plugin.getLogger().warning("Invalid slot range (start > end): '" + part + "' in '" + slotConfig + "'. Skipping range.");
                            continue; // Skip this invalid range
                        }
                        // Add all numbers within the range (inclusive) to the list
                        for (int i = start; i <= end; i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException e) {
                        // Log a warning if the range parts are not valid numbers
                        if (isDebugEnabled()) {
                            plugin.getLogger().warning("Invalid slot range format (non-numeric): '" + part + "' in '" + slotConfig + "'");
                        }
                    }
                } else {
                    // Log a warning if the range format is incorrect (e.g., "5-" or "-10")
                    if (isDebugEnabled()) {
                        plugin.getLogger().warning("Invalid slot range format (incorrect parts): '" + part + "' in '" + slotConfig + "'");
                    }
                }
            } else { // If not a range, try parsing as a single number
                try {
                    slots.add(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    // Log a warning if the part is not a valid single number
                    if (isDebugEnabled()) {
                        plugin.getLogger().warning("Invalid slot number: '" + part + "' in '" + slotConfig + "'");
                    }
                }
            }
        }
        return slots; // Return the populated list of slots
    }
    //</editor-fold>

    //<editor-fold desc="Lore Getters with Placeholders">

    /**
     * Gets the lore for a specific item in the Punish Details Menu, processing placeholders and applying replacements.
     *
     * @param punishmentType The type of punishment (e.g., "ban", "mute") to locate the correct config section.
     * @param itemKey        The key of the item within the punishment type's section (e.g., "set_time").
     * @param target         The target player context for placeholders.
     * @param replacements   Varargs of placeholder-value pairs to replace (e.g., "{placeholder}", "value").
     * @return A List of strings representing the processed lore lines. Returns an empty list if config is missing.
     */
    public List<String> getDetailsMenuItemLore(String punishmentType, String itemKey, OfflinePlayer target, String... replacements) {
        // Debug log: Trace the call with parameters
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getDetailsMenuItemLore called for punishmentType=" + punishmentType + ", itemKey=" + itemKey + ", target=" + (target != null ? target.getName() : "null") + ", replacements=" + Arrays.toString(replacements));
        }

        List<String> lore = new ArrayList<>();
        // Construct the full path to the lore list in the configuration
        String lorePath = "menu.punish_details." + punishmentType + ".items." + itemKey + ".lore";
        // Retrieve the raw lore strings from the configuration
        List<String> configLore = punishDetailsMenuConfig.getConfig().getStringList(lorePath);

        // Check if the lore list was successfully retrieved
        if (configLore == null || configLore.isEmpty()) {
            // Log a warning if the lore configuration is missing or empty (only in debug)
            if (isDebugEnabled() && !itemKey.equals(PunishDetailsMenu.UNSOFTBAN_BUTTON_KEY) && !itemKey.equals(PunishDetailsMenu.UNFREEZE_BUTTON_KEY)) {
                plugin.getLogger().warning("[MainConfigManager] Lore config list is null or empty for path: " + lorePath);
            }
            return lore; // Return an empty list to prevent potential NullPointerExceptions later
        }

        // Process each line of the lore
        for (String line : configLore) {
            if (line == null) continue; // Skip null lines
            // First, process standard placeholders ({target}, {prefix}, etc.) using the target context
            String processedLine = processPlaceholders(line, target);
            // Then, apply the specific key-value replacements provided as arguments
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break; // Safety check for odd number of replacements
                // Handle potential null replacement values gracefully
                String placeholder = replacements[i];
                String replacementValue = replacements[i + 1] != null ? replacements[i + 1] : ""; // Use empty string if value is null
                processedLine = processedLine.replace(placeholder, replacementValue);
            }
            // Add the fully processed line to the result list (color codes are handled by processPlaceholders)
            lore.add(processedLine);
        }

        // Debug log: Show the final processed lore
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed details menu lore: " + lore);
        }
        return lore;
    }

    /**
     * Gets the lore for a specific item in the History Menu, processing placeholders and applying replacements.
     *
     * @param itemKey      The key of the item within the 'menu.items' section (e.g., "history_entry").
     * @param target       The target player context for placeholders.
     * @param replacements Varargs of placeholder-value pairs to replace (e.g., "{punishment_type}", "ban").
     * @return A List of strings representing the processed lore lines. Returns an empty list if config is missing.
     */
    public List<String> getHistoryMenuItemLore(String itemKey, OfflinePlayer target, String... replacements) {
        // Debug log: Trace the call
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getHistoryMenuItemLore called for itemKey=" + itemKey + ", target=" + (target != null ? target.getName() : "null") + ", replacements=" + Arrays.toString(replacements));
        }

        List<String> lore = new ArrayList<>();
        // Construct the path to the lore list
        String lorePath = "menu.items." + itemKey + ".lore";
        // Retrieve the raw lore strings
        List<String> configLore = historyMenuConfig.getConfig().getStringList(lorePath);

        // Check if lore configuration exists
        if (configLore == null || configLore.isEmpty()) {
            if (isDebugEnabled()){ // Only log warning in debug mode
                plugin.getLogger().warning("[MainConfigManager] Lore config list is null or empty for path: " + lorePath);
            }
            return lore; // Return empty list
        }

        // Process each line
        for (String line : configLore) {
            if (line == null) continue; // Skip null lines
            // Process standard placeholders
            String processedLine = processPlaceholders(line, target);
            // Apply specific replacements
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                // Handle null replacement values
                String placeholder = replacements[i];
                String replacementValue = replacements[i + 1];
                if (replacementValue == null) {
                    replacementValue = "N/A"; // Default value if replacement is null
                    if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + placeholder + " in history menu lore. Using 'N/A'.");
                }
                processedLine = processedLine.replace(placeholder, replacementValue);
            }
            lore.add(processedLine);
        }

        // Debug log: Show final processed lore
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed history menu lore: " + lore);
        }
        return lore;
    }


    /**
     * Gets the lore for a specific item in the main Punish Menu, processing placeholders and applying replacements.
     *
     * @param itemKey      The key of the item within the 'menu.items' section (e.g., "ban", "info").
     * @param target       The target player context for placeholders.
     * @param replacements Varargs of placeholder-value pairs to replace.
     * @return A List of strings representing the processed lore lines. Returns an empty list if config is missing.
     */
    public List<String> getPunishMenuItemLore(String itemKey, OfflinePlayer target, String... replacements) {
        // Debug log: Trace the call
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getPunishMenuItemLore called for itemKey=" + itemKey + ", target=" + (target != null ? target.getName() : "null") + ", replacements=" + Arrays.toString(replacements));
        }

        List<String> lore = new ArrayList<>();
        // Construct the path to the lore list
        String lorePath = "menu.items." + itemKey + ".lore";
        // Retrieve the raw lore strings
        List<String> configLore = punishMenuConfig.getConfig().getStringList(lorePath);

        // Check if lore configuration exists
        if (configLore == null || configLore.isEmpty()) {
            if (isDebugEnabled()){ // Only log warning in debug mode
                plugin.getLogger().warning("[MainConfigManager] Lore config list is null or empty for path: " + lorePath);
            }
            return lore; // Return empty list
        }

        // Process each line
        for (String line : configLore) {
            if (line == null) continue; // Skip null lines
            // Process standard placeholders
            String processedLine = processPlaceholders(line, target);
            // Apply specific replacements
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                // Handle null replacement values
                String placeholder = replacements[i];
                String replacementValue = replacements[i + 1];
                if (replacementValue == null) {
                    replacementValue = "N/A"; // Default value if replacement is null
                    if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + placeholder + " in punish menu lore. Using 'N/A'.");
                }
                processedLine = processedLine.replace(placeholder, replacementValue);
            }
            lore.add(processedLine);
        }

        // Debug log: Show final processed lore
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed punish menu lore: " + lore);
        }
        return lore;
    }


    //</editor-fold>

    //<editor-fold desc="Command Template Getters">

    /** Gets the ban command template from config.yml. */
    public String getBanCommand() {
        return pluginConfig.getConfig().getString("commands.ban_command", "ban {target} {time} {reason}");
    }

    /** Gets the mute command template from config.yml. */
    public String getMuteCommand() {
        return pluginConfig.getConfig().getString("commands.mute_command", "mute {target} {time} {reason}");
    }

    /** Gets the unban command template from config.yml. */
    public String getUnbanCommand() {
        return pluginConfig.getConfig().getString("commands.unban_command", "pardon {target}");
    }

    /**
     * Gets the unwarn command template from config.yml.
     * Returns an empty string if not configured.
     * @return The unwarn command template string, or "" if not set.
     */
    public String getUnwarnCommand() {
        // Return empty string as default if the command is not specified in config.yml
        return pluginConfig.getConfig().getString("commands.unwarn_command", "");
    }

    /** Gets the unmute command template from config.yml. */
    public String getUnmuteCommand() {
        return pluginConfig.getConfig().getString("commands.unmute_command", "unmute {target}");
    }


    /** Gets the warn command template from config.yml. */
    public String getWarnCommand() {
        return pluginConfig.getConfig().getString("commands.warn_command", "warn {target} {reason}");
    }

    /** Gets the kick command template from config.yml. */
    public String getKickCommand() {
        return pluginConfig.getConfig().getString("commands.kick_command", "kick {target} {reason}");
    }

    /**
     * Gets the softban command template from config.yml.
     * Note: Softban is typically handled internally, so this might return an empty string.
     * @return The softban command template string.
     */
    public String getSoftBanCommand() {
        // Softban is handled internally by the plugin, so command might not be defined/needed.
        return pluginConfig.getConfig().getString("commands.softban_command", "");
    }
    //</editor-fold>

    //<editor-fold desc="Time Option Getters (TimeSelectorMenu)">

    /**
     * Gets the list of keys for predefined time options (e.g., "1h", "1d") from time_selector_menu.yml.
     * (Note: Seems unused based on current YAML, might be legacy or for future use).
     *
     * @return List of time option keys (strings).
     */
    public List<String> getTimeOptions() {
        // This retrieves keys from the 'menu.time_options' section, which might be empty/unused currently.
        ConfigurationSection section = timeSelectorMenuConfig.getConfig().getConfigurationSection("menu.time_options");
        if (section == null) {
            return new ArrayList<>(); // Return empty list if section doesn't exist
        }
        return new ArrayList<>(section.getKeys(false));
    }

    /**
     * Gets the time value string (e.g., "1h", "1d") associated with a specific time option key.
     * (Note: Seems unused based on current YAML).
     *
     * @param timeOptionKey The key of the time option.
     * @return The time value string (e.g., "1h"), or an empty string if not found.
     */
    public String getTimeOptionValue(String timeOptionKey) {
        // Retrieves the 'value' field under a specific time option key.
        return timeSelectorMenuConfig.getConfig().getString("menu.time_options." + timeOptionKey + ".value", "");
    }
    //</editor-fold>

    //<editor-fold desc="Sound Getters">

    /**
     * Gets the Bukkit sound name string for a given sound key from config.yml.
     *
     * @param soundKey The key identifying the sound in the 'sounds.' section (e.g., "punish_confirm").
     * @return The Bukkit sound name string (e.g., "ENTITY_EXPERIENCE_ORB_PICKUP"), or an empty string if not found.
     */
    public String getSoundName(String soundKey) {
        return pluginConfig.getConfig().getString("sounds." + soundKey, "");
    }
    //</editor-fold>

    //<editor-fold desc="Time Unit Getters">

    /**
     * Gets the configured string representation for a specific time unit key from config.yml.
     *
     * @param unitKey The key identifying the time unit in the 'time_units.' section (e.g., "seconds", "hours").
     * @return The configured time unit string (e.g., "s", "h"), or an empty string if not found.
     */
    public String getTimeUnit(String unitKey) {
        return pluginConfig.getConfig().getString("time_units." + unitKey, "");
    }

    /** Gets the configured string for the hours time unit. */
    public String getHoursTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.hours", "h");
    }

    /** Gets the configured string for the minutes time unit. */
    public String getMinutesTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.minutes", "m");
    }

    /** Gets the configured string for the seconds time unit. */
    public String getSecondsTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.seconds", "s");
    }

    /** Gets the configured string for the day time unit. */
    public String getDayTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.day", "d");
    }

    /** Gets the configured string for the years time unit. */
    public String getYearsTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.years", "y");
    }

    /** Gets the default time unit (used if no unit is specified in input). */
    public String getDefaultTimeUnit() {
        return this.defaultTimeUnit; // Returns the value loaded in the constructor
    }
    //</editor-fold>

    //<editor-fold desc="Softban Configuration Getters">

    /**
     * Gets the list of command names (lowercase) that should be blocked for softbanned players.
     * Loaded from the 'softban.blocked_commands' list in config.yml.
     *
     * @return A List of blocked command strings.
     */
    public List<String> getBlockedCommands() {
        return pluginConfig.getConfig().getStringList("softban.blocked_commands");
    }
    //</editor-fold>

    //<editor-fold desc="Database Configuration Getters">

    /** Gets the configured database type ('sqlite' or 'mysql'). */
    public String getDatabaseType() {
        return pluginConfig.getConfig().getString("database.type", "sqlite"); // Default to sqlite
    }

    /** Gets the configured database name (file name for SQLite, DB name for MySQL). */
    public String getDatabaseName() {
        return pluginConfig.getConfig().getString("database.name", "crownpunishments");
    }

    /** Gets the configured database server address (for MySQL). */
    public String getDatabaseAddress() {
        return pluginConfig.getConfig().getString("database.address", "localhost");
    }

    /** Gets the configured database server port (for MySQL). */
    public String getDatabasePort() {
        return pluginConfig.getConfig().getString("database.port", "3306");
    }

    /** Gets the configured database username (for MySQL). */
    public String getDatabaseUsername() {
        return pluginConfig.getConfig().getString("database.username", "username");
    }

    /** Gets the configured database password (for MySQL). */
    public String getDatabasePassword() {
        return pluginConfig.getConfig().getString("database.password", "password");
    }
    //</editor-fold>

    //<editor-fold desc="Freeze Configuration Getters">

    /**
     * Gets the interval (in ticks) at which freeze actions should be repeatedly executed for frozen players.
     * Loaded from 'freeze.freeze_actions.interval' in config.yml.
     *
     * @return The interval in ticks. Defaults to 40 (2 seconds) if not configured.
     */
    public int getFreezeActionsInterval() {
        // Default to 40 ticks (2 seconds) if not configured
        return pluginConfig.getConfig().getInt("freeze.freeze_actions.interval", 40);
    }

    /**
     * Loads the list of actions to be executed repeatedly for frozen players.
     * Reads the 'freeze.freeze_actions.actions' list from config.yml and parses each string into ClickActionData.
     *
     * @return A List of ClickActionData for freeze actions. Returns an empty list if none are configured.
     */
    public List<MenuItem.ClickActionData> loadFreezeActions() {
        // Get the list of action strings from the config path
        List<String> actionConfigs = pluginConfig.getConfig().getStringList("freeze.freeze_actions.actions");

        // Check if the list is not null and not empty
        if (actionConfigs != null && !actionConfigs.isEmpty()) {
            // Convert the list of action strings into ClickActionData objects
            return actionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString) // Parse each string
                    .filter(Objects::nonNull) // Filter out potential nulls from parsing errors
                    .collect(Collectors.toList()); // Collect into a list
        }
        // Return an empty list if no freeze actions are configured
        return Collections.emptyList();
    }
    //</editor-fold>

    /**
     * Gets the CustomConfig instance for the main plugin configuration (config.yml).
     * Useful for accessing less common config values directly.
     *
     * @return The CustomConfig instance wrapping config.yml.
     */
    public CustomConfig getPluginConfig() {
        return pluginConfig;
    }

    /**
     * Inner class extending PlaceholderAPI's PlaceholderExpansion.
     * Handles the registration and replacement logic for custom placeholders provided by CrownPunishments.
     */
    private class CrownPunishmentsPlaceholders extends PlaceholderExpansion {

        private final CrownPunishments plugin; // Reference to the main plugin instance

        /**
         * Constructor for the PAPI expansion.
         * @param plugin The main CrownPunishments plugin instance.
         */
        public CrownPunishmentsPlaceholders(CrownPunishments plugin) {
            this.plugin = plugin;
        }

        /**
         * Indicates that this expansion should persist through PlaceholderAPI reloads.
         * @return true to persist.
         */
        @Override
        public boolean persist() {
            return true;
        }

        /**
         * Returns the unique identifier for this expansion.
         * Placeholders will be prefixed with this identifier (e.g., %crownpunishments_...).
         * @return The identifier string "crownpunishments".
         */
        @Override
        public @NotNull String getIdentifier() {
            return "crownpunishments";
        }

        /**
         * Returns the author of this expansion.
         * @return The author's name "Corona".
         */
        @Override
        public @NotNull String getAuthor() {
            return "Corona";
        }

        /**
         * Returns the version of this expansion, which matches the plugin's version.
         * @return The plugin version string.
         */
        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        /**
         * Handles incoming placeholder requests.
         * Called by PlaceholderAPI when a placeholder starting with %crownpunishments_ is encountered.
         *
         * @param player The player context for the placeholder (can be null).
         * @param params The part of the placeholder *after* the identifier (e.g., "is_softbanned").
         * @return The string value to replace the placeholder with, or null if the placeholder is unknown.
         */
        @Override
        public @Nullable String onRequest(OfflinePlayer player, String params) { // Use @Nullable
            // Placeholders require a player context
            if (player == null) {
                return null;
            }

            // --- Status Placeholders ---
            if (params.equalsIgnoreCase("is_softbanned")) {
                return String.valueOf(plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId()));
            }
            if (params.equalsIgnoreCase("is_frozen")) {
                // Check the plugin's internal map for frozen status
                return String.valueOf(plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId()));
            }
            // --- End Status Placeholders ---

            // --- Time/Duration Placeholders ---
            if (params.equalsIgnoreCase("softban_time_left")) {
                long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(player.getUniqueId());
                if (endTime == 0) { // Not softbanned or expired
                    return "N/A";
                }
                if (endTime == Long.MAX_VALUE) { // Permanent softban
                    // Use the configured display text for permanent from the TOP-LEVEL 'placeholders' section
                    return getMessage("placeholders.permanent_time_display"); // CORRECTED PATH
                }
                // Calculate and format remaining time
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                // Use the TimeUtils formatter with the MainConfigManager context for units
                return TimeUtils.formatTime(remainingSeconds, MainConfigManager.this);
            }
            // --- End Time/Duration Placeholders ---

            // --- Punishment Count Placeholders ---
            if (params.endsWith("_count")) {
                // Get all counts for the player efficiently in one query
                HashMap<String, Integer> counts = plugin.getSoftBanDatabaseManager().getPunishmentCounts(player.getUniqueId());
                // Extract the punishment type from the placeholder parameter
                String punishmentType = params.substring(0, params.length() - "_count".length());

                // Handle specific count placeholders
                if (params.equalsIgnoreCase("ban_count")) return String.valueOf(counts.getOrDefault("ban", 0));
                if (params.equalsIgnoreCase("mute_count")) return String.valueOf(counts.getOrDefault("mute", 0));
                if (params.equalsIgnoreCase("warn_count")) return String.valueOf(counts.getOrDefault("warn", 0));
                if (params.equalsIgnoreCase("softban_count")) return String.valueOf(counts.getOrDefault("softban", 0));
                if (params.equalsIgnoreCase("kick_count")) return String.valueOf(counts.getOrDefault("kick", 0));
                if (params.equalsIgnoreCase("freeze_count")) return String.valueOf(counts.getOrDefault("freeze", 0));

                // Handle total punishment count placeholder
                if (params.equalsIgnoreCase("punish_count")) {
                    int totalPunishments = counts.values().stream().mapToInt(Integer::intValue).sum();
                    return String.valueOf(totalPunishments);
                }
            }
            // --- End Punishment Count Placeholders ---


            // If the placeholder parameter doesn't match any known placeholders, return null
            return null;
        }

        // Override register() to call super.register()
        @Override
        public boolean register() {
            return super.register();
        }

        // Override unregister() if necessary, though often not needed unless managing external resources
        // public void unregister() { super.unregister(); }

    } // End of CrownPunishmentsPlaceholders inner class
}
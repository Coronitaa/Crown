// MainConfigManager.java
package cp.corona.config;

import cp.corona.crown.Crown;
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.items.MenuItem;
import cp.corona.menus.items.MenuItem.ClickActionData;
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
import org.jetbrains.annotations.Nullable;

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
 * - Added methods to load and retrieve post-punishment/unpunishment action hooks from config.yml.
 * - Added getter for freeze disconnect commands.
 */
public class MainConfigManager {
    private final CustomConfig messagesConfig;
    private final CustomConfig pluginConfig;
    private final CustomConfig punishMenuConfig;
    private final CustomConfig punishDetailsMenuConfig;
    private final CustomConfig timeSelectorMenuConfig;
    private final CustomConfig historyMenuConfig;

    private final Crown plugin;
    private final String defaultTimeUnit;


    private boolean debugEnabled;
    private boolean placeholderAPIEnabled;
    private CrownPunishmentsPlaceholders placeholders;

    public MainConfigManager(Crown plugin) {
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

    /**
     * Loads or reloads all configuration files.
     * Updates debug status, PAPI placeholders, and punishment hooks.
     */
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

            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Loading punishment action hooks...");
            List<ClickActionData> kickActions = getOnPunishActions("kick");
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Debug: Loaded 'on_punish.kick' actions: " + kickActions.size());
        }

        if (placeholders != null && placeholderAPIEnabled) {
            if (placeholders.isRegistered()) {
                placeholders.unregister();
            }
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
        String prefix = pluginConfig.getConfig().getString("prefix", "&8[&6C&cP&8] &r");
        text = MessageUtils.getColorMessage(text).replace("{prefix}", prefix);

        if (target == null) return text;

        String displayYes = messagesConfig.getConfig().getString("placeholders.display_yes", "&a✔");
        displayYes = MessageUtils.getColorMessage(displayYes);

        String displayNo = messagesConfig.getConfig().getString("placeholders.display_no", "&c❌");
        displayNo = MessageUtils.getColorMessage(displayNo);

        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        text = text
                .replace("{target}", targetName)
                .replace("{target_online}", target.isOnline() ? displayYes : displayNo)
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
        String softbanStatus = isSoftBanned ? displayYes : displayNo;
        text = text.replace("{target_softban_status}", softbanStatus);

        if (isSoftBanned) {
            long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(target.getUniqueId());
            String remainingTimeFormatted;
            if (endTime == Long.MAX_VALUE) {
                remainingTimeFormatted = getMessage("placeholders.permanent_time_display");
            } else {
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                remainingTimeFormatted = TimeUtils.formatTime(remainingSeconds, this);
            }
            text = text.replace("{target_softban_remaining_time}", remainingTimeFormatted);
        } else {
            text = text.replace("{target_softban_remaining_time}", "N/A");
        }

        HashMap<String, Integer> counts = plugin.getSoftBanDatabaseManager().getPunishmentCounts(target.getUniqueId());
        text = text.replace("{ban_count}", String.valueOf(counts.getOrDefault("ban", 0)));
        text = text.replace("{mute_count}", String.valueOf(counts.getOrDefault("mute", 0)));
        text = text.replace("{kick_count}", String.valueOf(counts.getOrDefault("kick", 0)));
        text = text.replace("{softban_count}", String.valueOf(counts.getOrDefault("softban", 0)));
        text = text.replace("{warn_count}", String.valueOf(counts.getOrDefault("warn", 0)));
        text = text.replace("{freeze_count}", String.valueOf(counts.getOrDefault("freeze", 0)));
        int totalPunishments = counts.values().stream().mapToInt(Integer::intValue).sum();
        text = text.replace("{punish_count}", String.valueOf(totalPunishments));

        if (plugin.isPlaceholderAPIEnabled() && target.isOnline()) {
            text = PlaceholderAPI.setPlaceholders(target.getPlayer(), text);
        }

        return text;
    }

    /**
     * Gets the configurable display name for a punishment type, supporting noun or verb forms.
     * Falls back to a capitalized version of the type or type + "ed" if not found.
     *
     * @param punishmentType The base punishment type (e.g., "ban", "mute"). Case-insensitive.
     * @param isVerb         True to get the verb form (e.g., "banned" from placeholders.punishment_action_verbs.ban),
     *                       false for the noun form (e.g., "Ban" from placeholders.punishment_type_names.ban).
     * @return The configured display name, or a sensible fallback.
     */
    public String getPunishmentDisplayForm(String punishmentType, boolean isVerb) {
        if (punishmentType == null || punishmentType.isEmpty()) {
            return "unknown"; // Should not happen with current usage
        }
        String typeKey = punishmentType.toLowerCase();
        String path;
        String fallback;

        if (isVerb) {
            path = "placeholders.punishment_action_verbs." + typeKey;
            // Simple fallback for verbs
            switch (typeKey) {
                case "ban": fallback = "banned"; break;
                case "mute": fallback = "muted"; break;
                case "kick": fallback = "kicked"; break;
                case "warn": fallback = "warned"; break;
                case "softban": fallback = "softbanned"; break;
                case "freeze": fallback = "frozen"; break;
                default: fallback = typeKey + "ed"; // Generic fallback
            }
        } else { // Noun form
            path = "placeholders.punishment_type_names." + typeKey;
            // Fallback for nouns: capitalize the input type
            fallback = typeKey.substring(0, 1).toUpperCase() + typeKey.substring(1);
        }

        String configuredName = messagesConfig.getConfig().getString(path, fallback);
        // If the configuredName itself was the fallback, it's already processed.
        // If it came from config, apply colors.
        return MessageUtils.getColorMessage(configuredName);
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
        String message = messagesConfig.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) return "";

        message = processPlaceholders(message, null); // Process general placeholders

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break;
            String placeholderKey = replacements[i];
            String replacementValue = replacements[i + 1] != null ? replacements[i + 1] : "";

            // Special handling for {punishment_type} to use configurable noun forms
            if (placeholderKey.equals("{punishment_type}")) {
                // Attempt to get the configured display name for the noun form
                // The replacementValue here is expected to be the raw type (e.g., "ban")
                replacementValue = getPunishmentDisplayForm(replacementValue, false);
            }
            // MODIFICATION: Changed to use placeholderKey directly
            message = message.replace(placeholderKey, replacementValue);
        }
        // Apply colors at the very end if not already done by getPunishmentDisplayForm
        return MessageUtils.getColorMessage(message);
    }

    /**
     * Gets text specifically for the main Punish Menu, processing placeholders for the target player.
     *
     * @param path   The relative path within the 'menu.' section of punish_menu.yml.
     * @param target The target player context.
     * @return The processed text string.
     */
    public String getMenuText(String path, OfflinePlayer target) {
        String text = punishMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
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
        String fullPath = "menu.punish_details." + punishmentType + "." + path;
        String text = punishDetailsMenuConfig.getConfig().getString(fullPath, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }

    /**
     * Gets text specifically for the History Menu, processing placeholders for the target player.
     *
     * @param path   The relative path within the 'menu.' section of history_menu.yml.
     * @param target The target player context.
     * @return The processed text string.
     */
    public String getHistoryMenuText(String path, OfflinePlayer target) {
        String text = historyMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }

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
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getDetailsMenuItemConfig - Loading config from path: " + configPath);
        }
        MenuItem menuItem = loadMenuItemFromConfig(punishDetailsMenuConfig.getConfig(), configPath);
        if (menuItem == null && isDebugEnabled()) {
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
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_options." + itemKey);
    }

    /**
     * Gets the MenuItem configuration for a specific control item in the Time Selector Menu (e.g., adjust buttons, display).
     *
     * @param itemKey The unique key identifying the item in time_selector_menu.yml (e.g., "plus_1_day", "time_display").
     * @return The loaded MenuItem object, or null if not found.
     */
    public MenuItem getTimeSelectorMenuItemConfig(String itemKey) {
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

    /**
     * Gets the title for the Time Selector Menu, processing placeholders.
     *
     * @param target The target player context for placeholders.
     * @return The processed menu title string.
     */
    public String getTimeSelectorMenuTitle(OfflinePlayer target) {
        String title = timeSelectorMenuConfig.getConfig().getString("menu.time_selector_title", "&9&lSelect Punishment Time");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }

    /**
     * Gets the title for the History Menu, processing placeholders.
     *
     * @param target The target player context for placeholders.
     * @return The processed menu title string.
     */
    public String getHistoryMenuTitle(OfflinePlayer target) {
        String title = historyMenuConfig.getConfig().getString("menu.title", "&7&lPunishment History");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }

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
        if (config == null || configPath == null) {
            if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] loadMenuItemFromConfig - Cannot load item: Null config or configPath provided.");
            return null;
        }

        if (!config.isConfigurationSection(configPath)) {
            if (isDebugEnabled() && !configPath.contains("history_entry")) {
                plugin.getLogger().warning("[MainConfigManager] loadMenuItemFromConfig - Configuration section not found at path: " + configPath);
            }
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
        if (isDebugEnabled() && lore != null && !lore.isEmpty()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Lore lines loaded: " + lore.size());

        String playerHeadConfig = config.getString(configPath + ".player_head");
        menuItem.setPlayerHead(playerHeadConfig);
        if (isDebugEnabled() && playerHeadConfig != null) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - PlayerHead set to: " + playerHeadConfig);

        if (config.contains(configPath + ".custom_model_data") && config.isInt(configPath + ".custom_model_data")) {
            int cmd = config.getInt(configPath + ".custom_model_data");
            menuItem.setCustomModelData(cmd);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - CustomModelData set to: " + cmd);
        }

        if (config.contains(configPath + ".quantity") && config.isInt(configPath + ".quantity")) {
            int qty = config.getInt(configPath + ".quantity", 1);
            menuItem.setQuantity(Math.max(1, qty));
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Quantity set to: " + menuItem.getQuantity());
        } else {
            menuItem.setQuantity(1);
        }

        List<Integer> slots = parseSlots(config.getString(configPath + ".slot"));
        menuItem.setSlots(slots);
        if (isDebugEnabled() && slots != null && !slots.isEmpty()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Slots parsed and set: " + slots);

        List<String> leftClickActionConfigs = config.getStringList(configPath + ".left_click_actions");
        if (!leftClickActionConfigs.isEmpty()) {
            List<MenuItem.ClickActionData> leftClickActions = leftClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            menuItem.setLeftClickActions(leftClickActions);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Left-click actions loaded: " + leftClickActions.size());
        } else {
            menuItem.setLeftClickActions(Collections.emptyList());
        }

        List<String> rightClickActionConfigs = config.getStringList(configPath + ".right_click_actions");
        if (!rightClickActionConfigs.isEmpty()) {
            List<MenuItem.ClickActionData> rightClickActions = rightClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            menuItem.setRightClickActions(rightClickActions);
            if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - Right-click actions loaded: " + rightClickActions.size());
        } else {
            menuItem.setRightClickActions(Collections.emptyList());
        }

        if (isDebugEnabled()) plugin.getLogger().info("[MainConfigManager] loadMenuItemFromConfig - MenuItem loaded successfully from path: " + configPath);
        return menuItem;
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
        List<String> openActionConfigs = config.getStringList(configPath + ".open_actions");

        if (openActionConfigs != null && !openActionConfigs.isEmpty()) {
            return openActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
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
            return slots;
        }

        String[] parts = slotConfig.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.contains("-")) {
                String[] range = part.split("-", 2);
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        if (start > end) {
                            if (isDebugEnabled()) plugin.getLogger().warning("Invalid slot range (start > end): '" + part + "' in '" + slotConfig + "'. Skipping range.");
                            continue;
                        }
                        for (int i = start; i <= end; i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException e) {
                        if (isDebugEnabled()) {
                            plugin.getLogger().warning("Invalid slot range format (non-numeric): '" + part + "' in '" + slotConfig + "'");
                        }
                    }
                } else {
                    if (isDebugEnabled()) {
                        plugin.getLogger().warning("Invalid slot range format (incorrect parts): '" + part + "' in '" + slotConfig + "'");
                    }
                }
            } else {
                try {
                    slots.add(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    if (isDebugEnabled()) {
                        plugin.getLogger().warning("Invalid slot number: '" + part + "' in '" + slotConfig + "'");
                    }
                }
            }
        }
        return slots;
    }

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
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getDetailsMenuItemLore called for punishmentType=" + punishmentType + ", itemKey=" + itemKey + ", target=" + (target != null ? target.getName() : "null") + ", replacements=" + Arrays.toString(replacements));
        }

        List<String> lore = new ArrayList<>();
        String lorePath = "menu.punish_details." + punishmentType + ".items." + itemKey + ".lore";
        List<String> configLore = punishDetailsMenuConfig.getConfig().getStringList(lorePath);

        if (configLore == null || configLore.isEmpty()) {
            if (isDebugEnabled() && !itemKey.equals(PunishDetailsMenu.UNSOFTBAN_BUTTON_KEY) && !itemKey.equals(PunishDetailsMenu.UNFREEZE_BUTTON_KEY)) {
                plugin.getLogger().warning("[MainConfigManager] Lore config list is null or empty for path: " + lorePath);
            }
            return lore;
        }

        for (String line : configLore) {
            if (line == null) continue;
            String processedLine = processPlaceholders(line, target);
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                String placeholder = replacements[i];
                String replacementValue = replacements[i + 1] != null ? replacements[i + 1] : "";
                processedLine = processedLine.replace(placeholder, replacementValue);
            }
            lore.add(processedLine);
        }

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
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getHistoryMenuItemLore called for itemKey=" + itemKey + ", target=" + (target != null ? target.getName() : "null") + ", replacements=" + Arrays.toString(replacements));
        }

        List<String> lore = new ArrayList<>();
        String lorePath = "menu.items." + itemKey + ".lore";
        List<String> configLore = historyMenuConfig.getConfig().getStringList(lorePath);

        if (configLore == null || configLore.isEmpty()) {
            if (isDebugEnabled()){
                plugin.getLogger().warning("[MainConfigManager] Lore config list is null or empty for path: " + lorePath);
            }
            return lore;
        }

        for (String line : configLore) {
            if (line == null) continue;
            String processedLine = processPlaceholders(line, target);
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                String placeholder = replacements[i];
                String replacementValue = replacements[i + 1];
                if (replacementValue == null) {
                    replacementValue = "N/A";
                    if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + placeholder + " in history menu lore. Using 'N/A'.");
                }
                processedLine = processedLine.replace(placeholder, replacementValue);
            }
            lore.add(processedLine);
        }

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
        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] getPunishMenuItemLore called for itemKey=" + itemKey + ", target=" + (target != null ? target.getName() : "null") + ", replacements=" + Arrays.toString(replacements));
        }

        List<String> lore = new ArrayList<>();
        String lorePath = "menu.items." + itemKey + ".lore";
        List<String> configLore = punishMenuConfig.getConfig().getStringList(lorePath);

        if (configLore == null || configLore.isEmpty()) {
            if (isDebugEnabled()){
                plugin.getLogger().warning("[MainConfigManager] Lore config list is null or empty for path: " + lorePath);
            }
            return lore;
        }

        for (String line : configLore) {
            if (line == null) continue;
            String processedLine = processPlaceholders(line, target);
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                String placeholder = replacements[i];
                String replacementValue = replacements[i + 1];
                if (replacementValue == null) {
                    replacementValue = "N/A";
                    if (isDebugEnabled()) plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + placeholder + " in punish menu lore. Using 'N/A'.");
                }
                processedLine = processedLine.replace(placeholder, replacementValue);
            }
            lore.add(processedLine);
        }

        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Processed punish menu lore: " + lore);
        }
        return lore;
    }

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
        return pluginConfig.getConfig().getString("commands.softban_command", "");
    }

    /**
     * Gets the list of keys for predefined time options (e.g., "1h", "1d") from time_selector_menu.yml.
     * (Note: Seems unused based on current YAML, might be legacy or for future use).
     *
     * @return List of time option keys (strings).
     */
    public List<String> getTimeOptions() {
        ConfigurationSection section = timeSelectorMenuConfig.getConfig().getConfigurationSection("menu.time_options");
        if (section == null) {
            return new ArrayList<>();
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
        return timeSelectorMenuConfig.getConfig().getString("menu.time_options." + timeOptionKey + ".value", "");
    }

    /**
     * Gets the Bukkit sound name string for a given sound key from config.yml.
     *
     * @param soundKey The key identifying the sound in the 'sounds.' section (e.g., "punish_confirm").
     * @return The Bukkit sound name string (e.g., "ENTITY_EXPERIENCE_ORB_PICKUP"), or an empty string if not found.
     */
    public String getSoundName(String soundKey) {
        return pluginConfig.getConfig().getString("sounds." + soundKey, "");
    }

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
        return this.defaultTimeUnit;
    }

    /**
     * Gets the list of command names (lowercase) that should be blocked for softbanned players.
     * Loaded from the 'softban.blocked_commands' list in config.yml.
     *
     * @return A List of blocked command strings.
     */
    public List<String> getBlockedCommands() {
        return pluginConfig.getConfig().getStringList("softban.blocked_commands");
    }

    /** Gets the configured database type ('sqlite' or 'mysql'). */
    public String getDatabaseType() {
        return pluginConfig.getConfig().getString("database.type", "sqlite");
    }

    /** Gets the configured database name (file name for SQLite, DB name for MySQL). */
    public String getDatabaseName() {
        return pluginConfig.getConfig().getString("database.name", "crown");
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

    /**
     * Gets the interval (in ticks) at which freeze actions should be repeatedly executed for frozen players.
     * Loaded from 'freeze.freeze_actions.interval' in config.yml.
     *
     * @return The interval in ticks. Defaults to 40 (2 seconds) if not configured.
     */
    public int getFreezeActionsInterval() {
        return pluginConfig.getConfig().getInt("freeze.freeze_actions.interval", 40);
    }

    /**
     * Loads the list of actions to be executed repeatedly for frozen players.
     * Reads the 'freeze.freeze_actions.actions' list from config.yml and parses each string into ClickActionData.
     *
     * @return A List of ClickActionData for freeze actions. Returns an empty list if none are configured.
     */
    public List<MenuItem.ClickActionData> loadFreezeActions() {
        List<String> actionConfigs = pluginConfig.getConfig().getStringList("freeze.freeze_actions.actions");

        if (actionConfigs != null && !actionConfigs.isEmpty()) {
            return actionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Gets the list of console commands to execute when a frozen player disconnects.
     * Loaded from 'freeze.disconnect_commands' in config.yml. Supports placeholders and color codes.
     *
     * @return A List of command strings. Returns an empty list if none are configured.
     */
    public List<String> getFreezeDisconnectCommands() {
        return pluginConfig.getConfig().getStringList("freeze.disconnect_commands");
    }


    /**
     * Gets the list of ClickActionData configured to run after a specific punishment type is applied.
     * Loads actions from 'punishment_hooks.on_punish.{punishmentType}' in config.yml.
     *
     * @param punishmentType The type of punishment (e.g., "ban", "kick"). Case-insensitive.
     * @return A List of ClickActionData for the hook, or an empty list if none are configured or type is invalid.
     */
    public List<ClickActionData> getOnPunishActions(String punishmentType) {
        String path = "punishment_hooks.on_punish." + punishmentType.toLowerCase();
        return loadActionsFromPath(path);
    }

    /**
     * Gets the list of ClickActionData configured to run after a specific unpunishment type is applied.
     * Loads actions from 'punishment_hooks.on_unpunish.{punishmentType}' in config.yml.
     * Note: The key in the config (e.g., "ban") corresponds to the *original* punishment type being undone.
     *
     * @param unpunishType The type of punishment being undone (e.g., "ban", "softban"). Case-insensitive.
     * @return A List of ClickActionData for the hook, or an empty list if none are configured or type is invalid.
     */
    public List<ClickActionData> getOnUnpunishActions(String unpunishType) {
        String path = "punishment_hooks.on_unpunish." + unpunishType.toLowerCase();
        return loadActionsFromPath(path);
    }

    /**
     * Helper method to load a list of ClickActionData from a given path in config.yml.
     *
     * @param configPath The exact path to the list of action strings.
     * @return A List of ClickActionData, or an empty list if the path is invalid or list is empty.
     */
    private List<ClickActionData> loadActionsFromPath(String configPath) {
        FileConfiguration config = pluginConfig.getConfig();
        if (!config.contains(configPath) || !config.isList(configPath)) {
            if (isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "[MainConfigManager] No action hook list found at path: " + configPath);
            }
            return Collections.emptyList();
        }

        List<String> actionStrings = config.getStringList(configPath);
        if (actionStrings.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClickActionData> actions = actionStrings.stream()
                .map(ClickActionData::fromConfigString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (isDebugEnabled() && !actions.isEmpty()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Loaded " + actions.size() + " actions from path: " + configPath);
        }

        return actions;
    }

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
     * Handles the registration and replacement logic for custom placeholders provided by Crown.
     */
    private class CrownPunishmentsPlaceholders extends PlaceholderExpansion {

        private final Crown plugin;

        /**
         * Constructor for the PAPI expansion.
         * @param plugin The main Crown plugin instance.
         */
        public CrownPunishmentsPlaceholders(Crown plugin) {
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
         * @return The identifier string "crown".
         */
        @Override
        public @NotNull String getIdentifier() {
            return "crown";
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
        public @Nullable String onRequest(OfflinePlayer player, String params) {
            if (player == null) {
                return null;
            }

            if (params.equalsIgnoreCase("is_softbanned")) {
                return String.valueOf(plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId()));
            }
            if (params.equalsIgnoreCase("is_frozen")) {
                return String.valueOf(plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId()));
            }

            if (params.equalsIgnoreCase("softban_time_left")) {
                long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(player.getUniqueId());
                if (endTime == 0) {
                    return "N/A";
                }
                if (endTime == Long.MAX_VALUE) {
                    return getMessage("placeholders.permanent_time_display");
                }
                int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
                return TimeUtils.formatTime(remainingSeconds, MainConfigManager.this);
            }

            if (params.endsWith("_count")) {
                HashMap<String, Integer> counts = plugin.getSoftBanDatabaseManager().getPunishmentCounts(player.getUniqueId());
                String punishmentType = params.substring(0, params.length() - "_count".length());

                if (params.equalsIgnoreCase("ban_count")) return String.valueOf(counts.getOrDefault("ban", 0));
                if (params.equalsIgnoreCase("mute_count")) return String.valueOf(counts.getOrDefault("mute", 0));
                if (params.equalsIgnoreCase("warn_count")) return String.valueOf(counts.getOrDefault("warn", 0));
                if (params.equalsIgnoreCase("softban_count")) return String.valueOf(counts.getOrDefault("softban", 0));
                if (params.equalsIgnoreCase("kick_count")) return String.valueOf(counts.getOrDefault("kick", 0));
                if (params.equalsIgnoreCase("freeze_count")) return String.valueOf(counts.getOrDefault("freeze", 0));

                if (params.equalsIgnoreCase("punish_count")) {
                    int totalPunishments = counts.values().stream().mapToInt(Integer::intValue).sum();
                    return String.valueOf(totalPunishments);
                }
            }

            return null;
        }

        /**
         * Registers the PlaceholderAPI expansion.
         * @return true if registration was successful.
         */
        @Override
        public boolean register() {
            return super.register();
        }

    }
}
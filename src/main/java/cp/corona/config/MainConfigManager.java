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

public class MainConfigManager {
    private final CustomConfig messagesConfig;
    private final CustomConfig pluginConfig;
    private final CustomConfig punishMenuConfig;
    private final CustomConfig punishDetailsMenuConfig;
    private final CustomConfig timeSelectorMenuConfig;
    private final CustomConfig historyMenuConfig;
    private final Map<String, CustomConfig> punishmentConfigs = new HashMap<>();

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

        Arrays.asList("ban", "mute", "kick", "warn", "softban", "freeze").forEach(punishment ->
                punishmentConfigs.put(punishment, new CustomConfig(punishment + ".yml", "punishments", plugin, false))
        );

        messagesConfig.registerConfig();
        pluginConfig.registerConfig();
        punishMenuConfig.registerConfig();
        punishDetailsMenuConfig.registerConfig();
        timeSelectorMenuConfig.registerConfig();
        historyMenuConfig.registerConfig();
        punishmentConfigs.values().forEach(CustomConfig::registerConfig);

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
        punishmentConfigs.values().forEach(CustomConfig::reloadConfig);
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false);

        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Configurations reloaded and debug mode is " + (isDebugEnabled() ? "enabled" : "disabled"));
        }

        if (placeholders != null && placeholderAPIEnabled) {
            if (placeholders.isRegistered()) {
                placeholders.unregister();
            }
            placeholders.register();
        }
    }

    public boolean isPunishmentInternal(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return false;
        return config.getConfig().getBoolean("use-internal", false);
    }


    public String getPunishmentCommand(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return "";
        return config.getConfig().getString("punish-command", "");
    }

    public String getUnpunishCommand(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return "";
        return config.getConfig().getString("unpunish-command", "");
    }

    public List<String> getBlockedCommands() {
        CustomConfig config = punishmentConfigs.get("softban");
        if (config == null) return Collections.emptyList();
        return config.getConfig().getStringList("blocked_commands");
    }

    public List<String> getBlockedMuteCommands() {
        CustomConfig config = punishmentConfigs.get("mute");
        if (config == null) return Collections.emptyList();
        return config.getConfig().getStringList("blocked_commands");
    }

    public List<String> getFreezeDisconnectCommands() {
        CustomConfig config = punishmentConfigs.get("freeze");
        if (config == null) return Collections.emptyList();
        return config.getConfig().getStringList("disconnect_commands");
    }

    public int getFreezeActionsInterval() {
        CustomConfig config = punishmentConfigs.get("freeze");
        if (config == null) return 40;
        return config.getConfig().getInt("freeze_actions.interval", 40);
    }

    public List<MenuItem.ClickActionData> loadFreezeActions() {
        CustomConfig config = punishmentConfigs.get("freeze");
        if (config == null) return Collections.emptyList();
        List<String> actionConfigs = config.getConfig().getStringList("freeze_actions.actions");
        if (actionConfigs != null && !actionConfigs.isEmpty()) {
            return actionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public List<ClickActionData> getOnPunishActions(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return Collections.emptyList();
        return loadActionsFromPath(config.getConfig(), "hooks.on-punish");
    }

    public List<ClickActionData> getOnUnpunishActions(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return Collections.emptyList();
        return loadActionsFromPath(config.getConfig(), "hooks.on-unpunish");
    }

    private List<ClickActionData> loadActionsFromPath(FileConfiguration config, String configPath) {
        if (!config.contains(configPath) || !config.isList(configPath)) {
            return Collections.emptyList();
        }
        List<String> actionStrings = config.getStringList(configPath);
        if (actionStrings.isEmpty()) {
            return Collections.emptyList();
        }
        return actionStrings.stream()
                .map(ClickActionData::fromConfigString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public String getDefaultPunishmentReason(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return "No reason specified.";
        return config.getConfig().getString("default-reason", "No reason specified.");
    }

    public String getDefaultUnpunishmentReason(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return "Unpunished by a moderator.";
        return config.getConfig().getString("default-unpunish-reason", "Unpunished by a moderator.");
    }

    public String getMenuText(String path, OfflinePlayer target) {
        String text = punishMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }

    public String getDetailsMenuText(String path, OfflinePlayer target, String punishmentType) {
        String fullPath = "menu.punish_details." + punishmentType + "." + path;
        String text = punishDetailsMenuConfig.getConfig().getString(fullPath, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }

    public String getHistoryMenuText(String path, OfflinePlayer target) {
        String text = historyMenuConfig.getConfig().getString("menu." + path, "");
        if (text == null) return "";
        return processPlaceholders(text, target);
    }

    public MenuItem getPunishMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(punishMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    public MenuItem getDetailsMenuItemConfig(String punishmentType, String itemKey) {
        String configPath = "menu.punish_details." + punishmentType + ".items." + itemKey;
        return loadMenuItemFromConfig(punishDetailsMenuConfig.getConfig(), configPath);
    }

    public MenuItem getTimeSelectorMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(timeSelectorMenuConfig.getConfig(), "menu.time_selector_items." + itemKey);
    }

    public MenuItem getHistoryMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(historyMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    public String getTimeSelectorMenuTitle(OfflinePlayer target) {
        String title = timeSelectorMenuConfig.getConfig().getString("menu.time_selector_title", "&9&lSelect Punishment Time");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }

    public String getHistoryMenuTitle(OfflinePlayer target) {
        String title = historyMenuConfig.getConfig().getString("menu.title", "&7&lPunishment History");
        if (title == null) return "";
        return processPlaceholders(title, target);
    }

    public CustomConfig getPunishMenuConfig() {
        return punishMenuConfig;
    }

    public CustomConfig getPunishDetailsMenuConfig() {
        return punishDetailsMenuConfig;
    }

    public String getSoundName(String soundKey) {
        return pluginConfig.getConfig().getString("sounds." + soundKey, "");
    }

    public CustomConfig getTimeSelectorMenuConfig() {
        return timeSelectorMenuConfig;
    }

    public CustomConfig getHistoryMenuConfig() {
        return historyMenuConfig;
    }

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
        return MessageUtils.getColorMessage(configuredName);
    }

    public String getMessage(String path, String... replacements) {
        String message = messagesConfig.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) return "";

        message = processPlaceholders(message, null); // Process general placeholders

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break;
            String placeholderKey = replacements[i];
            String replacementValue = replacements[i + 1] != null ? replacements[i + 1] : "";

            if (placeholderKey.equals("{punishment_type}")) {
                replacementValue = getPunishmentDisplayForm(replacementValue, false);
            }
            message = message.replace(placeholderKey, replacementValue);
        }
        return MessageUtils.getColorMessage(message);
    }

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

    public String getTimeUnit(String unitKey) {
        return pluginConfig.getConfig().getString("time_units." + unitKey, "");
    }

    public String getHoursTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.hours", "h");
    }

    public String getMinutesTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.minutes", "m");
    }

    public String getSecondsTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.seconds", "s");
    }

    public String getDayTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.day", "d");
    }

    public String getYearsTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.years", "y");
    }

    public String getDefaultTimeUnit() {
        return this.defaultTimeUnit;
    }

    public String getDatabaseType() {
        return pluginConfig.getConfig().getString("database.type", "sqlite");
    }

    public String getDatabaseName() {
        return pluginConfig.getConfig().getString("database.name", "crown");
    }

    public String getDatabaseAddress() {
        return pluginConfig.getConfig().getString("database.address", "localhost");
    }

    public String getDatabasePort() {
        return pluginConfig.getConfig().getString("database.port", "3306");
    }

    public String getDatabaseUsername() {
        return pluginConfig.getConfig().getString("database.username", "username");
    }

    public String getDatabasePassword() {
        return pluginConfig.getConfig().getString("database.password", "password");
    }

    public CustomConfig getPluginConfig() {
        return pluginConfig;
    }

    private class CrownPunishmentsPlaceholders extends PlaceholderExpansion {

        private final Crown plugin;

        public CrownPunishmentsPlaceholders(Crown plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "crown";
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

        @Override
        public boolean register() {
            return super.register();
        }

    }
}
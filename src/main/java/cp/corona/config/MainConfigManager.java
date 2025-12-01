package cp.corona.config;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.items.MenuItem;
import cp.corona.menus.items.MenuItem.ClickActionData;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final CustomConfig profileMenuConfig;
    private final CustomConfig fullInventoryMenuConfig;
    private final CustomConfig enderChestMenuConfig;
    private final CustomConfig auditLogConfig;
    private final CustomConfig reportsMenuConfig;
    private final CustomConfig reportDetailsMenuConfig;
    private final CustomConfig reportsConfig;
    private final Map<String, CustomConfig> punishmentConfigs = new HashMap<>();
    private final CustomConfig modModeConfig;
    private final Crown plugin;
    private final String defaultTimeUnit;

    private boolean debugEnabled;
    private boolean placeholderAPIEnabled;
    private CrownPunishmentsPlaceholders placeholders;

    private final Map<Integer, WarnLevel> warnLevels = new HashMap<>();
    private String warnExpirationMode;

    private int reportCooldown;
    private boolean rateLimitEnabled;
    private int rateLimitAmount;
    private int rateLimitPeriod;
    private boolean reportRequiresPermission;

    public record ReportOption(String text, String hover, String action) {}
    public record ReportPage(String title, List<ReportOption> options) {}


    public MainConfigManager(Crown plugin) {
        this.plugin = plugin;

        messagesConfig = new CustomConfig("messages.yml", null, plugin, false);
        pluginConfig = new CustomConfig("config.yml", null, plugin, false);
        punishMenuConfig = new CustomConfig("punish_menu.yml", "menus", plugin, false);
        punishDetailsMenuConfig = new CustomConfig("punish_details_menu.yml", "menus", plugin, false);
        timeSelectorMenuConfig = new CustomConfig("time_selector_menu.yml", "menus", plugin, false);
        historyMenuConfig = new CustomConfig("history_menu.yml", "menus", plugin, false);
        profileMenuConfig = new CustomConfig("profile_menu.yml", "menus", plugin, false);
        fullInventoryMenuConfig = new CustomConfig("full_inventory_menu.yml", "menus", plugin, false);
        enderChestMenuConfig = new CustomConfig("enderchest_menu.yml", "menus", plugin, false);
        auditLogConfig = new CustomConfig("audit_log.yml", "menus", plugin, false);
        reportsMenuConfig = new CustomConfig("reports_menu.yml", "menus", plugin, false);
        reportDetailsMenuConfig = new CustomConfig("report_details_menu.yml", "menus", plugin, false);
        reportsConfig = new CustomConfig("reports.yml", "menus", plugin, false);
        modModeConfig = new CustomConfig("mod_mode.yml", null, plugin, false);

        Arrays.asList("ban", "mute", "kick", "warn", "softban", "freeze").forEach(punishment ->
                punishmentConfigs.put(punishment, new CustomConfig(punishment + ".yml", "punishments", plugin, false))
        );

        messagesConfig.registerConfig();
        pluginConfig.registerConfig();
        punishMenuConfig.registerConfig();
        punishDetailsMenuConfig.registerConfig();
        timeSelectorMenuConfig.registerConfig();
        historyMenuConfig.registerConfig();
        profileMenuConfig.registerConfig();
        fullInventoryMenuConfig.registerConfig();
        enderChestMenuConfig.registerConfig();
        auditLogConfig.registerConfig();
        modModeConfig.registerConfig();
        reportsMenuConfig.registerConfig();
        reportDetailsMenuConfig.registerConfig();
        reportsConfig.registerConfig();
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
        profileMenuConfig.reloadConfig();
        fullInventoryMenuConfig.reloadConfig();
        enderChestMenuConfig.reloadConfig();
        auditLogConfig.reloadConfig();
        modModeConfig.reloadConfig();
        reportsMenuConfig.reloadConfig();
        reportDetailsMenuConfig.reloadConfig();
        reportsConfig.reloadConfig();
        punishmentConfigs.values().forEach(CustomConfig::reloadConfig);
        this.debugEnabled = pluginConfig.getConfig().getBoolean("logging.debug", false);

        this.reportCooldown = pluginConfig.getConfig().getInt("report-system.cooldown", 60);
        this.rateLimitEnabled = pluginConfig.getConfig().getBoolean("report-system.rate-limit.enabled", true);
        this.rateLimitAmount = pluginConfig.getConfig().getInt("report-system.rate-limit.amount", 5);
        this.rateLimitPeriod = pluginConfig.getConfig().getInt("report-system.rate-limit.period", 300);
        this.reportRequiresPermission = pluginConfig.getConfig().getBoolean("report-system.require-permission", false);

        if (isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[MainConfigManager] Configurations reloaded and debug mode is " + (isDebugEnabled() ? "enabled" : "disabled"));
        }

        loadWarnLevels();

        if (placeholders != null && placeholderAPIEnabled) {
            if (placeholders.isRegistered()) {
                placeholders.unregister();
            }
            placeholders.register();
        }
    }

    public int getReportCooldown() {
        return reportCooldown;
    }

    public boolean isReportRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public boolean isReportPermissionRequired() {
        return reportRequiresPermission;
    }
    public int getReportRateLimitAmount() {
        return rateLimitAmount;
    }

    public int getReportRateLimitPeriod() {
        return rateLimitPeriod;
    }

    public String getReportBookTitle() {
        return reportsConfig.getConfig().getString("report-menu.book-title", "&c&lCreate a Report");
    }

    public String getReportInitialPageKey() {
        return reportsConfig.getConfig().getString("report-menu.initial-page", "root");
    }

    public ReportPage getReportPage(String key) {
        ConfigurationSection pageSection = reportsConfig.getConfig().getConfigurationSection("report-menu.pages." + key);
        if (pageSection == null) {
            return null;
        }

        String title = pageSection.getString("title", "Report Menu");
        List<ReportOption> options = new ArrayList<>();
        List<Map<?, ?>> optionsList = pageSection.getMapList("options");

        for (Map<?, ?> optionMap : optionsList) {
            String text = (String) optionMap.get("text");
            String hover = (String) optionMap.get("hover");
            String action = (String) optionMap.get("action");
            if (text != null && action != null) {
                options.add(new ReportOption(text, hover, action));
            }
        }
        return new ReportPage(title, options);
    }

    private void loadWarnLevels() {
        warnLevels.clear();
        CustomConfig warnConfig = punishmentConfigs.get("warn");
        if (warnConfig == null || !warnConfig.getConfig().getBoolean("use-internal", false)) {
            return;
        }

        this.warnExpirationMode = warnConfig.getConfig().getString("expiration-mode", "unique").toLowerCase();
        ConfigurationSection levelsSection = warnConfig.getConfig().getConfigurationSection("levels");
        if (levelsSection == null) {
            if (isDebugEnabled())
                plugin.getLogger().warning("[MainConfigManager] 'levels' section not found in warn.yml.");
            return;
        }

        for (String key : levelsSection.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                ConfigurationSection levelSection = levelsSection.getConfigurationSection(key);
                if (levelSection != null) {
                    String expiration = levelSection.getString("expiration", "-1");
                    List<String> onWarnActions = levelSection.getStringList("on-warn-actions");
                    List<String> onExpireActions = levelSection.getStringList("on-expire-actions");
                    List<String> softbanBlockedCommands = levelSection.getStringList("softban-blocked-commands");
                    warnLevels.put(level, new WarnLevel(expiration, onWarnActions, onExpireActions, softbanBlockedCommands));
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[MainConfigManager] Invalid warn level key in warn.yml: " + key + ". It must be a number.");
            }
        }
        if (isDebugEnabled())
            plugin.getLogger().info("[MainConfigManager] Loaded " + warnLevels.size() + " warning levels. Mode: " + this.warnExpirationMode);
    }

    public String getWarnExpirationMode() {
        return warnExpirationMode;
    }

    public WarnLevel getWarnLevel(int level) {
        if (warnLevels.containsKey(level)) {
            return warnLevels.get(level);
        }
        return warnLevels.keySet().stream().max(Integer::compareTo)
                .map(warnLevels::get).orElse(null);
    }

    public String getSupportLink() {
        return pluginConfig.getConfig().getString("support-link", "your.discord.gg");
    }

    public boolean isJoinAlertEnabled() {
        return pluginConfig.getConfig().getBoolean("on-join-alert.enabled", true);
    }

    public int getJoinAlertDuration() {
        return pluginConfig.getConfig().getInt("on-join-alert.freeze-chat-duration", 5);
    }

    public String getJoinAlertSound() {
        return pluginConfig.getConfig().getString("on-join-alert.sound", "BLOCK_NOTE_BLOCK_PLING");
    }
    public CustomConfig getModModeConfig() {
        return modModeConfig;
    }
    public List<String> getBanScreen() {
        CustomConfig config = punishmentConfigs.get("ban");
        if (config == null) return Collections.emptyList();
        return config.getConfig().getStringList("ban-screen");
    }

    public boolean isIpPunishmentSupported(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return false;
        return config.getConfig().contains("punish-by-ip");
    }

    public boolean isPunishmentByIp(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config == null) return false;
        return config.getConfig().getBoolean("punish-by-ip", false);
    }

    public List<String> getKickScreen() {
        CustomConfig config = punishmentConfigs.get("kick");
        if (config == null) return Collections.emptyList();
        return config.getConfig().getStringList("kick-screen");
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

    // ADDED: Getter for freeze allowed commands
    public List<String> getFreezeAllowedCommands() {
        CustomConfig config = punishmentConfigs.get("freeze");
        if (config == null) return Collections.emptyList();
        return config.getConfig().getStringList("allowed_commands");
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
        if (config != null && config.getConfig().contains("default-reason")) {
            return config.getConfig().getString("default-reason");
        }
        return getMessage("messages.default_reasons." + punishmentType.toLowerCase());
    }

    public String getDefaultUnpunishmentReason(String punishmentType) {
        CustomConfig config = punishmentConfigs.get(punishmentType.toLowerCase());
        if (config != null && config.getConfig().contains("default-unpunish-reason")) {
            return config.getConfig().getString("default-unpunish-reason");
        }
        return getMessage("messages.default_reasons.un" + punishmentType.toLowerCase());
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

    public MenuItem getProfileMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(profileMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    public MenuItem getFullInventoryMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(fullInventoryMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    public MenuItem getEnderChestMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(enderChestMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    public MenuItem getReportsMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(reportsMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    public Set<String> getReportsMenuItemKeys() {
        ConfigurationSection section = reportsMenuConfig.getConfig().getConfigurationSection("menu.items");
        return section != null ? section.getKeys(false) : Collections.emptySet();
    }

    public MenuItem getReportDetailsMenuItemConfig(String itemKey) {
        return loadMenuItemFromConfig(reportDetailsMenuConfig.getConfig(), "menu.items." + itemKey);
    }

    public Set<String> getReportDetailsMenuItemKeys() {
        ConfigurationSection section = reportDetailsMenuConfig.getConfig().getConfigurationSection("menu.items");
        return section != null ? section.getKeys(false) : Collections.emptySet();
    }

    public Set<String> getProfileMenuItemKeys() {
        ConfigurationSection section = profileMenuConfig.getConfig().getConfigurationSection("menu.items");
        return section != null ? section.getKeys(false) : Collections.emptySet();
    }

    public Set<String> getFullInventoryMenuItemKeys() {
        ConfigurationSection section = fullInventoryMenuConfig.getConfig().getConfigurationSection("menu.items");
        return section != null ? section.getKeys(false) : Collections.emptySet();
    }

    public Set<String> getEnderChestMenuItemKeys() {
        ConfigurationSection section = enderChestMenuConfig.getConfig().getConfigurationSection("menu.items");
        return section != null ? section.getKeys(false) : Collections.emptySet();
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

    public String getProfileMenuTitle(OfflinePlayer target) {
        String title = profileMenuConfig.getConfig().getString("menu.title", "&8Profile: &b{target}");
        return processPlaceholders(title, target);
    }

    public String getFullInventoryMenuTitle(OfflinePlayer target) {
        String title = fullInventoryMenuConfig.getConfig().getString("menu.title", "&8Inventory: &b{target}");
        return processPlaceholders(title, target);
    }

    public String getEnderChestMenuTitle(OfflinePlayer target) {
        String title = enderChestMenuConfig.getConfig().getString("menu.title", "&8Ender Chest: &b{target}");
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

    public CustomConfig getProfileMenuConfig() {
        return profileMenuConfig;
    }

    public CustomConfig getFullInventoryMenuConfig() {
        return fullInventoryMenuConfig;
    }

    public CustomConfig getEnderChestMenuConfig() {
        return enderChestMenuConfig;
    }

    public CustomConfig getReportsMenuConfig() {
        return reportsMenuConfig;
    }

    public CustomConfig getReportDetailsMenuConfig() {
        return reportDetailsMenuConfig;
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
            if (isDebugEnabled()) {
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
                    if (isDebugEnabled())
                        plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + placeholder + " in history menu lore. Using 'N/A'.");
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
            if (isDebugEnabled()) {
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
                    if (isDebugEnabled())
                        plugin.getLogger().warning("[MainConfigManager] Null replacement value for placeholder " + placeholder + " in punish menu lore. Using 'N/A'.");
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

    public ItemMetaBuilder getProfileMenuItemBuilder(String itemKey, OfflinePlayer target) {
        MenuItem menuItem = getProfileMenuItemConfig(itemKey);
        if (menuItem == null) return new ItemMetaBuilder(new ItemStack(Material.AIR));
        return new ItemMetaBuilder(menuItem.toItemStack(target, this));
    }

    public static class ItemMetaBuilder {
        private final ItemStack itemStack;
        private final ItemMeta itemMeta;

        public ItemMetaBuilder(ItemStack itemStack) {
            this.itemStack = itemStack.clone();
            this.itemMeta = itemStack.hasItemMeta() ? itemStack.getItemMeta().clone() : Bukkit.getItemFactory().getItemMeta(itemStack.getType());
        }

        public ItemMetaBuilder withPlaceholder(String placeholder, String value) {
            if (itemMeta.hasDisplayName()) {
                itemMeta.setDisplayName(itemMeta.getDisplayName().replace(placeholder, value));
            }
            if (itemMeta.hasLore()) {
                List<String> newLore = new ArrayList<>();
                for (String line : itemMeta.getLore()) {
                    newLore.add(line.replace(placeholder, value));
                }
                itemMeta.setLore(newLore);
            }
            return this;
        }

        public ItemStack build() {
            itemStack.setItemMeta(itemMeta);
            return itemStack;
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
            if (!placeholders.isRegistered()) {
                boolean registered = placeholders.register();
                if (registered) {
                    plugin.getLogger().log(Level.INFO, "[MainConfigManager] PlaceholderAPI placeholders registered successfully.");
                } else {
                    plugin.getLogger().log(Level.WARNING, "[MainConfigManager] PlaceholderAPI placeholders failed to register. Check for errors or conflicts.");
                }
            } else {
                if (isDebugEnabled())
                    plugin.getLogger().log(Level.INFO, "[MainConfigManager] PlaceholderAPI placeholders already registered.");
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
                .replace("{support_link}", getSupportLink());

        if (onlineTarget != null) {
            text = text
                    .replace("{target_ip}", onlineTarget.getAddress() != null ? onlineTarget.getAddress().getHostString() : "-")
                    .replace("{target_coords}", String.format("%d %d %d",
                            onlineTarget.getLocation().getBlockX(),
                            onlineTarget.getLocation().getBlockY(),
                            onlineTarget.getLocation().getBlockZ()))
                    .replace("{target_world}", onlineTarget.getWorld().getName())
                    .replace("{xp_level}", String.valueOf(onlineTarget.getLevel()))
                    .replace("{play_time}", TimeUtils.formatTime(onlineTarget.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20, this))
                    .replace("{health}", String.format("%.1f/%.1f", onlineTarget.getHealth(), onlineTarget.getMaxHealth()))
                    .replace("{food_level}", String.valueOf(onlineTarget.getFoodLevel()))
                    .replace("{player_kills}", String.valueOf(onlineTarget.getStatistic(Statistic.PLAYER_KILLS)))
                    .replace("{deaths}", String.valueOf(onlineTarget.getStatistic(Statistic.DEATHS)));
        } else {
            DatabaseManager.PlayerLastState lastState = plugin.getSoftBanDatabaseManager().getPlayerLastState(target.getUniqueId());
            if (lastState != null) {
                text = text
                        .replace("{target_ip}", lastState.getIp() != null ? lastState.getIp() : "-")
                        .replace("{target_coords}", lastState.getLocation() != null ? lastState.getLocation().replace(", ", " ") : "-")
                        .replace("{target_world}", lastState.getWorld() != null ? lastState.getWorld() : "-");
            } else {
                text = text
                        .replace("{target_ip}", "-")
                        .replace("{target_coords}", "-")
                        .replace("{target_world}", "-");
            }
            text = text.replace("{xp_level}", "N/A")
                    .replace("{play_time}", "N/A")
                    .replace("{health}", "N/A")
                    .replace("{food_level}", "N/A")
                    .replace("{player_kills}", "N/A")
                    .replace("{deaths}", "N/A");
        }


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

        // Active punishment counts
        HashMap<String, Integer> activeCounts = plugin.getSoftBanDatabaseManager().getActivePunishmentCounts(target.getUniqueId());
        text = text.replace("{active_ban_count}", String.valueOf(activeCounts.getOrDefault("ban", 0)));
        text = text.replace("{active_mute_count}", String.valueOf(activeCounts.getOrDefault("mute", 0)));
        text = text.replace("{active_kick_count}", String.valueOf(activeCounts.getOrDefault("kick", 0)));
        text = text.replace("{active_softban_count}", String.valueOf(activeCounts.getOrDefault("softban", 0)));
        text = text.replace("{active_warn_count}", String.valueOf(activeCounts.getOrDefault("warn", 0)));
        text = text.replace("{active_freeze_count}", String.valueOf(activeCounts.getOrDefault("freeze", 0)));

        if (plugin.isPlaceholderAPIEnabled() && target.isOnline()) {
            text = PlaceholderAPI.setPlaceholders(target.getPlayer(), text);
        }

        return text;
    }

    public String getPunishmentDisplayForm(String punishmentType, boolean isVerb) {
        if (punishmentType == null || punishmentType.isEmpty()) {
            return "unknown";
        }
        String typeKey = punishmentType.toLowerCase();
        String path;
        String fallback;

        if (isVerb) {
            path = "placeholders.punishment_action_verbs." + typeKey;
            switch (typeKey) {
                case "ban":
                    fallback = "banned";
                    break;
                case "mute":
                    fallback = "muted";
                    break;
                case "kick":
                    fallback = "kicked";
                    break;
                case "warn":
                    fallback = "warned";
                    break;
                case "softban":
                    fallback = "softbanned";
                    break;
                case "freeze":
                    fallback = "frozen";
                    break;
                default:
                    fallback = typeKey + "ed";
            }
        } else {
            path = "placeholders.punishment_type_names." + typeKey;
            fallback = typeKey.substring(0, 1).toUpperCase() + typeKey.substring(1);
        }

        String configuredName = messagesConfig.getConfig().getString(path, fallback);
        return MessageUtils.getColorMessage(configuredName);
    }

    public String getMessage(String path, String... replacements) {
        String message = messagesConfig.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) return "";

        message = processPlaceholders(message, null);

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
        if (config == null || configPath == null || !config.isConfigurationSection(configPath)) {
            return null;
        }

        MenuItem menuItem = loadMenuItemData(config, configPath);

        String confirmStatePath = configPath + ".confirm_state";
        if (config.isConfigurationSection(confirmStatePath)) {
            MenuItem confirmStateItem = loadMenuItemData(config, confirmStatePath);
            if (confirmStateItem != null) {
                if (confirmStateItem.getSlots() == null || confirmStateItem.getSlots().isEmpty()) {
                    confirmStateItem.setSlots(menuItem.getSlots());
                }
                if (confirmStateItem.getLeftClickActions().isEmpty()) {
                    confirmStateItem.setLeftClickActions(menuItem.getLeftClickActions());
                }
                if (confirmStateItem.getRightClickActions().isEmpty()) {
                    confirmStateItem.setRightClickActions(menuItem.getRightClickActions());
                }
                menuItem.setConfirmState(confirmStateItem);
            }
        }

        return menuItem;
    }

    private MenuItem loadMenuItemData(FileConfiguration config, String configPath) {
        MenuItem menuItem = new MenuItem();

        menuItem.setMaterial(config.getString(configPath + ".material", "STONE"));
        menuItem.setName(config.getString(configPath + ".name"));
        menuItem.setLore(config.getStringList(configPath + ".lore"));
        menuItem.setPlayerHead(config.getString(configPath + ".player_head"));
        if (config.contains(configPath + ".custom_model_data")) {
            menuItem.setCustomModelData(config.getInt(configPath + ".custom_model_data"));
        }
        menuItem.setQuantity(config.getInt(configPath + ".quantity", 1));
        menuItem.setSlots(parseSlots(config.getString(configPath + ".slot")));

        List<String> leftClickActionConfigs = config.getStringList(configPath + ".left_click_actions");
        if (!leftClickActionConfigs.isEmpty()) {
            menuItem.setLeftClickActions(leftClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        List<String> rightClickActionConfigs = config.getStringList(configPath + ".right_click_actions");
        if (!rightClickActionConfigs.isEmpty()) {
            menuItem.setRightClickActions(rightClickActionConfigs.stream()
                    .map(MenuItem.ClickActionData::fromConfigString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

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
                            if (isDebugEnabled())
                                plugin.getLogger().warning("Invalid slot range (start > end): '" + part + "' in '" + slotConfig + "'. Skipping range.");
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

    public String getMonthsTimeUnit() {
        return pluginConfig.getConfig().getString("time_units.months", "M");
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

    public CustomConfig getAuditLogConfig() {
        return auditLogConfig;
    }

    public String getAuditLogText(String path, String... replacements) {
        String message = auditLogConfig.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) return "";

        message = processPlaceholders(message, null);

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break;
            String placeholderKey = replacements[i];
            String replacementValue = replacements[i + 1] != null ? replacements[i + 1] : "";
            message = message.replace(placeholderKey, replacementValue);
        }
        return MessageUtils.getColorMessage(message);
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
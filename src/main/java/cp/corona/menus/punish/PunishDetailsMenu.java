// src/main/java/cp/corona/menus/PunishDetailsMenu.java
package cp.corona.menus.punish;

import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PunishDetailsMenu implements InventoryHolder {
    private final Inventory inventory;
    private final UUID targetUUID;
    private final Crown plugin;
    private final String punishmentType;
    private String banTime;
    private String banReason;
    private boolean timeSet = false;
    private boolean reasonSet = false;
    private boolean timeRequired = true;
    private boolean reasonRequiredForConfirmation = true;
    private final OfflinePlayer target;
    private boolean byIp;
    private String currentScope = "global";

    private final Set<String> menuItemKeys = new HashSet<>();

    public static final String SET_TIME_KEY = "set_time";
    public static final String SET_REASON_KEY = "set_reason";
    public static final String CONFIRM_PUNISH_KEY = "confirm_punish";
    public static final String BACK_BUTTON_KEY = "back_button";
    public static final String TOGGLE_METHOD_KEY = "toggle_method_button";
    public static final String TOGGLE_SCOPE_KEY = "toggle_scope_button";
    public static final String UNSOFTBAN_BUTTON_KEY = "unsoftban_button";
    public static final String UNFREEZE_BUTTON_KEY = "unfreeze_button";
    public static final String UNBAN_BUTTON_KEY = "unban_button";
    public static final String UNMUTE_BUTTON_KEY = "unmute_button";
    public static final String UNWARN_BUTTON_KEY = "unwarn_button";
    private static final String BACKGROUND_FILL_1_KEY = "background_fill_1";
    private static final String BACKGROUND_FILL_2_KEY = "background_fill_2";
    private static final String BACKGROUND_FILL_3_KEY = "background_fill_3";


    public PunishDetailsMenu(UUID targetUUID, Crown plugin, String punishmentType) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        this.punishmentType = punishmentType.toLowerCase();
        this.target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getDetailsMenuText("title", target, this.punishmentType);
        int inventorySize = 36;
        inventory = Bukkit.createInventory(this, inventorySize, MessageUtils.getColorMessage(title));

        this.byIp = plugin.getConfigManager().isPunishmentByIp(this.punishmentType);
        setTimeRequiredByType(this.punishmentType);
        setReasonRequiredForConfirmationByType(this.punishmentType);
        loadMenuItems();
        initializeItems();
        updateInventory();
    }

    private void loadMenuItems() {
        menuItemKeys.clear();
        FileConfiguration config = plugin.getConfigManager().getPunishDetailsMenuConfig().getConfig();
        if (config == null) {
            plugin.getLogger().warning("[PunishDetailsMenu] Config file is null for punish_details_menu.yml");
            return;
        }
        String sectionPath = "menu.punish_details." + this.punishmentType + ".items";
        ConfigurationSection itemsSection = config.getConfigurationSection(sectionPath);

        if (itemsSection != null) {
            menuItemKeys.addAll(itemsSection.getKeys(false));
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[PunishDetailsMenu] Loaded keys for type '" + this.punishmentType + "': " + menuItemKeys);
            }
        } else {
            plugin.getLogger().warning("[PunishDetailsMenu] No 'items' section found at path: " + sectionPath);
        }
    }

    private void setTimeRequiredByType(String type) {
        this.timeRequired = !(type.equals("kick") || type.equals("warn") || type.equals("freeze"));
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[PunishDetailsMenu] Time required for type '" + type + "': " + this.timeRequired);
        }
    }

    private void setReasonRequiredForConfirmationByType(String type) {
        this.reasonRequiredForConfirmation = false;
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[PunishDetailsMenu] Reason required for confirmation for type '" + type + "': " + this.reasonRequiredForConfirmation);
        }
    }

    private void initializeItems() {
        updateMenuItems();
    }

    private void updateMenuItems() {
        inventory.clear();

        for (String itemKey : menuItemKeys) {
            if (itemKey.equals(UNSOFTBAN_BUTTON_KEY) && !this.punishmentType.equals("softban")) continue;
            if (itemKey.equals(UNFREEZE_BUTTON_KEY) && !this.punishmentType.equals("freeze")) continue;
            if (itemKey.equals(UNBAN_BUTTON_KEY) && !this.punishmentType.equals("ban")) continue;
            if (itemKey.equals(UNMUTE_BUTTON_KEY) && !this.punishmentType.equals("mute")) continue;
            if (itemKey.equals(UNWARN_BUTTON_KEY) && !this.punishmentType.equals("warn")) continue;
            if (itemKey.equals(TOGGLE_SCOPE_KEY) && !plugin.getConfigManager().isNetworkMode()) continue;

            ItemStack itemStack;
            switch (itemKey) {
                case SET_TIME_KEY:
                    itemStack = getSetTimeItem();
                    break;
                case SET_REASON_KEY:
                    itemStack = getSetReasonItem();
                    break;
                case CONFIRM_PUNISH_KEY:
                    itemStack = getConfirmPunishItem();
                    break;
                case TOGGLE_METHOD_KEY:
                    itemStack = getToggleMethodItem();
                    break;
                case TOGGLE_SCOPE_KEY:
                    itemStack = getToggleScopeItem();
                    break;
                case UNSOFTBAN_BUTTON_KEY:
                    itemStack = getUnSoftBanButton();
                    break;
                case UNFREEZE_BUTTON_KEY:
                    itemStack = getUnFreezeButton();
                    break;
                case UNBAN_BUTTON_KEY:
                    itemStack = getUnBanButton();
                    break;
                case UNMUTE_BUTTON_KEY:
                    itemStack = getUnMuteButton();
                    break;
                case UNWARN_BUTTON_KEY:
                    itemStack = getUnWarnButton();
                    break;
                default:
                    itemStack = getItemStack(itemKey);
                    break;
            }

            if (itemStack != null) {
                setItemInMenu(itemKey, itemStack);
            }
        }
    }

    private ItemStack getItemStack(String itemKey) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[PunishDetailsMenu] getItemStack called for itemKey: " + itemKey + ", punishmentType: " + punishmentType);
        }
        MenuItem menuItemConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, itemKey);
        if (menuItemConfig != null) {
            return menuItemConfig.toItemStack(target, plugin.getConfigManager());
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.WARNING, "[PunishDetailsMenu] getItemStack - No MenuItem config found for itemKey: " + itemKey + " and punishmentType: " + punishmentType);
            }
            return null;
        }
    }

    private void setItemInMenu(String itemKey, ItemStack currentItemStack){
        MenuItem menuItemConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, itemKey);

        if (menuItemConfig != null && currentItemStack != null && menuItemConfig.getSlots() != null && !menuItemConfig.getSlots().isEmpty()) {
            for (int slot : menuItemConfig.getSlots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, currentItemStack.clone());
                } else {
                    plugin.getLogger().warning("Invalid slot " + slot + " configured for item '" + itemKey + "' in punish_details_menu.yml (type: " + punishmentType + "). Must be between 0-" + (inventory.getSize() - 1));
                }
            }
        } else if (menuItemConfig != null && currentItemStack != null && (menuItemConfig.getSlots() == null || menuItemConfig.getSlots().isEmpty())) {
            if(plugin.getConfigManager().isDebugEnabled()){
                plugin.getLogger().warning("No slots defined for item '" + itemKey + "' in punish_details_menu.yml (type: " + punishmentType + "). Item will not be placed.");
            }
        }
    }

    private ItemStack getSetTimeItem() {
        if (!timeRequired) return null;
        MenuItem setTimeConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, SET_TIME_KEY);
        if (setTimeConfig == null) return null;
        String displayTime = this.banTime != null ? this.banTime : plugin.getConfigManager().getMessage("placeholders.not_set");
        return setTimeConfig.toItemStack(target, plugin.getConfigManager(), "{time}", displayTime);
    }

    private ItemStack getSetReasonItem() {
        MenuItem setReasonConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, SET_REASON_KEY);
        if (setReasonConfig == null) return null;
        String displayReason = this.banReason != null ? this.banReason : plugin.getConfigManager().getMessage("placeholders.not_set");
        return setReasonConfig.toItemStack(target, plugin.getConfigManager(), "{reason}", displayReason);
    }

    private ItemStack getToggleMethodItem() {
        MenuItem toggleMethodConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, TOGGLE_METHOD_KEY);
        if (toggleMethodConfig == null) return null;
        String methodName = byIp ? plugin.getConfigManager().getMessage("placeholders.by_ip") : plugin.getConfigManager().getMessage("placeholders.by_local");
        return toggleMethodConfig.toItemStack(target, plugin.getConfigManager(), "{method}", methodName);
    }

    private ItemStack getToggleScopeItem() {
        MenuItem toggleScopeConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, TOGGLE_SCOPE_KEY);
        if (toggleScopeConfig == null) return null;
        return toggleScopeConfig.toItemStack(target, plugin.getConfigManager(), "{scope}", currentScope);
    }

    private ItemStack getConfirmPunishItem() {
        MenuItem confirmPunishConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, CONFIRM_PUNISH_KEY);
        if (confirmPunishConfig == null) return null;
        String methodName = byIp ? plugin.getConfigManager().getMessage("placeholders.by_ip") : plugin.getConfigManager().getMessage("placeholders.by_local");
        String displayTime = this.banTime != null ? this.banTime : plugin.getConfigManager().getMessage("placeholders.not_set");
        String displayReason = this.banReason != null ? this.banReason : plugin.getConfigManager().getMessage("placeholders.not_set");

        return confirmPunishConfig.toItemStack(target, plugin.getConfigManager(),
                "{method}", methodName,
                "{scope}", currentScope,
                "{time_status}", getTimeStatusText(),
                "{reason_status}", getReasonStatusText(),
                "{time}", displayTime,
                "{reason}", displayReason
        );
    }

    private ItemStack getBackButton() {
        return getItemStack(BACK_BUTTON_KEY);
    }

    private ItemStack getUnSoftBanButton() {
        return getItemStack(UNSOFTBAN_BUTTON_KEY);
    }

    private ItemStack getUnFreezeButton() {
        return getItemStack(UNFREEZE_BUTTON_KEY);
    }

    private ItemStack getUnBanButton() {
        return getItemStack(UNBAN_BUTTON_KEY);
    }

    private ItemStack getUnMuteButton() {
        return getItemStack(UNMUTE_BUTTON_KEY);
    }

    private ItemStack getUnWarnButton() {
        return getItemStack(UNWARN_BUTTON_KEY);
    }

    private String getTimeStatusText() {
        if (!timeRequired) {
            return MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.not_applicable"));
        }
        return timeSet ? MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.set"))
                : MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.not_set"));
    }

    private String getReasonStatusText() {
        if (reasonSet) {
            return MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.set"));
        }
        if (!reasonRequiredForConfirmation) {
            return MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.optional_not_set"));
        }
        return MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.not_set"));
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        updateInventory();
        player.openInventory(inventory);
    }

    public void togglePunishMethod() {
        this.byIp = !this.byIp;
        updateToggleMethodItem();
        updateConfirmButtonStatus();
    }

    public void toggleScope() {
        List<String> scopes = new ArrayList<>();
        scopes.add("global");
        scopes.addAll(plugin.getConfigManager().getKnownServers());
        
        int currentIndex = scopes.indexOf(currentScope);
        int nextIndex = (currentIndex + 1) % scopes.size();
        currentScope = scopes.get(nextIndex);
        
        updateToggleScopeItem();
        updateConfirmButtonStatus();
    }

    public void setByIp(boolean byIp) {
        this.byIp = byIp;
        updateToggleMethodItem();
        updateConfirmButtonStatus();
    }

    public void updateToggleMethodItem() {
        ItemStack toggleItem = getToggleMethodItem();
        if (toggleItem != null) {
            setItemInMenu(TOGGLE_METHOD_KEY, toggleItem);
        }
    }

    public void updateToggleScopeItem() {
        ItemStack toggleItem = getToggleScopeItem();
        if (toggleItem != null) {
            setItemInMenu(TOGGLE_SCOPE_KEY, toggleItem);
        }
    }

    public String getPunishmentType() {
        return punishmentType;
    }

    public String getBanTime() {
        return banTime;
    }

    public void setBanTime(String banTime) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[PunishDetailsMenu] setBanTime: " + banTime + " for type: " + punishmentType);
        }
        this.banTime = banTime;
        this.timeSet = (banTime != null && !banTime.isEmpty());
        updateSetTimeItem();
        updateConfirmButtonStatus();
    }

    public String getBanReason() {
        if (banReason == null || banReason.trim().isEmpty()) {
            return plugin.getConfigManager().getDefaultPunishmentReason(this.punishmentType);
        }
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
        this.reasonSet = (banReason != null && !banReason.trim().isEmpty());
        updateSetReasonItem();
        updateConfirmButtonStatus();
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public boolean isByIp() {
        return byIp;
    }

    public boolean isTimeSet() {
        return !timeRequired || timeSet;
    }

    public boolean isReasonSet() {
        return reasonSet;
    }

    public boolean isReasonRequiredForConfirmation() {
        return reasonRequiredForConfirmation;
    }

    public boolean isTimeRequired() {
        return timeRequired;
    }

    public void updateSetTimeItem() {
        ItemStack setTimeItem = getSetTimeItem();
        if (setTimeItem != null) {
            setItemInMenu(SET_TIME_KEY, setTimeItem);
        } else if (timeRequired) {
            plugin.getLogger().warning("Failed to update Set Time item for type " + punishmentType + ". Check configuration.");
        }
    }

    public void updateSetReasonItem() {
        ItemStack setReasonItem = getSetReasonItem();
        if (setReasonItem != null) {
            setItemInMenu(SET_REASON_KEY, setReasonItem);
        } else {
            plugin.getLogger().warning("Failed to update Set Reason item for type " + punishmentType + ". Check configuration.");
        }
    }

    public void updateConfirmButtonStatus() {
        ItemStack confirmPunishItem = getConfirmPunishItem();
        if (confirmPunishItem != null) {
            setItemInMenu(CONFIRM_PUNISH_KEY, confirmPunishItem);
        } else {
            plugin.getLogger().warning("Failed to update Confirm Punish item for type " + punishmentType + ". Check configuration.");
        }
    }

    private void updateInventory() {
        List<Player> viewers = inventory.getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .collect(Collectors.toList());

        if (viewers.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isTimeRequired()) {
                ItemStack timeItem = getSetTimeItem();
                if (timeItem != null) setItemInMenu(SET_TIME_KEY, timeItem);
            }
            ItemStack reasonItem = getSetReasonItem();
            if (reasonItem != null) setItemInMenu(SET_REASON_KEY, reasonItem);
            ItemStack confirmItem = getConfirmPunishItem();
            if (confirmItem != null) setItemInMenu(CONFIRM_PUNISH_KEY, confirmItem);
            ItemStack toggleItem = getToggleMethodItem();
            if (toggleItem != null) setItemInMenu(TOGGLE_METHOD_KEY, toggleItem);
            ItemStack toggleScopeItem = getToggleScopeItem();
            if (toggleScopeItem != null) setItemInMenu(TOGGLE_SCOPE_KEY, toggleScopeItem);

            for (Player viewer : viewers) {
                viewer.updateInventory();
            }
        });
    }

    public Set<String> getMenuItemKeys() {
        return menuItemKeys;
    }
}
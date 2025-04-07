package cp.corona.menus;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Represents the dynamic punishment details menu, versatile for Ban, Mute, SoftBan, Kick, Warn, and Freeze. - MODIFIED: Added Freeze
 * Allows setting specific details for a punishment like time and reason, adaptable by punishment type.
 *
 * **MODIFIED:**
 * - Implemented dynamic loading of menu items from configuration files.
 * - Removed hardcoded item keys and rely on dynamically loaded keys.
 * - **CORRECTION:** Called `updateInventory()` in constructor to initialize placeholders on menu open.
 */
public class PunishDetailsMenu implements InventoryHolder {
    private final Inventory inventory;
    private final UUID targetUUID;
    private final CrownPunishments plugin;
    private final String punishmentType;
    private String banTime;
    private String banReason;
    private boolean timeSet = false;
    private boolean reasonSet = false;
    private boolean timeRequired = true; // Flag to indicate if time is required for the punishment type
    private boolean reasonRequiredForConfirmation = true; // Flag to indicate if reason is required for confirmation - NEW
    private final OfflinePlayer target; // Target player for menu


    /**
     * Stores the item keys in a Set, dynamically loaded from config.
     */
    private final Set<String> menuItemKeys = new HashSet<>();

    // Constants for item keys in PunishDetailsMenu (Not needed anymore for dynamic items but kept for reference and existing code)
    public static final String SET_TIME_KEY = "set_time";
    public static final String SET_REASON_KEY = "set_reason";
    public static final String CONFIRM_PUNISH_KEY = "confirm_punish";
    public static final String BACK_BUTTON_KEY = "back_button";
    public static final String UNSOFTBAN_BUTTON_KEY = "unsoftban_button";
    public static final String UNFREEZE_BUTTON_KEY = "unfreeze_button"; // New button for unfreezing - NEW
    private static final String BACKGROUND_FILL_1_KEY = "background_fill_1"; // Background fill item key 1
    private static final String BACKGROUND_FILL_2_KEY = "background_fill_2"; // Background fill item key 2


    /**
     * Constructor for PunishDetailsMenu.
     * @param targetUUID UUID of the target player.
     * @param plugin Instance of the main plugin class.
     * @param punishmentType Type of punishment (ban, mute, softban, kick, warn, freeze). - MODIFIED: Added freeze
     */
    public PunishDetailsMenu(UUID targetUUID, CrownPunishments plugin, String punishmentType) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        this.punishmentType = punishmentType;
        this.target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getDetailsMenuText("title", target, punishmentType);
        inventory = Bukkit.createInventory(this, 36, title);
        setTimeRequiredByType(punishmentType);
        setReasonRequiredForConfirmationByType(punishmentType);
        loadMenuItems(); // Load menu items dynamically from config
        initializeItems();
        updateInventory(); // **CORRECTION: Call updateInventory() here to initialize placeholders on menu open**

        if (punishmentType.equalsIgnoreCase("freeze")) {
            menuItemKeys.remove(SET_TIME_KEY);
            this.reasonRequiredForConfirmation = false;
        }
    }

    /**
     * Loads menu items dynamically from punish_details_menu.yml for the specific punishment type.
     * Iterates through the 'items' section for the punishment type and adds each item key to menuItemKeys.
     */
    private void loadMenuItems() {
        menuItemKeys.clear(); // Clear any existing keys to reload fresh from config
        Set<String> configKeys = plugin.getConfigManager().getPunishDetailsMenuConfig().getConfig().getConfigurationSection("menu.punish_details." + punishmentType + ".items").getKeys(false);
        menuItemKeys.addAll(configKeys);
    }


    /**
     * Sets whether time is required for the current punishment type.
     * Kick, Warn, and Freeze do not require time. - MODIFIED: Added Freeze
     * @param punishmentType Type of punishment.
     */
    private void setTimeRequiredByType(String punishmentType) {
        if (punishmentType.equalsIgnoreCase("kick") || punishmentType.equalsIgnoreCase("warn") || punishmentType.equalsIgnoreCase("freeze")) {
            this.timeRequired = false;
        } else {
            this.timeRequired = true;
        }
    }

    /**
     * Sets whether reason is required for confirmation based on punishment type. - NEW
     * Reason is optional for Freeze. - NEW
     * @param punishmentType Type of punishment. - NEW
     */
    private void setReasonRequiredForConfirmationByType(String punishmentType) {
        if (punishmentType.equalsIgnoreCase("freeze")) {
            this.reasonRequiredForConfirmation = false;
        } else {
            this.reasonRequiredForConfirmation = true;
        }
    }


    /**
     * Initializes the items in the menu based on the punishment type.
     * Dynamically loads items based on menuItemKeys.
     */
    private void initializeItems() {
        updateMenuItems(); // Call updateMenuItems to initialize and set items
    }

    /**
     * Updates all menu items. Called on initialize and when inventory needs to refresh.
     */
    private void updateMenuItems() {
        for (String itemKey : menuItemKeys) {
            if (!itemKey.equals(UNSOFTBAN_BUTTON_KEY) && !itemKey.equals(UNFREEZE_BUTTON_KEY) ) {
                if (!itemKey.equals(SET_TIME_KEY) || !punishmentType.equalsIgnoreCase("freeze")) {
                    setItemInMenu(itemKey, getItemStack(itemKey));
                }
            }
        }

        if (punishmentType.equalsIgnoreCase("softban")) {
            setItemInMenu(UNSOFTBAN_BUTTON_KEY, getUnSoftBanButton());
        }

        if (punishmentType.equalsIgnoreCase("freeze")) {
            setItemInMenu(UNFREEZE_BUTTON_KEY, getUnFreezeButton());
        }
    }

    /**
     * @param itemKey Key of the item in the configuration.
     * @return The ItemStack or null if configuration is missing.
     */
    private ItemStack getItemStack(String itemKey) {
        if (plugin.getConfigManager().isDebugEnabled()) { // Debug log - getItemStack called
            plugin.getLogger().log(Level.INFO, "[PunishDetailsMenu] getItemStack called for itemKey: " + itemKey + ", punishmentType: " + punishmentType);
        }
        MenuItem menuItemConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, itemKey);
        if (menuItemConfig != null) {
            return menuItemConfig.toItemStack(target, plugin.getConfigManager());
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) { // Debug log if no config found
                plugin.getLogger().log(Level.WARNING, "[PunishDetailsMenu] getItemStack - No MenuItem config found for itemKey: " + itemKey + " and punishmentType: " + punishmentType);
            }
            return null; // Return null if no config found
        }
    }


    /**
     * Sets an item in the menu at the slots defined in the configuration.
     * @param itemKey Key of the item in the configuration.
     * @param currentItemStack ItemStack to set in the menu.
     */
    private void setItemInMenu(String itemKey, ItemStack currentItemStack){
        MenuItem menuItemConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, itemKey);
        if (menuItemConfig != null && currentItemStack != null && menuItemConfig.getSlots() != null) {
            for (int slot : menuItemConfig.getSlots()) {
                if (slot >= 0 && slot < inventory.getSize()) { // Check if slot is valid
                    inventory.setItem(slot, currentItemStack);
                } else {
                    plugin.getLogger().warning("Invalid slot " + slot + " for item " + itemKey + " in punish_details_menu.yml, must be between 0-" + (inventory.getSize() - 1));
                }
            }
        }
    }

    /**
     * Gets the "Set Time" item stack, updating lore with current time if set.
     * @return ItemStack for set time item.
     */
    private ItemStack getSetTimeItem() {
        if (punishmentType.equalsIgnoreCase("freeze")) return null; // Do not return set time item for freeze
        MenuItem setTimeConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, SET_TIME_KEY);
        if (setTimeConfig == null) return null;
        ItemStack item = setTimeConfig.toItemStack(target, plugin.getConfigManager());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayTime = this.banTime != null ? this.banTime : plugin.getConfigManager().getMessage("messages.not_set");
            List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(punishmentType, SET_TIME_KEY, target, "{time}", displayTime);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gets the "Set Reason" item stack, updating lore with current reason if set.
     * @return ItemStack for set reason item.
     */
    private ItemStack getSetReasonItem() {
        MenuItem setReasonConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, SET_REASON_KEY);
        if (setReasonConfig == null) return null;
        ItemStack item = setReasonConfig.toItemStack(target, plugin.getConfigManager());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayReason = this.banReason != null ? this.banReason : plugin.getConfigManager().getMessage("messages.not_set");
            List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(punishmentType, SET_REASON_KEY, target, "{reason}", displayReason);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates the "Confirm Punish" item, disabled if time or reason are not set (depending on punishment type).
     * @return ItemStack for confirm punish item.
     */
    private ItemStack getConfirmPunishItem() {
        MenuItem confirmPunishConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, CONFIRM_PUNISH_KEY);
        if (confirmPunishConfig == null) return null;
        ItemStack item = confirmPunishConfig.toItemStack(target, plugin.getConfigManager());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Ensure display name is set from config here as well, to cover all cases
            // FIXED: Use MenuItem's getName() to get the configured name, already processed with placeholders in toItemStack()
            meta.setDisplayName(item.getItemMeta().getDisplayName()); // Corrected line: Use existing display name from toItemStack

            if ((timeRequired && !timeSet) || (reasonRequiredForConfirmation && !reasonSet) && !punishmentType.equalsIgnoreCase("freeze")) { // Check timeRequired and reasonRequiredForConfirmation flags - MODIFIED CONDITION - Reason is NOT required for freeze confirmation
                List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(punishmentType, CONFIRM_PUNISH_KEY, target,
                        "{time_status}", getTimeStatusText(),
                        "{reason_status}", getReasonStatusText());
                meta.setLore(lore);
            } else {
                List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(punishmentType, CONFIRM_PUNISH_KEY, target,
                        "{time_status}", getTimeStatusText(),
                        "{reason_status}", getReasonStatusText());
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }


    /**
     * Gets the "Back Button" item.
     * @return ItemStack for back button item.
     */
    private ItemStack getBackButton() {
        MenuItem backButtonConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, BACK_BUTTON_KEY);
        if (backButtonConfig == null) return null;
        return backButtonConfig.toItemStack(target, plugin.getConfigManager());
    }

    /**
     * Creates the "Unsoftban" button, specifically for the SoftBanDetailsMenu.
     * @return ItemStack for unsoftban button item.
     */
    private ItemStack getUnSoftBanButton() {
        MenuItem unSoftbanButtonConfig = plugin.getConfigManager().getDetailsMenuItemConfig("softban", UNSOFTBAN_BUTTON_KEY);
        if (unSoftbanButtonConfig == null) return null;
        return unSoftbanButtonConfig.toItemStack(target, plugin.getConfigManager());
    }

    /**
     * Creates the "Unfreeze" button, specifically for the FreezeDetailsMenu. - NEW
     * @return ItemStack for unfreeze button item. - NEW
     */
    private ItemStack getUnFreezeButton() { // NEW
        MenuItem unfreezeButtonConfig = plugin.getConfigManager().getDetailsMenuItemConfig("freeze", UNFREEZE_BUTTON_KEY); // Get config for unfreeze button - NEW
        if (unfreezeButtonConfig == null) return null;
        return unfreezeButtonConfig.toItemStack(target, plugin.getConfigManager());
    }

    /**
     * Gets the formatted status text for time, indicating if it's set.
     * @return Formatted time status text.
     */
    private String getTimeStatusText() {
        // Using configurable messages for "set" and "not_set"
        return timeRequired ? (timeSet ? MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set")) : MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.not_set"))) : MessageUtils.getColorMessage("&a\u2705 N/A"); // Adjusted for timeRequired
    }

    /**
     * Gets the formatted status text for reason, indicating if it's set.
     * @return Formatted reason status text.
     */
    private String getReasonStatusText() {
        // Using configurable messages for "set" and "not_set"
        return reasonSet ? MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set")) : MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.not_set"));
    }

    /**
     * Returns the Inventory object for this menu.
     * @return The inventory of this menu.
     */
    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Opens the menu for a player.
     * @param player Player to open the menu for.
     */
    public void open(Player player) {
        player.openInventory(inventory);
        plugin.getMenuListener().executeMenuOpenActions(player, this); // Call executeMenuOpenActions from MenuListener
    }


    /**
     * Gets the punishment type of this details menu.
     * @return Punishment type string.
     */
    public String getPunishmentType() {
        return punishmentType;
    }

    /**
     * Gets the ban time set in this menu.
     * @return Ban time string.
     */
    public String getBanTime() {
        return banTime;
    }

    /**
     * Sets the ban time and updates menu items.
     * @param banTime Ban time string to set.
     */
    public void setBanTime(String banTime) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "PunishDetailsMenu setBanTime: " + banTime); // Log banTime being set
        }
        this.banTime = banTime;
        this.timeSet = true;
        updateSetTimeItem();
        updateConfirmButtonStatus();
    }

    /**
     * Gets the ban reason set in this menu.
     * @return Ban reason string.
     */
    public String getBanReason() {
        return banReason;
    }

    /**
     * Sets the ban reason and updates menu items.
     * @param banReason Ban reason string to set.
     */
    public void setBanReason(String banReason) {
        this.banReason = banReason;
        this.reasonSet = true;
        updateSetReasonItem();
        updateConfirmButtonStatus();
    }

    /**
     * Gets the target player UUID for this menu.
     * @return Target player UUID.
     */
    public UUID getTargetUUID() {
        return targetUUID;
    }

    /**
     * Checks if time is set for punishments requiring time.
     * @return true if time is set or not required, false otherwise.
     */
    public boolean isTimeSet() {
        return !timeRequired || timeSet; // Time is considered set if not required, or if it is set
    }

    /**
     * Checks if reason is set.
     * @return true if reason is set, false otherwise.
     */
    public boolean isReasonSet() {
        return reasonSet;
    }

    /**
     * Checks if reason is required for confirmation. - NEW
     * @return true if reason is required, false otherwise. - NEW
     */
    public boolean isReasonRequiredForConfirmation() { // NEW
        return reasonRequiredForConfirmation;
    }

    /**
     * Checks if time is required for the current punishment type.
     * @return true if time is required, false otherwise.
     */
    public boolean isTimeRequired() {
        return timeRequired;
    }

    /**
     * Updates the "Set Time" item in the menu.
     */
    public void updateSetTimeItem() {
        ItemStack setTimeItem = getSetTimeItem();
        if (setTimeItem != null) {
            setItemInMenu(SET_TIME_KEY, setTimeItem);
        }
        updateInventory();
    }

    /**
     * Updates the "Set Reason" item in the menu.
     */
    public void updateSetReasonItem() {
        ItemStack setReasonItem = getSetReasonItem();
        if (setReasonItem != null) {
            setItemInMenu(SET_REASON_KEY, setReasonItem);
        }
        updateInventory();
    }

    /**
     * Updates the "Confirm Punish" button based on whether time and reason are set (and if time is required).
     */
    public void updateConfirmButtonStatus() {
        ItemStack confirmPunishItem = getConfirmPunishItem();
        if (confirmPunishItem != null) {
            setItemInMenu(CONFIRM_PUNISH_KEY, confirmPunishItem);
        }
        updateInventory();
    }

    /**
     * Updates the inventory for players viewing the menu.
     * This is necessary to reflect changes made to menu items.
     *
     * **CORRECTION:** Re-fetching and re-setting dynamic items to refresh placeholders on inventory update.
     */
    private void updateInventory() {
        // Re-fetch and re-set dynamic items to update placeholders - **CORRECTION**
        setItemInMenu(SET_TIME_KEY, getSetTimeItem());
        setItemInMenu(SET_REASON_KEY, getSetReasonItem());
        setItemInMenu(CONFIRM_PUNISH_KEY, getConfirmPunishItem());

        // Get all viewers of the inventory and update their view
        List<Player> viewers = inventory.getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .collect(Collectors.toList());

        for (Player viewer : viewers) {
            viewer.updateInventory();
        }
    }

    /**
     * Gets the set of MenuItem keys used in this menu.
     * This is used for dynamic item loading in MenuListener.
     *
     * @return A Set of item keys (String).
     */
    public Set<String> getMenuItemKeys() {
        return menuItemKeys;
    }
}
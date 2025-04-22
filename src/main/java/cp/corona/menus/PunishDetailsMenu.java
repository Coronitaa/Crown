// PunishDetailsMenu.java
package cp.corona.menus;

import cp.corona.crownpunishments.CrownPunishments;
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
 * Represents the dynamic punishment details menu, versatile for Ban, Mute, SoftBan, Kick, Warn, and Freeze.
 * Allows setting specific details for a punishment like time and reason, adaptable by punishment type.
 *
 * **MODIFIED:**
 * - Implemented dynamic loading of menu items from configuration files.
 * - Removed hardcoded item keys and rely on dynamically loaded keys.
 * - **CORRECTION:** Called `updateInventory()` in constructor to initialize placeholders on menu open.
 * - **CORRECTION:** Uses correct paths for 'set'/'not_set' placeholders from messages.yml.
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
    private boolean reasonRequiredForConfirmation = true; // Flag to indicate if reason is required for confirmation
    private final OfflinePlayer target; // Target player for menu


    /**
     * Stores the item keys in a Set, dynamically loaded from config.
     */
    private final Set<String> menuItemKeys = new HashSet<>();

    // Constants for item keys (still useful for internal logic referencing specific items)
    public static final String SET_TIME_KEY = "set_time";
    public static final String SET_REASON_KEY = "set_reason";
    public static final String CONFIRM_PUNISH_KEY = "confirm_punish";
    public static final String BACK_BUTTON_KEY = "back_button";
    public static final String UNSOFTBAN_BUTTON_KEY = "unsoftban_button";
    public static final String UNFREEZE_BUTTON_KEY = "unfreeze_button";
    public static final String UNBAN_BUTTON_KEY = "unban_button"; // Added for consistency if used
    public static final String UNMUTE_BUTTON_KEY = "unmute_button"; // Added for consistency if used
    public static final String UNWARN_BUTTON_KEY = "unwarn_button"; // Added for consistency if used
    // Background keys can be handled dynamically if needed, or kept if logic depends on them
    private static final String BACKGROUND_FILL_1_KEY = "background_fill_1";
    private static final String BACKGROUND_FILL_2_KEY = "background_fill_2";
    private static final String BACKGROUND_FILL_3_KEY = "background_fill_3";


    /**
     * Constructor for PunishDetailsMenu.
     * @param targetUUID UUID of the target player.
     * @param plugin Instance of the main plugin class.
     * @param punishmentType Type of punishment (ban, mute, softban, kick, warn, freeze).
     */
    public PunishDetailsMenu(UUID targetUUID, CrownPunishments plugin, String punishmentType) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        this.punishmentType = punishmentType.toLowerCase(); // Ensure lowercase for consistency
        this.target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getDetailsMenuText("title", target, this.punishmentType);
        // Determine inventory size (example: 36) - could be made configurable per type
        int inventorySize = 36; // Example size
        inventory = Bukkit.createInventory(this, inventorySize, title);

        setTimeRequiredByType(this.punishmentType);
        setReasonRequiredForConfirmationByType(this.punishmentType);
        loadMenuItems(); // Load menu items dynamically from config
        initializeItems(); // Initialize items based on loaded keys
        updateInventory(); // Call updateInventory() here to initialize placeholders on menu open
    }

    /**
     * Loads menu items dynamically from punish_details_menu.yml for the specific punishment type.
     * Iterates through the 'items' section for the punishment type and adds each item key to menuItemKeys.
     */
    private void loadMenuItems() {
        menuItemKeys.clear(); // Clear any existing keys to reload fresh from config
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


    /**
     * Sets whether time is required for the current punishment type.
     * Kick, Warn, and Freeze do not require time.
     * @param type Type of punishment (lowercase).
     */
    private void setTimeRequiredByType(String type) {
        this.timeRequired = !(type.equals("kick") || type.equals("warn") || type.equals("freeze"));
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[PunishDetailsMenu] Time required for type '" + type + "': " + this.timeRequired);
        }
    }

    /**
     * Sets whether reason is required for confirmation based on punishment type.
     * Reason is optional for Freeze.
     * @param type Type of punishment (lowercase).
     */
    private void setReasonRequiredForConfirmationByType(String type) {
        this.reasonRequiredForConfirmation = !type.equals("freeze");
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[PunishDetailsMenu] Reason required for confirmation for type '" + type + "': " + this.reasonRequiredForConfirmation);
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
     * Updates all menu items based on the dynamically loaded keys and current state.
     * Called on initialize and when inventory needs to refresh.
     */
    private void updateMenuItems() {
        inventory.clear(); // Clear inventory before re-adding items

        for (String itemKey : menuItemKeys) {
            // Special handling for optional buttons or state-dependent items
            if (itemKey.equals(UNSOFTBAN_BUTTON_KEY) && !this.punishmentType.equals("softban")) continue;
            if (itemKey.equals(UNFREEZE_BUTTON_KEY) && !this.punishmentType.equals("freeze")) continue;
            if (itemKey.equals(UNBAN_BUTTON_KEY) && !this.punishmentType.equals("ban")) continue;
            if (itemKey.equals(UNMUTE_BUTTON_KEY) && !this.punishmentType.equals("mute")) continue;
            if (itemKey.equals(UNWARN_BUTTON_KEY) && !this.punishmentType.equals("warn")) continue;

            // Handle items that need state updates (time, reason, confirm)
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
                case UNSOFTBAN_BUTTON_KEY: // Explicitly handle unsoftban button if needed
                    itemStack = getUnSoftBanButton();
                    break;
                case UNFREEZE_BUTTON_KEY: // Explicitly handle unfreeze button
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
                    // For other items (like background, info, back), get directly
                    itemStack = getItemStack(itemKey);
                    break;
            }

            if (itemStack != null) {
                setItemInMenu(itemKey, itemStack); // Set the item in the menu
            }
        }
    }

    /**
     * Gets the ItemStack for a given item key by fetching configuration dynamically.
     * @param itemKey Key of the item in the configuration.
     * @return The ItemStack or null if configuration is missing.
     */
    private ItemStack getItemStack(String itemKey) {
        // Debug log: Trace the call
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[PunishDetailsMenu] getItemStack called for itemKey: " + itemKey + ", punishmentType: " + punishmentType);
        }
        MenuItem menuItemConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, itemKey);
        if (menuItemConfig != null) {
            // Pass the target player for placeholder processing in toItemStack
            return menuItemConfig.toItemStack(target, plugin.getConfigManager());
        } else {
            // Log warning only in debug mode to avoid spam for potentially optional items
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.WARNING, "[PunishDetailsMenu] getItemStack - No MenuItem config found for itemKey: " + itemKey + " and punishmentType: " + punishmentType);
            }
            return null; // Return null if no config found
        }
    }


    /**
     * Sets an item in the menu at the slots defined in the configuration.
     * Fetches the MenuItem configuration to determine the correct slots.
     *
     * @param itemKey Key of the item in the configuration.
     * @param currentItemStack ItemStack to set in the menu.
     */
    private void setItemInMenu(String itemKey, ItemStack currentItemStack){
        // Get the configuration for the item to find its designated slots
        MenuItem menuItemConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, itemKey);

        // Proceed only if configuration and itemstack are valid, and slots are defined
        if (menuItemConfig != null && currentItemStack != null && menuItemConfig.getSlots() != null && !menuItemConfig.getSlots().isEmpty()) {
            for (int slot : menuItemConfig.getSlots()) {
                // Validate slot number against inventory size before setting
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, currentItemStack.clone()); // Use clone to prevent issues with shared ItemStacks
                } else {
                    // Log a warning if an invalid slot number is specified in the config
                    plugin.getLogger().warning("Invalid slot " + slot + " configured for item '" + itemKey + "' in punish_details_menu.yml (type: " + punishmentType + "). Must be between 0-" + (inventory.getSize() - 1));
                }
            }
        } else if (menuItemConfig != null && currentItemStack != null && (menuItemConfig.getSlots() == null || menuItemConfig.getSlots().isEmpty())) {
            // Log a warning if an item is defined but has no valid slots configured
            if(plugin.getConfigManager().isDebugEnabled()){
                plugin.getLogger().warning("No slots defined for item '" + itemKey + "' in punish_details_menu.yml (type: " + punishmentType + "). Item will not be placed.");
            }
        }
        // No warning needed if itemStack is null, as getItemStack handles that case.
    }

    /**
     * Gets the "Set Time" item stack, updating lore with current time if set.
     * Returns null if the current punishment type does not require time (e.g., freeze).
     * @return ItemStack for set time item, or null.
     */
    private ItemStack getSetTimeItem() {
        // Don't show/create the "Set Time" item for punishment types that don't need it
        if (!timeRequired) return null;

        // Get the base item configuration
        MenuItem setTimeConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, SET_TIME_KEY);
        if (setTimeConfig == null) return null; // Config missing

        // Create the ItemStack from config (handles placeholders in name/base lore)
        ItemStack item = setTimeConfig.toItemStack(target, plugin.getConfigManager());
        if (item == null) return null; // Couldn't create ItemStack

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Determine the display text for the time (current value or "Not Set")
            String displayTime = this.banTime != null ? this.banTime : plugin.getConfigManager().getMessage("placeholders.not_set"); // Use corrected path
            // Get the configured lore and apply replacements
            List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(punishmentType, SET_TIME_KEY, target, "{time}", displayTime);
            meta.setLore(lore); // Set the processed lore
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gets the "Set Reason" item stack, updating lore with current reason if set.
     * @return ItemStack for set reason item.
     */
    private ItemStack getSetReasonItem() {
        // Get the base item configuration
        MenuItem setReasonConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, SET_REASON_KEY);
        if (setReasonConfig == null) return null; // Config missing

        // Create the ItemStack
        ItemStack item = setReasonConfig.toItemStack(target, plugin.getConfigManager());
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Determine the display text for the reason
            String displayReason = this.banReason != null ? this.banReason : plugin.getConfigManager().getMessage("placeholders.not_set"); // Use corrected path
            // Get the configured lore and apply replacements
            List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(punishmentType, SET_REASON_KEY, target, "{reason}", displayReason);
            meta.setLore(lore); // Set the processed lore
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates the "Confirm Punish" item, updating its lore based on whether requirements (time/reason) are met.
     * @return ItemStack for confirm punish item.
     */
    private ItemStack getConfirmPunishItem() {
        // Get the base item configuration
        MenuItem confirmPunishConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, CONFIRM_PUNISH_KEY);
        if (confirmPunishConfig == null) return null; // Config missing

        // Create the ItemStack
        ItemStack item = confirmPunishConfig.toItemStack(target, plugin.getConfigManager());
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Name might already be set correctly by toItemStack if it contains placeholders
            // meta.setDisplayName(...); // Usually not needed here if name is static or processed by toItemStack

            // Get the configured lore and apply status placeholders
            List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(
                    punishmentType,
                    CONFIRM_PUNISH_KEY,
                    target, // Target for standard placeholders in lore template
                    "{time_status}", getTimeStatusText(), // Replace {time_status}
                    "{reason_status}", getReasonStatusText() // Replace {reason_status}
            );
            meta.setLore(lore); // Set the processed lore
            item.setItemMeta(meta);
        }
        return item;
    }


    /**
     * Gets the "Back Button" item stack from configuration.
     * @return ItemStack for back button item.
     */
    private ItemStack getBackButton() {
        // Delegate directly to getItemStack using the specific key
        return getItemStack(BACK_BUTTON_KEY);
    }

    /**
     * Creates the "Unsoftban" button stack from configuration, intended for the SoftBanDetailsMenu.
     * @return ItemStack for unsoftban button item.
     */
    private ItemStack getUnSoftBanButton() {
        // Delegate directly to getItemStack using the specific key
        return getItemStack(UNSOFTBAN_BUTTON_KEY);
    }

    /**
     * Creates the "Unfreeze" button stack from configuration, intended for the FreezeDetailsMenu.
     * @return ItemStack for unfreeze button item.
     */
    private ItemStack getUnFreezeButton() {
        // Delegate directly to getItemStack using the specific key
        return getItemStack(UNFREEZE_BUTTON_KEY);
    }

    /**
     * Creates the "Unban" button stack from configuration, intended for the BanDetailsMenu.
     * @return ItemStack for the unban button.
     */
    private ItemStack getUnBanButton() {
        return getItemStack(UNBAN_BUTTON_KEY);
    }

    /**
     * Creates the "Unmute" button stack from configuration, intended for the MuteDetailsMenu.
     * @return ItemStack for the unmute button.
     */
    private ItemStack getUnMuteButton() {
        return getItemStack(UNMUTE_BUTTON_KEY);
    }

    /**
     * Creates the "Unwarn" button stack from configuration, intended for the WarnDetailsMenu.
     * @return ItemStack for the unwarn button.
     */
    private ItemStack getUnWarnButton() {
        return getItemStack(UNWARN_BUTTON_KEY);
    }


    /**
     * Gets the formatted status text for time, indicating if it's set.
     * Uses the configurable text from messages.yml (placeholders section).
     * Handles cases where time is not required (kick/warn/freeze).
     * @return Formatted time status text.
     */
    private String getTimeStatusText() {
        if (!timeRequired) {
            // Optionally make "N/A" configurable too, e.g., placeholders.not_applicable
            return MessageUtils.getColorMessage("&a\u2705 N/A"); // Checkmark + N/A if time not needed
        }
        // Use the correct path "placeholders.set" or "placeholders.not_set"
        return timeSet ? MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.set")) // CORRECTED PATH
                : MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.not_set")); // CORRECTED PATH
    }

    /**
     * Gets the formatted status text for reason, indicating if it's set.
     * Uses the configurable text from messages.yml (placeholders section).
     * Handles cases where reason is not required for confirmation (freeze).
     * @return Formatted reason status text.
     */
    private String getReasonStatusText() {
        if (!reasonRequiredForConfirmation) {
            // Optionally make "Optional" configurable, e.g., placeholders.optional
            return MessageUtils.getColorMessage("&eOptional"); // Indicate reason is optional
        }
        // Use the correct path "placeholders.set" or "placeholders.not_set"
        return reasonSet ? MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.set")) // CORRECTED PATH
                : MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("placeholders.not_set")); // CORRECTED PATH
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
        // Ensure items are up-to-date before opening
        updateInventory(); // Call this to refresh state just before opening
        player.openInventory(inventory);
    }


    /**
     * Gets the punishment type (lowercase) of this details menu.
     * @return Punishment type string.
     */
    public String getPunishmentType() {
        return punishmentType;
    }

    /**
     * Gets the ban time string set in this menu (e.g., "1d", "Permanent").
     * Returns null if not set.
     * @return Ban time string or null.
     */
    public String getBanTime() {
        return banTime;
    }

    /**
     * Sets the ban time and updates relevant menu items.
     * @param banTime Ban time string to set (e.g., "1h", "Permanent").
     */
    public void setBanTime(String banTime) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[PunishDetailsMenu] setBanTime: " + banTime + " for type: " + punishmentType);
        }
        this.banTime = banTime;
        this.timeSet = (banTime != null && !banTime.isEmpty()); // Update timeSet flag
        updateSetTimeItem(); // Update the visual item
        updateConfirmButtonStatus(); // Update confirm button state
    }

    /**
     * Gets the ban reason string set in this menu.
     * Returns null if not set.
     * @return Ban reason string or null.
     */
    public String getBanReason() {
        // Return a default reason if none is set and it's required, or just the reason/null
        if (banReason == null || banReason.trim().isEmpty()) {
            // Provide a default reason if none set, especially for types like kick/warn/freeze
            // where it might be expected even if not explicitly prompted.
            // Could be made configurable.
            switch(this.punishmentType) {
                case "kick": return "Kicked by moderator";
                case "warn": return "Warned by moderator";
                case "freeze": return "Frozen by moderator";
                // For ban/mute/softban, null/empty is fine if not set yet
                default: return banReason;
            }
        }
        return banReason;
    }

    /**
     * Sets the ban reason and updates relevant menu items.
     * @param banReason Ban reason string to set.
     */
    public void setBanReason(String banReason) {
        this.banReason = banReason;
        this.reasonSet = (banReason != null && !banReason.trim().isEmpty()); // Update reasonSet flag
        updateSetReasonItem(); // Update the visual item
        updateConfirmButtonStatus(); // Update confirm button state
    }

    /**
     * Gets the target player UUID for this menu.
     * @return Target player UUID.
     */
    public UUID getTargetUUID() {
        return targetUUID;
    }

    /**
     * Checks if time is set, considering if time is required for the punishment type.
     * @return true if time is set OR time is not required for this punishment type, false otherwise.
     */
    public boolean isTimeSet() {
        return !timeRequired || timeSet; // Time is considered set if not required, or if it is actually set
    }

    /**
     * Checks if reason is set (not null or empty).
     * @return true if reason is set, false otherwise.
     */
    public boolean isReasonSet() {
        return reasonSet;
    }

    /**
     * Checks if reason is required for confirming the punishment for the current type.
     * @return true if reason is required for confirmation, false otherwise.
     */
    public boolean isReasonRequiredForConfirmation() {
        return reasonRequiredForConfirmation;
    }

    /**
     * Checks if time is required for the current punishment type.
     * @return true if time is required (e.g., for ban, mute, softban), false otherwise (kick, warn, freeze).
     */
    public boolean isTimeRequired() {
        return timeRequired;
    }

    /**
     * Updates the "Set Time" item in the menu inventory.
     */
    public void updateSetTimeItem() {
        ItemStack setTimeItem = getSetTimeItem(); // Get the updated item stack
        if (setTimeItem != null) {
            setItemInMenu(SET_TIME_KEY, setTimeItem); // Set it in the inventory
        } else if (timeRequired) {
            // If time is required but item is somehow null (config error?), log it
            plugin.getLogger().warning("Failed to update Set Time item for type " + punishmentType + ". Check configuration.");
        }
        // No need to call updateInventory() here, as the methods calling this usually call it afterwards.
    }

    /**
     * Updates the "Set Reason" item in the menu inventory.
     */
    public void updateSetReasonItem() {
        ItemStack setReasonItem = getSetReasonItem(); // Get the updated item stack
        if (setReasonItem != null) {
            setItemInMenu(SET_REASON_KEY, setReasonItem); // Set it in the inventory
        } else {
            plugin.getLogger().warning("Failed to update Set Reason item for type " + punishmentType + ". Check configuration.");
        }
        // No need to call updateInventory() here.
    }

    /**
     * Updates the "Confirm Punish" button's appearance (lore) based on whether requirements are met.
     */
    public void updateConfirmButtonStatus() {
        ItemStack confirmPunishItem = getConfirmPunishItem(); // Get the updated item stack
        if (confirmPunishItem != null) {
            setItemInMenu(CONFIRM_PUNISH_KEY, confirmPunishItem); // Set it in the inventory
        } else {
            plugin.getLogger().warning("Failed to update Confirm Punish item for type " + punishmentType + ". Check configuration.");
        }
        // No need to call updateInventory() here.
    }

    /**
     * Updates the inventory view for all players currently viewing this menu instance.
     * This ensures that changes to items (like updated lore) are reflected immediately.
     */
    private void updateInventory() {
        // Use Bukkit's method to get viewers safely
        List<Player> viewers = inventory.getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .collect(Collectors.toList());

        if (viewers.isEmpty()) {
            return; // No one is viewing, no need to update
        }

        // Update the inventory on the main thread for all viewers
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Re-apply potentially state-dependent items to ensure they are current
            // This is slightly redundant if individual update methods were called, but ensures consistency.
            if (isTimeRequired()) {
                ItemStack timeItem = getSetTimeItem();
                if (timeItem != null) setItemInMenu(SET_TIME_KEY, timeItem);
            }
            ItemStack reasonItem = getSetReasonItem();
            if (reasonItem != null) setItemInMenu(SET_REASON_KEY, reasonItem);
            ItemStack confirmItem = getConfirmPunishItem();
            if (confirmItem != null) setItemInMenu(CONFIRM_PUNISH_KEY, confirmItem);

            // Now update for viewers
            for (Player viewer : viewers) {
                viewer.updateInventory();
            }
        });
    }

    /**
     * Gets the set of MenuItem keys used in this menu, loaded dynamically from the configuration.
     * Used by MenuListener to identify clicked items.
     *
     * @return A Set of item keys (String).
     */
    public Set<String> getMenuItemKeys() {
        return menuItemKeys;
    }
}
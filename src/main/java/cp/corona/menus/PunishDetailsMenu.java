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

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Represents the dynamic punishment details menu, versatile for Ban, Mute, SoftBan, Kick, and Warn.
 * Allows setting specific details for a punishment like time and reason, adaptable by punishment type.
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

    // Constants for item keys in PunishDetailsMenu
    public static final String SET_TIME_KEY = "set_time";
    public static final String SET_REASON_KEY = "set_reason";
    public static final String CONFIRM_PUNISH_KEY = "confirm_punish";
    public static final String BACK_BUTTON_KEY = "back_button";
    public static final String UNSOFTBAN_BUTTON_KEY = "unsoftban_button";

    /**
     * Constructor for PunishDetailsMenu.
     * @param targetUUID UUID of the target player.
     * @param plugin Instance of the main plugin class.
     * @param punishmentType Type of punishment (ban, mute, softban, kick, warn).
     */
    public PunishDetailsMenu(UUID targetUUID, CrownPunishments plugin, String punishmentType) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        this.punishmentType = punishmentType;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getDetailsMenuText("title", target, punishmentType);
        inventory = Bukkit.createInventory(this, 36, title);
        setTimeRequiredBasedOnType(punishmentType); // Set if time is required based on punishment type
        initializeItems(target);
    }

    /**
     * Sets whether time is required for the current punishment type.
     * Kick and Warn do not require time.
     * @param punishmentType Type of punishment.
     */
    private void setTimeRequiredBasedOnType(String punishmentType) {
        if (punishmentType.equalsIgnoreCase("kick") || punishmentType.equalsIgnoreCase("warn")) {
            this.timeRequired = false;
        } else {
            this.timeRequired = true;
        }
    }

    /**
     * Initializes the items in the menu based on the punishment type.
     *
     * @param target Target player for item placeholders.
     */
    private void initializeItems(OfflinePlayer target) {

        if (punishmentType.equalsIgnoreCase("ban") || punishmentType.equalsIgnoreCase("mute") || punishmentType.equalsIgnoreCase("softban")) {
            setItemInMenu(SET_TIME_KEY, getSetTimeItem(target));
        } else if (punishmentType.equalsIgnoreCase("kick") || punishmentType.equalsIgnoreCase("warn")) {
            // Kick and Warn do not have set time item
        }

        setItemInMenu(SET_REASON_KEY, getSetReasonItem(target));
        setItemInMenu(CONFIRM_PUNISH_KEY, getConfirmPunishItem(target));
        setItemInMenu(BACK_BUTTON_KEY, getBackButton(target));

        if (punishmentType.equalsIgnoreCase("softban")) {
            setItemInMenu(UNSOFTBAN_BUTTON_KEY, getUnSoftBanButton(target));
        }
    }

    /**
     * Sets an item in the menu at the slots defined in the configuration.
     * @param itemKey Key of the item in the configuration.
     * @param itemStack ItemStack to set in the menu.
     */
    private void setItemInMenu(String itemKey, ItemStack itemStack){
        MenuItem menuItemConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, itemKey);
        if (menuItemConfig != null && itemStack != null && menuItemConfig.getSlots() != null) {
            for (int slot : menuItemConfig.getSlots()) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    /**
     * Gets the "Set Time" item stack, updating lore with current time if set.
     * @param target Target player for item placeholders.
     * @return ItemStack for set time item.
     */
    private ItemStack getSetTimeItem(OfflinePlayer target) {
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
     * @param target Target player for item placeholders.
     * @return ItemStack for set reason item.
     */
    private ItemStack getSetReasonItem(OfflinePlayer target) {
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
     * @param target Target player for item placeholders.
     * @return ItemStack for confirm punish item.
     */
    private ItemStack getConfirmPunishItem(OfflinePlayer target) {
        MenuItem confirmPunishConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, CONFIRM_PUNISH_KEY);
        if (confirmPunishConfig == null) return null;
        ItemStack item = confirmPunishConfig.toItemStack(target, plugin.getConfigManager());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Ensure display name is set from config here as well, to cover all cases
            meta.setDisplayName(MessageUtils.getColorMessage(plugin.getConfigManager().getDetailsMenuText("items." + punishmentType + "." + CONFIRM_PUNISH_KEY + ".name", target, punishmentType)));
            if ((timeRequired && (!timeSet || !reasonSet)) || (!timeRequired && !reasonSet)) { // Check timeRequired flag
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
     * @param target Target player for item placeholders.
     * @return ItemStack for back button item.
     */
    private ItemStack getBackButton(OfflinePlayer target) {
        MenuItem backButtonConfig = plugin.getConfigManager().getDetailsMenuItemConfig(punishmentType, BACK_BUTTON_KEY);
        if (backButtonConfig == null) return null;
        return backButtonConfig.toItemStack(target, plugin.getConfigManager());
    }

    /**
     * Creates the "Unsoftban" button, specifically for the SoftBanDetailsMenu.
     * @param target Target player for item placeholders.
     * @return ItemStack for unsoftban button item.
     */
    private ItemStack getUnSoftBanButton(OfflinePlayer target) {
        MenuItem unSoftbanButtonConfig = plugin.getConfigManager().getDetailsMenuItemConfig("softban", UNSOFTBAN_BUTTON_KEY);
        if (unSoftbanButtonConfig == null) return null;
        return unSoftbanButtonConfig.toItemStack(target, plugin.getConfigManager());
    }

    /**
     * Gets the formatted status text for time, indicating if it's set.
     * @return Formatted time status text.
     */
    private String getTimeStatusText() {
        return timeRequired ? (timeSet ? MessageUtils.getColorMessage("&a\u2705 Set") : MessageUtils.getColorMessage("&c\u274c Not Set")) : MessageUtils.getColorMessage("&a\u2705 N/A"); // Adjusted for timeRequired
    }

    /**
     * Gets the formatted status text for reason, indicating if it's set.
     * @return Formatted reason status text.
     */
    private String getReasonStatusText() {
        return reasonSet ? MessageUtils.getColorMessage("&a\u2705 Set") : MessageUtils.getColorMessage("&c\u274c Not Set");
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
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        ItemStack setTimeItem = getSetTimeItem(target);
        if (setTimeItem != null) {
            setItemInMenu(SET_TIME_KEY, setTimeItem);
        }
    }

    /**
     * Updates the "Set Reason" item in the menu.
     */
    public void updateSetReasonItem() {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        ItemStack setReasonItem = getSetReasonItem(target);
        if (setReasonItem != null) {
            setItemInMenu(SET_REASON_KEY, setReasonItem);
        }
    }

    /**
     * Updates the "Confirm Punish" button based on whether time and reason are set (and if time is required).
     */
    public void updateConfirmButtonStatus() {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        ItemStack confirmPunishItem = getConfirmPunishItem(target);
        if (confirmPunishItem != null) {
            setItemInMenu(CONFIRM_PUNISH_KEY, confirmPunishItem);
        }
    }
}
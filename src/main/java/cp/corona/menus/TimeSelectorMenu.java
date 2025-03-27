package cp.corona.menus;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Menu for selecting predefined ban times or choosing a custom time.
 * Includes time increment/decrement buttons and options for permanent and custom times.
 */
public class TimeSelectorMenu implements InventoryHolder {
    private final Inventory inventory;
    private final PunishDetailsMenu punishDetailsMenu;
    private final CrownPunishments plugin;
    private int currentTimeSeconds = 0;
    private ItemStack timeDisplayItem;

    // Constants for item keys in TimeSelectorMenu
    private static final String MINUS_5_DAY_KEY = "minus_5_day";
    private static final String MINUS_1_DAY_KEY = "minus_1_day";
    private static final String MINUS_2_HOUR_KEY = "minus_2_hour";
    private static final String MINUS_5_MIN_KEY = "minus_5_min";
    private static final String TIME_DISPLAY_KEY = "time_display";
    private static final String PLUS_15_MIN_KEY = "plus_15_min";
    private static final String PLUS_6_HOUR_KEY = "plus_6_hour";
    private static final String PLUS_1_DAY_KEY = "plus_1_day";
    private static final String PLUS_7_DAY_KEY = "plus_7_day";
    private static final String PERMANENT_TIME_KEY = "permanent";
    private static final String CUSTOM_TIME_KEY = "custom";
    private static final String BACK_BUTTON_KEY = "back_button";


    private final List<String> timeSelectorItemKeys; // Dynamically loaded item keys


    /**
     * Constructor for TimeSelectorMenu.
     *
     * @param punishDetailsMenu The PunishDetailsMenu that opened this time selector.
     * @param plugin            Instance of the main plugin class.
     */
    public TimeSelectorMenu(PunishDetailsMenu punishDetailsMenu, CrownPunishments plugin) {
        this.punishDetailsMenu = punishDetailsMenu;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(punishDetailsMenu.getTargetUUID());
        String title = plugin.getConfigManager().getTimeSelectorMenuTitle(target);
        inventory = Bukkit.createInventory(this, 36, title);
        // Load time selector item keys dynamically from config
        timeSelectorItemKeys = loadTimeSelectorItemKeys();
        initializeItems(target);
    }


    /**
     * Initializes the items in the menu.
     * Dynamically loads items based on timeSelectorItemKeys.
     * @param target OfflinePlayer to display player-specific information (placeholders).
     */
    private void initializeItems(OfflinePlayer target) {
        for (String itemKey : timeSelectorItemKeys) {
            setItemInMenu(itemKey, plugin.getConfigManager().getTimeSelectorMenuItemConfig(itemKey), target);
        }
        timeDisplayItem = getTimeDisplayItem(target);
        setItemInMenu(TIME_DISPLAY_KEY, plugin.getConfigManager().getTimeSelectorMenuItemConfig(TIME_DISPLAY_KEY), timeDisplayItem); // Pass the itemStack directly
    }

    /**
     * Sets a MenuItem in the inventory, processing placeholders and using configuration.
     *
     * @param itemKey        Key of the item in the configuration.
     * @param menuItemConfig MenuItem configuration object.
     * @param target         OfflinePlayer for placeholder processing.
     */
    private void setItemInMenu(String itemKey, MenuItem menuItemConfig, OfflinePlayer target){
        if (menuItemConfig != null) {
            ItemStack itemStack = menuItemConfig.toItemStack(target, plugin.getConfigManager());
            if (itemStack != null && menuItemConfig.getSlots() != null) {
                for (int slot : menuItemConfig.getSlots()) {
                    inventory.setItem(slot, itemStack);
                }
            }
        }
    }

    /**
     * Overloaded method to directly set an ItemStack in the inventory.
     *
     * @param itemKey        Key of the item in the configuration.
     * @param menuItemConfig MenuItem configuration object.
     * @param itemStack      ItemStack to set in the menu.
     */
    private void setItemInMenu(String itemKey, MenuItem menuItemConfig, ItemStack itemStack){ //Overloaded method to directly set ItemStack
        if (menuItemConfig != null && itemStack != null && menuItemConfig.getSlots() != null) {
            for (int slot : menuItemConfig.getSlots()) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    /**
     * Gets the "Time Display" item stack, updating lore with the current selected time.
     *
     * @param target Target player for item placeholders.
     * @return ItemStack for time display item.
     */
    private ItemStack getTimeDisplayItem(OfflinePlayer target) {
        MenuItem timeDisplayConfig = plugin.getConfigManager().getTimeSelectorMenuItemConfig(TIME_DISPLAY_KEY);
        if (timeDisplayConfig == null) return null;

        ItemStack item = timeDisplayConfig.toItemStack(target, plugin.getConfigManager());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String formattedTime = getFormattedTime();
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "[TimeSelectorMenu] Formatted Time for TimeDisplay: " + formattedTime); // Log formatted time
                plugin.getLogger().log(Level.INFO, "[TimeSelectorMenu] Calling getDetailsMenuItemLore with params: punishType=" + punishDetailsMenu.getPunishmentType() + ", itemKey=set_time, target=" + target.getName() + ", time=" + formattedTime); // Log params
            }
            List<String> lore = plugin.getConfigManager().getDetailsMenuItemLore(punishDetailsMenu.getPunishmentType(), "set_time", target, "{time}", formattedTime);
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "[TimeSelectorMenu] Lore returned from getDetailsMenuItemLore: " + lore); // Log returned lore
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }


    /**
     * Updates the "Time Display" item in the menu for the player.
     *
     * @param player Player to update the inventory for.
     */
    public void updateTimeDisplayItem(Player player) {
        timeDisplayItem = getTimeDisplayItem(Bukkit.getOfflinePlayer(punishDetailsMenu.getTargetUUID()));
        setItemInMenu(TIME_DISPLAY_KEY, plugin.getConfigManager().getTimeSelectorMenuItemConfig(TIME_DISPLAY_KEY), timeDisplayItem); // Use overloaded setItemInMenu with itemStack
        player.updateInventory();
    }


    /**
     * Adjusts the current selected time by a given number of seconds, ensuring time does not go below zero.
     *
     * @param seconds Seconds to adjust the current time by (can be negative).
     */
    public void adjustTime(int seconds) {
        this.currentTimeSeconds += seconds;
        if (currentTimeSeconds < 0) {
            currentTimeSeconds = 0;
        }
    }

    /**
     * Gets the current selected time in seconds.
     *
     * @return Current time in seconds.
     */
    public int getCurrentTimeSeconds() {
        return currentTimeSeconds;
    }

    /**
     * Formats the current selected time into a human-readable string.
     *
     * @return Formatted time string.
     */
    public String getFormattedTime() {
        return TimeUtils.formatTime(currentTimeSeconds, plugin.getConfigManager());
    }

    /**
     * Returns the Inventory object for this menu.
     *
     * @return The inventory of this menu.
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Opens the menu for a player.
     *
     * @param player Player to open the menu for.
     */
    public void open(Player player) {
        player.openInventory(inventory);
        plugin.getMenuListener().executeMenuOpenActions(player, this); // Call executeMenuOpenActions from MenuListener
    }


    /**
     * Gets the PunishDetailsMenu associated with this TimeSelectorMenu.
     *
     * @return The PunishDetailsMenu instance.
     */
    public PunishDetailsMenu getPunishDetailsMenu() {
        return punishDetailsMenu;
    }

    /**
     * Gets the list of item keys used in this TimeSelectorMenu.
     *
     * @return List of time selector item keys.
     */
    public List<String> getTimeSelectorItemKeys() {
        return timeSelectorItemKeys;
    }

    /**
     * Loads time selector item keys dynamically from time_selector_menu.yml.
     * Retrieves keys under the 'menu.time_selector_items' configuration section.
     * @return List of item keys.
     */
    private List<String> loadTimeSelectorItemKeys() {
        return new ArrayList<>(plugin.getConfigManager().getTimeSelectorMenuConfig().getConfig().getConfigurationSection("menu.time_selector_items").getKeys(false));
    }
}
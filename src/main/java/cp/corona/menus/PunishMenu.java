// PunishMenu.java
package cp.corona.menus;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Represents the main punishment menu.
 * Allows selecting different punishment categories: Ban, Mute, SoftBan, Kick, Warn, and Freeze. - MODIFIED: Added Freeze
 *
 * **MODIFIED:**
 * - Implemented dynamic loading of menu items from configuration files.
 * - Removed hardcoded item keys and rely on dynamically loaded keys.
 */
public class PunishMenu implements InventoryHolder {
    private final Inventory inventory;
    private final UUID targetUUID;
    private final CrownPunishments plugin;

    /**
     * Stores the item keys in a Set, dynamically loaded from config.
     */
    private final Set<String> menuItemKeys = new HashSet<>();

    /**
     * Constructor for PunishMenu.
     *
     * @param targetUUID UUID of the target player.
     * @param plugin     Instance of the main plugin class.
     */
    public PunishMenu(UUID targetUUID, CrownPunishments plugin) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        // Using getMenuText for title to process placeholders and colors
        String title = plugin.getConfigManager().getMenuText("title", target);
        inventory = Bukkit.createInventory(this, 54, title); // Using a fixed size inventory (54 slots = 6 rows)
        loadMenuItems(); // Load menu items dynamically from config
        initializeItems(target); // Pass target to initializeItems
    }

    /**
     * Loads menu items dynamically from punish_menu.yml.
     * Iterates through the 'items' section in the config and adds each item key to menuItemKeys.
     * Includes debug logging to verify item keys loading.
     */
    private void loadMenuItems() {
        menuItemKeys.clear(); // Clear any existing keys to reload fresh from config
        FileConfiguration config = plugin.getConfigManager().getPunishMenuConfig().getConfig(); // Get FileConfiguration
        if (config == null) {
            plugin.getLogger().warning("[WARNING] PunishMenu - FileConfiguration for punish_menu.yml is null!");
            return; // Exit if config is null
        }
        ConfigurationSection itemsSection = config.getConfigurationSection("menu.items"); // Get menu.items section
        if (itemsSection == null) {
            plugin.getLogger().warning("[WARNING] PunishMenu - Configuration section 'menu.items' not found in punish_menu.yml!");
            return; // Exit if itemsSection is null
        }

        Set<String> configKeys = itemsSection.getKeys(false); // Get keys from menu.items section
        if (configKeys != null) {
            menuItemKeys.addAll(configKeys);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PunishMenu - Loaded menu item keys: " + menuItemKeys); // Debug log for loaded item keys
        } else {
            plugin.getLogger().warning("[WARNING] PunishMenu - No item keys found under 'menu.items' in punish_menu.yml!"); // Warning if no keys are loaded
        }
    }


    /**
     * Initializes the items in the menu.
     *
     * @param target OfflinePlayer to display player-specific information.
     */
    private void initializeItems(OfflinePlayer target) {
        // Iterate through item keys and set items in menu
        for (String itemKey : menuItemKeys) {
            setItemInMenu(itemKey, plugin.getConfigManager().getPunishMenuItemConfig(itemKey), target);
        }
    }

    /**
     * Sets a MenuItem in the inventory, processing placeholders and applying configuration.
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
     * @param player The player to open the menu for.
     */
    public void open(Player player) {
        player.openInventory(inventory);
    }


    /**
     * Gets the target player UUID for this menu.
     *
     * @return The target player UUID.
     */
    public UUID getTargetUUID() {
        return targetUUID;
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
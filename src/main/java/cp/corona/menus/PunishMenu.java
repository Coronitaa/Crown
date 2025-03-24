package cp.corona.menus;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
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
import java.util.stream.Collectors;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Represents the main punishment menu.
 * Allows selecting different punishment categories: Ban, Mute, SoftBan, Kick, Warn, and Freeze. - MODIFIED: Added Freeze
 */
public class PunishMenu implements InventoryHolder {
    private final Inventory inventory;
    private final UUID targetUUID;
    private final CrownPunishments plugin;

    // Item keys constants for menu items in PunishMenu
    private static final String INFO_ITEM_KEY = "info";
    private static final String BAN_ITEM_KEY = "ban";
    private static final String MUTE_ITEM_KEY = "mute";
    private static final String SOFTBAN_ITEM_KEY = "softban";
    private static final String KICK_ITEM_KEY = "kick";
    private static final String WARN_ITEM_KEY = "warn";
    private static final String FREEZE_ITEM_KEY = "freeze"; // New item for freeze - NEW
    private static final String HISTORY_ITEM_KEY = "history";
    private static final String BACKGROUND_FILL_1_KEY = "background_fill_1";
    private static final String BACKGROUND_FILL_2_KEY = "background_fill_2";


    /**
     * Stores the item keys in a Set for easy access and iteration.
     */
    private final Set<String> menuItemKeys = new HashSet<>(Arrays.asList(
            INFO_ITEM_KEY, BAN_ITEM_KEY, MUTE_ITEM_KEY, SOFTBAN_ITEM_KEY,
            KICK_ITEM_KEY, WARN_ITEM_KEY, FREEZE_ITEM_KEY, // Include FREEZE_ITEM_KEY - NEW
            HISTORY_ITEM_KEY,
            BACKGROUND_FILL_1_KEY, BACKGROUND_FILL_2_KEY // Include background fill keys if you have them listed as items
    ));

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
        initializeItems(target); // Pass target to initializeItems
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
        plugin.getMenuListener().executeMenuOpenActions(player, this); // Call executeMenuOpenActions from MenuListener
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
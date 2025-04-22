// HistoryMenu.java
package cp.corona.menus;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.database.SoftBanDatabaseManager;
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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

/**
 * Represents the History Menu for displaying a player's punishment history.
 * The menu displays up to 28 entries per page (using slots 10 to 43) and includes navigation buttons.
 * A next page is only created if the total number of entries exceeds (page × 28).
 *
 * **MODIFIED:**
 * - Implemented dynamic loading of menu items from configuration files.
 * - Removed hardcoded item keys and rely on dynamically loaded keys.
 * - **FIXED**: Previous page button not working.
 */
public class HistoryMenu implements InventoryHolder {

    private final Inventory inventory;
    private final UUID targetUUID;
    private final CrownPunishments plugin;
    private int page = 1;
    // Number of history entries per page: 28 (4 rows of 7 entries)
    private final int entriesPerPage = 28;
    private List<MenuItem> historyEntryItems = new ArrayList<>();
    private final Set<String> menuItemKeys = new HashSet<>(); // Removed hardcoded keys, now dynamically loaded

    // Configuration keys for items (Not needed anymore for dynamic items but kept for reference and existing code)
    private static final String BACK_BUTTON_KEY = "back_button";
    private static final String NEXT_PAGE_BUTTON_KEY = "next_page_button";
    private static final String PREVIOUS_PAGE_BUTTON_KEY = "previous_page_button";
    private static final String HISTORY_ENTRY_ITEM_KEY = "history_entry";
    private static final String WARN_HISTORY_ENTRY_ITEM_KEY = "warn_history_entry";
    private static final String BACKGROUND_FILL_KEY = "background_fill";

    // Valid slots for history entries (slots 10 to 43, arranged in 4 rows of 7)
    private static final List<Integer> validSlots = List.of(
            10, 11, 12, 13, 14, 15, 16,   // Row 1
            19, 20, 21, 22, 23, 24, 25,   // Row 2
            28, 29, 30, 31, 32, 33, 34,   // Row 3
            37, 38, 39, 40, 41, 42, 43    // Row 4
    );

    /**
     * Constructs a HistoryMenu for the specified player.
     *
     * @param targetUUID The UUID of the target player.
     * @param plugin     The main plugin instance.
     */
    public HistoryMenu(UUID targetUUID, CrownPunishments plugin) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getHistoryMenuTitle(target);
        // Create a fixed-size inventory (54 slots = 6 rows)
        inventory = Bukkit.createInventory(this, 54, title);
        loadMenuItems(); // Load menu items dynamically from config
        initializeItems(target);
    }

    /**
     * Initializes the menu items including control buttons and history entries.
     *
     * @param target The target OfflinePlayer.
     */
    private void initializeItems(OfflinePlayer target) {
        // Set back button at slot 53 (bottom right)
        setItemInMenu(BACK_BUTTON_KEY,
                plugin.getConfigManager().getHistoryMenuItemConfig(BACK_BUTTON_KEY),
                target, 53);

        // Load history entries for the current page
        loadHistoryPage(target, page);

        // Initialize navigation buttons based on total count
        updatePageButtons(target);

        // Fill empty slots with background items for aesthetics
        fillEmptySlotsWithBackground(target);

        // Load and set each menu item using getItemStack
        for (String itemKey : menuItemKeys) {
            if (!itemKey.equals(BACK_BUTTON_KEY) && !itemKey.equals(NEXT_PAGE_BUTTON_KEY) && !itemKey.equals(PREVIOUS_PAGE_BUTTON_KEY) && !itemKey.equals(BACKGROUND_FILL_KEY)) { // Avoid processing buttons and background again
                ItemStack itemStack = getItemStack(itemKey, target); // Use getItemStack and pass target - MODIFIED
                if (itemStack != null) {
                    setItemInMenu(itemKey, plugin.getConfigManager().getHistoryMenuItemConfig(itemKey), target);
                }
            }
        }
    }

    /**
     * Loads history entries for the given page into the menu.
     *
     * @param target The target OfflinePlayer.
     * @param page   The page number to load.
     */
    private void loadHistoryPage(OfflinePlayer target, int page) {
        clearHistoryEntries(); // Clear previous entries
        // getPunishmentHistory() uses the page number to calculate the proper offset internally.
        List<SoftBanDatabaseManager.PunishmentEntry> history =
                plugin.getSoftBanDatabaseManager().getPunishmentHistory(targetUUID, page, entriesPerPage);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        historyEntryItems.clear();

        int index = 0;
        for (SoftBanDatabaseManager.PunishmentEntry entry : history) {
            if (index >= validSlots.size()) break;
            int slot = validSlots.get(index);

            String entryTypeConfigName = entry.getType().toLowerCase() + "_history_entry";
            MenuItem historyItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(entryTypeConfigName);
            String duration = getDurationDisplay(entry);

            // Fallback to default history_entry if specific type config is not found
            if (historyItemConfig == null) {
                historyItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(HISTORY_ENTRY_ITEM_KEY);
                if (historyItemConfig == null) continue; // If even default is missing, skip.
            }

            // Create and set up the history entry item
            MenuItem historyEntryItem = new MenuItem();
            historyEntryItem.setMaterial(historyItemConfig.getMaterial()); // Get material from config
            historyEntryItem.setPlayerHead(historyItemConfig.getPlayerHead()); // Get player_head from config if set
            String entryName = plugin.getConfigManager().getHistoryMenuText("items.history_entry.name", target)
                    .replace("{punishment_type}", entry.getType());
            historyEntryItem.setName(MessageUtils.getColorMessage(entryName));
            List<String> lore = plugin.getConfigManager().getHistoryMenuItemLore(HISTORY_ENTRY_ITEM_KEY, target,
                    "{punishment_type}", entry.getType(),
                    "{reason}", entry.getReason(),
                    "{date}", dateFormat.format(entry.getTimestamp()),
                    "{punisher}", entry.getPunisherName(),
                    "{duration}", duration);
            historyEntryItem.setLore(lore);
            historyEntryItem.setSlots(List.of(slot));

            setItemInMenu(HISTORY_ENTRY_ITEM_KEY, historyEntryItem, target, slot);
            historyEntryItems.add(historyEntryItem);
            index++;
        }
        updatePageButtons(target);
    }



    /**
     * Returns a formatted duration string for the given punishment entry.
     *
     * @param entry The punishment entry.
     * @return The formatted duration string.
     */
    private String getDurationDisplay(SoftBanDatabaseManager.PunishmentEntry entry) {
        if (entry.getType().equalsIgnoreCase("warn") || entry.getType().equalsIgnoreCase("kick")) {
            return "Permanent";
        } else if (entry.getType().equalsIgnoreCase("mute") ||
                entry.getType().equalsIgnoreCase("ban") ||
                entry.getType().equalsIgnoreCase("softban")) {
            if (entry.getDurationString().equalsIgnoreCase("permanent")) {
                return "Permanent";
            } else if (!entry.getDurationString().isEmpty()) {
                return entry.getDurationString();
            } else {
                return "N/A";
            }
        }
        return "N/A";
    }

    /**
     * Updates the navigation buttons based on the total count of punishment entries.
     *
     * @param target The target OfflinePlayer.
     */
    private void updatePageButtons(OfflinePlayer target) {
        // Query the total count of entries for this player
        int totalCount = plugin.getSoftBanDatabaseManager().getPunishmentHistoryCount(targetUUID);
        // Only show a next page if total entries exceed the current page capacity
        boolean hasNextPage = totalCount > (page * entriesPerPage);

        // Previous page button: only show if current page is greater than 1
        if (page <= 1) {
            clearPageButton(51);
        } else {
            setItemInMenu(PREVIOUS_PAGE_BUTTON_KEY,
                    plugin.getConfigManager().getHistoryMenuItemConfig(PREVIOUS_PAGE_BUTTON_KEY),
                    target, 51);
        }

        // Next page button: show only if there is a next page
        if (!hasNextPage) {
            clearPageButton(52);
        } else {
            setItemInMenu(NEXT_PAGE_BUTTON_KEY,
                    plugin.getConfigManager().getHistoryMenuItemConfig(NEXT_PAGE_BUTTON_KEY),
                    target, 52);
        }
    }

    /**
     * Clears the specified navigation button slot.
     *
     * @param slot The slot number to clear.
     */
    private void clearPageButton(int slot) {
        inventory.clear(slot);
    }

    /**
     * Fills any empty slots (that are not designated for history entries) with a background item.
     *
     * @param target The target OfflinePlayer.
     */
    private void fillEmptySlotsWithBackground(OfflinePlayer target) {
        MenuItem backgroundItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(BACKGROUND_FILL_KEY);
        if (backgroundItemConfig != null) {
            ItemStack backgroundItemStack = backgroundItemConfig.toItemStack(target, plugin.getConfigManager());
            if (backgroundItemStack != null) {
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    // Fill slot if it's empty AND not a valid history entry slot AND not a button slot
                    if (inventory.getItem(slot) == null && !validSlots.contains(slot) && !isButtonSlot(slot)) { // FIX: Check if slot is not a button slot
                        inventory.setItem(slot, backgroundItemStack.clone());
                    }
                }
            }
        }
    }

    /**
     * Helper method to check if a slot is used by a navigation button.
     * @param slot The slot to check.
     * @return True if the slot is a button slot, false otherwise.
     */
    private boolean isButtonSlot(int slot) { // NEW: Helper method to check button slots
        return slot == 51 || slot == 52 || slot == 53; // Slots for Previous, Next, Back buttons
    }

    /**
     * Gets the ItemStack for a given item key, fetching configuration dynamically.
     * @param itemKey The key of the item configuration.
     * @param target  The target player for context and placeholders.
     * @return The ItemStack or null if no configuration is found.
     */
    private ItemStack getItemStack(String itemKey, OfflinePlayer target) {
        if (plugin.getConfigManager().isDebugEnabled()) { // Debug log - getItemStack called
            plugin.getLogger().log(Level.INFO, "[HistoryMenu] getItemStack called for itemKey: " + itemKey);
        }
        MenuItem menuItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(itemKey);
        if (menuItemConfig != null) {
            return menuItemConfig.toItemStack(target, plugin.getConfigManager()); // Pass target here
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) { // Debug log if no config found
                plugin.getLogger().log(Level.WARNING, "[HistoryMenu] getItemStack - No MenuItem config found for itemKey: " + itemKey);
            }
            return null; // Return null if no config found
        }
    }

    /**
     * Clears only the slots used for history entries.
     */
    private void clearHistoryEntries() {
        for (int slot : validSlots) {
            inventory.clear(slot);
        }
    }

    /**
     * Returns the material icon for the given punishment type.
     *
     * @param punishmentType The punishment type.
     * @return The corresponding material icon as a string.
     */
    private String getPunishmentIcon(String punishmentType) {
        return switch (punishmentType.toLowerCase()) {
            case "ban" -> "BARRIER";
            case "mute" -> "NOTE_BLOCK";
            case "softban" -> "IRON_DOOR";
            case "kick" -> "LEATHER_BOOTS";
            case "warn" -> "PAPER";
            case "unsoftban" -> "LIME_DYE";
            case "unban" -> "GREEN_WOOL";
            case "unmute" -> "EMERALD";
            case "freeze" -> "ICE";
            default -> "BOOK";
        };
    }

    /**
     * Places a MenuItem in the inventory at a specific slot.
     *
     * @param itemKey        The configuration key for the item.
     * @param menuItemConfig The MenuItem configuration.
     * @param target         The target OfflinePlayer for placeholder processing.
     * @param slot           The inventory slot.
     */
    private void setItemInMenu(String itemKey, MenuItem menuItemConfig, OfflinePlayer target, int slot) {
        if (menuItemConfig != null) {
            ItemStack itemStack = menuItemConfig.toItemStack(target, plugin.getConfigManager());
            if (itemStack != null) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    /**
     * Places a MenuItem in the inventory at its configured slots.
     *
     * @param itemKey        The configuration key for the item.
     * @param menuItemConfig The MenuItem configuration.
     * @param target         The target OfflinePlayer for placeholder processing.
     */
    private void setItemInMenu(String itemKey, MenuItem menuItemConfig, OfflinePlayer target) {
        if (menuItemConfig != null) {
            ItemStack itemStack = menuItemConfig.toItemStack(target, plugin.getConfigManager());
            if (itemStack != null && menuItemConfig.getSlots() != null) {
                for (int slot : menuItemConfig.getSlots()) {
                    inventory.setItem(slot, itemStack);
                }
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Opens the History Menu for the specified player.
     *
     * @param player The player who will see the menu.
     */
    public void open(Player player) {
        player.openInventory(inventory);
    }


    /**
     * Creates the punishment counts item stack. - NEW FEATURE
     *
     * @param target The target OfflinePlayer.
     * @return ItemStack for the punishment counts item.
     */
    private ItemStack createPunishmentCountsItem(OfflinePlayer target) {
        MenuItem countsConfig = plugin.getConfigManager().getHistoryMenuItemConfig("punishment_counts_item");
        if (countsConfig == null) return null;

        ItemStack itemStack = countsConfig.toItemStack(target, plugin.getConfigManager());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            HashMap<String, Integer> counts = plugin.getSoftBanDatabaseManager().getPunishmentCounts(targetUUID);
            List<String> lore = plugin.getConfigManager().getHistoryMenuItemLore("punishment_counts_item", target,
                    "{ban_count}", String.valueOf(counts.getOrDefault("ban", 0)),
                    "{mute_count}", String.valueOf(counts.getOrDefault("mute", 0)),
                    "{kick_count}", String.valueOf(counts.getOrDefault("kick", 0)),
                    "{softban_count}", String.valueOf(counts.getOrDefault("softban", 0)),
                    "{warn_count}", String.valueOf(counts.getOrDefault("warn", 0)),
                    "{freeze_count}", String.valueOf(counts.getOrDefault("freeze", 0))
            );
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    /**
     * Adds the punishment counts item to the history menu. - NEW FEATURE
     *
     * @param target The target OfflinePlayer.
     */
    private void addPunishmentCountsItem(OfflinePlayer target) {
        ItemStack countsItem = createPunishmentCountsItem(target);
        if (countsItem != null) {
            inventory.setItem(49, countsItem); // Example slot, adjust as needed
        }
    }

    /**
     * Handles menu open actions for this menu.
     * @param player The player opening the menu.
     */
    private void handleMenuOpenActions(Player player) { // NEW
        plugin.getMenuListener().executeMenuOpenActions(player, this);
    }

    /**
     * Returns the target player's UUID.
     *
     * @return The UUID of the target player.
     */
    public UUID getTargetUUID() {
        return targetUUID;
    }

    /**
     * Navigates to the next page if available and updates the menu.
     *
     * @param player The player interacting with the menu.
     */
    public void nextPage(Player player) {
        int totalCount = plugin.getSoftBanDatabaseManager().getPunishmentHistoryCount(targetUUID);
        // Only proceed if total entries exceed the current page capacity
        if (totalCount <= (page * entriesPerPage)) {
            return;
        }
        page++;
        loadHistoryPage(Bukkit.getOfflinePlayer(targetUUID), page);
        fillEmptySlotsWithBackground(Bukkit.getOfflinePlayer(targetUUID));
        // updateInventory() is marked as internal; it's commonly used in plugins.
        player.updateInventory();
    }

    /**
     * Navigates to the previous page if available and updates the menu.
     *
     * @param player The player interacting with the menu.
     */
    public void previousPage(Player player) {
        if (page > 1) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] HistoryMenu - previousPage() called. Current page: " + page); // Debug log - previousPage called
            page--; // Decrement page number to go to previous page - FIX: Missing decrement operator
            loadHistoryPage(Bukkit.getOfflinePlayer(targetUUID), page);
            fillEmptySlotsWithBackground(Bukkit.getOfflinePlayer(targetUUID));
            player.updateInventory();
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] HistoryMenu - Navigated to previous page. New page: " + page); // Debug log - new page
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] HistoryMenu - previousPage() called, but already on first page."); // Debug log - already on first page
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.history_menu_first_page")));
        }
    }

    /**
     * Returns the list of currently displayed history entry items.
     *
     * @return A list of MenuItem objects.
     */
    public List<MenuItem> getHistoryEntryItems() {
        return historyEntryItems;
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


    /**
     * Loads menu items dynamically from history_menu.yml.
     * Iterates through the 'items' section in the config and adds each item key to menuItemKeys.
     */
    private void loadMenuItems() {
        menuItemKeys.clear(); // Clear any existing keys to reload fresh from config
        Set<String> configKeys = plugin.getConfigManager().getHistoryMenuConfig().getConfig().getConfigurationSection("menu.items").getKeys(false);
        menuItemKeys.addAll(configKeys);
    }
}
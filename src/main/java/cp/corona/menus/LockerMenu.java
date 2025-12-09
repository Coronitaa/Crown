package cp.corona.menus;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LockerMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Crown plugin;
    private final Player viewer;
    private int page;
    private final int entriesPerPage = 45;
    private int totalPages = 1;

    public LockerMenu(Crown plugin, Player viewer, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54, MessageUtils.getColorMessage("&8Confiscated Locker (Page " + page + ")"));
        loadPageAsync();
    }

    public void loadPageAsync() {
        // Set loading state or clear
        inventory.clear();
        placeControlItems();

        plugin.getSoftBanDatabaseManager().countConfiscatedItems().thenAcceptBothAsync(
                plugin.getSoftBanDatabaseManager().getConfiscatedItems(page, entriesPerPage),
                (totalCount, items) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer == null || !viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder() != this) return;

                    this.totalPages = (int) Math.ceil((double) totalCount / (double) entriesPerPage);
                    if (this.totalPages == 0) this.totalPages = 1;

                    // Handle page overflow if items were deleted
                    if (page > totalPages) {
                        new LockerMenu(plugin, viewer, totalPages).open();
                        return;
                    }

                    // Update title if needed (Bukkit API limitation: can't update title easily without reopening, 
                    // generally we reopen for page changes anyway)

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    NamespacedKey idKey = new NamespacedKey(plugin, "locker_item_id");

                    for (int i = 0; i < items.size(); i++) {
                        DatabaseManager.ConfiscatedItemEntry entry = items.get(i);
                        ItemStack item = AuditLogBook.deserialize(entry.getItemData());

                        if (item != null) {
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                                
                                // Separator used for stripping in MenuListener
                                lore.add(MessageUtils.getColorMessage("&8&m----------------")); 
                                
                                lore.add(MessageUtils.getColorMessage("&7Confiscated: &f" + sdf.format(new Date(entry.getConfiscatedAt()))));
                                lore.add(MessageUtils.getColorMessage("&7By: &e" + (entry.getConfiscatedBy().equals("Console") ? "Console" : Bukkit.getOfflinePlayer(java.util.UUID.fromString(entry.getConfiscatedBy())).getName())));
                                
                                // Display Source/Coordinates
                                lore.add(MessageUtils.getColorMessage("&7Source:"));
                                lore.add(MessageUtils.getColorMessage("&b" + entry.getOriginalType()));
                                
                                lore.add(" ");
                                lore.add(MessageUtils.getColorMessage("&eDouble Q &7to &cDelete"));
                                lore.add(MessageUtils.getColorMessage("&eRight-Click &7to &bTake"));
                                lore.add(MessageUtils.getColorMessage("&eShift+R-Click &7to &dCopy"));
                                meta.setLore(lore);

                                meta.getPersistentDataContainer().set(idKey, PersistentDataType.INTEGER, entry.getId());
                                item.setItemMeta(meta);
                            }
                            inventory.setItem(i, item);
                        }
                    }
                    
                    placeControlItems(); // Refresh controls (pagination arrows)
                    viewer.updateInventory();
                })
        );
    }

    private void placeControlItems() {
        // Clear control area
        for(int i=45; i<54; i++) inventory.clear(i);

        MenuItem bg = plugin.getConfigManager().getLockerMenuItemConfig("background_fill");
        if (bg != null) setItem(bg);

        if (page > 1) {
            MenuItem prev = plugin.getConfigManager().getLockerMenuItemConfig("previous_page");
            if (prev != null) setItem(prev);
        }

        if (page < totalPages) {
            MenuItem next = plugin.getConfigManager().getLockerMenuItemConfig("next_page");
            if (next != null) setItem(next);
        }

        MenuItem clearBtn = plugin.getConfigManager().getLockerMenuItemConfig("clear_locker");
        if (clearBtn != null) setItem(clearBtn);
    }

    private void setItem(MenuItem item) {
        if(item.getSlots() != null) {
            ItemStack stack = item.toItemStack(null, plugin.getConfigManager());
            for(int slot : item.getSlots()) {
                if(slot >= 45 && slot < 54) inventory.setItem(slot, stack);
            }
        }
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public void nextPage() {
        if (page < totalPages) {
            new LockerMenu(plugin, viewer, page + 1).open();
        }
    }

    public void prevPage() {
        if (page > 1) {
            new LockerMenu(plugin, viewer, page - 1).open();
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

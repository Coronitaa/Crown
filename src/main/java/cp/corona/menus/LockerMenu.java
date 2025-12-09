package cp.corona.menus;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;

public class LockerMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Crown plugin;
    private final Player viewer;
    private final UUID ownerUUID; // Whose locker is this?
    private final boolean isEditable; // Can the viewer modify it?
    private int page;
    private final int entriesPerPage = 45;
    private int totalPages = 1;

    public LockerMenu(Crown plugin, Player viewer, UUID ownerUUID, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.ownerUUID = ownerUUID;
        this.page = page;

        // Calculate permissions
        boolean isOwner = viewer.getUniqueId().equals(ownerUUID);
        boolean hasAdminPerm = viewer.hasPermission("crown.mod.locker.admin");
        
        // Editable if it's your locker OR you have admin perm. 
        // Note: MenuListener also checks if viewer is in ModMode for certain restrictions.
        this.isEditable = isOwner || hasAdminPerm;

        String ownerName = Bukkit.getOfflinePlayer(ownerUUID).getName();
        String title = plugin.getConfigManager().getMessage("messages.locker_title")
                .replace("{owner}", ownerName != null ? ownerName : "Unknown")
                .replace("{page}", String.valueOf(page));

        this.inventory = Bukkit.createInventory(this, 54, MessageUtils.getColorMessage(title));
        loadPageAsync();
    }

    public void loadPageAsync() {
        inventory.clear();
        placeControlItems();

        plugin.getSoftBanDatabaseManager().countConfiscatedItems(ownerUUID).thenAcceptBothAsync(
                plugin.getSoftBanDatabaseManager().getConfiscatedItems(ownerUUID, page, entriesPerPage),
                (totalCount, items) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer == null || !viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder() != this) return;

                    this.totalPages = (int) Math.ceil((double) totalCount / (double) entriesPerPage);
                    if (this.totalPages == 0) this.totalPages = 1;

                    if (page > totalPages) {
                        new LockerMenu(plugin, viewer, ownerUUID, totalPages).open();
                        return;
                    }

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
                                
                                if (isEditable) {
                                    lore.add(" ");
                                    lore.add(MessageUtils.getColorMessage("&eDouble Q &7to &cDelete"));
                                    lore.add(MessageUtils.getColorMessage("&eRight-Click &7to &bTake"));
                                }
                                lore.add(MessageUtils.getColorMessage("&eShift+R-Click &7to &dCopy")); // Copy always allowed
                                
                                meta.setLore(lore);
                                meta.getPersistentDataContainer().set(idKey, PersistentDataType.INTEGER, entry.getId());
                                item.setItemMeta(meta);
                            }
                            inventory.setItem(i, item);
                        }
                    }
                    
                    placeControlItems();
                    viewer.updateInventory();
                })
        );
    }

    private void placeControlItems() {
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

        // Only show Clear button if editable
        if (isEditable) {
            MenuItem clearBtn = plugin.getConfigManager().getLockerMenuItemConfig("clear_locker");
            if (clearBtn != null) setItem(clearBtn);
        }
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
            new LockerMenu(plugin, viewer, ownerUUID, page + 1).open();
        }
    }

    public void prevPage() {
        if (page > 1) {
            new LockerMenu(plugin, viewer, ownerUUID, page - 1).open();
        }
    }

    public boolean isEditable() {
        return isEditable;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

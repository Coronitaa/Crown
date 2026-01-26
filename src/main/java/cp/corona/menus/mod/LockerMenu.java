package cp.corona.menus.mod;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.profile.AuditLogBook;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LockerMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Crown plugin;
    private final Player viewer;
    private final UUID ownerUUID; // Null represents Global Locker
    public final boolean isEditable;
    private final int page;
    private final int entriesPerPage = 45;
    private int totalPages = 1;

    @SuppressWarnings("deprecation")
    public LockerMenu(Crown plugin, Player viewer, UUID ownerUUID, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.ownerUUID = ownerUUID;
        this.page = page;

        // Global Locker is always read-only/admin only logic handled in command/listener
        // If ownerUUID is null, we treat it as Global.
        boolean isGlobal = (ownerUUID == null);
        
        boolean isOwner = !isGlobal && viewer.getUniqueId().equals(ownerUUID);
        boolean hasAdminPerm = viewer.hasPermission("crown.locker.admin");
        
        this.isEditable = isOwner || hasAdminPerm;

        String title;
        if (isGlobal) {
            title = "&8Global Locker (Pg " + page + ")";
        } else {
            String ownerName = Bukkit.getOfflinePlayer(ownerUUID).getName();
            title = plugin.getConfigManager().getLockerMenuConfig().getConfig().getString("menu.title", "&8Locker: &1{owner} &8(Pg {page})")
                    .replace("{owner}", ownerName != null ? ownerName : "Unknown")
                    .replace("{page}", String.valueOf(page));
        }

        this.inventory = Bukkit.createInventory(this, 54, MessageUtils.getColorMessage(title));
        loadPageAsync();
    }

    @SuppressWarnings("deprecation")
    public void loadPageAsync() {
        inventory.clear();
        placeControlItems();

        CompletableFuture<Integer> countFuture;
        CompletableFuture<List<DatabaseManager.ConfiscatedItemEntry>> itemsFuture;

        if (ownerUUID == null) {
            countFuture = plugin.getSoftBanDatabaseManager().countAllConfiscatedItems();
            itemsFuture = plugin.getSoftBanDatabaseManager().getAllConfiscatedItems(page, entriesPerPage);
        } else {
            countFuture = plugin.getSoftBanDatabaseManager().countConfiscatedItems(ownerUUID);
            itemsFuture = plugin.getSoftBanDatabaseManager().getConfiscatedItems(ownerUUID, page, entriesPerPage);
        }

        // Procesamiento ASÍNCRONO de los items (Deserialización pesada aquí)
        countFuture.thenCombineAsync(itemsFuture, (totalCount, entries) -> {
            List<PreLoadedItem> loadedItems = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            NamespacedKey idKey = new NamespacedKey(plugin, "locker_item_id");

            for (DatabaseManager.ConfiscatedItemEntry entry : entries) {
                ItemStack item = AuditLogBook.deserialize(entry.getItemData());
                if (item != null) {
                    // Preparar metadata en async hasta donde la API de Bukkit lo permita de forma segura
                    // (Nota: Crear ItemMeta suele ser seguro async, aplicarlo al inventario NO)
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                        lore.add(MessageUtils.getColorMessage("&8&m----------------"));
                        lore.add(MessageUtils.getColorMessage("&7Confiscated: &f" + sdf.format(new Date(entry.getConfiscatedAt()))));
                        
                        String confiscatorName = entry.getConfiscatedBy().equals("Console") ? "Console" : Bukkit.getOfflinePlayer(UUID.fromString(entry.getConfiscatedBy())).getName();
                        lore.add(MessageUtils.getColorMessage("&7By: &e" + (confiscatorName != null ? confiscatorName : "Unknown")));
                        lore.add(MessageUtils.getColorMessage("&7Source:"));
                        lore.add(MessageUtils.getColorMessage("&b" + entry.getOriginalType()));
                        
                        // Lógica de permisos visuales pre-calculada
                        boolean inModMode = plugin.getModeratorModeManager().isInModeratorMode(viewer.getUniqueId());
                        if (isEditable) {
                            lore.add(" ");
                            lore.add(MessageUtils.getColorMessage("&eDouble Q &7to &cDelete"));
                            if (!inModMode) lore.add(MessageUtils.getColorMessage("&eRight-Click &7to &bTake"));
                        }
                        if (!inModMode) lore.add(MessageUtils.getColorMessage("&eShift+R-Click &7to &dCopy"));
                        
                        meta.setLore(lore);
                        // No podemos setear PersistentDataContainer async seguramente en todas las versiones,
                        // así que guardamos el item, el meta y el ID para ensamblar en sync.
                        item.setItemMeta(meta);
                        loadedItems.add(new PreLoadedItem(item, entry.getId()));
                    }
                }
            }
            return new Pair<>(totalCount, loadedItems);
        }).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            // Tarea SÍNCRONA (Main Thread) - Solo colocación rápida
            if (viewer == null || !viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder() != this) return;

            int totalCount = result.getKey();
            List<PreLoadedItem> loadedItems = result.getValue();

            this.totalPages = (int) Math.ceil((double) totalCount / (double) entriesPerPage);
            if (this.totalPages == 0) this.totalPages = 1;

            if (page > totalPages) {
                new LockerMenu(plugin, viewer, ownerUUID, totalPages).open();
                return;
            }

            NamespacedKey idKey = new NamespacedKey(plugin, "locker_item_id");
            for (int i = 0; i < loadedItems.size(); i++) {
                PreLoadedItem pItem = loadedItems.get(i);
                ItemStack stack = pItem.stack;
                ItemMeta meta = stack.getItemMeta();
                // Aplicar PersistentDataContainer en hilo principal por seguridad
                meta.getPersistentDataContainer().set(idKey, PersistentDataType.INTEGER, pItem.dbId);
                stack.setItemMeta(meta);
                inventory.setItem(i, stack);
            }

            placeControlItems();
            viewer.updateInventory();
        }));
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

        // Only show Clear button if editable AND not global (prevent clearing global accidentally/massively)
        if (isEditable && ownerUUID != null) {
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

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // Clases auxiliares para transporte de datos
    private record PreLoadedItem(ItemStack stack, int dbId) {}
    private record Pair<K, V>(K key, V value) {
        public K getKey() { return key; }
        public V getValue() { return value; }
    }
}

// C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\menus\FullInventoryMenu.java
package cp.corona.menus.profile;

import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class FullInventoryMenu implements InventoryHolder {

    private final Inventory inventory;
    private final UUID targetUUID;
    private final Crown plugin;

    public FullInventoryMenu(UUID targetUUID, Crown plugin) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getFullInventoryMenuTitle(target);
        this.inventory = Bukkit.createInventory(this, 54, title);
        initializeItems();
    }

    private void initializeItems() {
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer == null) return;

        inventory.clear();

        // First, display main inventory content (slots 0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack item = targetPlayer.getInventory().getItem(i);
            if (item != null) {
                inventory.setItem(i, item);
            }
        }

        // Then, load static items like back button and background
        plugin.getConfigManager().getFullInventoryMenuItemKeys().forEach(itemKey -> {
            MenuItem menuItem = plugin.getConfigManager().getFullInventoryMenuItemConfig(itemKey);
            if (menuItem != null) {
                setItemInMenu(menuItem, menuItem.toItemStack(targetPlayer, plugin.getConfigManager()));
            }
        });
    }

    private void setItemInMenu(MenuItem menuItem, ItemStack itemStack) {
        if (menuItem != null && itemStack != null && menuItem.getSlots() != null) {
            for (int slot : menuItem.getSlots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, itemStack);
                }
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }
}
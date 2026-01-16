// C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\menus\EnderChestMenu.java
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

public class EnderChestMenu implements InventoryHolder {

    private final Inventory inventory;
    private final UUID targetUUID;
    private final Crown plugin;

    public EnderChestMenu(UUID targetUUID, Crown plugin) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getEnderChestMenuTitle(target);
        this.inventory = Bukkit.createInventory(this, 45, title); // 5 rows to fit content and buttons
        initializeItems();
    }

    private void initializeItems() {
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer == null) return;

        inventory.clear();

        // Display Ender Chest content (slots 0-26)
        for (int i = 0; i < 27; i++) {
            ItemStack item = targetPlayer.getEnderChest().getItem(i);
            if (item != null) {
                inventory.setItem(i, item);
            }
        }

        // Load static items like back button and background
        plugin.getConfigManager().getEnderChestMenuItemKeys().forEach(itemKey -> {
            MenuItem menuItem = plugin.getConfigManager().getEnderChestMenuItemConfig(itemKey);
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
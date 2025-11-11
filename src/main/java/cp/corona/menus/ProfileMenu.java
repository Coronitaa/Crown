// C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\menus\ProfileMenu.java
package cp.corona.menus;

import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ProfileMenu implements InventoryHolder {

    private final Inventory inventory;
    private final UUID targetUUID;
    private final Crown plugin;

    public ProfileMenu(UUID targetUUID, Crown plugin) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getProfileMenuTitle(target);
        this.inventory = Bukkit.createInventory(this, 54, title);
        initializeItems();
    }

    private void initializeItems() {
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer == null) return;

        inventory.clear();

        // First, display player's actual equipment
        PlayerInventory playerInv = targetPlayer.getInventory();
        inventory.setItem(10, playerInv.getHelmet());
        inventory.setItem(19, playerInv.getChestplate());
        inventory.setItem(28, playerInv.getLeggings());
        inventory.setItem(37, playerInv.getBoots());
        inventory.setItem(23, playerInv.getItemInMainHand());
        inventory.setItem(24, playerInv.getItemInOffHand());

        // Then, load static items from config, which will fill around the equipment
        plugin.getConfigManager().getProfileMenuItemKeys().forEach(itemKey -> {
            MenuItem menuItem = plugin.getConfigManager().getProfileMenuItemConfig(itemKey);
            if (menuItem != null) {
                ItemStack itemStack;
                // Special handling for player_info to inject live stats
                if (itemKey.equals("player_info")) {
                    itemStack = menuItem.toItemStack(targetPlayer, plugin.getConfigManager());
                    if (itemStack != null && itemStack.hasItemMeta()) {
                        itemStack.setItemMeta(plugin.getConfigManager().getProfileMenuItemBuilder("player_info", targetPlayer)
                                .withPlaceholder("{xp_level}", String.valueOf(targetPlayer.getLevel()))
                                .withPlaceholder("{play_time}", TimeUtils.formatTime(targetPlayer.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20, plugin.getConfigManager()))
                                .withPlaceholder("{health}", String.format("%.1f/%.1f", targetPlayer.getHealth(), targetPlayer.getMaxHealth()))
                                .withPlaceholder("{food_level}", String.valueOf(targetPlayer.getFoodLevel()))
                                .withPlaceholder("{player_kills}", String.valueOf(targetPlayer.getStatistic(Statistic.PLAYER_KILLS)))
                                .withPlaceholder("{deaths}", String.valueOf(targetPlayer.getStatistic(Statistic.DEATHS)))
                                .build().getItemMeta());
                    }
                } else {
                    itemStack = menuItem.toItemStack(targetPlayer, plugin.getConfigManager());
                }
                setItemInMenu(menuItem, itemStack);
            }
        });
    }

    private void setItemInMenu(MenuItem menuItem, ItemStack itemStack) {
        if (menuItem != null && itemStack != null && menuItem.getSlots() != null) {
            for (int slot : menuItem.getSlots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    // Only place the item if the slot is not an equipment slot that we already filled
                    if (!isEquipmentSlot(slot)) {
                        inventory.setItem(slot, itemStack);
                    }
                }
            }
        }
    }

    private boolean isEquipmentSlot(int slot) {
        return slot == 10 || slot == 19 || slot == 28 || slot == 37 || slot == 23 || slot == 24;
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
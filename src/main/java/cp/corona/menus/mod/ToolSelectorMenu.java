package cp.corona.menus.mod;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ToolSelectorMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Crown plugin;
    private final Player viewer;
    private final NamespacedKey toolIdKey;

    public ToolSelectorMenu(Crown plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.toolIdKey = new NamespacedKey(plugin, "tool-id");

        ConfigurationSection menuConfig = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("tool-selector-menu");
        String title = MessageUtils.getColorMessage(menuConfig.getString("title", "&8Mod Tools"));
        int size = menuConfig.getInt("size", 27);

        this.inventory = Bukkit.createInventory(this, size, title);
        initializeItems();
    }

    private void initializeItems() {
        ConfigurationSection itemsSection = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("tool-selector-menu.items");
        if (itemsSection == null) return;

        List<String> favoriteTools = plugin.getModeratorModeManager().getFavoriteTools(viewer.getUniqueId());

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
            if (itemConfig == null) continue;

            String toolId = itemConfig.getString("tool-id");
            if (toolId == null) continue;

            ItemStack item = plugin.getModeratorModeManager().getModeratorTools().get(toolId);
            if (item == null) continue;
            item = item.clone(); // Work with a copy

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            boolean isFavorite = favoriteTools.contains(toolId);

            // Update name and lore for favorite status
            if (isFavorite) {
                meta.setDisplayName("§e⭐ " + meta.getDisplayName());
                meta.addEnchant(Enchantment.FORTUNE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(""); // Spacer

            // Add hotbar status
            boolean isInHotbar = false;
            for (int i = 0; i < 9; i++) {
                ItemStack hotbarItem = viewer.getInventory().getItem(i);
                if (hotbarItem != null && hotbarItem.hasItemMeta()) {
                    String hotbarToolId = hotbarItem.getItemMeta().getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
                    if (toolId.equals(hotbarToolId)) {
                        isInHotbar = true;
                        break;
                    }
                }
            }

            if (isInHotbar) {
                lore.add(MessageUtils.getColorMessage("&a✔ In Hotbar"));
            } else {
                lore.add(MessageUtils.getColorMessage("&7✖ Not in Hotbar"));
            }

            // Add favorite instructions
            if (isFavorite) {
                lore.add(MessageUtils.getColorMessage("&cRight-click to remove from favorites."));
            } else {
                lore.add(MessageUtils.getColorMessage("&aRight-click to add to favorites."));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(itemConfig.getInt("slot"), item);
        }
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

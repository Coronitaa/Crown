package cp.corona.menus;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.stream.Collectors;

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

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
            if (itemConfig == null) continue;

            String toolId = itemConfig.getString("tool-id");
            ItemStack item;

            // Inherit from moderator-inventory if available
            if (plugin.getModeratorModeManager().getModeratorTools().containsKey(toolId)) {
                item = plugin.getModeratorModeManager().getModeratorTools().get(toolId).clone();
            } else {
                Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
                item = new ItemStack(material);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            // Override name/lore if specified in tool-selector-menu
            if(itemConfig.isSet("name")) meta.setDisplayName(MessageUtils.getColorMessage(itemConfig.getString("name")));
            if(itemConfig.isSet("lore")) meta.setLore(itemConfig.getStringList("lore").stream().map(MessageUtils::getColorMessage).collect(Collectors.toList()));

            meta.getPersistentDataContainer().set(toolIdKey, PersistentDataType.STRING, toolId);

            // Check if selected
            boolean isSelected = false;
            for (int i = 0; i <= 8; i++) {
                ItemStack hotbarItem = viewer.getInventory().getItem(i);
                if (hotbarItem != null && hotbarItem.hasItemMeta()) {
                    String hotbarToolId = hotbarItem.getItemMeta().getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
                    if (toolId.equals(hotbarToolId)) {
                        isSelected = true;
                        break;
                    }
                }
            }

            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            if (isSelected) {
                meta.addEnchant(Enchantment.FORTUNE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                lore.add(MessageUtils.getColorMessage("&a&lSELECTED"));
            } else {
                lore.add(MessageUtils.getColorMessage("&eClick to select"));
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
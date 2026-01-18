package cp.corona.menus.mod;

import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import cp.corona.moderator.ModeratorModeManager;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ModSettingsMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Crown plugin;
    private final Player viewer;

    public ModSettingsMenu(Crown plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;

        ConfigurationSection menuConfig = plugin.getConfigManager().getSettingsMenuConfig().getConfig().getConfigurationSection("menu");
        String title = MessageUtils.getColorMessage(menuConfig.getString("title", "&8Personal Settings"));
        int size = menuConfig.getInt("size", 45);

        this.inventory = Bukkit.createInventory(this, size, title);
        initializeItems();
    }

    private void initializeItems() {
        ConfigurationSection config = plugin.getConfigManager().getSettingsMenuConfig().getConfig().getConfigurationSection("menu.items");
        if (config == null) return;

        ModeratorModeManager manager = plugin.getModeratorModeManager();

        // Fetch current states
        boolean interactions = manager.isInteractionsAllowed(viewer.getUniqueId());
        boolean containerSpy = manager.isContainerSpyEnabled(viewer.getUniqueId());
        boolean flyEnabled = manager.isFlyEnabled(viewer.getUniqueId());
        boolean modOnJoin = manager.isModOnJoinEnabled(viewer.getUniqueId());
        boolean silent = manager.isSilent(viewer.getUniqueId());
        float walkSpeed = manager.getWalkSpeed(viewer.getUniqueId());
        float flySpeed = manager.getFlySpeed(viewer.getUniqueId());
        float jumpMultiplier = manager.getJumpMultiplier(viewer.getUniqueId());
        boolean nightVision = manager.isNightVisionEnabled(viewer.getUniqueId());
        boolean glowingEnabled = manager.isGlowingEnabled(viewer.getUniqueId());

        for (String key : config.getKeys(false)) {
            MenuItem menuItem = plugin.getConfigManager().getSettingsMenuItemConfig(key);
            if (menuItem == null) continue;

            ItemStack item;
            String status = "";
            String speed = "";
            String multiplier = "";

            // Determine state for toggle items
            boolean state = false;
            boolean isToggleItem = false;
            
            switch (key) {
                case "interactions":
                    state = interactions;
                    isToggleItem = true;
                    break;
                case "container-spy":
                    state = containerSpy;
                    isToggleItem = true;
                    break;
                case "fly":
                    state = flyEnabled;
                    isToggleItem = true;
                    break;
                case "mod-on-join":
                    state = modOnJoin;
                    isToggleItem = true;
                    break;
                case "silent":
                    state = silent;
                    isToggleItem = true;
                    break;
                case "glowing":
                    state = glowingEnabled;
                    isToggleItem = true;
                    break;
                case "night-vision":
                    state = nightVision;
                    isToggleItem = true;
                    break;
                case "walk-speed":
                    speed = String.format("%.2f", walkSpeed);
                    break;
                case "fly-speed":
                    speed = String.format("%.2f", flySpeed);
                    break;
                case "jump-boost":
                    multiplier = String.format("%.2f", jumpMultiplier);
                    break;
            }

            if (isToggleItem) {
                status = state ? "&a&lENABLED" : "&c&lDISABLED";
                // Use material_on/off if available
                if (menuItem.getMaterialOn() != null && menuItem.getMaterialOff() != null) {
                    Material mat = Material.matchMaterial(state ? menuItem.getMaterialOn() : menuItem.getMaterialOff());
                    if (mat != null) menuItem.setMaterial(mat.name());
                }
                
                // Use player_head_on/off if available
                if (menuItem.getPlayerHeadOn() != null && menuItem.getPlayerHeadOff() != null) {
                    menuItem.setPlayerHead(state ? menuItem.getPlayerHeadOn() : menuItem.getPlayerHeadOff());
                }
                
                // Use custom_model_data_on/off if available
                if (menuItem.getCustomModelDataOn() != null && menuItem.getCustomModelDataOff() != null) {
                    menuItem.setCustomModelData(state ? menuItem.getCustomModelDataOn() : menuItem.getCustomModelDataOff());
                }
            }

            // Build item with placeholders
            item = menuItem.toItemStack(viewer, plugin.getConfigManager());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasLore()) {
                    List<String> lore = meta.getLore();
                    List<String> newLore = new ArrayList<>();
                    for (String line : lore) {
                        line = line.replace("{status}", MessageUtils.getColorMessage(status))
                                   .replace("{speed}", speed)
                                   .replace("{multiplier}", multiplier);
                        newLore.add(line);
                    }
                    meta.setLore(newLore);
                }
                item.setItemMeta(meta);
            }

            for (int slot : menuItem.getSlots()) {
                inventory.setItem(slot, item);
            }
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

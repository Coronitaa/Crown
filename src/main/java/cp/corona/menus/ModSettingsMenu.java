package cp.corona.menus;

import cp.corona.crown.Crown;
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

        ConfigurationSection menuConfig = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("mod-settings-menu");
        String title = MessageUtils.getColorMessage(menuConfig.getString("title", "&8Personal Settings"));
        int size = menuConfig.getInt("size", 45);

        this.inventory = Bukkit.createInventory(this, size, title);
        initializeItems();
    }

    private void initializeItems() {
        ConfigurationSection config = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("mod-settings-menu.items");
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

        // Create items
        createToggleItem(config.getConfigurationSection("interactions"), interactions);
        createToggleItem(config.getConfigurationSection("container-spy"), containerSpy);
        createToggleItem(config.getConfigurationSection("fly"), flyEnabled);
        createToggleItem(config.getConfigurationSection("mod-on-join"), modOnJoin);
        createToggleItem(config.getConfigurationSection("silent"), silent);
        createSpeedItem(config.getConfigurationSection("walk-speed"), walkSpeed, "speed");
        createSpeedItem(config.getConfigurationSection("fly-speed"), flySpeed, "speed");
        createSpeedItem(config.getConfigurationSection("jump-boost"), jumpMultiplier, "multiplier");
        createToggleItem(config.getConfigurationSection("night-vision"), nightVision);
    }

    private void createToggleItem(ConfigurationSection itemConfig, boolean state) {
        if (itemConfig == null) return;

        Material matOn = Material.matchMaterial(itemConfig.getString("material_on", "LIME_DYE"));
        Material matOff = Material.matchMaterial(itemConfig.getString("material_off", "GRAY_DYE"));
        Material finalMat = state ? matOn : matOff;

        ItemStack item = new ItemStack(finalMat != null ? finalMat : Material.STONE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = itemConfig.getString("name", "Toggle");
            meta.setDisplayName(MessageUtils.getColorMessage(name));

            List<String> lore = itemConfig.getStringList("lore");
            List<String> processedLore = new ArrayList<>();
            String status = state ? "&a&lENABLED" : "&c&lDISABLED";

            for (String line : lore) {
                processedLore.add(MessageUtils.getColorMessage(line.replace("{status}", status)));
            }
            meta.setLore(processedLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        inventory.setItem(itemConfig.getInt("slot"), item);
    }

    private void createSpeedItem(ConfigurationSection itemConfig, float value, String placeholder) {
        if (itemConfig == null) return;

        Material mat = Material.matchMaterial(itemConfig.getString("material", "FEATHER"));
        ItemStack item = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = itemConfig.getString("name", "Speed");
            meta.setDisplayName(MessageUtils.getColorMessage(name));

            List<String> lore = itemConfig.getStringList("lore");
            List<String> processedLore = new ArrayList<>();
            String valueStr = String.format("%.2f", value);

            for (String line : lore) {
                processedLore.add(MessageUtils.getColorMessage(line.replace("{" + placeholder + "}", valueStr)));
            }
            meta.setLore(processedLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        inventory.setItem(itemConfig.getInt("slot"), item);
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

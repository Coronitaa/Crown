// MenuItem.java
package cp.corona.menus.items;

import cp.corona.config.MainConfigManager;
import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.actions.ClickAction;
import cp.corona.utils.MessageUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Represents a menu item with configurable properties loaded from configuration files.
 * Includes material, display name, lore, player head, custom model data, click action, and sound.
 *
 * Refactored to support multiple actions for left and right clicks.
 * Replaced single click actions with lists of ClickActionData for left and right clicks.
 */
public class MenuItem {

    private String material;
    private String name;
    private List<String> lore;
    private String playerHeadValue;
    private String playerHeadName;
    private Integer customModelData;
    private int quantity = 1;
    private List<Integer> slots;
    private String clickSound;
    private Float clickVolume = 1.0f;
    private Float clickPitch = 1.0f;

    // Refactored actions to support multiple actions per click type
    private List<ClickActionData> leftClickActions = Collections.emptyList();
    private List<ClickActionData> rightClickActions = Collections.emptyList();


    public MenuItem() {
    }

    // Material
    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    // Name
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Lore
    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore;
    }

    // Player Head Value
    public String getPlayerHeadValue() {
        return playerHeadValue;
    }

    public void setPlayerHeadValue(String playerHeadValue) {
        this.playerHeadValue = playerHeadValue;
    }

    // Player Head Name
    public String getPlayerHeadName() {
        return playerHeadName;
    }

    public void setPlayerHeadName(String playerHeadName) {
        this.playerHeadName = playerHeadName;
    }

    // Custom Model Data
    public Integer getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(Integer customModelData) {
        this.customModelData = customModelData;
    }

    // Quantity
    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    // Slots
    public List<Integer> getSlots() {
        return slots;
    }

    public void setSlots(List<Integer> slots) {
        this.slots = slots;
    }

    // Click Sound
    public String getClickSound() {
        return clickSound;
    }

    public void setClickSound(String clickSound) {
        this.clickSound = clickSound;
    }

    // Click Volume
    public Float getClickVolume() {
        return clickVolume;
    }

    public void setClickVolume(Float clickVolume) {
        this.clickVolume = clickVolume;
    }

    // Click Pitch
    public Float getClickPitch() {
        return clickPitch;
    }

    public void setClickPitch(Float clickPitch) {
        this.clickPitch = clickPitch;
    }

    // Left Click Actions - List of actions to be performed on left click
    public List<ClickActionData> getLeftClickActions() {
        return leftClickActions;
    }

    public void setLeftClickActions(List<ClickActionData> leftClickActions) {
        this.leftClickActions = leftClickActions;
    }

    // Right Click Actions - List of actions to be performed on right click
    public List<ClickActionData> getRightClickActions() {
        return rightClickActions;
    }

    public void setRightClickActions(List<ClickActionData> rightClickActions) {
        this.rightClickActions = rightClickActions;
    }


    /**
     * Converts the MenuItem configuration to an ItemStack for display in menus.
     * Processes placeholders in name and lore, and sets item meta properties.
     *
     * @param target        The target player for placeholder replacement (can be null).
     * @param configManager MainConfigManager instance for placeholder processing.
     * @return The ItemStack representing this MenuItem.
     */
    public ItemStack toItemStack(OfflinePlayer target, MainConfigManager configManager) {
        Material itemMaterial = Material.matchMaterial(this.material.toUpperCase());
        if (itemMaterial == null) {
            itemMaterial = Material.STONE; // Default material if invalid
        }
        ItemStack itemStack = new ItemStack(itemMaterial, quantity);
        ItemMeta meta = itemStack.getItemMeta();

        if (meta != null) {
            if (this.name != null) {
                meta.setDisplayName(MessageUtils.getColorMessage(processText(this.name, target, configManager)));
            }
            if (this.lore != null) {
                List<String> processedLore = this.lore.stream()
                        .map(line -> processText(line, target, configManager))
                        .map(MessageUtils::getColorMessage)
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }
            if (this.customModelData != null) {
                meta.setCustomModelData(this.customModelData);
            }
            if (itemMaterial == Material.PLAYER_HEAD) {
                if (this.playerHeadValue != null && !this.playerHeadValue.isEmpty()) {
                    SkullMeta skullMeta = (SkullMeta) meta;
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                    PlayerTextures textures = profile.getTextures();
                    try {
                        textures.setSkin(new URL(this.playerHeadValue));
                    } catch (MalformedURLException e) {
                        Bukkit.getLogger().warning("Invalid player_head_value URL: " + this.playerHeadValue);
                    }
                    profile.setTextures(textures);
                    skullMeta.setOwnerProfile(profile);
                    meta = skullMeta;
                } else if (this.playerHeadName != null && !this.playerHeadName.isEmpty() && target != null) {
                    SkullMeta skullMeta = (SkullMeta) meta;
                    OfflinePlayer headOwner = Bukkit.getOfflinePlayer(processText(this.playerHeadName, target, configManager));
                    skullMeta.setOwningPlayer(headOwner);
                    meta = skullMeta;
                }
            }
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    /**
     * Processes text for placeholders, using MainConfigManager and PlaceholderAPI if available.
     *
     * @param text          The text to process.
     * @param target        The target player for placeholders.
     * @param configManager MainConfigManager instance for placeholder processing.
     * @return The processed text.
     */
    private String processText(String text, OfflinePlayer target, MainConfigManager configManager) {
        if (text == null) return null;
        String processedText = text;
        if (target != null) {
            processedText = configManager.processPlaceholders(processedText, target);
        }
        if (plugin.isPlaceholderAPIEnabled() && target != null && target.isOnline()) {
            processedText = PlaceholderAPI.setPlaceholders(target.getPlayer(), processedText);
        }
        return processedText;
    }

    private static CrownPunishments plugin = (CrownPunishments) Bukkit.getPluginManager().getPlugin("CrownPunishments");

    /**
     * Plays the configured click sound for this MenuItem to a player.
     * Sound details (name, volume, pitch) are taken from the MenuItem's configuration.
     * Handles invalid sound names gracefully by logging a warning.
     *
     * @param player The player to play the click sound for.
     */
    public void playClickSound(Player player) {
        if (this.clickSound != null) {
            try {
                Sound sound = Sound.valueOf(this.clickSound.toUpperCase());
                player.playSound(player.getLocation(), sound, this.clickVolume, this.clickPitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound configured: " + this.clickSound);
            }
        }
    }

    /**
     * Inner class to represent a Click Action with its associated data.
     * This allows for multiple actions per click and cleaner configuration.
     */
    public static class ClickActionData {
        private ClickAction action;
        private String actionData;

        public ClickActionData(ClickAction action, String actionData) {
            this.action = action;
            this.actionData = actionData;
        }

        public ClickAction getAction() {
            return action;
        }

        public String getActionData() {
            return actionData;
        }

        /**
         * Parses a configuration string to create a ClickActionData object.
         * Handles cases where action data is present or absent.
         *
         * @param configString The configuration string in the format "ACTION:data" or just "ACTION".
         * @return ClickActionData object.
         */
        public static ClickActionData fromConfigString(String configString) {
            String[] parts = configString.split(":", 2); // Split into action and data at the first ":"
            ClickAction action = ClickAction.safeValueOf(parts[0]); // Get action, default to NO_ACTION if invalid
            String actionData = parts.length > 1 ? parts[1] : null; // Action data is null if not provided
            return new ClickActionData(action, actionData);
        }
    }
}
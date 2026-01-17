// MenuItem.java
package cp.corona.menus.items;

import cp.corona.config.MainConfigManager;
import cp.corona.crown.Crown;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * ////////////////////////////////////////////////
 * //             Crown             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Represents a menu item with configurable properties loaded from configuration files.
 * Includes material, display name, lore, player head, custom model data, click action, and sound.
 *
 * Refactored to support multiple actions for left and right clicks.
 * Replaced single click actions with lists of ClickActionData for left and right clicks.
 *
 * **MODIFIED:**
 * - Unified player head configuration to use single entry `player_head` for both usernames and URLs.
 * - Improved player head texture loading from `player_head` (username or texture URL).
 * - Added robust error handling and logging for texture loading.
 * - Refactored texture setting logic into a separate `applyPlayerHeadTexture` method.
 * - Added comments in English and improved code aesthetics.
 * - Added support for dynamic replacements in toItemStack to ensure correct coloring order.
 */
public class MenuItem {

    private String material;
    private String name;
    private List<String> lore;
    private String playerHead; // Unified field for player head, can be username or URL
    private Integer customModelData;
    private int quantity = 1;
    private List<Integer> slots;
    private String clickSound;
    private Float clickVolume = 1.0f;
    private Float clickPitch = 1.0f;

    // Refactored actions to support multiple actions per click type
    private List<ClickActionData> leftClickActions = Collections.emptyList();
    private List<ClickActionData> rightClickActions = Collections.emptyList();
    private MenuItem confirmState;


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

    // Player Head
    public String getPlayerHead() {
        return playerHead;
    }

    public MenuItem getConfirmState() {
        return confirmState;
    }

    public void setConfirmState(MenuItem confirmState) {
        this.confirmState = confirmState;
    }

    public void setPlayerHead(String playerHead) {
        this.playerHead = playerHead;
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

    // Left Click Actions
    public List<ClickActionData> getLeftClickActions() {
        return leftClickActions;
    }

    public void setLeftClickActions(List<ClickActionData> leftClickActions) {
        this.leftClickActions = leftClickActions;
    }

    // Right Click Actions
    public List<ClickActionData> getRightClickActions() {
        return rightClickActions;
    }

    public void setRightClickActions(List<ClickActionData> rightClickActions) {
        this.rightClickActions = rightClickActions;
    }


    /**
     * Converts the MenuItem configuration to an ItemStack for display in menus.
     * Overload for when no custom replacements are needed.
     */
    public ItemStack toItemStack(final OfflinePlayer target, final MainConfigManager configManager) {
        return toItemStack(target, configManager, (String[]) null);
    }

    /**
     * Converts the MenuItem configuration to an ItemStack for display in menus.
     * Processes placeholders in name and lore, applies custom replacements, and sets item meta properties.
     * Handles player head textures from username or direct texture URL using the unified `player_head` config.
     *
     * @param target        The target player for placeholder replacement (can be null).
     * @param configManager MainConfigManager instance for placeholder processing.
     * @param replacements  Optional key-value pairs for custom replacements (e.g. "{time}", "10m").
     * @return The ItemStack representing this MenuItem.
     */
    public ItemStack toItemStack(final OfflinePlayer target, final MainConfigManager configManager, String... replacements) {
        Material itemMaterial = Material.STONE; // Default material
        Integer metaData = null; // Default metadata

        if (this.material != null) {
            String[] materialParts = this.material.toUpperCase().split(":", 2); // Split material and metadata
            itemMaterial = Material.matchMaterial(materialParts[0]);
            if (itemMaterial == null) {
                itemMaterial = Material.STONE; // Fallback to STONE if material is invalid
                plugin.getLogger().warning("Invalid material: " + materialParts[0] + " in item config. Using STONE.");
            }
            if (materialParts.length > 1) {
                try {
                    metaData = Integer.parseInt(materialParts[1]);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid metadata format: " + materialParts[1] + " for material " + materialParts[0] + ". Metadata must be an integer.");
                }
            }
        }


        ItemStack itemStack = new ItemStack(itemMaterial, quantity);
        ItemMeta meta = itemStack.getItemMeta();

        if (meta != null) {
            if (this.name != null) {
                String processedName = processText(this.name, target, configManager);
                processedName = applyReplacements(processedName, replacements);
                meta.setDisplayName(MessageUtils.getColorMessage(processedName));
            }
            if (this.lore != null) {
                List<String> processedLore = this.lore.stream()
                        .map(line -> processText(line, target, configManager))
                        .map(line -> applyReplacements(line, replacements))
                        .map(MessageUtils::getColorMessage)
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }
            if (this.customModelData != null) {
                meta.setCustomModelData(this.customModelData);
            } else if (metaData != null) {
                meta.setCustomModelData(metaData); // Apply parsed metadata as CustomModelData - MODIFIED
            }

            if (itemMaterial == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) meta;
                this.applyPlayerHeadTexture(skullMeta, target, configManager);
                meta = skullMeta;
            }
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private String applyReplacements(String text, String[] replacements) {
        if (text == null || replacements == null || replacements.length == 0) return text;
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break;
            String key = replacements[i];
            String value = replacements[i+1] != null ? replacements[i+1] : "";
            text = text.replace(key, value);
        }
        return text;
    }


    /**
     * Applies player head texture to the SkullMeta, using player username or direct texture URL from the unified `player_head` config.
     * Automatically detects if `playerHead` is a username or a URL.
     *
     * @param skullMeta   The SkullMeta to apply the texture to.
     * @param target      The target player for context and placeholders.
     * @param configManager MainConfigManager instance for placeholder processing.
     */
    private void applyPlayerHeadTexture(final SkullMeta skullMeta, final OfflinePlayer target, final MainConfigManager configManager) {
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        boolean textureSet = false;

        // Unified playerHead config - can be username or URL
        if (this.playerHead != null && !this.playerHead.isEmpty()) {
            if (this.playerHead.startsWith("http://") || this.playerHead.startsWith("https://")) {
                // 1. Treat playerHead as texture URL
                try {
                    URL textureURL = new URL(this.playerHead);
                    textures.setSkin(textureURL);
                    textureSet = true;
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().log(Level.INFO, "[DEBUG] Successfully set skin from player_head URL: " + this.playerHead + " for item: " + this.name);
                } catch (MalformedURLException e) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().warning("Invalid player_head URL: " + this.playerHead + " for item: " + this.name + ". Must be a direct texture URL from textures.minecraft.net.");
                }
            } else {
                // 2. Treat playerHead as username
                final String playerName = processText(this.playerHead, target, configManager);
                PlayerProfile loadedProfile = Bukkit.getOfflinePlayer(playerName).getPlayerProfile(); // Synchronously fetch profile

                if (loadedProfile != null && loadedProfile.getTextures() != null && loadedProfile.getTextures().getSkin() != null) {
                    skullMeta.setOwnerProfile(loadedProfile);
                    textureSet = true;
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().log(Level.INFO, "[DEBUG] Successfully set skin from player name (sync): " + playerName + " for item: " + this.name); // Log as sync now
                } else {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().warning("Failed to load skin for player: " + playerName + " (sync load failed) for item: " + this.name + ". Using default head."); // Log as sync load failed
                }
            }
        }

        // 3. Fallback and Validation: If textures are set, set OwnerProfile
        if (textures != null && textures.getSkin() != null && textureSet) {
            skullMeta.setOwnerProfile(profile);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Owner profile set SUCCESSFULLY for item: " + this.name);
        } else if (!textureSet && (playerHead != null)) { // Warn if texture was intended to be set but failed
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().warning("Skull profile is still missing textures after attempting to set for item: " + this.name + ". Check player_head config. Ensure player_head is a valid username or a DIRECT texture URL from textures.minecraft.net.");
        }
        // No warning if textureSet is false and playerHead is null/empty, assuming default head is intentional
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

    private static Crown plugin = (Crown) Bukkit.getPluginManager().getPlugin("Crown");

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
        private String[] actionArgs; // Changed to String[] to hold multiple arguments - MODIFIED

        public ClickActionData(ClickAction action, String... actionArgs) { // Changed to accept varargs - MODIFIED
            this.action = action;
            this.actionArgs = actionArgs;
        }

        public ClickAction getAction() {
            return action;
        }

        public String[] getActionData() { return actionArgs; } //Return String array - MODIFIED

        /**
         * Parses a configuration string to create a ClickActionData object.
         * Handles cases where action data is present or absent, and for new actions with multiple parameters.
         *
         * @param configString The configuration string in the format "ACTION:data1:data2:..." or just "ACTION".
         * @return ClickActionData object.
         */
        public static ClickActionData fromConfigString(String configString) {
            String[] parts = configString.split(":", 2);
            ClickAction action = ClickAction.safeValueOf(parts[0]);
            String[] actionArgs = parts.length > 1 ? new String[]{parts[1]} : new String[0];
            return new ClickActionData(action, actionArgs);
        }
    }
}
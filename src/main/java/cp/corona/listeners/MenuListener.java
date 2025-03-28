package cp.corona.listeners;

import cp.corona.config.MainConfigManager;
import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.*;
import cp.corona.menus.actions.ClickAction;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.ColorUtils;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static cp.corona.menus.PunishDetailsMenu.*;

/**
 * Menu interaction listener for CrownPunishments plugin.
 * Handles inventory clicks, chat input, and menu actions.
 */
public class MenuListener implements Listener {
    private final CrownPunishments plugin;
    private final HashMap<UUID, BukkitTask> inputTimeouts = new HashMap<>();
    private final HashMap<UUID, PunishDetailsMenu> pendingDetailsMenus = new HashMap<>();
    private final HashMap<UUID, String> inputTypes = new HashMap<>();

    // Constants for punishment types and menu item keys
    private static final String BAN_PUNISHMENT_TYPE = "ban";
    private static final String MUTE_PUNISHMENT_TYPE = "mute";
    private static final String SOFTBAN_PUNISHMENT_TYPE = "softban";
    private static final String KICK_PUNISHMENT_TYPE = "kick";
    private static final String WARN_PUNISHMENT_TYPE = "warn";
    private static final String FREEZE_PUNISHMENT_TYPE = "freeze"; // New punishment type - NEW
    private static final String PERMANENT_TIME_KEY = "permanent";
    private static final String CUSTOM_TIME_KEY = "custom";
    private static final String INFO_ITEM_KEY = "info";
    private static final String BAN_ITEM_MENU_KEY = "ban";
    private static final String MUTE_ITEM_MENU_KEY = "mute";
    private static final String SOFTBAN_ITEM_MENU_KEY = "softban";
    private static final String KICK_ITEM_MENU_KEY = "kick";
    private static final String WARN_ITEM_MENU_KEY = "warn";
    private static final String FREEZE_ITEM_MENU_KEY = "freeze"; // New item key - NEW
    private static final String HISTORY_ITEM_MENU_KEY = "history";
    private static final String TIME_DISPLAY_KEY = "time_display";
    private static final String MINUS_5_MIN_KEY = "minus_5_min";
    private static final String MINUS_2_HOUR_KEY = "minus_2_hour";
    private static final String MINUS_1_DAY_KEY = "minus_1_day";
    private static final String MINUS_5_DAY_KEY = "minus_5_day";
    private static final String PLUS_15_MIN_KEY = "plus_15_min";
    private static final String PLUS_6_HOUR_KEY = "plus_6_hour";
    private static final String PLUS_1_DAY_KEY = "plus_1_day";
    private static final String PLUS_7_DAY_KEY = "plus_7_day";
    private static final String BACK_BUTTON_KEY = "back_button";
    private static final String NEXT_PAGE_BUTTON_KEY = "next_page_button";
    private static final String PREVIOUS_PAGE_BUTTON_KEY = "previous_page_button";
    private static final String ADMIN_PERMISSION = "crown.admin"; // ADDED ADMIN_PERMISSION constant here
    private static final String PUNISH_PERMISSION = "crown.punish"; // Permission to access punish menu
    private static final String PUNISH_BAN_PERMISSION = "crown.punish.ban"; // Permission for ban related actions
    private static final String UNPUNISH_BAN_PERMISSION = "crown.unpunish.ban"; // Permission for unban related actions
    private static final String PUNISH_MUTE_PERMISSION = "crown.punish.mute"; // Permission for mute related actions
    private static final String UNPUNISH_MUTE_PERMISSION = "crown.unpunish.mute"; // Permission for unmute related actions
    private static final String PUNISH_SOFTBAN_PERMISSION = "crown.punish.softban"; // Permission for softban related actions
    private static final String UNPUNISH_SOFTBAN_PERMISSION = "crown.unpunish.softban"; // Permission for unsoftban related actions
    private static final String PUNISH_KICK_PERMISSION = "crown.punish.kick"; // Permission for kick related actions
    private static final String PUNISH_WARN_PERMISSION = "crown.punish.warn"; // Permission for warn related actions
    private static final String PUNISH_FREEZE_PERMISSION = "crown.punish.freeze"; // Permission for freeze related actions - NEW
    private static final String UNPUNISH_FREEZE_PERMISSION = "crown.unpunish.freeze"; // Permission for unfreeze related actions - NEW

    /**
     * Constructor for MenuListener.
     * @param plugin Instance of the main plugin class.
     */
    public MenuListener(CrownPunishments plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles inventory click events.
     * Determines the clicked MenuItem and performs the corresponding action based on click type (LEFT or RIGHT).
     *
     * @param event The InventoryClickEvent.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER || clickedItem == null) return;

        playSound(player, "menu_click");
        event.setCancelled(true); // IMPORTANT: Cancel the event to prevent item movement

        MenuItem clickedMenuItem = getMenuItemClicked(event.getRawSlot(), holder);
        if (clickedMenuItem != null) {
            clickedMenuItem.playClickSound(player);

            // Handle left click actions
            if (event.getClick().isLeftClick()) {
                List<MenuItem.ClickActionData> leftClickActions = clickedMenuItem.getLeftClickActions();
                if (!leftClickActions.isEmpty()) {
                    for (MenuItem.ClickActionData actionData : leftClickActions) {
                        handleMenuItemClick(player, holder, actionData.getAction(), actionData.getActionData(), event, clickedMenuItem);
                    }
                }
            }

            // Handle right click actions
            if (event.getClick().isRightClick()) {
                List<MenuItem.ClickActionData> rightClickActions = clickedMenuItem.getRightClickActions();
                if (!rightClickActions.isEmpty()) {
                    for (MenuItem.ClickActionData actionData : rightClickActions) {
                        handleMenuItemClick(player, holder, actionData.getAction(), actionData.getActionData(), event, clickedMenuItem);
                    }
                }
            }
        }
    }


    /**
     * Retrieves the MenuItem clicked based on the inventory holder and slot.
     * Dynamically gets MenuItem based on the menu type and slot.
     * @param slot The slot number clicked.
     * @param holder The InventoryHolder.
     * @return The MenuItem clicked, or null if no MenuItem is found.
     */
    private MenuItem getMenuItemClicked(int slot, InventoryHolder holder) {
        if (holder instanceof PunishMenu) {
            return getPunishMenuItem(slot, (PunishMenu) holder);
        } else if (holder instanceof PunishDetailsMenu) {
            return getPunishDetailsMenuItem(slot, (PunishDetailsMenu) holder);
        } else if (holder instanceof TimeSelectorMenu) {
            return getTimeSelectorMenuItem(slot, (TimeSelectorMenu) holder);
        }  else if (holder instanceof HistoryMenu) {
            return getHistoryMenuItem(slot, (HistoryMenu) holder);
        }
        return null;
    }

    /**
     * Gets the PunishMenuItem based on slot number.
     * Iterates through all MenuItem keys of PunishMenu to find the clicked item.
     * @param slot The slot number.
     * @param menu The PunishMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getPunishMenuItem(int slot, PunishMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getMenuItemKeys()) { // Loop through dynamically loaded item keys
            MenuItem menuItem = configManager.getPunishMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots().contains(slot)) {
                return menuItem;
            }
        }
        return null;
    }

    /**
     * Gets the PunishDetailsMenuItem based on slot number.
     * Iterates through all MenuItem keys of PunishDetailsMenu to find the clicked item.
     * @param slot The slot number.
     * @param menu The PunishDetailsMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getPunishDetailsMenuItem(int slot, PunishDetailsMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        String punishmentType = menu.getPunishmentType();
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] getPunishDetailsMenuItem - Punishment Type: " + punishmentType); // Log punishment type
        }

        for (String itemKey : menu.getMenuItemKeys()) { // Loop through dynamically loaded item keys
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] getPunishDetailsMenuItem - Checking item key: " + itemKey); // Log each item key being checked
            }
            MenuItem menuItem = configManager.getDetailsMenuItemConfig(punishmentType, itemKey);
            if (menuItem != null) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[DEBUG] getPunishDetailsMenuItem - MenuItem config found for key: " + itemKey); // Log if config is found
                }
                if (menuItem.getSlots().contains(slot)) {
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("[DEBUG] getPunishDetailsMenuItem - Slot " + slot + " matches item key: " + itemKey); // Log if slot matches
                    }
                    return menuItem;
                } else {
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("[DEBUG] getPunishDetailsMenuItem - Slot " + slot + " does NOT match item key: " + itemKey + ", slots: " + menuItem.getSlots()); // Log if slot doesn't match
                    }
                }
            } else {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().warning("[WARNING] getPunishDetailsMenuItem - NO MenuItem config found for key: " + itemKey); // Warning if config is missing
                }
            }
        }
        return null;
    }

    /**
     * Gets the TimeSelectorMenuItem based on slot number.
     * Iterates through all MenuItem keys of TimeSelectorMenu to find the clicked item.
     * @param slot The slot number.
     * @param menu The TimeSelectorMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getTimeSelectorMenuItem(int slot, TimeSelectorMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getTimeSelectorItemKeys()) { // Loop through dynamically loaded item keys
            MenuItem menuItem = configManager.getTimeSelectorMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots().contains(slot)) {
                return menuItem;
            }
        }
        return null;
    }

    /**
     * Gets the HistoryMenuItem based on slot number.
     * Iterates through all MenuItem keys of HistoryMenu to find the clicked item.
     * @param slot The slot number.
     * @param menu The HistoryMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getHistoryMenuItem(int slot, HistoryMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getMenuItemKeys()) { // Loop through dynamically loaded item keys
            MenuItem menuItem = configManager.getHistoryMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots().contains(slot)) {
                return menuItem;
            }
        }
        // Check if the clicked slot corresponds to a punishment history item slot range.
        for (MenuItem historyEntryItem : menu.getHistoryEntryItems()) {
            if (historyEntryItem != null && historyEntryItem.getSlots().contains(slot)) {
                return historyEntryItem;
            }
        }
        return null;
    }

    /**
     * Handles menu item clicks based on the inventory holder and ClickAction.
     * Executes actions based on ClickAction type such as opening menus, requesting input, or confirming punishments.
     *
     * @param player The player who clicked.
     * @param holder The InventoryHolder representing the menu.
     * @param action The ClickAction enum value representing the action to take.
     * @param actionData The data associated with the ClickAction, used for specifying menu names or commands.
     * @param event The InventoryClickEvent.
     * @param clickedMenuItem The MenuItem that was clicked.
     */
    private void handleMenuItemClick(Player player, InventoryHolder holder, ClickAction action, String[] actionData, InventoryClickEvent event, MenuItem clickedMenuItem) {
        // Log entry to debug menu item click handling, including action, data, item name and holder type
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - START - Action: " + action + ", ActionData: " + Arrays.toString(actionData) + ", Item: " + clickedMenuItem.getName() + ", Holder Type: " + holder.getClass().getSimpleName()); // Modified ActionData log - MODIFIED

        String[] processedActionData = actionData; // Initialize processedActionData with original actionData

        // Process actionData for actions that support text placeholders
        if (action == ClickAction.CONSOLE_COMMAND || action == ClickAction.PLAYER_COMMAND || action == ClickAction.PLAYER_COMMAND_OP ||
                action == ClickAction.MESSAGE || action == ClickAction.MESSAGE_TARGET ||
                action == ClickAction.TITLE || action == ClickAction.TITLE_TARGET) {
            if (actionData != null && actionData.length > 0) { // Check if actionData has content
                processedActionData = Arrays.stream(actionData) // Stream the actionData array
                        .filter(Objects::nonNull) // Filter out null elements just in case
                        .map(data -> replacePlaceholders(player, data, holder)) // Process each element for placeholders
                        .toArray(String[]::new); // Convert back to String array
            }
        }


        // Handle actions based on the type of menu holder
        if (holder instanceof PunishMenu punishMenu) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - Holder is PunishMenu"); // Debug log to identify PunishMenu holder
            handlePunishMenuActions(player, punishMenu, action, actionData[0], clickedMenuItem); // Delegate action handling to PunishMenuActions
        } else if (holder instanceof PunishDetailsMenu punishDetailsMenu) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - Holder is PunishDetailsMenu"); // Debug log for PunishDetailsMenu
            handlePunishDetailsMenuActions(player, punishDetailsMenu, action, actionData[0], clickedMenuItem); // Delegate action handling to PunishDetailsMenuActions
        } else if (holder instanceof TimeSelectorMenu timeSelectorMenu) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - Holder is TimeSelectorMenu"); // Debug log for TimeSelectorMenu
            handleTimeSelectorMenuActions(player, timeSelectorMenu, action, actionData[0], clickedMenuItem); // Delegate action handling to TimeSelectorMenuActions
        } else if (holder instanceof HistoryMenu historyMenu) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - Holder is HistoryMenu"); // Debug log for HistoryMenu
            handleHistoryMenuActions(player, historyMenu, action, actionData[0], clickedMenuItem); // Delegate action handling to HistoryMenuActions
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - Holder is UNKNOWN Type: " + holder.getClass().getSimpleName()); // Log unknown holder types
        }

        // Handle actions that are common to all menus, regardless of the holder type
        if (action == ClickAction.CONSOLE_COMMAND || action == ClickAction.PLAYER_COMMAND || action == ClickAction.PLAYER_COMMAND_OP) { // Handle all command types here
            executeCommandAction(player, action, processedActionData, holder); // Execute command action with processed data
        } else if (action == ClickAction.CLOSE_MENU) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - CLOSE_MENU action detected."); // Debug log for CLOSE_MENU action
            player.closeInventory(); // Close the player's inventory
        }  else if (action == ClickAction.PLAY_SOUND) { // NEW: Handle PLAY_SOUND action
            executePlaySoundAction(player, actionData);
        } else if (action == ClickAction.TITLE) { // NEW: Handle TITLE action
            executeTitleAction(player, processedActionData); // Execute Title Action with processed data
        } else if (action == ClickAction.MESSAGE) { // NEW: Handle MESSAGE action
            executeMessageAction(player, processedActionData, holder); // Execute Message Action with processed data and holder
        } else if (action == ClickAction.UN_BAN) { // Handle UN_BAN action
            executeUnbanAction(player, holder);
        } else if (action == ClickAction.UN_MUTE) { // Handle UN_MUTE action
            executeUnmuteAction(player, holder);
        } else if (action == ClickAction.PLAY_SOUND_TARGET) { // NEW: Handle PLAY_SOUND_TARGET action
            executePlaySoundTargetAction(player, holder, actionData);
        } else if (action == ClickAction.GIVE_EFFECT_TARGET) { // NEW: Handle GIVE_EFFECT_TARGET action
            executeGiveEffectTargetAction(player, holder, actionData);
        } else if (action == ClickAction.TITLE_TARGET) { // NEW: Handle TITLE_TARGET action
            executeTitleTargetAction(player, holder, processedActionData); // Execute Title Target Action with processed data
        } else if (action == ClickAction.MESSAGE_TARGET) { // NEW: Handle MESSAGE_TARGET action
            executeMessageTargetAction(player, holder, processedActionData); // Execute Message Target Action with processed data and holder
        }


        // Log exit from menu item click handling
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - END - Action: " + action + ", ActionData: " + Arrays.toString(actionData)); // Modified ActionData log - MODIFIED
    }

    /**
     * Executes the PLAY_SOUND_TARGET action, playing sound to the target player. - NEW - IMPLEMENTED
     * @param player The player initiating the action (punisher).
     * @param holder The InventoryHolder, to get target player context.
     * @param soundArgs Sound arguments from configuration: [sound_name, volume, pitch].
     */
    private void executePlaySoundTargetAction(Player player, InventoryHolder holder, String[] soundArgs) { // NEW - IMPLEMENTED
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            sendConfigMessage(player, "messages.player_not_online", "{input}", target != null ? target.getName() : "target");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (soundArgs.length >= 1) { // Check if at least sound name is provided
            try {
                Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
                float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f; // Default volume
                float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;   // Default pitch
                targetPlayer.playSound(targetPlayer.getLocation(), sound, volume, pitch);
            } catch (NumberFormatException e) { // Moved NumberFormatException catch block BEFORE IllegalArgumentException
                plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND_TARGET action: " + Arrays.toString(soundArgs));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND_TARGET action: " + soundArgs[0]);
            }
        } else {
            plugin.getLogger().warning("PLAY_SOUND_TARGET action requires at least a sound name.");
        }
    }

    /**
     * Executes the GIVE_EFFECT_TARGET action, applying potion effect to the target player. - NEW
     * @param player The player initiating the action (punisher).
     * @param holder InventoryHolder to retrieve target player.
     * @param effectArgs Effect arguments from configuration (not currently used in provided code, placeholder for future).
     */
    /**
     * Executes the GIVE_EFFECT_TARGET action, applying potion effect to the target player. - NEW - IMPLEMENTED
     * @param player The player initiating the action (punisher).
     * @param holder InventoryHolder to retrieve target player.
     * @param effectArgs Effect arguments from configuration: [effect_type, duration_seconds, amplifier].
     */
    private void executeGiveEffectTargetAction(Player player, InventoryHolder holder, String[] effectArgs) { // NEW - IMPLEMENTED - CORRECTED: Using PotionEffectType.getByKey
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            sendConfigMessage(player, "messages.player_not_online", "{input}", target != null ? target.getName() : "target");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (effectArgs.length >= 3) { // Expecting effect type, duration, and amplifier
            try {
                PotionEffectType effectType = PotionEffectType.getByKey(NamespacedKey.minecraft(effectArgs[0].toLowerCase())); // Use getByKey and lowercase input - CORRECTED
                int durationSeconds = Integer.parseInt(effectArgs[1]);
                int amplifier = Integer.parseInt(effectArgs[2]);
                boolean showParticles = effectArgs.length <= 3 || Boolean.parseBoolean(effectArgs[3]); // Default to true if not specified

                if (effectType == null) {
                    plugin.getLogger().warning("Invalid PotionEffectType configured: " + effectArgs[0] + " for GIVE_EFFECT_TARGET action. Please use Bukkit namespaced keys (e.g., minecraft:slowness).");
                    return;
                }

                PotionEffect effect = new PotionEffect(effectType, durationSeconds * 20, amplifier, true, showParticles); // Duration in ticks, ambient=true, showParticles from config
                targetPlayer.addPotionEffect(effect);
                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] GIVE_EFFECT_TARGET action executed for player: " + targetPlayer.getName() + ", effect: " + effectType.getName() + ", duration: " + durationSeconds + "s, amplifier: " + amplifier + ", showParticles: " + showParticles);

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid duration or amplifier format for GIVE_EFFECT_TARGET action: " + Arrays.toString(effectArgs) + ". Expected [effect_type, duration_seconds, amplifier, show_particles(optional)]");
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("IllegalArgumentException in GIVE_EFFECT_TARGET action: " + e.getMessage() + ", Args: " + Arrays.toString(effectArgs));
            }
        } else {
            plugin.getLogger().warning("GIVE_EFFECT_TARGET action requires effect_type, duration_seconds, and amplifier arguments.");
        }
    }


    /**
     * Executes the TITLE_TARGET action, showing a title to the target player. - NEW - IMPLEMENTED
     * @param player The player initiating the action (punisher).
     * @param holder The InventoryHolder, to get target player context.
     * @param titleArgs Title arguments from configuration: [title, subtitle, time_seconds, fade_in_ticks, fade_out_ticks]. - NOW EXPECTS PROCESSED TEXT
     */
    private void executeTitleTargetAction(Player player, InventoryHolder holder, String[] titleArgs) { // NEW - IMPLEMENTED - NOW EXPECTS PROCESSED TEXT
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            sendConfigMessage(player, "messages.player_not_online", "{input}", target != null ? target.getName() : "target");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (titleArgs.length >= 3) { // title, subtitle, time are mandatory
            // Get title text and subtitle text, colorize directly, no underscore replacement yet
            String titleText = ColorUtils.translateRGBColors(titleArgs[0]); // Colorize, NO underscore replacement here
            String subtitleText = ColorUtils.translateRGBColors(titleArgs[1]); // Colorize, NO underscore replacement here
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            int fadeInTicks = titleArgs.length > 3 ? Integer.parseInt(titleArgs[3]) : 10; // Default fadeIn
            int fadeOutTicks = titleArgs.length > 4 ? Integer.parseInt(titleArgs[4]) : 20; // Default fadeOut

            targetPlayer.sendTitle(titleText.replace("_", " "), subtitleText.replace("_", " "), fadeInTicks, timeSeconds * 20, fadeOutTicks); // Apply underscore replacement for display here
        } else {
            plugin.getLogger().warning("TITLE_TARGET action requires at least title, subtitle, and time_seconds arguments.");
        }
    }

    /**
     * Executes the MESSAGE_TARGET action, sending a message to the target player. - NEW - IMPLEMENTED
     * @param player The player initiating the action (punisher).
     * @param holder The InventoryHolder, to get target player context.
     * @param messageArgs Message arguments from configuration: [message_text]. - NOW EXPECTS PROCESSED TEXT
     */
    private void executeMessageTargetAction(Player player, InventoryHolder holder, String[] messageArgs) { // NEW - IMPLEMENTED - CORRECTED: Now processes placeholders - NOW EXPECTS PROCESSED TEXT
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            sendConfigMessage(player, "messages.player_not_online", "{input}", target != null ? target.getName() : "target");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (messageArgs.length >= 1) {
            // Get the message text and colorize directly, no underscore replacement yet
            String messageText = MessageUtils.getColorMessage(messageArgs[0]); // Colorize, NO underscore replacement here
            targetPlayer.sendMessage(messageText.replace("_", " ")); // Apply underscore replacement for display here
        } else {
            plugin.getLogger().warning("MESSAGE_TARGET action requires a message text argument.");
        }
    }

    /**
     * Helper method to get the target player from the InventoryHolder context. - NEW
     * @param holder The InventoryHolder.
     * @return OfflinePlayer target or null if holder is not a PunishMenu or PunishDetailsMenu.
     */
    private OfflinePlayer getTargetForAction(InventoryHolder holder) {
        if (holder instanceof PunishMenu) {
            return Bukkit.getOfflinePlayer(((PunishMenu) holder).getTargetUUID());
        } else if (holder instanceof PunishDetailsMenu) {
            return Bukkit.getOfflinePlayer(((PunishDetailsMenu) holder).getTargetUUID());
        } else if (holder instanceof TimeSelectorMenu) {
            return Bukkit.getOfflinePlayer(((TimeSelectorMenu) holder).getPunishDetailsMenu().getTargetUUID());
        } else if (holder instanceof  HistoryMenu){
            return Bukkit.getOfflinePlayer(((HistoryMenu) holder).getTargetUUID());
        }
        return null;
    }


    /**
     * Executes the UN_BAN action, unbanning the target player. - NEW
     * @param player The player initiating the unban.
     * @param holder The InventoryHolder, used to get target player context.
     */
    private void executeUnbanAction(Player player, InventoryHolder holder) {
        if (!(holder instanceof PunishDetailsMenu)) {
            plugin.getLogger().warning("UN_BAN action called from invalid menu holder: " + holder.getClass().getName());
            return;
        }
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String unbanCommand = plugin.getConfigManager().getUnbanCommand()
                .replace("{target}", target.getName());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), unbanCommand));
        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, "ban"); // Use generic unpunish confirmation
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unban", "Unbanned via Menu", player.getName(), 0L, "permanent"); // Log unban action
    }

    /**
     * Executes the UN_MUTE action, unmuting the target player. - NEW
     * @param player The player initiating the unmute.
     * @param holder The InventoryHolder, used to get target player context.
     */
    private void executeUnmuteAction(Player player, InventoryHolder holder) {
        if (!(holder instanceof PunishDetailsMenu)) {
            plugin.getLogger().warning("UN_MUTE action called from invalid menu holder: " + holder.getClass().getName());
            return;
        }
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String unmuteCommand = plugin.getConfigManager().getUnmuteCommand()
                .replace("{target}", target.getName());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), unmuteCommand));
        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, "mute"); // Use generic unpunish confirmation
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unmute", "Unmuted via Menu", player.getName(), 0L, "permanent"); // Log unmute action
    }

    /**
     * Sends a generic unpunish confirmation message to the punisher. - NEW
     * @param player The punisher player.
     * @param target The unpunished player.
     * @param punishType The type of punishment that was removed (ban, mute).
     */
    private void sendUnpunishConfirmation(Player player, OfflinePlayer target, String punishType) {
        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.direct_unpunishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : "unknown",
                "{punishment_type}", punishType)));
    }


    /**
     * Executes a command as the player, replacing placeholders and handling colors.
     *
     * @param player     The player who triggered the command.
     * @param action     The ClickAction type (PLAYER_COMMAND or PLAYER_COMMAND_OP).
     * @param commandData The command string from the configuration.
     * @param holder      The InventoryHolder (not used in this method, but kept for consistency).
     */
    private void executeCommandAction(Player player, ClickAction action, String[] commandData, InventoryHolder holder) {
        // Check if there is at least one command argument provided and it's not null or empty
        if (commandData.length > 0 && commandData[0] != null && !commandData[0].isEmpty()) {
            // Prepare the command to be executed, no additional placeholder processing needed here as it's already processed in handleMenuItemClick
            String commandToExecute = ColorUtils.translateRGBColors(commandData[0]); // Colorize the command string

            // Debug logging to indicate player command execution with the action and command
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing COMMAND: " + action + " Command: " + commandToExecute);
            // Make command final for use in lambda
            final String finalCommand = commandToExecute;

            // Execute the player command asynchronously on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (action) {
                    case CONSOLE_COMMAND:
                        // Console command execution (already handled elsewhere if action is CONSOLE_COMMAND but included for completeness)
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                        break;
                    case PLAYER_COMMAND:
                        // Player command execution: command is performed by the player
                        player.performCommand(finalCommand);
                        break;
                    case PLAYER_COMMAND_OP:
                        // Player command execution as OP: temporarily grants OP status to player to execute command
                        boolean originalOp = player.isOp(); // Store original OP status
                        try {
                            player.setOp(true); // Set player as OP temporarily
                            player.performCommand(finalCommand); // Execute command as OP
                        } finally {
                            player.setOp(originalOp); // Revert player to original OP status after command execution
                        }
                        break;
                }
            });
        } else {
            // Log a warning if the COMMAND action data is invalid or missing for the given action type
            plugin.getLogger().warning("Invalid COMMAND action data for action type: " + action + " Data: " + Arrays.toString(commandData));
        }
    }


    /**
     * Executes a console command, replacing placeholders and handling colors.
     *
     * @param player     The player who triggered the command (for player-specific placeholders).
     * @param commandData The command string from the configuration.
     * @param holder      The InventoryHolder, providing context for placeholder replacement.
     */
    private void executeConsoleCommand(Player player, String[] commandData, InventoryHolder holder) { // Modified actionData type - MODIFIED
        // Check if there is at least one command argument provided and it's not null or empty
        if (commandData.length > 0 && commandData[0] != null && !commandData[0].isEmpty()) { // Check for commandData array and content - MODIFIED
            // Extract the command string, removing "command:" prefix if present and trim whitespace
            String rawCommand = commandData[0].startsWith("command:") ? commandData[0].substring("command:".length()).trim() : commandData[0]; // Use commandData[0] - MODIFIED
            // Prepare the command to be executed, no additional placeholder processing needed here as it's already done in handleMenuItemClick
            String commandToExecute = rawCommand;
            // Apply color formatting to the command string
            commandToExecute = ColorUtils.translateRGBColors(commandToExecute); // Apply color formatting

            // Debug logging to indicate console command execution with the command
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing CONSOLE_COMMAND: " + commandToExecute);
            // Create a final copy of the command string for use in the Bukkit scheduler
            final String finalCommand = commandToExecute; // Finalize for lambda
            // Execute the console command asynchronously on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
        } else {
            // Log a warning if the CONSOLE_COMMAND action data is invalid or missing
            plugin.getLogger().warning("Invalid CONSOLE_COMMAND action data: " + Arrays.toString(commandData)); // Modified actionData log - MODIFIED
        }
    }

    /**
     * Executes the PLAY_SOUND action.
     * @param player The player to play the sound for.
     * @param soundArgs Sound arguments: sound name, volume, pitch.
     */
    private void executePlaySoundAction(Player player, String[] soundArgs) { // NEW
        if (soundArgs.length >= 1) { // Check if at least sound name is provided
            try {
                Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
                float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f; // Default volume
                float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;   // Default pitch
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (NumberFormatException e) { // Moved NumberFormatException catch block BEFORE IllegalArgumentException
                plugin.getLogger().warning("Invalid volume or pitch format: " + Arrays.toString(soundArgs));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name configured: " + soundArgs[0]);
            }
        } else {
            plugin.getLogger().warning("PLAY_SOUND action requires at least a sound name.");
        }
    }

    /**
     * Executes the TITLE action.
     * @param player The player to show the title to.
     * @param titleArgs Title arguments: title, subtitle, time_seconds, fade_in_ticks, fade_out_ticks. - NOW EXPECTS PROCESSED TEXT
     */
    private void executeTitleAction(Player player, String[] titleArgs) {
        // Check if there are enough arguments provided for the TITLE action
        if (titleArgs.length >= 3) { // title, subtitle, time_seconds are mandatory
            // Get title text from the first argument and colorize directly after placeholder processing
            String titleText = ColorUtils.translateRGBColors(titleArgs[0]); // Colorize, NO underscore replacement here
            // Get subtitle text from the second argument and colorize directly after placeholder processing
            String subtitleText = ColorUtils.translateRGBColors(titleArgs[1]); // Colorize, NO underscore replacement here
            // Parse the time in seconds from the third argument
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            // Parse fade-in duration from the fourth argument, default to 10 ticks if not provided
            int fadeInTicks = titleArgs.length > 3 ? Integer.parseInt(titleArgs[3]) : 10; // Default fadeIn
            // Parse fade-out duration from the fifth argument, default to 20 ticks if not provided
            int fadeOutTicks = titleArgs.length > 4 ? Integer.parseInt(titleArgs[4]) : 20; // Default fadeOut

            // Send the title to the player with parsed and formatted arguments
            player.sendTitle(titleText.replace("_", " "), subtitleText.replace("_", " "), fadeInTicks, timeSeconds * 20, fadeOutTicks); // Apply underscore replacement for display here
        } else {
            // Log a warning if not enough arguments are provided for the TITLE action
            plugin.getLogger().warning("TITLE action requires at least title, subtitle, and time_seconds arguments.");
        }
    }

    /**
     * Executes the MESSAGE action.
     * @param player The player to send the message to.
     * @param messageArgs Message arguments: text. - NOW EXPECTS PROCESSED TEXT
     */
    private void executeMessageAction(Player player, String[] messageArgs, InventoryHolder holder) { // MODIFIED: Added holder - NOW EXPECTS PROCESSED TEXT
        // Check if there is at least one message argument provided
        if (messageArgs.length >= 1) {
            // Get the message text and colorize directly, no underscore replacement yet
            String messageText = MessageUtils.getColorMessage(messageArgs[0]); // Colorize, NO underscore replacement here
            // Send the colorized message to the player, applying underscore replacement for display
            player.sendMessage(messageText.replace("_", " ")); // Apply underscore replacement for display here
        } else {
            // Log a warning if no message text argument is provided for the MESSAGE action
            plugin.getLogger().warning("MESSAGE action requires a message text argument.");
        }
    }


    /**
     * Executes menu open actions for the given InventoryHolder.
     * @param player The player opening the menu.
     * @param holder The InventoryHolder representing the menu.
     */
    public void executeMenuOpenActions(Player player, InventoryHolder holder) { // NEW - MODIFIED: Corrected to pass holder
        List<MenuItem.ClickActionData> openActions = Collections.emptyList();

        if (holder instanceof PunishMenu) {
            openActions = plugin.getConfigManager().loadMenuOpenActions(plugin.getConfigManager().getPunishMenuConfig().getConfig(), "menu");
        } else if (holder instanceof PunishDetailsMenu) {
            openActions = plugin.getConfigManager().loadMenuOpenActions(plugin.getConfigManager().getPunishDetailsMenuConfig().getConfig(), "menu.punish_details." + ((PunishDetailsMenu) holder).getPunishmentType());
        } else if (holder instanceof TimeSelectorMenu) {
            openActions = plugin.getConfigManager().loadMenuOpenActions(plugin.getConfigManager().getTimeSelectorMenuConfig().getConfig(), "menu");
        } else if (holder instanceof HistoryMenu) {
            openActions = plugin.getConfigManager().loadMenuOpenActions(plugin.getConfigManager().getHistoryMenuConfig().getConfig(), "menu");
        }

        for (MenuItem.ClickActionData actionData : openActions) {
            ClickAction action = actionData.getAction();
            String[] actionArgs = actionData.getActionData();

            if (action == ClickAction.PLAY_SOUND) {
                executePlaySoundAction(player, actionArgs);
            } else if (action == ClickAction.TITLE) {
                executeTitleAction(player, actionArgs);
            } else if (action == ClickAction.MESSAGE) {
                executeMessageAction(player, actionArgs, holder); // MODIFIED: Pass holder here!
            }
        }
    }

    /**
     * Executes a MenuItem ClickAction directly for a player, without inventory click context. - NEW
     * This method is used by tasks that need to trigger menu actions programmatically, e.g., freeze actions.
     *
     * @param player     The player for whom to execute the action.
     * @param action     The ClickAction to execute.
     * @param actionData The data associated with the ClickAction.
     */
    public void executeMenuItemAction(Player player, ClickAction action, String[] actionData) { // NEW Method - MODIFIED: Corrected to pass null holder
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] executeMenuItemAction - START - Action: " + action + ", ActionData: " + Arrays.toString(actionData)); // Debug log - entry

        if (action == ClickAction.CONSOLE_COMMAND) {
            executeConsoleCommand(player, actionData, null); // Holder is null as it's outside menu context - MODIFIED: Holder can be null now
        } else if (action == ClickAction.PLAY_SOUND) { // NEW: Handle PLAY_SOUND action
            executePlaySoundAction(player, actionData);
        } else if (action == ClickAction.TITLE) { // NEW: Handle TITLE action
            executeTitleAction(player, actionData);
        } else if (action == ClickAction.MESSAGE) { // NEW: Handle MESSAGE action
            executeMessageAction(player, actionData, null); // MODIFIED: Pass null holder here
        } else if (action == ClickAction.PLAY_SOUND_TARGET) { // NEW: Handle PLAY_SOUND_TARGET action
            executePlaySoundTargetAction(player, null, actionData); // MODIFIED: Pass null holder here
        } else if (action == ClickAction.TITLE_TARGET) { // NEW: Handle TITLE action
            executeTitleTargetAction(player, null, actionData); // MODIFIED: Pass null holder here
        } else if (action == ClickAction.MESSAGE_TARGET) { // NEW: Handle MESSAGE action
            executeMessageTargetAction(player, null, actionData); // MODIFIED: Pass null holder here
        }


        // Log exit from menu item click handling
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] executeMenuItemAction - END - Action: " + action + ", ActionData: " + Arrays.toString(actionData)); // Debug log - exit
    }


    /**
     * Replaces placeholders in a command string.
     *
     * @param player    The player context for placeholders.
     * @param command   The command string with placeholders.
     * @param holder    The InventoryHolder, providing context for menu-specific placeholders.
     * @return The command string with placeholders replaced.
     */
    private String replacePlaceholders(Player player, String command, InventoryHolder holder) {
        String processedCommand = command.replace("{player}", player.getName());

        OfflinePlayer target = null;
        if (holder instanceof PunishMenu) {
            target = Bukkit.getOfflinePlayer(((PunishMenu) holder).getTargetUUID());
        } else if (holder instanceof PunishDetailsMenu) {
            target = Bukkit.getOfflinePlayer(((PunishDetailsMenu) holder).getTargetUUID());
        } else if (holder instanceof TimeSelectorMenu) {
            target = Bukkit.getOfflinePlayer(((TimeSelectorMenu) holder).getPunishDetailsMenu().getTargetUUID());
        } else if (holder instanceof  HistoryMenu){
            target = Bukkit.getOfflinePlayer(((HistoryMenu) holder).getTargetUUID());
        }

        if (target != null) {
            processedCommand = plugin.getConfigManager().processPlaceholders(processedCommand, target); // Use MainConfigManager's placeholder processing
        }
        return processedCommand;
    }


    private void handlePunishMenuActions(Player player, PunishMenu punishMenu, ClickAction action, String actionData, MenuItem clickedMenuItem) {
        // Log entry to debug PunishMenu action handling, including action, data, and item name
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishMenuActions - START - Action: " + action + ", ActionData: " + actionData + ", Item: " + clickedMenuItem.getName());

        UUID targetUUID = punishMenu.getTargetUUID(); // Get the UUID of the target player for this PunishMenu
        switch (action) {
            case OPEN_MENU:
                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishMenuActions - OPEN_MENU case entered - ActionData: " + actionData); // Debug log when OPEN_MENU action is detected
                if (actionData != null) {
                    // Handle different OPEN_MENU action data to open specific sub-menus
                    if (actionData.equalsIgnoreCase("ban_details")) {
                        if (!player.hasPermission(PUNISH_BAN_PERMISSION)) { // Permission check for ban details menu - NEW
                            sendNoPermissionMenuMessage(player, "ban details");
                            return;
                        }
                        new PunishDetailsMenu(targetUUID, plugin, BAN_PUNISHMENT_TYPE).open(player); // Open Ban details menu
                    } else if (actionData.equalsIgnoreCase("mute_details")) {
                        if (!player.hasPermission(PUNISH_MUTE_PERMISSION)) { // Permission check for mute details menu - NEW
                            sendNoPermissionMenuMessage(player, "mute details");
                            return;
                        }
                        new PunishDetailsMenu(targetUUID, plugin, MUTE_PUNISHMENT_TYPE).open(player); // Open Mute details menu
                    } else if (actionData.equalsIgnoreCase("softban_details")) {
                        if (!player.hasPermission(PUNISH_SOFTBAN_PERMISSION)) { // Permission check for softban details menu - NEW
                            sendNoPermissionMenuMessage(player, "softban details");
                            return;
                        }
                        new PunishDetailsMenu(targetUUID, plugin, SOFTBAN_PUNISHMENT_TYPE).open(player); // Open Softban details menu
                    } else if (actionData.equalsIgnoreCase("kick_details")) {
                        if (!player.hasPermission(PUNISH_KICK_PERMISSION)) { // Permission check for kick details menu - NEW
                            sendNoPermissionMenuMessage(player, "kick details");
                            return;
                        }
                        new PunishDetailsMenu(targetUUID, plugin, KICK_ITEM_MENU_KEY).open(player); // Open Kick details menu
                    } else if (actionData.equalsIgnoreCase("warn_details")) {
                        if (!player.hasPermission(PUNISH_WARN_PERMISSION)) { // Permission check for warn details menu - NEW
                            sendNoPermissionMenuMessage(player, "warn details");
                            return;
                        }
                        new PunishDetailsMenu(targetUUID, plugin, WARN_PUNISHMENT_TYPE).open(player); // Open Warn details menu
                    } else if (actionData.equalsIgnoreCase("freeze_details")) { // Open Freeze details menu - NEW
                        if (!player.hasPermission(PUNISH_FREEZE_PERMISSION)) { // Permission check for freeze details menu - NEW
                            sendNoPermissionMenuMessage(player, "freeze details");
                            return;
                        }
                        new PunishDetailsMenu(targetUUID, plugin, FREEZE_PUNISHMENT_TYPE).open(player); // Open Freeze details menu - NEW
                    } else if (actionData.equalsIgnoreCase("history_menu")) {
                        if (!player.hasPermission(PUNISH_PERMISSION)) { // Basic punish permission needed for history - NEW
                            sendNoPermissionMenuMessage(player, "history menu");
                            return;
                        }
                        new HistoryMenu(targetUUID, plugin).open(player); // Open History menu
                    } else if (actionData.equalsIgnoreCase("punish_menu")) { // Handle reopening of the PunishMenu
                        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishMenuActions - Opening PunishMenu AGAIN - ActionData: punish_menu"); // Specific log for reopen action
                        new PunishMenu(targetUUID, plugin).open(player); // Re-open the PunishMenu
                    } else if (actionData.equalsIgnoreCase("change_target")) {
                        if (!player.hasPermission(PUNISH_PERMISSION)) { // Basic punish permission needed for change target action - NEW
                            sendNoPermissionMenuMessage(player, "change target action");
                            return;
                        }
                        player.closeInventory(); // Close the current inventory
                        requestNewTargetName(player); // Request a new target player name from the player
                    } else {
                        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishMenuActions - OPEN_MENU case - No Matching ActionData: " + actionData); // Log if actionData doesn't match expected values
                    }
                } else {
                    plugin.getLogger().warning("[DEBUG] handlePunishMenuActions - OPEN_MENU case - ActionData is NULL, which is unexpected for OPEN_MENU action."); // Warning log if actionData is unexpectedly null
                }
                break;
            case NO_ACTION:
                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishMenuActions - NO_ACTION case entered"); // Debug log for NO_ACTION case
                break;
            default:
                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishMenuActions - DEFAULT case entered - Action: " + action); // Log for any unhandled action types
                break;
        }
        // Log exit from PunishMenu action handling
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishMenuActions - END - Action: " + action + ", ActionData: " + actionData);
    }

    private void handlePunishDetailsMenuActions(Player player, PunishDetailsMenu punishDetailsMenu, ClickAction action, String actionData, MenuItem clickedMenuItem) { // ADDED clickedMenuItem
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishDetailsMenuActions - Action: " + action + ", ActionData: " + actionData + ", Item: " + clickedMenuItem.getName()); // Log action details
        switch (action) {
            case OPEN_MENU:
                if (actionData != null) { // Null check for actionData
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishDetailsMenuActions - OPEN_MENU ActionData: " + actionData); // <-- DEBUG LOGGING

                    if (actionData.equalsIgnoreCase("time_selector")) {
                        new TimeSelectorMenu(punishDetailsMenu, plugin).open(player);
                    } else if (actionData.equalsIgnoreCase("punish_menu")) {
                        new PunishMenu(punishDetailsMenu.getTargetUUID(), plugin).open(player);
                    } else {
                        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handlePunishDetailsMenuActions - OPEN_MENU - No matching ActionData: " + actionData); // <-- DEBUG LOGGING
                    }
                } else {
                    plugin.getLogger().warning("[DEBUG] handlePunishDetailsMenuActions - OPEN_MENU - ActionData is NULL"); // <-- DEBUG LOGGING
                }
                break;
            case REQUEST_INPUT:
                if (actionData != null && actionData.equalsIgnoreCase("reason_input")) { // Null and value check for actionData
                    requestReasonInput(player, punishDetailsMenu);
                }
                break;
            case CONFIRM_PUNISHMENT:
                handleConfirmButtonClick(player, punishDetailsMenu);
                break;
            case UN_SOFTBAN:
                if (!player.hasPermission(UNPUNISH_SOFTBAN_PERMISSION)) { // Permission check for unsoftban - NEW
                    sendNoPermissionMenuMessage(player, "unsoftban");
                    return;
                }
                handleUnsoftbanButtonClick(player, punishDetailsMenu);
                break;
            case UN_FREEZE: // Handle unfreeze button click - NEW
                if (!player.hasPermission(UNPUNISH_FREEZE_PERMISSION)) { // Permission check for unfreeze - NEW
                    sendNoPermissionMenuMessage(player, "unfreeze");
                    return;
                }
                handleUnfreezeButtonClick(player, punishDetailsMenu); // Call unfreeze handler - NEW
                break;
            case UN_BAN: // Handle unban button click - NEW
                if (!player.hasPermission(UNPUNISH_BAN_PERMISSION)) { // Permission check for unban - NEW
                    sendNoPermissionMenuMessage(player, "unban");
                    return;
                }
                executeUnbanAction(player, punishDetailsMenu);
                break;
            case UN_MUTE: // Handle unmute button click - NEW
                if (!player.hasPermission(UNPUNISH_MUTE_PERMISSION)) { // Permission check for unmute - NEW
                    sendNoPermissionMenuMessage(player, "unmute");
                    return;
                }
                executeUnmuteAction(player, punishDetailsMenu);
                break;
            case NO_ACTION:
            default:
                break;
        }
    }

    /**
     * Sends a message to the player indicating they do not have permission for a menu action. - NEW
     * @param player The player to send the message to.
     * @param actionName The name of the action they do not have permission for.
     */
    private void sendNoPermissionMenuMessage(Player player, String actionName) {
        sendConfigMessage(player, "messages.no_permission_menu_action", "{action}", actionName);
    }


    private void handleTimeSelectorMenuActions(Player player, TimeSelectorMenu timeSelectorMenu, ClickAction action, String actionData, MenuItem clickedMenuItem) { // ADDED clickedMenuItem
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleTimeSelectorMenuActions - Action: " + action + ", ActionData: " + actionData + ", Item: " + clickedMenuItem.getName()); // Logging
        PunishDetailsMenu detailsMenu = timeSelectorMenu.getPunishDetailsMenu();
        switch (action) {
            case ADJUST_TIME:
                if (actionData != null) { // Null check for actionData
                    if (actionData.equalsIgnoreCase("minus_5_min")) {
                        timeSelectorMenu.adjustTime(-300);
                    } else if (actionData.equalsIgnoreCase("minus_2_hour")) {
                        timeSelectorMenu.adjustTime(-7200);
                    } else if (actionData.equalsIgnoreCase("minus_1_day")) {
                        timeSelectorMenu.adjustTime(-86400);
                    } else if (actionData.equalsIgnoreCase("minus_5_day")) {
                        timeSelectorMenu.adjustTime(-432000);
                    } else if (actionData.equalsIgnoreCase("plus_15_min")) {
                        timeSelectorMenu.adjustTime(900);
                    } else if (actionData.equalsIgnoreCase("plus_6_hour")) {
                        timeSelectorMenu.adjustTime(21600);
                    } else if (actionData.equalsIgnoreCase("plus_1_day")) {
                        timeSelectorMenu.adjustTime(86400);
                    } else if (actionData.equalsIgnoreCase("plus_7_day")) {
                        timeSelectorMenu.adjustTime(604800);
                    }
                    timeSelectorMenu.updateTimeDisplayItem(player);
                }
                break;
            case REQUEST_INPUT:
                if (actionData != null && actionData.equalsIgnoreCase("custom_time_input")) { // Null and value check for actionData
                    player.closeInventory();
                    requestCustomTimeInput(player, detailsMenu);
                }
                break;
            case SET_PUNISHMENT_TYPE:
                if (actionData != null) { // Null check for actionData
                    if (actionData.equalsIgnoreCase("permanent_time")) {
                        setPermanentTime(detailsMenu, player);
                    } else if (actionData.equalsIgnoreCase("confirm_time")) {
                        handleTimeDisplayClick(timeSelectorMenu, detailsMenu, player);
                    }
                }
                break;
            case OPEN_MENU:
                if (actionData != null && actionData.equalsIgnoreCase("punish_details")) { // Null and value check for actionData
                    detailsMenu.open(player);
                }
                break;
            case NO_ACTION:
            default:
                break;
        }
    }

    private void handleHistoryMenuActions(Player player, HistoryMenu historyMenu, ClickAction action, String actionData, MenuItem clickedMenuItem) { // ADDED clickedMenuItem
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleHistoryMenuActions - Action: " + action + ", ActionData: " + actionData + ", Item: " + clickedMenuItem.getName()); // Logging
        switch (action) {
            case OPEN_MENU:
                if (actionData != null && actionData.equalsIgnoreCase("punish_menu")) { // Null and value check for actionData
                    new PunishMenu(historyMenu.getTargetUUID(), plugin).open(player);
                }
                break;
            case ADJUST_PAGE:
                if (actionData != null) { // Null check for actionData
                    if (actionData.equalsIgnoreCase("next_page")) {
                        historyMenu.nextPage(player);
                    } else if (actionData.equalsIgnoreCase("previous_page")) {
                        historyMenu.previousPage(player);
                    } else if (actionData.equalsIgnoreCase("no_action")) { // Prevent click action when button is "disabled"
                        // Do nothing, effectively cancelling the click
                    }
                }
                break;
            case NO_ACTION:
            default:
                break;
        }
    }


    /**
     * Handles inventory open events to play menu open sound.
     * @param event The InventoryOpenEvent.
     */
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        Player player = (Player) event.getPlayer();
        if (holder instanceof PunishMenu || holder instanceof PunishDetailsMenu || holder instanceof TimeSelectorMenu || holder instanceof HistoryMenu) {
            // Removed playSound(player, "menu_open"); - Replaced by open_actions
            executeMenuOpenActions(player, holder); // Execute menu open actions - NEW
        }
    }

    /**
     * Requests a new target player name from the player via chat input.
     * @param player The player to request input from.
     */
    private void requestNewTargetName(Player player) {
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.prompt_new_target")));
        setupChatInputTimeout(player, null, "new_target");
    }

    /**
     * Requests a punishment reason from the player via chat input.
     * @param player The player to request input from.
     * @param punishDetailsMenu The PunishDetailsMenu context.
     */
    private void requestReasonInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.prompt_" + punishDetailsMenu.getPunishmentType() + "_reason")));
        pendingDetailsMenus.put(player.getUniqueId(), punishDetailsMenu);
        setupChatInputTimeout(player, punishDetailsMenu, "ban_reason");
    }

    /**
     * Requests a custom time input from the player via chat.
     * @param player The player to request input from.
     * @param punishDetailsMenu The PunishDetailsMenu context.
     */
    private void requestCustomTimeInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.prompt_custom_time")));
        pendingDetailsMenus.put(player.getUniqueId(), punishDetailsMenu);
        setupChatInputTimeout(player, punishDetailsMenu, "custom_time");
    }


    /**
     * Sets up a timeout for chat input, cancelling existing timeouts and storing input data.
     *
     * @param player    The player providing input.
     * @param menu      The PunishDetailsMenu context.
     * @param inputType The type of input requested (reason, time, etc.).
     */
    private void setupChatInputTimeout(Player player, PunishDetailsMenu menu, String inputType) {
        cancelExistingTimeout(player);

        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                handleInputTimeout(player);
            }
        }.runTaskLater(plugin, 400L); // 20 seconds timeout (20 ticks * 20)

        storeInputData(player, timeoutTask, menu, inputType);
    }

    /**
     * Cancels any existing chat input timeout for a player.
     * @param player The player whose timeout should be cancelled.
     */
    private void cancelExistingTimeout(Player player) {
        if (inputTimeouts.containsKey(player.getUniqueId())) {
            inputTimeouts.get(player.getUniqueId()).cancel();
        }
    }

    /**
     * Handles input timeout, informing the player and clearing input data.
     * @param player The player who timed out.
     */
    private void handleInputTimeout(Player player) {
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.input_timeout")));
        clearPlayerInputData(player);
    }

    /**
     * Stores input related data for a player, including timeout task, menu, and input type.
     * @param player The player providing input.
     * @param task The BukkitTask for timeout.
     * @param menu The PunishDetailsMenu context.
     * @param inputType The type of input.
     */
    private void storeInputData(Player player, BukkitTask task, PunishDetailsMenu menu, String inputType) {
        inputTimeouts.put(player.getUniqueId(), task);
        pendingDetailsMenus.put(player.getUniqueId(), menu);
        inputTypes.put(player.getUniqueId(), inputType);
    }


    /**
     * Handles player chat input, processing and validating input based on the current input type.
     *
     * @param event The AsyncPlayerChatEvent.
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!inputTimeouts.containsKey(player.getUniqueId())) return;

        // Check if the sender is frozen - NEW
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            if (!player.hasPermission(ADMIN_PERMISSION)) {
                event.setCancelled(true); // Cancel chat message
                // Send message only to admins - NEW
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission(ADMIN_PERMISSION))
                        .forEach(admin -> admin.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.frozen_player_chat_admin_only", "{player}", player.getName(), "{message}", event.getMessage()))));
                return; // Stop further processing
            } else {
                // Admins can chat even when frozen for monitoring purposes
                plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.frozen_player_chat_admin", "{player}", player.getName(), "{message}", event.getMessage()))));
            }
        }

        event.setCancelled(true);
        handlePlayerInput(player, event.getMessage());
    }

    /**
     * Processes the player's chat input, handling cancellation and valid input scenarios.
     *
     * @param player The player providing input.
     * @param input  The chat input message.
     */
    private void handlePlayerInput(Player player, String input) {
        cancelInputTimeout(player);

        PunishDetailsMenu detailsMenu = pendingDetailsMenus.get(player.getUniqueId()); //Get, not remove. We need to reopen it.
        String inputType = inputTypes.remove(player.getUniqueId()); //Remove input type since input is handled

        if (input.equalsIgnoreCase("cancel")) {
            handleCancelInput(player, detailsMenu);
            clearPlayerInputData(player); // Clear all input data on cancel
            return;
        }

        processValidInput(player, input, detailsMenu, inputType);
        clearPlayerInputData(player); // Clear all input data after processing valid input
    }

    /**
     * Cancels the chat input timeout for a player.
     * @param player The player whose timeout should be cancelled.
     */
    private void cancelInputTimeout(Player player) {
        BukkitTask timeoutTask = inputTimeouts.remove(player.getUniqueId());
        if (timeoutTask != null && !timeoutTask.isCancelled()) timeoutTask.cancel(); // Check if task is not already cancelled
    }

    /**
     * Handles the scenario when player types 'cancel' during input request.
     * @param player The player who cancelled input.
     * @param detailsMenu The PunishDetailsMenu context.
     */
    private void handleCancelInput(Player player, PunishDetailsMenu detailsMenu) {
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.input_cancelled")));
        if (detailsMenu != null) {
            Bukkit.getScheduler().runTask(plugin, () -> detailsMenu.open(player));
        }
    }

    /**
     * Processes valid input based on input type and context.
     * @param player      The player providing input.
     * @param input       The validated input string.
     * @param detailsMenu The PunishDetailsMenu context.
     * @param inputType   The type of input being processed.
     */
    private void processValidInput(Player player, String input, PunishDetailsMenu detailsMenu, String inputType) {
        if (inputType.equals("new_target")) {
            handleNewTargetInput(player, input, detailsMenu);
        } else if (detailsMenu != null) {
            handleMenuSpecificInput(player, input, detailsMenu, inputType);
        }
    }

    /**
     * Handles input specific to the PunishDetailsMenu, such as reason or custom time.
     * @param player      The player providing input.
     * @param input       The validated input string.
     * @param detailsMenu The PunishDetailsMenu context.
     * @param inputType   The type of input (reason, custom_time).
     */
    private void handleMenuSpecificInput(Player player, String input, PunishDetailsMenu detailsMenu, String inputType) {
        if (inputType.equals("ban_reason")) {
            handleReasonInput(player, input, detailsMenu);
        } else if (inputType.equals("custom_time")) {
            handleCustomTimeInput(player, input, detailsMenu);
        }
    }

    /**
     * Handles new target input, opening PunishMenu for the new target.
     * @param player      The player providing input.
     * @param input       The new target player name.
     * @param detailsMenu The PunishDetailsMenu context (can be null).
     */
    private void handleNewTargetInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        OfflinePlayer newTarget = Bukkit.getOfflinePlayer(input);
        if (!newTarget.hasPlayedBefore() && !newTarget.isOnline()) {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.player_never_played", "{input}", input)));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> new PunishMenu(newTarget.getUniqueId(), plugin).open(player));
        }
    }


    /**
     * Handles reason input, setting the reason in PunishDetailsMenu and updating UI.
     * @param player      The player providing input.
     * @param input       The punishment reason.
     * @param detailsMenu The PunishDetailsMenu context.
     */
    private void handleReasonInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        detailsMenu.setBanReason(input);
        detailsMenu.updateSetReasonItem();
        detailsMenu.updateConfirmButtonStatus();
        reopenDetailsMenu(player, detailsMenu);
    }

    /**
     * Handles custom time input, validating format, setting time in PunishDetailsMenu, and updating UI.
     * @param player      The player providing input.
     * @param input       The custom time string.
     * @param detailsMenu The PunishDetailsMenu context.
     */
    private void handleCustomTimeInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        if (isValidTimeFormat(input)) {
            detailsMenu.setBanTime(input); // Corrected: Only set the time input
            detailsMenu.updateSetTimeItem();
            detailsMenu.updateConfirmButtonStatus();
            reopenDetailsMenu(player, detailsMenu);
        } else {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.invalid_time_format")));
            requestCustomTimeInput(player, detailsMenu);
        }
    }

    /**
     * Reopens the PunishDetailsMenu for the player.
     * @param player      The player for whom to reopen the menu.
     * @param detailsMenu The PunishDetailsMenu instance.
     */
    private void reopenDetailsMenu(Player player, PunishDetailsMenu detailsMenu) {
        Bukkit.getScheduler().runTask(plugin, () -> detailsMenu.open(player));
    }

    /**
     * Validates if the input string is a valid time format.
     * @param time The time string to validate.
     * @return true if valid, false otherwise.
     */
    private boolean isValidTimeFormat(String time) {
        String units = String.join("|",
                plugin.getConfigManager().getDayTimeUnit(),
                plugin.getConfigManager().getHoursTimeUnit(),
                plugin.getConfigManager().getMinutesTimeUnit(),
                plugin.getConfigManager().getSecondsTimeUnit()
        );
        return time.matches("\\d+[" + units + "]");
    }


    /**
     * Confirms and executes a dynamic punishment based on the punishment type set in PunishDetailsMenu.
     * @param player          The player confirming the punishment.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void confirmDynamicPunishment(Player player, PunishDetailsMenu punishDetailsMenu) {
        switch (punishDetailsMenu.getPunishmentType().toLowerCase()) {
            case BAN_PUNISHMENT_TYPE:
            case MUTE_PUNISHMENT_TYPE:
                confirmStandardPunishment(player, punishDetailsMenu);
                break;
            case SOFTBAN_PUNISHMENT_TYPE:
                confirmSoftBan(player, punishDetailsMenu);
                break;
            case KICK_PUNISHMENT_TYPE:
                confirmKick(player, punishDetailsMenu);
                break;
            case WARN_PUNISHMENT_TYPE:
                confirmWarn(player, punishDetailsMenu);
                break;
            case FREEZE_PUNISHMENT_TYPE: // Handle Freeze punishment - NEW
                confirmFreeze(player, punishDetailsMenu); // Call confirmFreeze method - NEW
                break;
            default:
                plugin.getLogger().warning("Unknown punishment type: " + punishDetailsMenu.getPunishmentType());
        }
    }

    /**
     * Confirms and executes a standard punishment (ban, mute).
     *
     * @param player      The player confirming the punishment.
     * @param detailsMenu The PunishDetailsMenu instance.
     */
    private void confirmStandardPunishment(Player player, PunishDetailsMenu detailsMenu) {
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String command = getPunishmentCommand(detailsMenu.getPunishmentType());
        String timeInput = detailsMenu.getBanTime();
        String reason = detailsMenu.getBanReason();

        String processedCommand = command
                .replace("{target}", target.getName() != null ? target.getName() : "unknown")
                .replace("{time}", timeInput)
                .replace("{reason}", reason);

        executePunishmentCommand(player, processedCommand, target, detailsMenu);
    }

    /**
     * Gets the punishment command string based on punishment type.
     * @param punishmentType The type of punishment (ban, mute).
     * @return The command string from config.
     */
    private String getPunishmentCommand(String punishmentType) {
        switch (punishmentType.toLowerCase()) {
            case BAN_PUNISHMENT_TYPE:
                return plugin.getConfigManager().getBanCommand();
            case MUTE_PUNISHMENT_TYPE:
                return plugin.getConfigManager().getMuteCommand();
            case KICK_PUNISHMENT_TYPE:
                return plugin.getConfigManager().getKickCommand();
            case WARN_PUNISHMENT_TYPE:
                return plugin.getConfigManager().getWarnCommand();
            case SOFTBAN_PUNISHMENT_TYPE: // Added softban type here
                return plugin.getConfigManager().getSoftBanCommand(); // Although softban is handled internally, added to avoid potential issues
            case FREEZE_PUNISHMENT_TYPE: // Freeze type - NEW
                return ""; // Freeze is handled internally, no external command
            default:
                return "";
        }
    }

    /**
     * Executes the punishment command and sends confirmation messages.
     * @param player      The player executing the punishment.
     * @param command     The command string to execute.
     * @param target      The target player.
     * @param detailsMenu The PunishDetailsMenu instance.
     */
    private void executePunishmentCommand(Player player, String command, OfflinePlayer target, PunishDetailsMenu detailsMenu) {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
        playSound(player, "punish_confirm");
        sendConfirmationMessage(player, target, detailsMenu);
        long punishmentEndTime = 0L;
        String durationString = detailsMenu.getBanTime(); // Store duration string for logging
        if (detailsMenu.getPunishmentType().equalsIgnoreCase("ban") || detailsMenu.getPunishmentType().equalsIgnoreCase("mute")) {
            String timeInput = detailsMenu.getBanTime();
            punishmentEndTime = calculateEndTime(timeInput);

        }
        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), detailsMenu.getPunishmentType(), detailsMenu.getBanReason(), player.getName(), punishmentEndTime, durationString); // Log punishment with endTime and duration string
    }

    /**
     * Sends a confirmation message to the punisher after successful punishment.
     * @param player      The punisher player.
     * @param target      The punished player.
     * @param detailsMenu The PunishDetailsMenu instance.
     */
    private void sendConfirmationMessage(Player player, OfflinePlayer target, PunishDetailsMenu detailsMenu) {
        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.punishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : "unknown",
                "{time}", detailsMenu.getBanTime(),
                "{reason}", detailsMenu.getBanReason(),
                "{punishment_type}", detailsMenu.getPunishmentType())));
    }


    /**
     * Confirms and executes a softban, storing softban data in the database.
     *
     * @param player          The player confirming the softban.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void confirmSoftBan(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason();
        String timeInput = punishDetailsMenu.getBanTime();

        long endTime = calculateEndTime(timeInput);
        plugin.getSoftBanDatabaseManager().softBanPlayer(targetUUID, endTime, reason, player.getName());

        playSound(player, "punish_confirm");
        sendSoftbanConfirmation(player, target, timeInput, reason);
        // Softban is logged inside SoftBanDatabaseManager.softBanPlayer to avoid duplicates.
    }

    /**
     * Confirms and executes a freeze punishment. - NEW
     *
     * @param player          The player confirming the freeze.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void confirmFreeze(Player player, PunishDetailsMenu punishDetailsMenu) {
        plugin.getLogger().info("[DEBUG] confirmFreeze - start"); // Debug log - entry
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason(); // Reason is still collected and can be logged even if not displayed in UI

        // Check if player is already frozen - NEW - Added check here for menu freeze
        if (plugin.getPluginFrozenPlayers().containsKey(target.getUniqueId())) {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.already_frozen", "{target}", target.getName()))); // Send "already_frozen" message - NEW
            return; // Prevent duplicate freeze
        }


        plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true); // Mark player as frozen in plugin's internal list - NEW

        playSound(player, "punish_confirm");
        sendFreezeConfirmation(player, target, reason); // Send freeze confirmation message - NEW
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, FREEZE_PUNISHMENT_TYPE, reason, player.getName(), Long.MAX_VALUE, "permanent"); // Log freeze punishment - NEW

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            sendFreezeReceivedMessage(onlineTarget, reason); // Inform the frozen player - NEW
            plugin.getLogger().info("[DEBUG] confirmFreeze - Calling startFreezeActionsTask for player: " + onlineTarget.getName()); // Debug log - before starting task
            plugin.getFreezeListener().startFreezeActionsTask(onlineTarget); // START FREEZE ACTIONS TASK HERE - NEW - Corrected call to FreezeListener
            plugin.getLogger().info("[DEBUG] confirmFreeze - startFreezeActionsTask called"); // Debug log - after starting task
        }
        plugin.getLogger().info("[DEBUG] confirmFreeze - end"); // Debug log - exit
    }


    /**
     * Confirms and executes a kick command.
     *
     * @param player          The player confirming the kick.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void confirmKick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason();

        String command = plugin.getConfigManager().getKickCommand()
                .replace("{target}", target.getName())
                .replace("{reason}", reason);

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
        playSound(player, "punish_confirm");
        sendKickConfirmation(player, target, reason);
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, KICK_PUNISHMENT_TYPE, reason, player.getName(), 0L, "permanent"); // Log kick with 0L for time and "permanent" duration
    }

    /**
     * Confirms and executes a warn command.
     *
     * @param player          The player confirming the warn.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void confirmWarn(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason();

        String command = plugin.getConfigManager().getWarnCommand()
                .replace("{target}", target.getName())
                .replace("{reason}", reason);

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
        playSound(player, "punish_confirm");
        sendWarnConfirmation(player, target, reason);
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, WARN_PUNISHMENT_TYPE, reason, player.getName(), 0L, "permanent"); // Log warn with 0L for time and "permanent" duration
    }

    /**
     * Calculates the end time in milliseconds based on the time input string.
     * @param timeInput The time input string (e.g., "1d", "permanent").
     * @return End time in milliseconds.
     */
    private long calculateEndTime(String timeInput) {
        if (timeInput.equalsIgnoreCase("Permanent")) {
            return Long.MAX_VALUE;
        }
        int seconds = TimeUtils.parseTime(timeInput, plugin.getConfigManager());
        return System.currentTimeMillis() + (seconds * 1000L);
    }

    /**
     * Sends a softban confirmation message to the punisher.
     * @param player    The punisher player.
     * @param target    The softbanned player.
     * @param timeValue The softban duration string.
     * @param reason    The softban reason.
     */
    private void sendSoftbanConfirmation(Player player, OfflinePlayer target, String timeValue, String reason) {
        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.punishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : "unknown",
                "{time}", timeValue,
                "{reason}", reason,
                "{punishment_type}", SOFTBAN_PUNISHMENT_TYPE)));
    }


    /**
     * Sends a freeze confirmation message to the punisher. - NEW
     * @param player    The punisher player.
     * @param target    The frozen player.
     * @param reason    The freeze reason.
     */
    private void sendFreezeConfirmation(Player player, OfflinePlayer target, String reason) {
        plugin.getLogger().info("[DEBUG] sendFreezeConfirmation - start"); // Debug log - entry
        plugin.getLogger().info("[DEBUG] sendFreezeConfirmation - message path: messages.punishment_confirmed"); // Debug log - message path

        // Handle potentially null reason to prevent NullPointerException
        String actualReason = (reason != null) ? reason : "No reason provided"; // Provide default reason if null
        // Retrieve disconnect ban time from config for confirmation message - NEW
        String disconnectBanTime = plugin.getConfigManager().getPluginConfig().getConfig().getString("freeze.disconnect_ban_time", "permanent"); // Get configured time - NEW

        String message = plugin.getConfigManager().getMessage("messages.punishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : "unknown",
                "{time}", disconnectBanTime, // Use configured disconnectBanTime here - MODIFIED
                "{reason}", actualReason, // Use actualReason here - MODIFIED
                "{punishment_type}", FREEZE_PUNISHMENT_TYPE);

        plugin.getLogger().info("[DEBUG] sendFreezeConfirmation - message retrieved: " + message); // Debug log - message content
        if (message == null) {
            plugin.getLogger().warning("[DEBUG] sendFreezeConfirmation - message is NULL for path: messages.punishment_confirmed"); // Warning log if message is null
        }


        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(message)); // Send processed message

        plugin.getLogger().info("[DEBUG] sendFreezeConfirmation - end"); // Debug log - exit

    }

    /**
     * Sends a freeze received message to the frozen player. - NEW
     * @param player    The frozen player.
     * @param reason    The freeze reason.
     */
    private void sendFreezeReceivedMessage(Player player, String reason) {
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_frozen")));
    }

    /**
     * Sends a kick confirmation message to the punisher.
     * @param player The punisher player.
     * @param target The kicked player.
     * @param reason The kick reason.
     */
    private void sendKickConfirmation(Player player, OfflinePlayer target, String reason) {
        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.direct_kick_confirmed",
                "{target}", target.getName() != null ? target.getName() : "unknown",
                "{reason}", reason)));
    }

    /**
     * Sends a warn confirmation message to the punisher.
     * @param player The punisher player.
     * @param target The warned player.
     * @param reason The warn reason.
     */
    private void sendWarnConfirmation(Player player, OfflinePlayer target, String reason) {
        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.direct_warn_confirmed",
                "{target}", target.getName() != null ? target.getName() : "unknown",
                "{reason}", reason)));
    }


    /**
     * Confirms and executes an unsoftban operation.
     *
     * @param player          The player confirming the unsoftban.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void confirmUnsoftban(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);

        // Log unsoftban action BEFORE unSoftBanPlayer to prevent potential race conditions if logging fails after unbanning
        plugin.getSoftBanDatabaseManager().unSoftBanPlayer(targetUUID, player.getName()); // Passing punisher name

        playSound(player, "punish_confirm");

        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.unsoftban_success",
                "{target}", target.getName() != null ? target.getName() : "unknown")));
    }

    /**
     * Confirms and executes an unfreeze operation. - NEW
     *
     * @param player          The player confirming the unfreeze. - NEW
     * @param punishDetailsMenu The PunishDetailsMenu instance. - NEW
     */
    private void confirmUnfreeze(Player player, PunishDetailsMenu punishDetailsMenu) { // NEW
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);

        plugin.getPluginFrozenPlayers().remove(targetUUID); // Remove player from frozen list - NEW

        playSound(player, "punish_confirm");

        player.closeInventory();
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.unfreeze_success", // You might want to add this message to messages.yml - NEW
                "{target}", target.getName() != null ? target.getName() : "unknown")));
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            sendUnfreezeMessage(onlineTarget); // Inform the player they are unfrozen - NEW
        }
    }

    /**
     * Sends a unfreeze message to the unfreezed player. - NEW
     * @param player    The unfreezed player. - NEW
     */
    private void sendUnfreezeMessage(Player player) { // NEW
        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_unfrozen")));
    }


    /**
     * Handles inventory close events, clearing player input data.
     *
     * @param event The InventoryCloseEvent.
     */
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        Player player = (Player) event.getPlayer();

        if (holder instanceof PunishMenu || holder instanceof PunishDetailsMenu || holder instanceof TimeSelectorMenu || holder instanceof HistoryMenu) {
            clearPlayerInputData(player);
        }
    }

    /**
     * Clears player specific input data, such as timeouts and pending menus.
     * @param player The player whose data should be cleared.
     */
    private void clearPlayerInputData(Player player) {
        cancelInputTimeout(player); // Ensure timeout is cancelled
        inputTimeouts.remove(player.getUniqueId());
        pendingDetailsMenus.remove(player.getUniqueId());
        inputTypes.remove(player.getUniqueId());
    }

    /**
     * Plays a sound for the player based on the sound key from configuration.
     * @param player The player to play the sound for.
     * @param soundKey The sound key from config.
     */
    private void playSound(Player player, String soundKey) {
        try {
            Sound sound = Sound.valueOf(plugin.getConfigManager().getSoundName(soundKey).toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound configured: " + plugin.getConfigManager().getSoundName(soundKey));
        }
    }

    /**
     * Handles new target input, opening PunishMenu for the new target.
     * (Duplicate method - can be removed, already defined above)
     * @param player The player providing input.
     * @param input The new target player name.
     */
    private void handleNewTargetInput(Player player, String input) {
        OfflinePlayer newTarget = Bukkit.getOfflinePlayer(input);
        if (!newTarget.hasPlayedBefore() && !newTarget.isOnline()) {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.player_never_played", "{input}", input)));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> new PunishMenu(newTarget.getUniqueId(), plugin).open(player));
        }
    }


    /**
     * Handles the confirm button click in PunishDetailsMenu, validating input and confirming punishment.
     * @param player          The player clicking the confirm button.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void handleConfirmButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        if (punishDetailsMenu.isTimeSet() || (!punishDetailsMenu.isTimeRequired())) { // Time is set OR time is not required (for kick/warn/freeze) - MODIFIED: Added freeze
            if (punishDetailsMenu.isReasonSet() || !punishDetailsMenu.isReasonRequiredForConfirmation()) { // MODIFIED: Reason is optional for freeze now, added isReasonRequiredForConfirmation check
                confirmDynamicPunishment(player, punishDetailsMenu);
            } else {
                sendValidationMessages(player, punishDetailsMenu);
            }
        } else {
            sendValidationMessages(player, punishDetailsMenu);
        }
    }

    /**
     * Handles the unsoftban button click, confirming and executing unsoftban if player is softbanned.
     * @param player          The player clicking the unsoftban button.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void handleUnsoftbanButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getSoftBanDatabaseManager().isSoftBanned(targetUUID)) {
            confirmUnsoftban(player, punishDetailsMenu);
        } else {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.no_active_softban")));
        }
    }

    /**
     * Handles the unfreeze button click, confirming and executing unfreeze if player is frozen. - NEW
     * @param player          The player clicking the unfreeze button. - NEW
     * @param punishDetailsMenu The PunishDetailsMenu instance. - NEW
     */
    private void handleUnfreezeButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) { // NEW
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getPluginFrozenPlayers().containsKey(targetUUID)) { // Check if player is frozen - NEW
            confirmUnfreeze(player, punishDetailsMenu);
        } else {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.no_active_freeze"))); // Message if player is not frozen - NEW
        }
    }


    /**
     * Sets permanent time in PunishDetailsMenu and updates UI.
     * @param detailsMenu The PunishDetailsMenu instance.
     * @param player      The player setting permanent time.
     */
    private void setPermanentTime(PunishDetailsMenu detailsMenu, Player player) {
        detailsMenu.setBanTime("Permanent");
        detailsMenu.updateSetTimeItem();
        detailsMenu.updateConfirmButtonStatus();
        detailsMenu.open(player);
    }

    /**
     * Handles the time display click in TimeSelectorMenu, confirming selected time and returning to PunishDetailsMenu.
     * @param menu        The TimeSelectorMenu instance.
     * @param detailsMenu   The PunishDetailsMenu instance.
     * @param player      The player confirming time.
     */
    private void handleTimeDisplayClick(TimeSelectorMenu menu, PunishDetailsMenu detailsMenu, Player player) {
        if (menu.getCurrentTimeSeconds() > 0) {
            String formattedTime = menu.getFormattedTime();
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "Time Selected from TimeSelector: " + formattedTime); // Log selected time
            }
            detailsMenu.setBanTime(formattedTime);
            detailsMenu.updateSetTimeItem();
            detailsMenu.updateConfirmButtonStatus();
            detailsMenu.open(player);
        } else {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set_valid_time_confirm")));
        }
    }

    /**
     * Sends a message from the configuration to the command sender, with optional replacements.
     * @param sender Command sender.
     * @param path Path to the message in messages.yml.
     * @param replacements Placeholders to replace in the message.
     */
    private void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        MessageUtils.sendConfigMessage(plugin, sender, path, replacements);
    }

    /**
     * Sends validation messages to the player if time or reason are not set before confirming punishment.
     * @param player          The player needing validation messages.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void sendValidationMessages(Player player, PunishDetailsMenu punishDetailsMenu) {
        if (!punishDetailsMenu.isTimeSet() && !punishDetailsMenu.isReasonSet() && punishDetailsMenu.isTimeRequired()) { // Check if time is required
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set_time_reason_before_confirm")));
        } else if (!punishDetailsMenu.isTimeSet() && punishDetailsMenu.isTimeRequired()) { // Check if time is required
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set_time_before_confirm")));
        } else if (!punishDetailsMenu.isReasonSet() && punishDetailsMenu.isReasonRequiredForConfirmation()) { // Check if reason is required - MODIFIED: Added reason required check
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set_reason_before_confirm")));
        }
    }
}
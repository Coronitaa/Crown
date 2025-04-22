package cp.corona.listeners;

import cp.corona.config.MainConfigManager;
import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.HistoryMenu;
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.PunishMenu;
import cp.corona.menus.TimeSelectorMenu;
import cp.corona.menus.actions.ClickAction;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.ColorUtils;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material; // Needed for Material.AIR check
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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
import org.bukkit.inventory.meta.ItemMeta; // Needed for potential meta manipulation
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Menu interaction listener for CrownPunishments plugin.
 * Handles inventory clicks, chat input, and menu actions.
 * Implements various click actions including commands, sounds, titles, messages,
 * action bars, and target/moderator specific variations.
 * Follows good practices and aims for SOLID principles where applicable.
 *
 * **MODIFIED:** Added handlers for ACTIONBAR, ACTIONBAR_TARGET, ACTIONBAR_MODS,
 *             MESSAGE_MODS, TITLE_MODS, PLAY_SOUND_MODS actions.
 *             ActionBar implementation uses standard API (no duration/fade control).
 */
public class MenuListener implements Listener {
    private final CrownPunishments plugin;
    // Input tracking maps
    private final HashMap<UUID, BukkitTask> inputTimeouts = new HashMap<>();
    private final HashMap<UUID, PunishDetailsMenu> pendingDetailsMenus = new HashMap<>();
    private final HashMap<UUID, String> inputTypes = new HashMap<>();

    // Constants for punishment types (consistency)
    private static final String BAN_PUNISHMENT_TYPE = "ban";
    private static final String MUTE_PUNISHMENT_TYPE = "mute";
    private static final String SOFTBAN_PUNISHMENT_TYPE = "softban";
    private static final String KICK_PUNISHMENT_TYPE = "kick";
    private static final String WARN_PUNISHMENT_TYPE = "warn";
    private static final String FREEZE_PUNISHMENT_TYPE = "freeze";

    // Permission constants
    private static final String MOD_PERMISSION = "crown.mod";
    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final String USE_PERMISSION = "crown.use";
    private static final String PUNISH_BAN_PERMISSION = "crown.punish.ban";
    private static final String UNPUNISH_BAN_PERMISSION = "crown.unpunish.ban";
    private static final String PUNISH_MUTE_PERMISSION = "crown.punish.mute";
    private static final String UNPUNISH_MUTE_PERMISSION = "crown.unpunish.mute";
    private static final String PUNISH_SOFTBAN_PERMISSION = "crown.punish.softban";
    private static final String UNPUNISH_SOFTBAN_PERMISSION = "crown.unpunish.softban";
    private static final String UNPUNISH_WARN_PERMISSION = "crown.unpunish.warn";
    private static final String PUNISH_KICK_PERMISSION = "crown.punish.kick";
    private static final String PUNISH_WARN_PERMISSION = "crown.punish.warn";
    private static final String PUNISH_FREEZE_PERMISSION = "crown.punish.freeze";
    private static final String UNPUNISH_FREEZE_PERMISSION = "crown.unpunish.freeze";

    /**
     * Constructor for MenuListener.
     * @param plugin Instance of the main plugin class.
     */
    public MenuListener(CrownPunishments plugin) {
        this.plugin = plugin;
    }

    // ========================================================================
    // Event Handlers
    // ========================================================================

    /**
     * Handles inventory click events within plugin menus.
     * Identifies the clicked item and associated actions, then dispatches handling.
     *
     * @param event The InventoryClickEvent.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        // Early exit if the holder isn't one of our plugin menus
        if (!(holder instanceof PunishMenu) && !(holder instanceof PunishDetailsMenu) &&
                !(holder instanceof TimeSelectorMenu) && !(holder instanceof HistoryMenu)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return; // Should not happen, but safeguard
        }

        // Prevent interaction with the player's inventory while a plugin menu is open
        if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true); // Prevent taking items from player inv while menu open
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        // Ignore clicks on empty slots within the menu GUI
        if (event.getClickedInventory() == null || clickedItem == null || clickedItem.getType() == Material.AIR) {
            event.setCancelled(true); // Cancel clicks on empty slots in the GUI
            return;
        }

        // Click is confirmed within the plugin GUI top inventory
        event.setCancelled(true); // Prevent item moving

        MenuItem clickedMenuItem = getMenuItemClicked(event.getRawSlot(), holder);
        if (clickedMenuItem != null) {
            clickedMenuItem.playClickSound(player); // Play click sound if configured

            List<MenuItem.ClickActionData> actionsToExecute = Collections.emptyList();
            if (event.isLeftClick()) {
                actionsToExecute = clickedMenuItem.getLeftClickActions();
            } else if (event.isRightClick()) {
                actionsToExecute = clickedMenuItem.getRightClickActions();
            }

            if (!actionsToExecute.isEmpty()) {
                for (MenuItem.ClickActionData actionData : actionsToExecute) {
                    // Pass event for potential future use, though currently not used in handleMenuItemClick
                    handleMenuItemClick(player, holder, actionData.getAction(), actionData.getActionData(), event, clickedMenuItem);
                }
            }
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Clicked on slot " + event.getRawSlot() + " in " + holder.getClass().getSimpleName() + " with no associated MenuItem found.");
        }
    }

    /**
     * Handles inventory open events for plugin menus.
     * Executes configured 'open_actions'.
     * This is the SINGLE place where open actions should be triggered.
     *
     * @param event The InventoryOpenEvent.
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (event.getPlayer() instanceof Player player) {
            // Check if the opened inventory belongs to one of the plugin's menus
            if (holder instanceof PunishMenu || holder instanceof PunishDetailsMenu || holder instanceof TimeSelectorMenu || holder instanceof HistoryMenu) {
                // Execute open actions ONLY when the inventory actually opens
                executeMenuOpenActions(player, holder);
            }
        }
    }

    /**
     * Handles inventory close events for plugin menus.
     * Cleans up any pending input requests for the player.
     *
     * @param event The InventoryCloseEvent.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (event.getPlayer() instanceof Player player) {
            // Check if the closed inventory belonged to one of the plugin's menus
            if (holder instanceof PunishMenu || holder instanceof PunishDetailsMenu || holder instanceof TimeSelectorMenu || holder instanceof HistoryMenu) {

                if (plugin.getConfigManager().isDebugEnabled()) {
                    // Optional log to show the event fired, but emphasize it's not clearing input state.
                    // plugin.getLogger().info("[DEBUG] InventoryCloseEvent fired for " + player.getName() + " - Menu: " + holder.getClass().getSimpleName() + ". Input state NOT cleared here.");
                }
            }
        }
    }

    /**
     * Handles asynchronous player chat events, capturing input if expected.
     *
     * @param event The AsyncPlayerChatEvent.
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        // Ignore if the plugin is not waiting for input from this player
        if (!inputTimeouts.containsKey(player.getUniqueId())) {
            return;
        }

        // Prevent frozen players (non-mods) from submitting input
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId()) && !player.hasPermission(MOD_PERMISSION)) {
            event.setCancelled(true);
            sendConfigMessage(player, "messages.freeze_command_blocked"); // Inform them they can't chat/input
            return;
        }

        event.setCancelled(true); // Prevent the message from appearing in global chat

        // Process the input synchronously on the main thread
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handlePlayerInput(player, message));
    }

    // ========================================================================
    // Core Action Dispatching
    // ========================================================================

    /**
     * Central dispatcher for handling menu item clicks and executing associated actions.
     * Processes placeholders relevant to the clicking player ({player}) and delegates execution
     * first to menu-specific handlers, then to common action handlers.
     *
     * @param player The player who clicked.
     * @param holder The InventoryHolder representing the menu.
     * @param action The ClickAction enum value representing the action to take.
     * @param actionData The data associated with the ClickAction.
     * @param event The InventoryClickEvent (can be null if called programmatically).
     * @param clickedMenuItem The MenuItem that was clicked (can be null if called programmatically).
     */
    private void handleMenuItemClick(Player player, InventoryHolder holder, ClickAction action, String[] actionData, InventoryClickEvent event, MenuItem clickedMenuItem) {
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - START - Action: " + action + ", ActionData: " + Arrays.toString(actionData) + ", Item: " + (clickedMenuItem != null ? clickedMenuItem.getName() : "null") + ", Holder Type: " + holder.getClass().getSimpleName());

        // 1. Process {player} placeholders using the clicking player's context
        String[] processedActionData = actionData;
        if (actionData != null && actionData.length > 0) {
            processedActionData = Arrays.stream(actionData)
                    .filter(Objects::nonNull)
                    .map(data -> replacePlaceholders(player, data, holder)) // Primarily for {player}
                    .toArray(String[]::new);
        }

        // 2. Delegate to Menu-Specific Handlers FIRST
        boolean handledByMenuSpecific = false;
        if (holder instanceof PunishMenu punishMenu) {
            handledByMenuSpecific = handlePunishMenuActions(player, punishMenu, action, processedActionData, clickedMenuItem);
        } else if (holder instanceof PunishDetailsMenu punishDetailsMenu) {
            handledByMenuSpecific = handlePunishDetailsMenuActions(player, punishDetailsMenu, action, processedActionData, clickedMenuItem);
        } else if (holder instanceof TimeSelectorMenu timeSelectorMenu) {
            handledByMenuSpecific = handleTimeSelectorMenuActions(player, timeSelectorMenu, action, processedActionData, clickedMenuItem);
        } else if (holder instanceof HistoryMenu historyMenu) {
            handledByMenuSpecific = handleHistoryMenuActions(player, historyMenu, action, processedActionData, clickedMenuItem);
        }

        // 3. Handle Common Actions (if not already handled by specific menu logic)
        if (!handledByMenuSpecific) {
            switch (action) {
                // Command Actions (Processes {target} internally)
                case CONSOLE_COMMAND: executeConsoleCommand(player, processedActionData, holder); break;
                case PLAYER_COMMAND:
                case PLAYER_COMMAND_OP: executeCommandAction(player, action, processedActionData, holder); break;

                // Menu Interaction
                case CLOSE_MENU: player.closeInventory(); break;

                // Player Feedback
                case PLAY_SOUND: executePlaySoundAction(player, processedActionData); break;
                case TITLE: executeTitleAction(player, processedActionData); break;
                case MESSAGE: executeMessageAction(player, processedActionData, holder); break;
                case ACTIONBAR: executeActionbarAction(player, processedActionData); break;

                // Target Feedback (Processes {target} internally)
                case PLAY_SOUND_TARGET: executePlaySoundTargetAction(player, holder, processedActionData); break;
                case TITLE_TARGET: executeTitleTargetAction(player, holder, processedActionData); break;
                case MESSAGE_TARGET: executeMessageTargetAction(player, holder, processedActionData); break;
                case ACTIONBAR_TARGET: executeActionbarTargetAction(player, holder, processedActionData); break;
                case GIVE_EFFECT_TARGET: executeGiveEffectTargetAction(player, holder, actionData); break; // Original data for effects

                // Moderator Feedback (Processes {player}/{target} internally)
                case PLAY_SOUND_MODS: executePlaySoundModsAction(player, holder, processedActionData); break;
                case TITLE_MODS: executeTitleModsAction(player, holder, processedActionData); break;
                case MESSAGE_MODS: executeMessageModsAction(player, holder, processedActionData); break;
                case ACTIONBAR_MODS: executeActionbarModsAction(player, holder, processedActionData); break;

                // NO_ACTION or actions handled purely by menu-specific logic (fall through)
                case NO_ACTION:
                case OPEN_MENU:
                case REQUEST_INPUT:
                case SET_PUNISHMENT_TYPE:
                case ADJUST_TIME:
                case ADJUST_PAGE:
                case CONFIRM_PUNISHMENT:
                case UN_SOFTBAN:
                case UN_FREEZE:
                case UN_BAN:
                case UN_MUTE:
                case UN_WARN:
                default:
                    // If debug is enabled and action wasn't NO_ACTION, log it might be unhandled
                    if (plugin.getConfigManager().isDebugEnabled() && action != ClickAction.NO_ACTION) {
                        plugin.getLogger().info("[DEBUG] Action " + action + " was not handled by common handlers (expected if menu-specific).");
                    }
                    break;
            }
        }

        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - END - Action: " + action + ", ActionData: " + Arrays.toString(actionData));
    }

    /**
     * Programmatically executes a ClickAction for a player, typically used by background tasks (e.g., FreezeListener).
     * Processes {player} placeholders but cannot process {target} or execute TARGET/MODS actions reliably due to lack of menu context.
     *
     * @param player     The player context.
     * @param action     The action to execute.
     * @param actionData The arguments for the action.
     */
    public void executeMenuItemAction(Player player, ClickAction action, String[] actionData) {
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] executeMenuItemAction - START - Player: " + player.getName() + ", Action: " + action + ", ActionData: " + Arrays.toString(actionData));

        // Process {player} placeholders using the player's context
        String[] processedActionData = actionData;
        if (actionData != null && actionData.length > 0) {
            processedActionData = Arrays.stream(actionData)
                    .filter(Objects::nonNull)
                    .map(data -> replacePlaceholders(player, data, null)) // Process {player}, no holder context
                    .toArray(String[]::new);
        }

        // Execute actions that only require the 'player' context
        switch (action) {
            // Commands
            case CONSOLE_COMMAND: executeConsoleCommand(player, processedActionData, null); break;
            case PLAYER_COMMAND:
            case PLAYER_COMMAND_OP: executeCommandAction(player, action, processedActionData, null); break;
            // Player Feedback
            case PLAY_SOUND: executePlaySoundAction(player, processedActionData); break;
            case TITLE: executeTitleAction(player, processedActionData); break;
            case MESSAGE: executeMessageAction(player, processedActionData, null); break;
            case ACTIONBAR: executeActionbarAction(player, processedActionData); break;
            // Unsupported actions without menu context
            case PLAY_SOUND_TARGET: case TITLE_TARGET: case MESSAGE_TARGET: case ACTIONBAR_TARGET:
            case GIVE_EFFECT_TARGET: case PLAY_SOUND_MODS: case TITLE_MODS: case MESSAGE_MODS:
            case ACTIONBAR_MODS: case OPEN_MENU: case REQUEST_INPUT:
                // ... other context-dependent actions ...
                plugin.getLogger().warning("[WARNING] executeMenuItemAction called with context-dependent action ("+action+") without inventory context. Action skipped for player " + player.getName() + ".");
                break;
            case NO_ACTION: default: break; // Ignore NO_ACTION or unhandled actions
        }

        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] executeMenuItemAction - END - Player: " + player.getName() + ", Action: " + action);
    }

    /**
     * Executes menu open actions configured for the given menu.
     * @param player The player opening the menu.
     * @param holder The InventoryHolder representing the menu.
     */
    public void executeMenuOpenActions(Player player, InventoryHolder holder) {
        List<MenuItem.ClickActionData> openActions;
        FileConfiguration config = null;
        String path = null;

        // Determine config and path based on holder type
        if (holder instanceof PunishMenu) {
            config = plugin.getConfigManager().getPunishMenuConfig().getConfig(); path = "menu";
        } else if (holder instanceof PunishDetailsMenu detailsMenu) {
            config = plugin.getConfigManager().getPunishDetailsMenuConfig().getConfig(); path = "menu.punish_details." + detailsMenu.getPunishmentType();
        } else if (holder instanceof TimeSelectorMenu) {
            config = plugin.getConfigManager().getTimeSelectorMenuConfig().getConfig(); path = "menu";
        } else if (holder instanceof HistoryMenu) {
            config = plugin.getConfigManager().getHistoryMenuConfig().getConfig(); path = "menu";
        }

        // Load and execute actions if config and path are valid
        if (config != null && path != null) {
            openActions = plugin.getConfigManager().loadMenuOpenActions(config, path);
            if (!openActions.isEmpty() && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Executing " + openActions.size() + " open actions for " + holder.getClass().getSimpleName());
            }
            for (MenuItem.ClickActionData actionData : openActions) {
                // Use executeMenuItemAction to run actions in the player's context
                // Note: TARGET/MODS actions won't work correctly here due to lack of holder context.
                executeMenuItemAction(player, actionData.getAction(), actionData.getActionData());
            }
        } else if (plugin.getConfigManager().isDebugEnabled()){
            plugin.getLogger().info("[DEBUG] No valid config/path found for open actions for " + holder.getClass().getSimpleName());
        }
    }

    // ========================================================================
    // Action Execution Implementations
    // ========================================================================

    // --- Player Context Actions ---

    private void executePlaySoundAction(Player player, String[] soundArgs) {
        if (soundArgs == null || soundArgs.length < 1 || soundArgs[0] == null || soundArgs[0].isEmpty()) {
            plugin.getLogger().warning("PLAY_SOUND action requires at least a non-empty sound name."); return;
        }
        try {
            Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
            float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f;
            float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;
            player.playSound(player.getLocation(), sound, volume, pitch);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND played '" + sound.name() + "' for " + player.getName());
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND: " + Arrays.toString(soundArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND: " + soundArgs[0]); }
    }

    private void executeTitleAction(Player player, String[] titleArgs) {
        if (titleArgs == null || titleArgs.length < 3 || titleArgs[0] == null || titleArgs[1] == null || titleArgs[2] == null) {
            plugin.getLogger().warning("TITLE action requires at least non-null title, subtitle, and time_seconds arguments."); return;
        }
        String titleText = MessageUtils.getColorMessage(titleArgs[0]);
        String subtitleText = MessageUtils.getColorMessage(titleArgs[1]);
        try {
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            int fadeInTicks = titleArgs.length > 3 && titleArgs[3] != null ? Integer.parseInt(titleArgs[3]) : 10;
            int fadeOutTicks = titleArgs.length > 4 && titleArgs[4] != null ? Integer.parseInt(titleArgs[4]) : 20;
            player.sendTitle(titleText, subtitleText, fadeInTicks, timeSeconds * 20, fadeOutTicks);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE sent to " + player.getName() + ": " + titleText);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid number format for time/fade values in TITLE action: " + Arrays.toString(titleArgs)); }
    }

    private void executeMessageAction(Player player, String[] messageArgs, InventoryHolder holder) {
        if (messageArgs == null || messageArgs.length < 1 || messageArgs[0] == null) {
            plugin.getLogger().warning("MESSAGE action requires a non-null message text argument."); return;
        }
        // Process {target} here if holder is present, otherwise it uses already processed text
        OfflinePlayer target = getTargetForAction(holder);
        String messageText = plugin.getConfigManager().processPlaceholders(messageArgs[0], target); // Process {target} if possible
        messageText = MessageUtils.getColorMessage(messageText); // Colorize final message
        player.sendMessage(messageText);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE sent to " + player.getName() + ": " + messageText);
    }

    private void executeActionbarAction(Player player, String[] messageArgs) {
        if (messageArgs == null || messageArgs.length < 1 || messageArgs[0] == null) {
            plugin.getLogger().warning("ACTIONBAR action requires a non-null message text argument."); return;
        }
        String messageText = MessageUtils.getColorMessage(messageArgs[0]); // Colorize
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(messageText));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR sent to " + player.getName() + ": " + messageText);
    }


    // --- Target Context Actions ---

    private void executePlaySoundTargetAction(Player player, InventoryHolder holder, String[] soundArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (soundArgs == null || soundArgs.length < 1 || soundArgs[0] == null || soundArgs[0].isEmpty()) { plugin.getLogger().warning("PLAY_SOUND_TARGET action requires at least a non-empty sound name."); return; }
        try {
            Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
            float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f;
            float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;
            targetPlayer.playSound(targetPlayer.getLocation(), sound, volume, pitch);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_TARGET played '" + sound.name() + "' for " + targetPlayer.getName());
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND_TARGET: " + Arrays.toString(soundArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND_TARGET: " + soundArgs[0]); }
    }

    private void executeTitleTargetAction(Player player, InventoryHolder holder, String[] titleArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (titleArgs == null || titleArgs.length < 3 || titleArgs[0] == null || titleArgs[1] == null || titleArgs[2] == null) { plugin.getLogger().warning("TITLE_TARGET action requires at least non-null title, subtitle, and time_seconds arguments."); return; }

        String titleText = plugin.getConfigManager().processPlaceholders(titleArgs[0], target); // Process {target}
        titleText = MessageUtils.getColorMessage(titleText);
        String subtitleText = plugin.getConfigManager().processPlaceholders(titleArgs[1], target); // Process {target}
        subtitleText = MessageUtils.getColorMessage(subtitleText);
        try {
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            int fadeInTicks = titleArgs.length > 3 && titleArgs[3] != null ? Integer.parseInt(titleArgs[3]) : 10;
            int fadeOutTicks = titleArgs.length > 4 && titleArgs[4] != null ? Integer.parseInt(titleArgs[4]) : 20;
            targetPlayer.sendTitle(titleText, subtitleText, fadeInTicks, timeSeconds * 20, fadeOutTicks);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_TARGET sent to " + targetPlayer.getName() + ": " + titleText);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid number format for time/fade values in TITLE_TARGET action: " + Arrays.toString(titleArgs)); }
    }

    private void executeMessageTargetAction(Player player, InventoryHolder holder, String[] messageArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (messageArgs == null || messageArgs.length < 1 || messageArgs[0] == null) { plugin.getLogger().warning("MESSAGE_TARGET action requires a non-null message text argument."); return; }
        String messageText = plugin.getConfigManager().processPlaceholders(messageArgs[0], target); // Process {target}
        messageText = MessageUtils.getColorMessage(messageText); // Colorize
        targetPlayer.sendMessage(messageText);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_TARGET sent to " + targetPlayer.getName() + ": " + messageText);
    }

    private void executeActionbarTargetAction(Player player, InventoryHolder holder, String[] messageArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (messageArgs == null || messageArgs.length < 1 || messageArgs[0] == null) { plugin.getLogger().warning("ACTIONBAR_TARGET action requires a non-null message text argument."); return; }
        String messageText = plugin.getConfigManager().processPlaceholders(messageArgs[0], target); // Process {target}
        messageText = MessageUtils.getColorMessage(messageText); // Colorize
        targetPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(messageText));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_TARGET sent to " + targetPlayer.getName() + ": " + messageText);
    }

    private void executeGiveEffectTargetAction(Player player, InventoryHolder holder, String[] effectArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] GIVE_EFFECT_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (effectArgs == null || effectArgs.length < 3 || effectArgs[0] == null || effectArgs[1] == null || effectArgs[2] == null) { plugin.getLogger().warning("GIVE_EFFECT_TARGET action requires at least effect_type, duration_seconds, and amplifier arguments."); return; }
        try {
            NamespacedKey effectKey = NamespacedKey.minecraft(effectArgs[0].toLowerCase()); PotionEffectType effectType = PotionEffectType.getByKey(effectKey);
            if (effectType == null) effectType = PotionEffectType.getByName(effectArgs[0].toUpperCase()); // Fallback
            if (effectType == null) { plugin.getLogger().warning("Invalid PotionEffectType configured: " + effectArgs[0] + " for GIVE_EFFECT_TARGET action."); return; }
            int durationSeconds = Integer.parseInt(effectArgs[1]); int amplifier = Integer.parseInt(effectArgs[2]);
            boolean particles = effectArgs.length <= 3 || effectArgs[3] == null || Boolean.parseBoolean(effectArgs[3]);
            boolean icon = particles; boolean ambient = false;
            PotionEffect effect = new PotionEffect(effectType, durationSeconds * 20, amplifier, ambient, particles, icon);
            targetPlayer.addPotionEffect(effect);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] GIVE_EFFECT_TARGET action executed for player: " + targetPlayer.getName() + ", effect: " + effectType.getKey() + ", duration: " + durationSeconds + "s, amplifier: " + amplifier);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid duration or amplifier format for GIVE_EFFECT_TARGET action: " + Arrays.toString(effectArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("IllegalArgumentException in GIVE_EFFECT_TARGET action: " + e.getMessage() + ", Args: " + Arrays.toString(effectArgs)); }
    }


    // --- Moderator Context Actions ---

    private void executePlaySoundModsAction(Player player, InventoryHolder holder, String[] soundArgs) {
        if (soundArgs == null || soundArgs.length < 1 || soundArgs[0] == null || soundArgs[0].isEmpty()) { plugin.getLogger().warning("PLAY_SOUND_MODS action requires at least a non-empty sound name."); return; }
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_MODS skipped: No online mods."); return; }
        try {
            Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
            float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f;
            float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;
            mods.forEach(mod -> mod.playSound(mod.getLocation(), sound, volume, pitch));
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_MODS played sound '" + sound.name() + "' for " + mods.size() + " mods.");
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND_MODS: " + Arrays.toString(soundArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND_MODS: " + soundArgs[0]); }
    }

    private void executeTitleModsAction(Player player, InventoryHolder holder, String[] titleArgs) {
        if (titleArgs == null || titleArgs.length < 3 || titleArgs[0] == null || titleArgs[1] == null || titleArgs[2] == null) { plugin.getLogger().warning("TITLE_MODS action requires at least non-null title, subtitle, and time_seconds arguments."); return; }
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_MODS skipped: No online mods."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String titleText = plugin.getConfigManager().processPlaceholders(titleArgs[0], target); titleText = replacePlaceholders(player, titleText, holder); titleText = MessageUtils.getColorMessage(titleText);
        String subtitleText = plugin.getConfigManager().processPlaceholders(titleArgs[1], target); subtitleText = replacePlaceholders(player, subtitleText, holder); subtitleText = MessageUtils.getColorMessage(subtitleText);
        try {
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            int fadeInTicks = titleArgs.length > 3 && titleArgs[3] != null ? Integer.parseInt(titleArgs[3]) : 10;
            int fadeOutTicks = titleArgs.length > 4 && titleArgs[4] != null ? Integer.parseInt(titleArgs[4]) : 20;
            final String finalTitle = titleText; final String finalSubtitle = subtitleText;
            mods.forEach(mod -> mod.sendTitle(finalTitle, finalSubtitle, fadeInTicks, timeSeconds * 20, fadeOutTicks));
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_MODS sent to " + mods.size() + " mods. Title: " + finalTitle);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid number format for time/fade values in TITLE_MODS action: " + Arrays.toString(titleArgs)); }
    }

    private void executeMessageModsAction(Player player, InventoryHolder holder, String[] messageArgs) {
        if (messageArgs == null || messageArgs.length < 1 || messageArgs[0] == null) { plugin.getLogger().warning("MESSAGE_MODS action requires a non-null message text argument."); return; }
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_MODS skipped: No online mods."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String baseMessage = plugin.getConfigManager().processPlaceholders(messageArgs[0], target); // Process {target}
        baseMessage = replacePlaceholders(player, baseMessage, holder); // Process {player} (initiator)
        baseMessage = MessageUtils.getColorMessage(baseMessage); // Colorize
        final String finalMessage = baseMessage;
        mods.forEach(mod -> mod.sendMessage(finalMessage));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_MODS sent to " + mods.size() + " mods: " + finalMessage);
    }

    private void executeActionbarModsAction(Player player, InventoryHolder holder, String[] messageArgs) {
        if (messageArgs == null || messageArgs.length < 1 || messageArgs[0] == null) { plugin.getLogger().warning("ACTIONBAR_MODS action requires a non-null message text argument."); return; }
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_MODS skipped: No online mods."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String baseMessage = plugin.getConfigManager().processPlaceholders(messageArgs[0], target); // Process {target}
        baseMessage = replacePlaceholders(player, baseMessage, holder); // Process {player} (initiator)
        baseMessage = MessageUtils.getColorMessage(baseMessage); // Colorize
        final String finalMessage = baseMessage;
        mods.forEach(mod -> mod.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(finalMessage)));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_MODS sent to " + mods.size() + " mods: " + finalMessage);
    }


    // --- Command Execution ---

    private void executeCommandAction(Player player, ClickAction action, String[] commandData, InventoryHolder holder) {
        if (commandData == null || commandData.length < 1 || commandData[0] == null || commandData[0].isEmpty()) { plugin.getLogger().warning("Invalid COMMAND action data: Command string is missing or empty."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String commandToExecute = plugin.getConfigManager().processPlaceholders(commandData[0], target); // Process {target}
        commandToExecute = ColorUtils.translateRGBColors(commandToExecute); // Colorize
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing COMMAND: " + action + " Command: " + commandToExecute);
        final String finalCommand = commandToExecute;
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (action) {
                case CONSOLE_COMMAND: Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand); break;
                case PLAYER_COMMAND: player.performCommand(finalCommand); break;
                case PLAYER_COMMAND_OP:
                    boolean wasOp = player.isOp();
                    try { player.setOp(true); player.performCommand(finalCommand); }
                    catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Error executing OP command '" + finalCommand + "' for player " + player.getName(), e); }
                    finally { if (!wasOp) player.setOp(false); } // Revert OP status
                    break;
                default: plugin.getLogger().warning("executeCommandAction called with non-command action: " + action); break;
            }
        });
    }

    private void executeConsoleCommand(Player player, String[] commandData, InventoryHolder holder) {
        if (commandData == null || commandData.length < 1 || commandData[0] == null || commandData[0].isEmpty()) { plugin.getLogger().warning("Invalid CONSOLE_COMMAND action data: Command string is missing or empty."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String commandToExecute = plugin.getConfigManager().processPlaceholders(commandData[0], target); // Process {target}
        commandToExecute = ColorUtils.translateRGBColors(commandToExecute);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing CONSOLE_COMMAND: " + commandToExecute);
        final String finalCommand = commandToExecute;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }


    /**
     * Handles actions specific to PunishMenu clicks.
     * Now handles change_target via REQUEST_INPUT.
     *
     * @param player          The player who clicked.
     * @param punishMenu      The PunishMenu instance.
     * @param action          The ClickAction enum value.
     * @param actionData      The data associated with the ClickAction.
     * @param clickedMenuItem The MenuItem that was clicked.
     * @return true if the action was handled here, false otherwise.
     */
    private boolean handlePunishMenuActions(Player player, PunishMenu punishMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        UUID targetUUID = punishMenu.getTargetUUID();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;

        switch (action) {
            case OPEN_MENU:
                if (firstArg != null) {
                    // Handle standard menu openings
                    switch (firstArg.toLowerCase()) {
                        case "ban_details": if (!player.hasPermission(PUNISH_BAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "ban details"); return true; } new PunishDetailsMenu(targetUUID, plugin, BAN_PUNISHMENT_TYPE).open(player); return true;
                        case "mute_details": if (!player.hasPermission(PUNISH_MUTE_PERMISSION)) { sendNoPermissionMenuMessage(player, "mute details"); return true; } new PunishDetailsMenu(targetUUID, plugin, MUTE_PUNISHMENT_TYPE).open(player); return true;
                        case "softban_details": if (!player.hasPermission(PUNISH_SOFTBAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "softban details"); return true; } new PunishDetailsMenu(targetUUID, plugin, SOFTBAN_PUNISHMENT_TYPE).open(player); return true;
                        case "kick_details": if (!player.hasPermission(PUNISH_KICK_PERMISSION)) { sendNoPermissionMenuMessage(player, "kick details"); return true; } new PunishDetailsMenu(targetUUID, plugin, KICK_PUNISHMENT_TYPE).open(player); return true;
                        case "warn_details": if (!player.hasPermission(PUNISH_WARN_PERMISSION)) { sendNoPermissionMenuMessage(player, "warn details"); return true; } new PunishDetailsMenu(targetUUID, plugin, WARN_PUNISHMENT_TYPE).open(player); return true;
                        case "freeze_details": if (!player.hasPermission(PUNISH_FREEZE_PERMISSION)) { sendNoPermissionMenuMessage(player, "freeze details"); return true; } new PunishDetailsMenu(targetUUID, plugin, FREEZE_PUNISHMENT_TYPE).open(player); return true;
                        case "history_menu": if (!player.hasPermission(USE_PERMISSION)) { sendNoPermissionMenuMessage(player, "history menu"); return true; } new HistoryMenu(targetUUID, plugin).open(player); return true;
                        // Removed 'change_target' from here
                        default: return false;
                    }
                }
                return false;

            case REQUEST_INPUT: // Handle input requests
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("change_target")) { // Handle change_target here
                        if (!player.hasPermission(USE_PERMISSION)) { sendNoPermissionMenuMessage(player, "change target action"); return true; }
                        player.closeInventory();
                        requestNewTargetName(player); // Request input for new target name
                        return true; // Action handled
                    }
                    // Handle other potential REQUEST_INPUT types specific to PunishMenu if needed
                }
                return false; // Unrecognized input request

            // Handle other actions specific to PunishMenu if any
            default:
                return false; // Action not handled by this specific menu handler
        }
    }

    private boolean handlePunishDetailsMenuActions(Player player, PunishDetailsMenu punishDetailsMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case OPEN_MENU:
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("time_selector")) { if (punishDetailsMenu.isTimeRequired()) { new TimeSelectorMenu(punishDetailsMenu, plugin).open(player); } else { sendConfigMessage(player, "messages.time_not_applicable"); } return true; }
                    else if (firstArg.equalsIgnoreCase("punish_menu")) { new PunishMenu(punishDetailsMenu.getTargetUUID(), plugin).open(player); return true; }
                } return false;
            case REQUEST_INPUT:
                if (firstArg != null && firstArg.equalsIgnoreCase("reason_input")) { requestReasonInput(player, punishDetailsMenu); return true; } return false;
            case CONFIRM_PUNISHMENT: handleConfirmButtonClick(player, punishDetailsMenu); return true;
            case UN_SOFTBAN: if (!player.hasPermission(UNPUNISH_SOFTBAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "unsoftban"); return true; } handleUnsoftbanButtonClick(player, punishDetailsMenu); return true;
            case UN_FREEZE: if (!player.hasPermission(UNPUNISH_FREEZE_PERMISSION)) { sendNoPermissionMenuMessage(player, "unfreeze"); return true; } handleUnfreezeButtonClick(player, punishDetailsMenu); return true;
            case UN_BAN: if (!player.hasPermission(UNPUNISH_BAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "unban"); return true; } executeUnbanAction(player, punishDetailsMenu); return true;
            case UN_MUTE: if (!player.hasPermission(UNPUNISH_MUTE_PERMISSION)) { sendNoPermissionMenuMessage(player, "unmute"); return true; } executeUnmuteAction(player, punishDetailsMenu); return true;
            case UN_WARN: if (!player.hasPermission(UNPUNISH_WARN_PERMISSION)) { sendNoPermissionMenuMessage(player, "unwarn"); return true; } executeUnwarnAction(player, punishDetailsMenu); return true;
            default: return false;
        }
    }

    private boolean handleTimeSelectorMenuActions(Player player, TimeSelectorMenu timeSelectorMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        PunishDetailsMenu detailsMenu = timeSelectorMenu.getPunishDetailsMenu();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case ADJUST_TIME:
                if (firstArg != null) {
                    int secondsToAdd = switch (firstArg.toLowerCase()) { case "minus_5_min" -> -300; case "minus_2_hour" -> -7200; case "minus_1_day" -> -86400; case "minus_5_day" -> -432000; case "plus_15_min" -> 900; case "plus_6_hour" -> 21600; case "plus_1_day" -> 86400; case "plus_7_day" -> 604800; default -> 0; };
                    if (secondsToAdd != 0) { timeSelectorMenu.adjustTime(secondsToAdd); timeSelectorMenu.updateTimeDisplayItem(player); } return true;
                } return false;
            case REQUEST_INPUT:
                if (firstArg != null && firstArg.equalsIgnoreCase("custom_time_input")) { requestCustomTimeInput(player, detailsMenu); return true; } return false;
            case SET_PUNISHMENT_TYPE: // Used for time setting actions here
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("permanent_time")) { setPermanentTime(detailsMenu, player); return true; }
                    else if (firstArg.equalsIgnoreCase("confirm_time")) { handleTimeDisplayClick(timeSelectorMenu, detailsMenu, player); return true; }
                } return false;
            case OPEN_MENU:
                if (firstArg != null && firstArg.equalsIgnoreCase("punish_details")) { detailsMenu.open(player); return true; } return false;
            default: return false;
        }
    }

    private boolean handleHistoryMenuActions(Player player, HistoryMenu historyMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case OPEN_MENU:
                if (firstArg != null && firstArg.equalsIgnoreCase("punish_menu")) { new PunishMenu(historyMenu.getTargetUUID(), plugin).open(player); return true; } return false;
            case ADJUST_PAGE:
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("next_page")) historyMenu.nextPage(player);
                    else if (firstArg.equalsIgnoreCase("previous_page")) historyMenu.previousPage(player);
                    return true; // NO_ACTION is also handled here (by doing nothing)
                } return false;
            default: return false;
        }
    }

    /**
     * Requests a new target player name from the player via chat input.
     * Uses "change_target" as the inputType.
     *
     * @param player The player to request input from.
     */
    private void requestNewTargetName(Player player) {
        sendConfigMessage(player, "messages.prompt_new_target");
        // Use "change_target" as the inputType
        storeInputData(player, setupChatInputTimeout(player, null, "change_target"), null, "change_target");
    }

    private void requestReasonInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        // Dynamically get the correct prompt message based on punishment type
        String promptPath = "messages.prompt_" + punishDetailsMenu.getPunishmentType().toLowerCase() + "_reason";
        sendConfigMessage(player, promptPath); // Send the prompt
        storeInputData(player, setupChatInputTimeout(player, punishDetailsMenu, "reason_input"), punishDetailsMenu, "reason_input"); // Store data
    }

    /**
     * Requests a custom time input from the player via chat.
     * Stores the pending menu context and the timeout task correctly.
     *
     * @param player The player to request input from.
     * @param punishDetailsMenu The PunishDetailsMenu context.
     */
    private void requestCustomTimeInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        // Send the prompt message first
        sendConfigMessage(player, "messages.prompt_custom_time");
        // Setup the timeout task and store the input state
        storeInputData(player, setupChatInputTimeout(player, punishDetailsMenu, "custom_time"), punishDetailsMenu, "custom_time");
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Requested custom_time input from " + player.getName() + ". Timeout task stored.");
        }
    }

    /**
     * Sets up a timeout for chat input, cancelling existing timeouts.
     * Returns the created BukkitTask so it can be stored.
     *
     * @param player    The player providing input.
     * @param menu      The PunishDetailsMenu context (can be null).
     * @param inputType The type of input requested (reason, time, etc.).
     * @return The BukkitTask representing the timeout runnable.
     */
    private BukkitTask setupChatInputTimeout(Player player, PunishDetailsMenu menu, String inputType) {
        cancelExistingTimeout(player); // Cancel previous timeout first

        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Check if the player is still expecting this specific input type when timeout occurs
                // This prevents the timeout message if the player already provided input.
                if (inputTypes.getOrDefault(player.getUniqueId(), "").equals(inputType)) {
                    handleInputTimeout(player);
                }
                // If inputTypes doesn't match, it means the player likely submitted input
                // or another input request overwrote this one, so the maps were cleared.
                // No message needed in that case.
            }
        }.runTaskLater(plugin, 400L); // 20 seconds timeout (20 ticks/sec * 20)

        // Return the task so the calling method can store it
        return timeoutTask;
    }


    /**
     * Stores input related data for a player, including timeout task, menu, and input type.
     *
     * @param player The player providing input.
     * @param task The BukkitTask for timeout.
     * @param menu The PunishDetailsMenu context (can be null).
     * @param inputType The type of input.
     */
    private void storeInputData(Player player, BukkitTask task, PunishDetailsMenu menu, String inputType) {
        // Only store if the task is valid
        if (task != null) {
            inputTimeouts.put(player.getUniqueId(), task);
        } else {
            plugin.getLogger().warning("Attempted to store a null timeout task for " + player.getName() + ", inputType: " + inputType);
        }

        if (menu != null) { // Store menu context only if provided
            pendingDetailsMenus.put(player.getUniqueId(), menu);
        } else {
            // If a previous menu was stored for this player but isn't needed now, remove it.
            // This might happen if change_target overwrites a pending reason input.
            pendingDetailsMenus.remove(player.getUniqueId());
        }
        inputTypes.put(player.getUniqueId(), inputType);

        if (plugin.getConfigManager().isDebugEnabled() && task != null) {
            plugin.getLogger().info("[DEBUG] Stored input data for " + player.getName() + ": inputType=" + inputType + ", menuContext=" + (menu != null) + ", taskID=" + task.getTaskId());
        }
    }

    private void cancelExistingTimeout(Player player) {
        BukkitTask existingTask = inputTimeouts.remove(player.getUniqueId());
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }
    }

    /**
     * Handles the actual timeout logic: sends message and clears data.
     * Called by the BukkitRunnable in setupChatInputTimeout.
     *
     * @param player The player who timed out.
     */
    private void handleInputTimeout(Player player) {
        // Check if the player still exists and is online before sending message
        if (player != null && player.isOnline()) {
            // Only send timeout message if we were *actually* still waiting for input from them
            // (check inputTypes before clearing)
            if (inputTypes.containsKey(player.getUniqueId())) {
                sendConfigMessage(player, "messages.input_timeout");
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[DEBUG] Input timed out for " + player.getName() + ". Type was: " + inputTypes.get(player.getUniqueId()));
                }
            }
        }
        // Clean up maps regardless of whether message was sent
        clearPlayerInputData(player);
    }


    /**
     * Processes the player's chat input after receiving it.
     * Cancels the associated timeout task.
     *
     * @param player The player providing input.
     * @param input  The chat input message.
     */
    private void handlePlayerInput(Player player, String input) {
        // Retrieve context *before* cancelling timeout/clearing data
        PunishDetailsMenu detailsMenu = pendingDetailsMenus.get(player.getUniqueId());
        String inputType = inputTypes.get(player.getUniqueId());

        if (inputType == null) {
            // This might happen if the input arrives *after* the timeout task has run and cleared the maps.
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Received chat input from " + player.getName() + " but no input type was stored (likely timed out or manually closed menu just before input). Input: " + input);
            }
            // Do not attempt to process, as state is gone.
            return;
        }

        // Now cancel the timeout since we received input
        cancelExistingTimeout(player);

        // Handle cancellation input
        if (input.equalsIgnoreCase("cancel")) {
            handleCancelInput(player, detailsMenu); // Reopen menu if applicable and clear data
            return; // Exit after handling cancellation
        }

        // Process valid input based on type
        processValidInput(player, input, detailsMenu, inputType);
        // Input data is cleared inside processValidInput or handleCancelInput now
    }


    private void handleCancelInput(Player player, PunishDetailsMenu detailsMenu) {
        sendConfigMessage(player, "messages.input_cancelled");
        // Reopen the previous menu if context exists
        if (detailsMenu != null) {
            reopenDetailsMenu(player, detailsMenu);
        }
        // No need to call clearPlayerInputData here, it's called after return in handlePlayerInput
    }

    /**
     * Processes valid input based on input type and context.
     * Handles specific input types like changing target, setting reason, or custom time.
     *
     * @param player      The player providing input.
     * @param input       The validated input string.
     * @param detailsMenu The PunishDetailsMenu context (can be null for some input types).
     * @param inputType   The type of input being processed.
     */
    private void processValidInput(Player player, String input, PunishDetailsMenu detailsMenu, String inputType) {
        // Use toLowerCase() for case-insensitive comparison of inputType
        switch (inputType.toLowerCase()) {
            case "change_target":
                handleNewTargetInput(player, input); // Call the handler
                break; // Add break statement

            case "reason_input":
                if (detailsMenu != null) {
                    handleReasonInput(player, input, detailsMenu);
                } else {
                    // Log error if detailsMenu context is missing when expected
                    plugin.getLogger().warning("Reason input received but no details menu context for " + player.getName());
                }
                break; // Add break statement

            case "custom_time":
                if (detailsMenu != null) {
                    handleCustomTimeInput(player, input, detailsMenu);
                } else {
                    // Log error if detailsMenu context is missing when expected
                    plugin.getLogger().warning("Custom time input received but no details menu context for " + player.getName());
                }
                break; // Add break statement

            default:
                // Log if the inputType doesn't match any known cases
                plugin.getLogger().warning("Unknown input type processed: " + inputType + " for player " + player.getName());
                break;
        }
        // Clear player input data AFTER processing is attempted (successful or not)
        clearPlayerInputData(player);
    }

    private void handleNewTargetInput(Player player, String input) {
        OfflinePlayer newTarget = Bukkit.getOfflinePlayer(input);
        // Use the more robust check from MainCommand
        if (!newTarget.hasPlayedBefore() && !newTarget.isOnline()) {
            sendConfigMessage(player, "messages.never_played", "{input}", input);
        } else {
            // Open PunishMenu for the new target synchronously
            Bukkit.getScheduler().runTask(plugin, () -> new PunishMenu(newTarget.getUniqueId(), plugin).open(player));
        }
    }


    private void handleReasonInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        detailsMenu.setBanReason(input); // Set the reason in the menu state
        reopenDetailsMenu(player, detailsMenu); // Reopen to show updated state
    }

    private void handleCustomTimeInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        // Use TimeUtils for parsing, check for non-zero return and not permanent keyword
        int seconds = TimeUtils.parseTime(input, plugin.getConfigManager());
        String permanentKeyword = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

        if (seconds > 0 || input.equalsIgnoreCase(permanentKeyword)) {
            String timeToSet = input.equalsIgnoreCase(permanentKeyword) ? permanentKeyword : TimeUtils.formatTime(seconds, plugin.getConfigManager()); // Use formatted or permanent
            detailsMenu.setBanTime(timeToSet);
            reopenDetailsMenu(player, detailsMenu);
        } else {
            sendConfigMessage(player, "messages.invalid_time_format_command", "{input}", input);
            // Reopen the previous details menu directly instead of time selector
            reopenDetailsMenu(player, detailsMenu);
        }
    }

    private void reopenDetailsMenu(Player player, PunishDetailsMenu detailsMenu) {
        // Run on next tick to ensure chat processing is complete
        Bukkit.getScheduler().runTask(plugin, () -> detailsMenu.open(player));
    }

    private void clearPlayerInputData(Player player) {
        cancelExistingTimeout(player); // Ensure timeout is cancelled
        pendingDetailsMenus.remove(player.getUniqueId());
        inputTypes.remove(player.getUniqueId());
    }

    // ========================================================================
    // Punishment / Unpunishment Confirmation Logic
    // ========================================================================

    private void handleConfirmButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        // Check if time is required AND not set
        boolean timeMissing = punishDetailsMenu.isTimeRequired() && !punishDetailsMenu.isTimeSet();
        // Check if reason is required for confirmation AND not set
        boolean reasonMissing = punishDetailsMenu.isReasonRequiredForConfirmation() && !punishDetailsMenu.isReasonSet();

        if (timeMissing || reasonMissing) {
            sendValidationMessages(player, timeMissing, reasonMissing); // Pass boolean flags
        } else {
            confirmDynamicPunishment(player, punishDetailsMenu); // Proceed with punishment
        }
    }

    private void handleUnsoftbanButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getSoftBanDatabaseManager().isSoftBanned(targetUUID)) {
            confirmUnsoftban(player, punishDetailsMenu);
        } else {
            // Fetch target name for message
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            sendConfigMessage(player, "messages.no_active_softban", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error"); // Play error sound
        }
    }

    private void handleUnfreezeButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getPluginFrozenPlayers().containsKey(targetUUID)) {
            confirmUnfreeze(player, punishDetailsMenu);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            sendConfigMessage(player, "messages.no_active_freeze", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }

    private void confirmDynamicPunishment(Player player, PunishDetailsMenu punishDetailsMenu) {
        // Permission check should happen BEFORE confirming
        String type = punishDetailsMenu.getPunishmentType().toLowerCase();
        if (!checkPunishmentPermission(player, type)) {
            sendNoPermissionMenuMessage(player, type + " punishment"); // Inform player about missing perm
            playSound(player, "punish_error");
            return;
        }

        // Bypass check should also happen before execution
        OfflinePlayer target = Bukkit.getOfflinePlayer(punishDetailsMenu.getTargetUUID());
        if (hasBypassPermission(target, type)) {
            sendBypassError(player, target, type);
            playSound(player, "punish_error");
            player.closeInventory(); // Close menu on bypass error
            return;
        }


        // Proceed with specific confirmation logic
        switch (type) {
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
            case FREEZE_PUNISHMENT_TYPE:
                confirmFreeze(player, punishDetailsMenu);
                break;
            default:
                plugin.getLogger().warning("Attempted to confirm unknown punishment type: " + type);
                sendConfigMessage(player, "messages.invalid_punishment_type", "{types}", "Known Types"); // Generic error
                playSound(player, "punish_error");
                break;
        }
    }

    // --- Specific Confirmation Methods ---

    private void confirmStandardPunishment(Player player, PunishDetailsMenu detailsMenu) {
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String punishmentType = detailsMenu.getPunishmentType();
        String commandTemplate = getPunishmentCommand(punishmentType);
        String timeInput = detailsMenu.getBanTime() != null ? detailsMenu.getBanTime() : "permanent"; // Default if somehow null
        String reason = detailsMenu.getBanReason() != null ? detailsMenu.getBanReason() : "No reason specified"; // Default if somehow null

        String processedCommand = commandTemplate
                .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                .replace("{time}", timeInput)
                .replace("{reason}", reason);

        executePunishmentCommand(player, processedCommand, target, detailsMenu);
    }

    private void confirmSoftBan(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Softbanned by moderator";
        String timeInput = punishDetailsMenu.getBanTime() != null ? punishDetailsMenu.getBanTime() : "permanent";

        long endTime = calculateEndTime(timeInput);
        String durationString = timeInput; // Use the input string for logging

        // Correct the end time for the 1-second bug if not permanent
        if (endTime != Long.MAX_VALUE) {
            endTime += 1000L; // Add 1 second
            // Recalculate duration string based on corrected time for logging consistency if needed
            // durationString = TimeUtils.formatTime((int)((endTime - System.currentTimeMillis())/1000), plugin.getConfigManager());
        } else {
            durationString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display"); // Use permanent display for log
        }


        // SoftbanPlayer now handles logging internally
        plugin.getSoftBanDatabaseManager().softBanPlayer(targetUUID, endTime, reason, player.getName());

        playSound(player, "punish_confirm");
        sendPunishmentConfirmation(player, target, timeInput, reason, SOFTBAN_PUNISHMENT_TYPE); // Use generic confirmation
    }

    private void confirmFreeze(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Frozen by moderator";

        // Check if already frozen again just before applying (race condition mitigation)
        if (plugin.getPluginFrozenPlayers().containsKey(targetUUID)) {
            sendConfigMessage(player, "messages.already_frozen", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
            return;
        }

        plugin.getPluginFrozenPlayers().put(targetUUID, true); // Mark as frozen

        playSound(player, "punish_confirm");
        sendPunishmentConfirmation(player, target, "Permanent", reason, FREEZE_PUNISHMENT_TYPE); // Use generic confirmation
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, FREEZE_PUNISHMENT_TYPE, reason, player.getName(), Long.MAX_VALUE, plugin.getConfigManager().getMessage("placeholders.permanent_time_display")); // Log

        // Apply effects and start task if player is online
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            sendFreezeReceivedMessage(onlineTarget);
            plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
        }
    }

    private void confirmKick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Kicked by moderator";
        String commandTemplate = plugin.getConfigManager().getKickCommand();

        String processedCommand = commandTemplate
                .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                .replace("{reason}", reason);

        executePunishmentCommand(player, processedCommand, target, punishDetailsMenu); // Uses the generic executor
    }

    private void confirmWarn(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Warned by moderator";
        String commandTemplate = plugin.getConfigManager().getWarnCommand();

        String processedCommand = commandTemplate
                .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                .replace("{reason}", reason);

        executePunishmentCommand(player, processedCommand, target, punishDetailsMenu); // Uses the generic executor
    }


    // --- Unpunishment Methods ---

    private void confirmUnsoftban(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        plugin.getSoftBanDatabaseManager().unSoftBanPlayer(targetUUID, player.getName()); // Handles logging internally now
        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, SOFTBAN_PUNISHMENT_TYPE);
    }

    private void confirmUnfreeze(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean removed = plugin.getPluginFrozenPlayers().remove(targetUUID) != null; // Remove and check if existed

        if(removed) {
            plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unfreeze", "Unfrozen via Menu", player.getName(), 0L, "N/A"); // Log unfreeze
            playSound(player, "punish_confirm");
            sendUnpunishConfirmation(player, target, FREEZE_PUNISHMENT_TYPE);
            // Stop freeze task and remove effects if player is online
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                plugin.getFreezeListener().stopFreezeActionsTask(targetUUID); // Stop task using UUID
                sendUnfreezeMessage(onlineTarget);
            }
        } else {
            // Should have been caught by handleUnfreezeButtonClick, but double-check
            sendConfigMessage(player, "messages.no_active_freeze", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }

    private void executeUnbanAction(Player player, InventoryHolder holder) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder; // Assume called correctly
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String commandTemplate = plugin.getConfigManager().getUnbanCommand();
        String processedCommand = commandTemplate.replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand));
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unban", "Unbanned via Menu", player.getName(), 0L, "N/A");
        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, BAN_PUNISHMENT_TYPE);
    }

    private void executeUnmuteAction(Player player, InventoryHolder holder) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String commandTemplate = plugin.getConfigManager().getUnmuteCommand();
        String processedCommand = commandTemplate.replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand));
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unmute", "Unmuted via Menu", player.getName(), 0L, "N/A");
        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, MUTE_PUNISHMENT_TYPE);
    }

    private void executeUnwarnAction(Player player, InventoryHolder holder) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String commandTemplate = plugin.getConfigManager().getUnwarnCommand();
        if (commandTemplate == null || commandTemplate.isEmpty()) {
            sendConfigMessage(player, "messages.unpunish_command_not_configured", "{punishment_type}", "warn");
            playSound(player, "punish_error");
            return;
        }
        String processedCommand = commandTemplate.replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand));
        plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unwarn", "Unwarned via Menu", player.getName(), 0L, "N/A");
        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, WARN_PUNISHMENT_TYPE);
    }


    // ========================================================================
    // Time Selection Logic
    // ========================================================================

    private void setPermanentTime(PunishDetailsMenu detailsMenu, Player player) {
        String permanentDisplayString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        detailsMenu.setBanTime(permanentDisplayString); // Set time in details menu state
        reopenDetailsMenu(player, detailsMenu); // Reopen details menu to reflect change
    }

    private void handleTimeDisplayClick(TimeSelectorMenu timeSelectorMenu, PunishDetailsMenu detailsMenu, Player player) {
        if (timeSelectorMenu.getCurrentTimeSeconds() > 0) {
            String formattedTime = timeSelectorMenu.getFormattedTime();
            detailsMenu.setBanTime(formattedTime); // Set time in details menu state
            reopenDetailsMenu(player, detailsMenu); // Open details menu
        } else {
            sendConfigMessage(player, "messages.set_valid_time_confirm");
            playSound(player, "punish_error");
            // Stay in TimeSelectorMenu
        }
    }


    // ========================================================================
    // Helper & Utility Methods
    // ========================================================================

    /**
     * Retrieves the MenuItem that was clicked based on the slot and menu holder.
     * @param slot   The raw slot number clicked.
     * @param holder The InventoryHolder (the menu object).
     * @return The clicked MenuItem, or null if not found.
     */
    private MenuItem getMenuItemClicked(int slot, InventoryHolder holder) {
        // Delegate based on the specific menu type
        if (holder instanceof PunishMenu menu) return getPunishMenuItem(slot, menu);
        if (holder instanceof PunishDetailsMenu menu) return getPunishDetailsMenuItem(slot, menu);
        if (holder instanceof TimeSelectorMenu menu) return getTimeSelectorMenuItem(slot, menu);
        if (holder instanceof HistoryMenu menu) return getHistoryMenuItem(slot, menu);
        return null; // Unknown menu type
    }

    /** Helper to find MenuItem in PunishMenu */
    private MenuItem getPunishMenuItem(int slot, PunishMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getPunishMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        } return null;
    }

    /** Helper to find MenuItem in PunishDetailsMenu */
    private MenuItem getPunishDetailsMenuItem(int slot, PunishDetailsMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager(); String punishmentType = menu.getPunishmentType();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getDetailsMenuItemConfig(punishmentType, itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        } return null;
    }

    /** Helper to find MenuItem in TimeSelectorMenu */
    private MenuItem getTimeSelectorMenuItem(int slot, TimeSelectorMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getTimeSelectorItemKeys()) {
            MenuItem menuItem = configManager.getTimeSelectorMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        } return null;
    }

    /** Helper to find MenuItem in HistoryMenu */
    private MenuItem getHistoryMenuItem(int slot, HistoryMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        // Check configured static items first (like buttons)
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getHistoryMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        }
        // Check dynamically generated history entry items
        for (MenuItem historyEntryItem : menu.getHistoryEntryItems()) {
            if (historyEntryItem != null && historyEntryItem.getSlots() != null && historyEntryItem.getSlots().contains(slot)) return historyEntryItem;
        }
        return null;
    }


    /** Gets the target player from the InventoryHolder context. */
    private OfflinePlayer getTargetForAction(InventoryHolder holder) {
        if (holder instanceof PunishMenu menu) return Bukkit.getOfflinePlayer(menu.getTargetUUID());
        if (holder instanceof PunishDetailsMenu menu) return Bukkit.getOfflinePlayer(menu.getTargetUUID());
        if (holder instanceof TimeSelectorMenu menu) return Bukkit.getOfflinePlayer(menu.getPunishDetailsMenu().getTargetUUID());
        if (holder instanceof HistoryMenu menu) return Bukkit.getOfflinePlayer(menu.getTargetUUID());
        return null;
    }

    /** Replaces placeholders in text. Primarily handles {player}. */
    private String replacePlaceholders(Player player, String text, InventoryHolder holder) {
        if (text == null) return null;
        return text.replace("{player}", player.getName());
        // Note: {target} and other context-specific placeholders are handled later
        // by MainConfigManager or within specific execute* methods.
    }

    /** Gets online players with the moderator permission. */
    private List<Player> getMods() {
        return Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(MOD_PERMISSION)).collect(Collectors.toList());
    }

    /** Plays a sound configured in config.yml for the player. */
    private void playSound(Player player, String soundKey) {
        String soundName = plugin.getConfigManager().getSoundName(soundKey);
        if (soundName != null && !soundName.isEmpty()) {
            try { Sound sound = Sound.valueOf(soundName.toUpperCase()); player.playSound(player.getLocation(), sound, 1.0f, 1.0f); }
            catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound configured for key '" + soundKey + "': " + soundName); }
        }
    }

    /** Sends a message from messages.yml to a sender. */
    private void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        MessageUtils.sendConfigMessage(plugin, sender, path, replacements);
    }

    /** Sends a "no permission for menu action" message. */
    private void sendNoPermissionMenuMessage(Player player, String actionName) {
        sendConfigMessage(player, "messages.no_permission_menu_action", "{action}", actionName);
    }

    /** Sends validation messages if time/reason are missing. */
    private void sendValidationMessages(Player player, boolean timeMissing, boolean reasonMissing) {
        if (timeMissing && reasonMissing) sendConfigMessage(player, "messages.set_time_reason_before_confirm");
        else if (timeMissing) sendConfigMessage(player, "messages.set_time_before_confirm");
        else if (reasonMissing) sendConfigMessage(player, "messages.set_reason_before_confirm");
        playSound(player, "punish_error");
    }

    /** Sends a generic punishment confirmation message. */
    private void sendPunishmentConfirmation(Player player, OfflinePlayer target, String timeValue, String reason, String punishmentType) {
        player.closeInventory();
        sendConfigMessage(player, "messages.punishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString(),
                "{time}", timeValue,
                "{reason}", reason,
                "{punishment_type}", punishmentType);
    }

    /** Sends a generic unpunishment confirmation message. */
    private void sendUnpunishConfirmation(Player player, OfflinePlayer target, String punishType) {
        player.closeInventory();
        sendConfigMessage(player, "messages.direct_unpunishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString(),
                "{punishment_type}", punishType);
    }

    /** Sends the "you are frozen" message. */
    private void sendFreezeReceivedMessage(Player player) {
        sendConfigMessage(player, "messages.you_are_frozen");
    }

    /** Sends the "you are unfrozen" message. */
    private void sendUnfreezeMessage(Player player) {
        sendConfigMessage(player, "messages.you_are_unfrozen");
    }

    /** Calculates the punishment end time in milliseconds. */
    private long calculateEndTime(String timeInput) {
        if (timeInput == null) return 0L; // Or handle as permanent? Decide based on desired behavior.
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        if (timeInput.equalsIgnoreCase(permanentDisplay)) return Long.MAX_VALUE;
        int seconds = TimeUtils.parseTime(timeInput, plugin.getConfigManager());
        if (seconds <= 0) return 0L; // Indicate invalid duration
        return System.currentTimeMillis() + (seconds * 1000L);
    }

    /** Checks if the target has bypass permission for a specific punishment type. */
    private boolean hasBypassPermission(OfflinePlayer target, String punishmentType) {
        if (!(target instanceof Player onlineTarget)) return false; // Cannot check perms for offline players
        String bypassPerm = "crown.bypass." + punishmentType.toLowerCase();
        return onlineTarget.hasPermission(bypassPerm);
    }

    /** Sends the appropriate bypass error message to the punisher. */
    private void sendBypassError(Player punisher, OfflinePlayer target, String punishmentType) {
        String messageKey = "messages.bypass_error_" + punishmentType.toLowerCase();
        // Check if a specific bypass message exists, otherwise use a generic one?
        // For now, assume specific messages exist as per messages.yml structure.
        sendConfigMessage(punisher, messageKey, "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString());
    }

    /** Checks if the executor has permission to apply a specific punishment type. */
    private boolean checkPunishmentPermission(Player executor, String punishmentType) {
        String perm = "crown.punish." + punishmentType.toLowerCase();
        return executor.hasPermission(perm) || executor.hasPermission(USE_PERMISSION); // Allow if they have specific or base use perm? Adjust as needed. For now, require specific.
        // return executor.hasPermission(perm); // More strict: requires specific permission
    }

    /** Retrieves the command template string for a given punishment type from config. */
    private String getPunishmentCommand(String punishmentType) {
        return switch (punishmentType.toLowerCase()) {
            case BAN_PUNISHMENT_TYPE -> plugin.getConfigManager().getBanCommand();
            case MUTE_PUNISHMENT_TYPE -> plugin.getConfigManager().getMuteCommand();
            case KICK_PUNISHMENT_TYPE -> plugin.getConfigManager().getKickCommand();
            case WARN_PUNISHMENT_TYPE -> plugin.getConfigManager().getWarnCommand();
            case SOFTBAN_PUNISHMENT_TYPE -> plugin.getConfigManager().getSoftBanCommand(); // May not be used
            case FREEZE_PUNISHMENT_TYPE -> ""; // Internal handling
            default -> ""; // Unknown type
        };
    }

    /** Executes the final punishment command and handles logging/confirmation. */
    private void executePunishmentCommand(Player player, String command, OfflinePlayer target, PunishDetailsMenu detailsMenu) {
        // Bypass check already done in confirmDynamicPunishment
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        playSound(player, "punish_confirm");
        String punishmentType = detailsMenu.getPunishmentType();
        String timeValue = detailsMenu.getBanTime() != null ? detailsMenu.getBanTime() : "N/A"; // Time might be null for kick/warn
        String reason = detailsMenu.getBanReason() != null ? detailsMenu.getBanReason() : "No reason specified";

        sendPunishmentConfirmation(player, target, timeValue, reason, punishmentType); // Use the generic message

        long punishmentEndTime = 0L;
        String durationString = "permanent"; // Default for kick/warn

        if (punishmentType.equalsIgnoreCase(BAN_PUNISHMENT_TYPE) || punishmentType.equalsIgnoreCase(MUTE_PUNISHMENT_TYPE)) {
            punishmentEndTime = calculateEndTime(timeValue);
            durationString = timeValue; // Log the input string
            if (punishmentEndTime == Long.MAX_VALUE) {
                durationString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
            }
        }
        // Kick/Warn use 0L and "permanent" duration string

        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishmentType, reason, player.getName(), punishmentEndTime, durationString);
    }


} // End of MenuListener class
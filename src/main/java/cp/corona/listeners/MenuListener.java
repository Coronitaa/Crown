// MenuListener.java
package cp.corona.listeners;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.*;
import cp.corona.menus.actions.ClickAction;
import cp.corona.menus.items.MenuItem;
import cp.corona.config.MainConfigManager;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static cp.corona.menus.PunishDetailsMenu.*;

/**
 * Listener for handling menu interactions in the CrownPunishments plugin.
 * Manages inventory click events, chat input for reasons and times,
 * and interactions with PunishMenu, PunishDetailsMenu, and TimeSelectorMenu.
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
    private static final String KICK_PUNISHMENT_TYPE = "kick"; // New punishment type
    private static final String WARN_PUNISHMENT_TYPE = "warn"; // New punishment type
    private static final String PERMANENT_TIME_KEY = "permanent";
    private static final String CUSTOM_TIME_KEY = "custom";
    private static final String INFO_ITEM_KEY = "info";
    private static final String BAN_ITEM_MENU_KEY = "ban";
    private static final String MUTE_ITEM_MENU_KEY = "mute";
    private static final String SOFTBAN_ITEM_MENU_KEY = "softban";
    private static final String KICK_ITEM_MENU_KEY = "kick"; // New item key
    private static final String WARN_ITEM_MENU_KEY = "warn"; // New item key
    private static final String HISTORY_ITEM_MENU_KEY = "history"; // New item key for history
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
    private static final String NEXT_PAGE_BUTTON_KEY = "next_page_button"; // For history menu pagination
    private static final String PREVIOUS_PAGE_BUTTON_KEY = "previous_page_button"; // For history menu pagination

    /**
     * Constructor for MenuListener.
     * @param plugin Instance of the main plugin class.
     */
    public MenuListener(CrownPunishments plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles inventory click events.
     * Determines the clicked MenuItem and performs the corresponding action.
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
        event.setCancelled(true);

        MenuItem clickedMenuItem = getMenuItemClicked(event.getRawSlot(), holder);
        if (clickedMenuItem != null) {
            clickedMenuItem.playClickSound(player);

            ClickAction action = clickedMenuItem.getClickAction();
            String actionData = clickedMenuItem.getClickActionData();

            handleMenuItemClick(player, holder, action, actionData, event, clickedMenuItem);
        }
    }

    /**
     * Retrieves the MenuItem clicked based on the inventory holder and slot.
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
        } else if (holder instanceof HistoryMenu) {
            return getHistoryMenuItem(slot, (HistoryMenu) holder);
        }
        return null;
    }

    /**
     * Gets the PunishMenuItem based on slot number.
     * @param slot The slot number.
     * @param menu The PunishMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getPunishMenuItem(int slot, PunishMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        MenuItem infoItem = configManager.getPunishMenuItemConfig(INFO_ITEM_KEY);
        if (infoItem != null && infoItem.getSlots().contains(slot)){
            return infoItem;
        }
        MenuItem banItem = configManager.getPunishMenuItemConfig(BAN_ITEM_MENU_KEY);
        if (banItem != null && banItem.getSlots().contains(slot)){
            return banItem;
        }
        MenuItem muteItem = configManager.getPunishMenuItemConfig(MUTE_ITEM_MENU_KEY);
        if (muteItem != null && muteItem.getSlots().contains(slot)){
            return muteItem;
        }
        MenuItem softbanItem = configManager.getPunishMenuItemConfig(SOFTBAN_ITEM_MENU_KEY);
        if (softbanItem != null && softbanItem.getSlots().contains(slot)){
            return softbanItem;
        }
        MenuItem kickItem = configManager.getPunishMenuItemConfig(KICK_ITEM_MENU_KEY);
        if (kickItem != null && kickItem.getSlots().contains(slot)){
            return kickItem;
        }
        MenuItem warnItem = configManager.getPunishMenuItemConfig(WARN_ITEM_MENU_KEY);
        if (warnItem != null && warnItem.getSlots().contains(slot)){
            return warnItem;
        }
        MenuItem historyItem = configManager.getPunishMenuItemConfig(HISTORY_ITEM_MENU_KEY);
        if (historyItem != null && historyItem.getSlots().contains(slot)){
            return historyItem;
        }
        return null;
    }

    /**
     * Gets the PunishDetailsMenuItem based on slot number.
     * @param slot The slot number.
     * @param menu The PunishDetailsMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getPunishDetailsMenuItem(int slot, PunishDetailsMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        String punishmentType = menu.getPunishmentType();
        MenuItem setTimeItem = configManager.getDetailsMenuItemConfig(punishmentType, SET_TIME_KEY);
        if (setTimeItem != null && setTimeItem.getSlots().contains(slot)){
            return setTimeItem;
        }
        MenuItem setReasonItem = configManager.getDetailsMenuItemConfig(punishmentType, SET_REASON_KEY);
        if (setReasonItem != null && setReasonItem.getSlots().contains(slot)){
            return setReasonItem;
        }
        MenuItem confirmPunishItem = configManager.getDetailsMenuItemConfig(punishmentType, CONFIRM_PUNISH_KEY);
        if (confirmPunishItem != null && confirmPunishItem.getSlots().contains(slot)){
            return confirmPunishItem;
        }
        MenuItem backButtonItem = configManager.getDetailsMenuItemConfig(punishmentType, BACK_BUTTON_KEY);
        if (backButtonItem != null && backButtonItem.getSlots().contains(slot)){
            return backButtonItem;
        }
        if (punishmentType.equalsIgnoreCase("softban")){
            MenuItem unSoftbanItem = configManager.getDetailsMenuItemConfig("softban", UNSOFTBAN_BUTTON_KEY);
            if (unSoftbanItem != null && unSoftbanItem.getSlots().contains(slot)){
                return unSoftbanItem;
            }
        }
        return null;
    }

    /**
     * Gets the TimeSelectorMenuItem based on slot number.
     * @param slot The slot number.
     * @param menu The TimeSelectorMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getTimeSelectorMenuItem(int slot, TimeSelectorMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        MenuItem customTimeItem = configManager.getTimeSelectorMenuItemConfig(CUSTOM_TIME_KEY);
        if (customTimeItem != null && customTimeItem.getSlots().contains(slot)){
            return customTimeItem;
        }
        MenuItem permanentTimeItem = configManager.getTimeSelectorMenuItemConfig(PERMANENT_TIME_KEY);
        if (permanentTimeItem != null && permanentTimeItem.getSlots().contains(slot)){
            return permanentTimeItem;
        }
        MenuItem timeDisplayItem = configManager.getTimeSelectorMenuItemConfig(TIME_DISPLAY_KEY);
        if (timeDisplayItem != null && timeDisplayItem.getSlots().contains(slot)){
            return timeDisplayItem;
        }
        MenuItem minus5MinItem = configManager.getTimeSelectorMenuItemConfig(MINUS_5_MIN_KEY);
        if (minus5MinItem != null && minus5MinItem.getSlots().contains(slot)){
            return minus5MinItem;
        }
        MenuItem minus2HourItem = configManager.getTimeSelectorMenuItemConfig(MINUS_2_HOUR_KEY);
        if (minus2HourItem != null && minus2HourItem.getSlots().contains(slot)){
            return minus2HourItem;
        }
        MenuItem minus1DayItem = configManager.getTimeSelectorMenuItemConfig(MINUS_1_DAY_KEY);
        if (minus1DayItem != null && minus1DayItem.getSlots().contains(slot)){
            return minus1DayItem;
        }
        MenuItem minus5DayItem = configManager.getTimeSelectorMenuItemConfig(MINUS_5_DAY_KEY);
        if (minus5DayItem != null && minus5DayItem.getSlots().contains(slot)){
            return minus5DayItem;
        }
        MenuItem plus15MinItem = configManager.getTimeSelectorMenuItemConfig(PLUS_15_MIN_KEY);
        if (plus15MinItem != null && plus15MinItem.getSlots().contains(slot)){
            return plus15MinItem;
        }
        MenuItem plus6HourItem = configManager.getTimeSelectorMenuItemConfig(PLUS_6_HOUR_KEY);
        if (plus6HourItem != null && plus6HourItem.getSlots().contains(slot)){
            return plus6HourItem;
        }
        MenuItem plus1DayItem = configManager.getTimeSelectorMenuItemConfig(PLUS_1_DAY_KEY);
        if (plus1DayItem != null && plus1DayItem.getSlots().contains(slot)){
            return plus1DayItem;
        }
        MenuItem plus7DayItem = configManager.getTimeSelectorMenuItemConfig(PLUS_7_DAY_KEY);
        if (plus7DayItem != null && plus7DayItem.getSlots().contains(slot)){
            return plus7DayItem;
        }
        MenuItem backButtonItem = configManager.getTimeSelectorMenuItemConfig(BACK_BUTTON_KEY);
        if (backButtonItem != null && backButtonItem.getSlots().contains(slot)){
            return backButtonItem;
        }
        return null;
    }

    /**
     * Gets the HistoryMenuItem based on slot number.
     * @param slot The slot number.
     * @param menu The HistoryMenu instance.
     * @return MenuItem or null if not found.
     */
    private MenuItem getHistoryMenuItem(int slot, HistoryMenu menu) {
        if (menu == null) return null;
        MainConfigManager configManager = plugin.getConfigManager();
        MenuItem backButtonItem = configManager.getHistoryMenuItemConfig(BACK_BUTTON_KEY);
        if (backButtonItem != null && backButtonItem.getSlots().contains(slot)){
            return backButtonItem;
        }
        MenuItem nextPageItem = configManager.getHistoryMenuItemConfig(NEXT_PAGE_BUTTON_KEY);
        if (nextPageItem != null && nextPageItem.getSlots().contains(slot)) {
            return nextPageItem;
        }
        MenuItem previousPageItem = configManager.getHistoryMenuItemConfig(PREVIOUS_PAGE_BUTTON_KEY);
        if (previousPageItem != null && previousPageItem.getSlots().contains(slot)) {
            return previousPageItem;
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
     * Handles menu item clicks based on the inventory holder.
     * @param player The player who clicked.
     * @param holder The InventoryHolder.
     * @param action The ClickAction of the clicked item.
     * @param actionData The data associated with the ClickAction.
     * @param event The InventoryClickEvent.
     * @param clickedMenuItem The MenuItem that was clicked.
     */
    private void handleMenuItemClick(Player player, InventoryHolder holder, ClickAction action, String actionData, InventoryClickEvent event, MenuItem clickedMenuItem) {
        if (holder instanceof PunishMenu punishMenu) {
            handlePunishMenuActions(player, punishMenu, action, actionData, clickedMenuItem, event); // Pass event here
        } else if (holder instanceof PunishDetailsMenu punishDetailsMenu) {
            handlePunishDetailsMenuActions(player, punishDetailsMenu, action, actionData, event, clickedMenuItem);
        } else if (holder instanceof TimeSelectorMenu timeSelectorMenu) {
            handleTimeSelectorMenuActions(player, timeSelectorMenu, action, actionData, event, clickedMenuItem);
        } else if (holder instanceof HistoryMenu historyMenu) {
            handleHistoryMenuActions(player, historyMenu, action, actionData, clickedMenuItem, event);
        }
    }

    /**
     * Handles actions for clicks in the PunishMenu.
     * @param player The player who clicked.
     * @param punishMenu The PunishMenu instance.
     * @param action The ClickAction.
     * @param actionData The action data.
     * @param clickedMenuItem The clickedMenuItem.
     * @param event The InventoryClickEvent.
     */
    private void handlePunishMenuActions(Player player, PunishMenu punishMenu, ClickAction action, String actionData, MenuItem clickedMenuItem, InventoryClickEvent event) {
        UUID targetUUID = punishMenu.getTargetUUID();
        switch (action) {
            case OPEN_MENU:
                if (actionData.equalsIgnoreCase("ban_details")) {
                    new PunishDetailsMenu(targetUUID, plugin, BAN_PUNISHMENT_TYPE).open(player);
                } else if (actionData.equalsIgnoreCase("mute_details")) {
                    new PunishDetailsMenu(targetUUID, plugin, MUTE_PUNISHMENT_TYPE).open(player);
                } else if (actionData.equalsIgnoreCase("softban_details")) {
                    new PunishDetailsMenu(targetUUID, plugin, SOFTBAN_PUNISHMENT_TYPE).open(player);
                } else if (actionData.equalsIgnoreCase("kick_details")) {
                    new PunishDetailsMenu(targetUUID, plugin, KICK_PUNISHMENT_TYPE).open(player);
                } else if (actionData.equalsIgnoreCase("warn_details")) {
                    new PunishDetailsMenu(targetUUID, plugin, WARN_PUNISHMENT_TYPE).open(player);
                } else if (actionData.equalsIgnoreCase("change_target")) {
                    player.closeInventory();
                    requestNewTargetName(player);
                } else if (actionData.equalsIgnoreCase("history_menu")) {
                    new HistoryMenu(targetUUID, plugin).open(player);
                }
                break;
            case NO_ACTION:
            default:
                break;
        }
    }

    /**
     * Handles actions for clicks in the PunishDetailsMenu.
     * @param player The player who clicked.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     * @param action The ClickAction.
     * @param actionData The action data.
     * @param event The InventoryClickEvent.
     * @param clickedMenuItem The clickedMenuItem.
     */
    private void handlePunishDetailsMenuActions(Player player, PunishDetailsMenu punishDetailsMenu, ClickAction action, String actionData, InventoryClickEvent event, MenuItem clickedMenuItem) {
        switch (action) {
            case OPEN_MENU:
                if (actionData.equalsIgnoreCase("time_selector")) {
                    new TimeSelectorMenu(punishDetailsMenu, plugin).open(player);
                } else if (actionData.equalsIgnoreCase("punish_menu")) {
                    new PunishMenu(punishDetailsMenu.getTargetUUID(), plugin).open(player);
                }
                break;
            case REQUEST_INPUT:
                if (actionData.equalsIgnoreCase("reason_input")) {
                    requestReasonInput(player, punishDetailsMenu);
                }
                break;
            case CONFIRM_PUNISHMENT:
                handleConfirmButtonClick(player, punishDetailsMenu);
                break;
            case UN_SOFTBAN:
                handleUnsoftbanButtonClick(player, punishDetailsMenu);
                break;
            case NO_ACTION:
            default:
                break;
        }
    }

    /**
     * Handles actions for clicks in the TimeSelectorMenu.
     * @param player The player who clicked.
     * @param timeSelectorMenu The TimeSelectorMenu instance.
     * @param action The ClickAction.
     * @param actionData The action data.
     * @param event The InventoryClickEvent.
     * @param clickedMenuItem The clickedMenuItem.
     */
    private void handleTimeSelectorMenuActions(Player player, TimeSelectorMenu timeSelectorMenu, ClickAction action, String actionData, InventoryClickEvent event, MenuItem clickedMenuItem) {
        PunishDetailsMenu detailsMenu = timeSelectorMenu.getPunishDetailsMenu();
        switch (action) {
            case ADJUST_TIME:
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
                break;
            case REQUEST_INPUT:
                if (actionData.equalsIgnoreCase("custom_time_input")) {
                    player.closeInventory();
                    requestCustomTimeInput(player, detailsMenu);
                }
                break;
            case SET_PUNISHMENT_TYPE:
                if (actionData.equalsIgnoreCase("permanent_time")) {
                    setPermanentTime(detailsMenu, player);
                } else if (actionData.equalsIgnoreCase("confirm_time")) {
                    handleTimeDisplayClick(timeSelectorMenu, detailsMenu, player);
                }
                break;
            case OPEN_MENU:
                if (actionData.equalsIgnoreCase("punish_details")) {
                    detailsMenu.open(player);
                }
                break;
            case NO_ACTION:
            default:
                break;
        }
    }

    /**
     * Handles actions for clicks in the HistoryMenu.
     * @param player The player who clicked.
     * @param historyMenu The HistoryMenu instance.
     * @param action The ClickAction.
     * @param actionData The action data.
     * @param clickedMenuItem The clickedMenuItem.
     * @param event The InventoryClickEvent.
     */
    private void handleHistoryMenuActions(Player player, HistoryMenu historyMenu, ClickAction action, String actionData, MenuItem clickedMenuItem, InventoryClickEvent event) {
        switch (action) {
            case OPEN_MENU:
                if (actionData.equalsIgnoreCase("punish_menu")) {
                    new PunishMenu(historyMenu.getTargetUUID(), plugin).open(player);
                }
                break;
            case ADJUST_PAGE:
                if (actionData.equalsIgnoreCase("next_page")) {
                    historyMenu.nextPage(player);
                } else if (actionData.equalsIgnoreCase("previous_page")) {
                    historyMenu.previousPage(player);
                } else if (actionData.equalsIgnoreCase("no_action")) { // Prevent click action when button is "disabled"
                    // Do nothing, effectively cancelling the click
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
            playSound(player, "menu_open");
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
        if (punishDetailsMenu.isTimeSet() || (!punishDetailsMenu.isTimeRequired())) { // Time is set OR time is not required (for kick/warn)
            if (punishDetailsMenu.isReasonSet()) {
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
     * Sends validation messages to the player if time or reason are not set before confirming punishment.
     * @param player          The player needing validation messages.
     * @param punishDetailsMenu The PunishDetailsMenu instance.
     */
    private void sendValidationMessages(Player player, PunishDetailsMenu punishDetailsMenu) {
        if (!punishDetailsMenu.isTimeSet() && !punishDetailsMenu.isReasonSet() && punishDetailsMenu.isTimeRequired()) { // Check if time is required
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set_time_reason_before_confirm")));
        } else if (!punishDetailsMenu.isTimeSet() && punishDetailsMenu.isTimeRequired()) { // Check if time is required
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set_time_before_confirm")));
        } else if (!punishDetailsMenu.isReasonSet()) {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.set_reason_before_confirm")));
        }
    }
}
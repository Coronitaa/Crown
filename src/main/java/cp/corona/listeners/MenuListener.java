package cp.corona.listeners;

import cp.corona.config.MainConfigManager;
import cp.corona.config.WarnLevel;
import cp.corona.crown.Crown;
import cp.corona.database.ActiveWarningEntry;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.actions.ClickAction;
import cp.corona.menus.items.MenuItem;
import cp.corona.menus.items.MenuItem.ClickActionData;
import cp.corona.menus.mod.LockerMenu;
import cp.corona.menus.profile.*;
import cp.corona.menus.punish.HistoryMenu;
import cp.corona.menus.punish.PunishDetailsMenu;
import cp.corona.menus.punish.PunishMenu;
import cp.corona.menus.punish.TimeSelectorMenu;
import cp.corona.menus.report.ReportDetailsMenu;
import cp.corona.menus.report.ReportsMenu;
import cp.corona.report.ReportStatus;
import cp.corona.utils.ColorUtils;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class MenuListener implements Listener {
    private final Crown plugin;
    private final HashMap<UUID, BukkitTask> inputTimeouts = new HashMap<>();
    private final HashMap<UUID, PunishDetailsMenu> pendingDetailsMenus = new HashMap<>();
    private final HashMap<UUID, String> inputTypes = new HashMap<>();
    private final HashMap<UUID, String> inputOrigin = new HashMap<>();
    private final Map<UUID, BukkitTask> inventorySyncTasks = new HashMap<>();

    // A single, centralized map to handle all pending confirmations for all
    // players.
    private final Map<UUID, ConfirmationContext> pendingConfirmations = new HashMap<>();

    // ADDED START: Fields for report input handling
    private enum ReportInputType {
        FILTER_TARGET_NAME, FILTER_REQUESTER_NAME, ASSIGN_MODERATOR
    }

    private record ReportInputContext(InventoryHolder menu, ReportInputType type, String reportId) {
    }

    private final Map<UUID, ReportInputContext> pendingReportInputs = new HashMap<>();
    // ADDED END

    private final Set<UUID> bypassCloseSync = new HashSet<>();

    private static final Set<Integer> PROFILE_ARMOR_SLOTS = Set.of(10, 19, 28, 37);
    private static final Set<Integer> PROFILE_HAND_SLOTS = Set.of(21, 30);

    private static final String BAN_PUNISHMENT_TYPE = "ban";
    private static final String MUTE_PUNISHMENT_TYPE = "mute";
    private static final String SOFTBAN_PUNISHMENT_TYPE = "softban";
    private static final String KICK_PUNISHMENT_TYPE = "kick";
    private static final String WARN_PUNISHMENT_TYPE = "warn";
    private static final String FREEZE_PUNISHMENT_TYPE = "freeze";

    private static final String MOD_PERMISSION = "crown.mod";
    private static final String USE_PERMISSION = "crown.use";
    private static final String EDIT_INVENTORY_PERMISSION = "crown.profile.editinventory";
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
    private static final String REPORT_ASSIGN_PERMISSION = "crown.report.assign";

    private final Map<UUID, DropConfirmData> dropConfirmations = new HashMap<>();

    private record DropConfirmData(int slot, long timestamp) {
    }

    private final Map<UUID, ConfiscateConfirmData> confiscateConfirmations = new HashMap<>();

    private record ConfiscateConfirmData(int slot, long timestamp, ItemStack itemSnapshot) {
    }

    public MenuListener(Crown plugin) {
        this.plugin = plugin;
    }

    /**
     * Holds the context of a pending confirmation action to allow for proper
     * cancellation and state reversion.
     */
    private record ConfirmationContext(Inventory inventory, int slot, ItemStack originalItem,
            BukkitTask timeoutTask, String confirmationKey) {
    }

    /**
     * Cancels any existing confirmation for a player, reverting the button's visual
     * state.
     * This ensures only one confirmation can be active per player at any time.
     * 
     * @param player The player whose confirmation should be canceled.
     */
    private void cancelExistingConfirmation(Player player) {
        ConfirmationContext existingContext = pendingConfirmations.remove(player.getUniqueId());
        if (existingContext != null) {
            if (!existingContext.timeoutTask.isCancelled()) {
                existingContext.timeoutTask.cancel();
            }
            // Restore the item.
            try {
                existingContext.inventory.setItem(existingContext.slot, existingContext.originalItem);
            } catch (Exception e) {
                // Ignore invalid inventory access
            }
        }
    }

    private String[] getPlaceholders(InventoryHolder holder) {
        if (holder instanceof ReportDetailsMenu menu) {
            DatabaseManager.ReportEntry report = menu.getReportEntry();
            if (report != null && report.getModeratorUUID() != null) {
                OfflinePlayer mod = Bukkit.getOfflinePlayer(report.getModeratorUUID());
                return new String[] { "{moderator}", mod.getName() != null ? mod.getName() : "Unknown" };
            }
        }
        return new String[0];
    }

    private boolean checkAndHandleConfirmation(Player player, InventoryHolder holder, MenuItem item, int slot,
            InventoryClickEvent event) {
        boolean isButton = !item.getLeftClickActions().isEmpty() || !item.getRightClickActions().isEmpty()
                || !item.getShiftLeftClickActions().isEmpty();

        ConfirmationContext context = pendingConfirmations.get(player.getUniqueId());

        if (context != null && context.inventory.equals(event.getClickedInventory()) && context.slot == slot) {
            cancelExistingConfirmation(player);
            return true;
        }

        if (isButton || item.getConfirmState() != null) {
            cancelExistingConfirmation(player);
        }

        if (item.getConfirmState() == null) {
            return true;
        }

        // Special handling for ReportDetailsMenu take_report to skip confirmation if
        // not needed
        if (holder instanceof ReportDetailsMenu menu) {
            for (ClickActionData action : item.getLeftClickActions()) {
                if (action.getAction() == ClickAction.SET_REPORT_STATUS_TAKE) {
                    DatabaseManager.ReportEntry report = menu.getReportEntry();
                    if (report != null && (report.getModeratorUUID() == null
                            || report.getModeratorUUID().equals(player.getUniqueId()))) {
                        return true;
                    }
                }
            }
        }

        OfflinePlayer target = getTargetForAction(holder);
        ItemStack confirmStack = item.getConfirmState().toItemStack(target, plugin.getConfigManager(),
                getPlaceholders(holder));

        ItemMeta meta = confirmStack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.FORTUNE, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            confirmStack.setItemMeta(meta);
        }

        ItemStack current = event.getCurrentItem();
        ItemStack original = current != null ? current.clone() : null;

        event.getClickedInventory().setItem(slot, confirmStack);
        playSound(player, "confirm_again");

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                cancelExistingConfirmation(player);
            }
        }.runTaskLater(plugin, 40L); // 2 seconds

        pendingConfirmations.put(player.getUniqueId(),
                new ConfirmationContext(event.getClickedInventory(), slot, original, task, "GENERIC"));
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder topHolder = event.getView().getTopInventory().getHolder();

        boolean isPluginMenu = topHolder instanceof PunishMenu || topHolder instanceof PunishDetailsMenu ||
                topHolder instanceof TimeSelectorMenu || topHolder instanceof HistoryMenu ||
                topHolder instanceof ProfileMenu || topHolder instanceof FullInventoryMenu ||
                topHolder instanceof EnderChestMenu || topHolder instanceof ReportsMenu
                || topHolder instanceof ReportDetailsMenu;

        if (topHolder instanceof LockerMenu lockerMenu) {
            handleLockerMenuClick(event, player, lockerMenu);
            return;
        }

        if (!isPluginMenu) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        boolean isTopInventoryClick = clickedInventory.equals(event.getView().getTopInventory());

        if (isTopInventoryClick) {
            if (topHolder instanceof ProfileMenu || topHolder instanceof FullInventoryMenu
                    || topHolder instanceof EnderChestMenu) {
                handleInteractiveMenuClick(event, player, topHolder);
            } else {
                handleStaticMenuClick(event, player, topHolder);
            }
        } else {
            if (topHolder instanceof ProfileMenu || topHolder instanceof FullInventoryMenu
                    || topHolder instanceof EnderChestMenu) {
                if (player.hasPermission(EDIT_INVENTORY_PERMISSION) && event.getClick().isShiftClick()) {
                    event.setCancelled(true);
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    private void handleLockerMenuClick(InventoryClickEvent event, Player player, LockerMenu menu) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null)
            return;

        // --- Bottom Inventory (Player Inv) - Add Item to Locker ---
        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            cancelExistingConfirmation(player);
            if (event.isLeftClick()) {
                if (!menu.isEditable) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.locker_read_only");
                    return;
                }

                if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.locker_read_only_in_mod");
                    return;
                }

                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR)
                    return;

                String serialized = AuditLogBook.serialize(clicked);
                plugin.getSoftBanDatabaseManager().addConfiscatedItem(serialized, menu.getOwnerUUID(), "Manual Upload")
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                            event.getClickedInventory().setItem(event.getSlot(), null);
                            menu.loadPageAsync();
                            MessageUtils.sendConfigMessage(plugin, player, "messages.locker_item_added");
                        }));
            }
            return;
        }

        // --- Top Inventory (Locker) ---
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            int slot = event.getSlot();
            ItemStack clicked = event.getCurrentItem();

            // Controls logic (Pagination / Clear)
            MenuItem itemConfig = getMenuItemClicked(slot, menu);
            if (itemConfig != null) {
                if (!checkAndHandleConfirmation(player, menu, itemConfig, slot, event)) {
                    return;
                }

                if (itemConfig.getLeftClickActions() != null) {
                    for (MenuItem.ClickActionData action : itemConfig.getLeftClickActions()) {
                        if (action.getAction() == ClickAction.ADJUST_PAGE) {
                            if ("next_page".equals(action.getActionData()[0]))
                                menu.nextPage();
                            if ("previous_page".equals(action.getActionData()[0]))
                                menu.prevPage();
                        } else if (action.getAction() == ClickAction.REQUEST_CLEAR_FULL_INVENTORY) {
                            executeClear(player, null, "locker");
                        }
                    }
                    return;
                }
            } else {
                if (clicked != null && clicked.getType() != Material.AIR) {
                    NamespacedKey idKey = new NamespacedKey(plugin, "locker_item_id");
                    ItemMeta clickedMeta = clicked.getItemMeta();
                    if (clickedMeta != null
                            && clickedMeta.getPersistentDataContainer().has(idKey, PersistentDataType.INTEGER)) {
                        cancelExistingConfirmation(player);
                    }
                }
            }

            if (clicked == null || clicked.getType() == Material.AIR)
                return;

            NamespacedKey idKey = new NamespacedKey(plugin, "locker_item_id");
            ItemMeta clickedMeta = clicked.getItemMeta();
            if (clickedMeta == null || !clickedMeta.getPersistentDataContainer().has(idKey, PersistentDataType.INTEGER))
                return;
            Integer dbId = clickedMeta.getPersistentDataContainer().get(idKey, PersistentDataType.INTEGER);
            if (dbId == null)
                return;

            // --- DELETE (Drop Key) ---
            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                if (!menu.isEditable) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.locker_read_only");
                    return;
                }
                // Allowed in Mod Mode (exception)

                DropConfirmData last = dropConfirmations.get(player.getUniqueId());
                long now = System.currentTimeMillis();

                if (last != null && last.slot() == slot) {
                    long diff = now - last.timestamp();

                    // DEBOUNCE PARA LA Q EN EL LOCKER
                    if (diff < 500) {
                        return; // Ignorar si es demasiado rápido (anti-spam / tecla apretada)
                    }

                    if (diff < 3000) {
                        // Confirmado
                        plugin.getSoftBanDatabaseManager().removeConfiscatedItem(dbId).thenAccept(success -> {
                            if (success) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    menu.loadPageAsync();
                                    MessageUtils.sendConfigMessage(plugin, player, "messages.locker_item_deleted");
                                    playSound(player, "punish_confirm");
                                });
                            }
                        });
                        dropConfirmations.remove(player.getUniqueId());
                        return;
                    }
                }

                // Primer Drop o tiempo expirado
                dropConfirmations.put(player.getUniqueId(), new DropConfirmData(slot, now));
                MessageUtils.sendConfigMessage(plugin, player, "messages.locker_drop_confirm");
                playSound(player, "confirm_again");

            }
            // --- TAKE / COPY (Right Click) ---
            else if (event.isRightClick()) {
                // Block both Take and Copy in Mod Mode
                if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.locker_read_only_in_mod");
                    return;
                }

                boolean isShift = event.isShiftClick();

                if (!isShift) {
                    if (!menu.isEditable) {
                        MessageUtils.sendConfigMessage(plugin, player, "messages.locker_read_only");
                        return;
                    }
                }

                ItemStack itemToGive = clicked.clone();
                ItemMeta meta = itemToGive.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().remove(idKey);
                    List<String> lore = meta.getLore();
                    if (lore != null) {
                        List<String> originalLore = new ArrayList<>();
                        String separator = MessageUtils.getColorMessage("&8&m----------------");
                        for (String line : lore) {
                            if (line.equals(separator))
                                break;
                            originalLore.add(line);
                        }
                        meta.setLore(originalLore);
                    }
                    itemToGive.setItemMeta(meta);
                }

                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(itemToGive);
                if (!overflow.isEmpty()) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_hotbar_full");
                    return;
                }

                if (!isShift) {
                    // Remove from DB
                    plugin.getSoftBanDatabaseManager().removeConfiscatedItem(dbId).thenRun(menu::loadPageAsync);
                    MessageUtils.sendConfigMessage(plugin, player, "messages.locker_item_taken");
                } else {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.locker_item_copied");
                }
            }
        }
    }

    private void handleInteractiveMenuClick(InventoryClickEvent event, Player player, InventoryHolder holder) {
        MenuItem clickedMenuItem = getMenuItemClicked(event.getRawSlot(), holder);
        boolean isStaticButton = clickedMenuItem != null && clickedMenuItem.getLeftClickActions() != null
                && !clickedMenuItem.getLeftClickActions().isEmpty();

        if (isStaticButton) {
            handleStaticMenuClick(event, player, holder);
            return;
        }

        cancelExistingConfirmation(player);

        if (!player.hasPermission(EDIT_INVENTORY_PERMISSION)) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == null)
            return;

        int clickedSlot = event.getRawSlot();
        boolean isTopInventory = event.getClickedInventory() == event.getView().getTopInventory();

        if (isTopInventory) {
            boolean isEditableSlot;
            if (holder instanceof ProfileMenu) {
                isEditableSlot = PROFILE_ARMOR_SLOTS.contains(clickedSlot) || PROFILE_HAND_SLOTS.contains(clickedSlot);
            } else if (holder instanceof FullInventoryMenu) {
                isEditableSlot = clickedSlot >= 0 && clickedSlot < 36;
            } else {
                isEditableSlot = clickedSlot >= 0 && clickedSlot < 27;
            }

            event.setCancelled(true);

            if (isEditableSlot) {
                ClickType click = event.getClick();
                ItemStack cursorItem = event.getCursor();
                ItemStack currentItem = event.getCurrentItem();

                boolean isSlotEmpty = currentItem == null || currentItem.getType() == Material.AIR;
                boolean isCursorEmpty = cursorItem == null || cursorItem.getType() == Material.AIR;

                // 1. Confiscation (Removal)
                if (!isSlotEmpty) {
                    if (click == ClickType.LEFT && isCursorEmpty) {
                        handleConfiscation(player, holder, clickedSlot, currentItem);
                        return;
                    }

                    // Block other removal methods (Right click, Shift click, Swapping)
                    if (click.isRightClick() || click.isShiftClick() || !isCursorEmpty) {
                        return;
                    }
                }

                // 2. Placing items (Only allowed if slot is empty)
                if (isSlotEmpty && !isCursorEmpty && (click.isLeftClick() || click.isRightClick())) {
                    event.getView().setCursor(currentItem);
                    event.getClickedInventory().setItem(clickedSlot, cursorItem);
                    logItemAction(player.getUniqueId(), getTargetForAction(holder).getUniqueId(), cursorItem,
                            currentItem, holder);
                    Bukkit.getScheduler().runTask(plugin,
                            () -> synchronizeInventory(holder, event.getView().getTopInventory()));
                }
            }
        }
    }

    private void handleConfiscation(Player player, InventoryHolder holder, int slot, ItemStack item) {
        UUID playerUUID = player.getUniqueId();
        ConfiscateConfirmData lastClick = confiscateConfirmations.get(playerUUID);
        long now = System.currentTimeMillis();

        if (lastClick == null || lastClick.slot() != slot || !lastClick.itemSnapshot().isSimilar(item)
                || lastClick.itemSnapshot().getAmount() != item.getAmount()) {
            confiscateConfirmations.put(playerUUID, new ConfiscateConfirmData(slot, now, item.clone()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
            MessageUtils.sendConfigMessage(plugin, player, "messages.confiscate_confirm");
            return;
        }

        long diff = now - lastClick.timestamp();
        if (diff < 200)
            return;

        if (diff <= 2000) {
            confiscateConfirmations.put(playerUUID, new ConfiscateConfirmData(slot, now, item.clone()));

            String serialized = AuditLogBook.serialize(item);

            OfflinePlayer target = getTargetForAction(holder);
            String targetName = (target != null && target.getName() != null) ? target.getName() : "Unknown";

            String containerType = "Inventory";
            if (holder instanceof ProfileMenu)
                containerType = "Profile Menu";
            else if (holder instanceof FullInventoryMenu)
                containerType = "Full Inventory Menu";
            else if (holder instanceof EnderChestMenu)
                containerType = "Ender Chest Menu";

            String source = containerType + " (" + targetName + ")";

            plugin.getSoftBanDatabaseManager().addConfiscatedItem(serialized, playerUUID, source)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        player.getOpenInventory().getTopInventory().setItem(slot, null);
                        logItemAction(player.getUniqueId(), getTargetForAction(holder).getUniqueId(), null, item,
                                holder);
                        synchronizeInventory(holder, player.getOpenInventory().getTopInventory());
                        MessageUtils.sendConfigMessage(plugin, player, "messages.item_confiscated");
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
                    }));
        } else {
            confiscateConfirmations.put(playerUUID, new ConfiscateConfirmData(slot, now, item.clone()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
            MessageUtils.sendConfigMessage(plugin, player, "messages.confiscate_confirm");
        }
    }

    private void logItemAction(UUID executorUUID, UUID targetUUID, ItemStack cursorItem, ItemStack currentItem,
            InventoryHolder holder) {
        boolean cursorEmpty = (cursorItem == null || cursorItem.getType() == Material.AIR);
        boolean currentEmpty = (currentItem == null || currentItem.getType() == Material.AIR);

        String action = null;
        ItemStack involvedItem = null;

        if (!cursorEmpty && currentEmpty) {
            action = "ITEM_ADD";
            involvedItem = cursorItem;
        } else if (cursorEmpty && !currentEmpty) {
            action = "ITEM_REMOVE";
            involvedItem = currentItem;
        } else if (!cursorEmpty) {
            action = "ITEM_MOVE";
            involvedItem = cursorItem;
        }

        if (action != null) {
            String inventoryType;
            if (holder instanceof FullInventoryMenu) {
                inventoryType = "_INVENTORY";
            } else if (holder instanceof EnderChestMenu) {
                inventoryType = "_ENDERCHEST";
            } else if (holder instanceof ProfileMenu) {
                inventoryType = "_PROFILE";
            } else {
                inventoryType = "";
            }

            String actionType = action + inventoryType;
            String details = involvedItem.getAmount() + ":" + AuditLogBook.serialize(involvedItem);
            plugin.getSoftBanDatabaseManager().logOperatorAction(targetUUID, executorUUID, actionType, details);
        }
    }

    private void executeClear(Player moderator, OfflinePlayer target, String type) {
        if ("locker".equals(type)) {
            // Get the menu instance to check ownership
            if (!(moderator.getOpenInventory().getTopInventory().getHolder() instanceof LockerMenu menu))
                return;

            if (!menu.isEditable) {
                MessageUtils.sendConfigMessage(plugin, moderator, "messages.locker_read_only");
                return;
            }
            // Allowed in Mod Mode (exception)

            // Clear items for the LOCKER OWNER
            plugin.getSoftBanDatabaseManager().clearConfiscatedItems(menu.getOwnerUUID())
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        menu.loadPageAsync();
                        MessageUtils.sendConfigMessage(plugin, moderator, "messages.locker_cleared");
                        playSound(moderator, "punish_confirm");
                    }));
            return;
        }
        UUID moderatorId = moderator.getUniqueId();
        UUID targetId = target.getUniqueId();

        Player targetPlayer = Bukkit.getPlayer(targetId);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sendConfigMessage(moderator, "messages.player_not_online", "{input}", target.getName());
            return;
        }

        int clearedCount;
        String actionType;

        if (type.equals("full")) {
            PlayerInventory inv = targetPlayer.getInventory();
            clearedCount = (int) Arrays.stream(inv.getContents())
                    .filter(item -> item != null && item.getType() != Material.AIR).count();
            inv.clear();
            inv.setArmorContents(new ItemStack[4]);
            inv.setItemInOffHand(null);
            actionType = "CLEAR_INVENTORY";
        } else { // "ender"
            Inventory enderChest = targetPlayer.getEnderChest();
            clearedCount = (int) Arrays.stream(enderChest.getContents())
                    .filter(item -> item != null && item.getType() != Material.AIR).count();
            enderChest.clear();
            actionType = "CLEAR_ENDER_CHEST";
        }

        plugin.getSoftBanDatabaseManager().logOperatorAction(targetId, moderatorId, actionType,
                String.valueOf(clearedCount));

        sendConfigMessage(moderator, "messages.clear_inventory_success", "{target}", target.getName(),
                "{inventory_type}", type.equals("full") ? "inventory" : "ender chest");
        playSound(moderator, "punish_confirm");

        bypassCloseSync.add(moderatorId);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (type.equals("full")) {
                new FullInventoryMenu(targetId, plugin).open(moderator);
            } else {
                new EnderChestMenu(targetId, plugin).open(moderator);
            }
        });
    }

    private void handleShiftClick(PlayerInventory playerInv, ItemStack itemToMove) {
        if (itemToMove == null || itemToMove.getType() == Material.AIR)
            return;
        playerInv.addItem(itemToMove.clone());
    }

    private void handleStaticMenuClick(InventoryClickEvent event, Player player, InventoryHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (event.getClickedInventory() == null || clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        MenuItem clickedMenuItem = getMenuItemClicked(event.getRawSlot(), holder);
        if (clickedMenuItem != null) {
            if (!checkAndHandleConfirmation(player, holder, clickedMenuItem, event.getRawSlot(), event)) {
                return;
            }

            clickedMenuItem.playClickSound(player);

            List<MenuItem.ClickActionData> actionsToExecute = Collections.emptyList();
            if (event.isLeftClick()) {
                actionsToExecute = clickedMenuItem.getLeftClickActions();
            } else if (event.isRightClick()) {
                actionsToExecute = clickedMenuItem.getRightClickActions();
            }

            if (!actionsToExecute.isEmpty()) {
                for (MenuItem.ClickActionData actionData : actionsToExecute) {
                    handleMenuItemClick(player, holder, actionData.getAction(), actionData.getActionData(), event,
                            clickedMenuItem);
                }
            }
        } else {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] Clicked on slot " + event.getRawSlot() + " in "
                        + holder.getClass().getSimpleName() + " with no associated MenuItem found.");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ProfileMenu) && !(holder instanceof FullInventoryMenu)
                && !(holder instanceof EnderChestMenu)) {
            return;
        }

        if (event.getWhoClicked() instanceof Player player) {
            if (!player.hasPermission(EDIT_INVENTORY_PERMISSION)) {
                event.setCancelled(true);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> synchronizeInventory(holder, event.getInventory()));
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getPlayer() instanceof Player player))
            return;

        if (holder instanceof PunishMenu || holder instanceof PunishDetailsMenu || holder instanceof TimeSelectorMenu
                || holder instanceof HistoryMenu || holder instanceof ReportsMenu
                || holder instanceof ReportDetailsMenu) {
            executeMenuOpenActions(player, holder);
        } else if (holder instanceof ProfileMenu || holder instanceof FullInventoryMenu
                || holder instanceof EnderChestMenu) {
            executeMenuOpenActions(player, holder);
            startInventorySyncTask(player, holder);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        // Always cancel any pending confirmation when a menu is closed.
        cancelExistingConfirmation(player);
        confiscateConfirmations.remove(player.getUniqueId());

        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof ProfileMenu || holder instanceof FullInventoryMenu || holder instanceof EnderChestMenu) {
            stopInventorySyncTask(player);
            if (bypassCloseSync.remove(player.getUniqueId())) {
                return;
            }
            if (player.hasPermission(EDIT_INVENTORY_PERMISSION)) {
                synchronizeInventory(holder, event.getInventory());
            }
        }

        // Clear any pending chat inputs for this player
        if (pendingReportInputs.containsKey(player.getUniqueId()) || inputTimeouts.containsKey(player.getUniqueId())) {
            clearPlayerInputData(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Close any open profile/inventory menus if the target disconnects
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = onlinePlayer.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof ProfileMenu || holder instanceof FullInventoryMenu
                    || holder instanceof EnderChestMenu) {
                OfflinePlayer target = getTargetForAction(holder);
                if (target != null && target.getUniqueId().equals(playerUUID)) {
                    onlinePlayer.closeInventory();
                    sendConfigMessage(onlinePlayer, "messages.player_not_online", "{input}", player.getName());
                }
            }
        }
    }

    private void startInventorySyncTask(Player moderator, InventoryHolder holder) {
        stopInventorySyncTask(moderator);

        UUID targetUUID = getTargetForAction(holder).getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player target = Bukkit.getPlayer(targetUUID);
                if (!moderator.isOnline() || moderator.getOpenInventory().getTopInventory().getHolder() != holder
                        || target == null || !target.isOnline()) {
                    this.cancel();
                    inventorySyncTasks.remove(moderator.getUniqueId());
                    return;
                }

                Inventory guiInventory = moderator.getOpenInventory().getTopInventory();

                if (holder instanceof ProfileMenu) {
                    PlayerInventory targetInv = target.getInventory();
                    updateSlotIfNeeded(guiInventory, 10, targetInv.getHelmet());
                    updateSlotIfNeeded(guiInventory, 19, targetInv.getChestplate());
                    updateSlotIfNeeded(guiInventory, 28, targetInv.getLeggings());
                    updateSlotIfNeeded(guiInventory, 37, targetInv.getBoots());
                    updateSlotIfNeeded(guiInventory, 21, targetInv.getItemInMainHand());
                    updateSlotIfNeeded(guiInventory, 30, targetInv.getItemInOffHand());
                } else if (holder instanceof FullInventoryMenu) {
                    PlayerInventory targetInv = target.getInventory();
                    for (int i = 0; i < 36; i++) {
                        updateSlotIfNeeded(guiInventory, i, targetInv.getItem(i));
                    }
                } else if (holder instanceof EnderChestMenu) {
                    Inventory targetEnderChest = target.getEnderChest();
                    for (int i = 0; i < 27; i++) {
                        updateSlotIfNeeded(guiInventory, i, targetEnderChest.getItem(i));
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);

        inventorySyncTasks.put(moderator.getUniqueId(), task);
    }

    private void stopInventorySyncTask(Player moderator) {
        BukkitTask task = inventorySyncTasks.remove(moderator.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void updateSlotIfNeeded(Inventory gui, int slot, ItemStack playerItem) {
        ItemStack guiItem = gui.getItem(slot);
        if (!Objects.equals(guiItem, playerItem)) {
            gui.setItem(slot, playerItem);
        }
    }

    private void synchronizeInventory(InventoryHolder holder, Inventory guiInventory) {
        UUID targetUUID;
        if (holder instanceof ProfileMenu profileMenu) {
            targetUUID = profileMenu.getTargetUUID();
        } else if (holder instanceof FullInventoryMenu inventoryMenu) {
            targetUUID = inventoryMenu.getTargetUUID();
        } else if (holder instanceof EnderChestMenu enderChestMenu) {
            targetUUID = enderChestMenu.getTargetUUID();
        } else {
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            return;
        }

        if (holder instanceof ProfileMenu) {
            PlayerInventory targetInv = targetPlayer.getInventory();
            targetInv.setHelmet(guiInventory.getItem(10));
            targetInv.setChestplate(guiInventory.getItem(19));
            targetInv.setLeggings(guiInventory.getItem(28));
            targetInv.setBoots(guiInventory.getItem(37));
            targetInv.setItemInMainHand(guiInventory.getItem(21));
            targetInv.setItemInOffHand(guiInventory.getItem(30));
        } else if (holder instanceof FullInventoryMenu) {
            PlayerInventory targetInv = targetPlayer.getInventory();
            for (int i = 0; i < 36; i++) {
                targetInv.setItem(i, guiInventory.getItem(i));
            }
        } else {
            Inventory targetEnderChest = targetPlayer.getEnderChest();
            for (int i = 0; i < 27; i++) {
                targetEnderChest.setItem(i, guiInventory.getItem(i));
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (plugin.getReportBookManager().isAwaitingInput(player)) {
            event.setCancelled(true);
            String message = event.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getReportBookManager().handleChatInput(player, message));
            return;
        }

        if (pendingReportInputs.containsKey(playerUUID)) {
            event.setCancelled(true);
            String message = event.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> handleReportChatInput(player, message));
            return;
        }

        if (!inputTimeouts.containsKey(playerUUID)) {
            return;
        }

        if (plugin.getPluginFrozenPlayers().containsKey(playerUUID) && !player.hasPermission(MOD_PERMISSION)) {
            event.setCancelled(true);
            sendConfigMessage(player, "messages.freeze_command_blocked");
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handlePlayerInput(player, message));
    }

    private String processAllPlaceholders(String text, CommandSender executor, InventoryHolder holder) {
        if (text == null)
            return null;

        OfflinePlayer target = getTargetForAction(holder);
        String processedText = plugin.getConfigManager().processPlaceholders(text, target);

        if (executor != null) {
            processedText = processedText.replace("{player}", executor.getName());

            if (executor instanceof Player && plugin.isPlaceholderAPIEnabled()) {
                processedText = PlaceholderAPI.setPlaceholders((Player) executor, processedText);
            }
        }

        return processedText;
    }

    private void handleMenuItemClick(Player player, InventoryHolder holder, ClickAction action, String[] actionData,
            InventoryClickEvent event, MenuItem clickedMenuItem) {
        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger()
                    .info("[DEBUG] handleMenuItemClick - START - Action: " + action + ", ActionData: "
                            + Arrays.toString(actionData) + ", Item: "
                            + (clickedMenuItem != null ? clickedMenuItem.getName() : "null") + ", Holder Type: "
                            + holder.getClass().getSimpleName());

        switch (action) {
            case REQUEST_CLEAR_FULL_INVENTORY -> {
                executeClear(player, getTargetForAction(holder), "full");
                return;
            }
            case REQUEST_CLEAR_ENDER_CHEST -> {
                executeClear(player, getTargetForAction(holder), "ender");
                return;
            }
            case REQUEST_CLEAR_LOCKER -> {
                executeClear(player, null, "locker");
                return;
            }
        }

        boolean handledByMenuSpecific = false;
        if (holder instanceof PunishMenu punishMenu) {
            handledByMenuSpecific = handlePunishMenuActions(player, punishMenu, action, actionData);
        } else if (holder instanceof PunishDetailsMenu punishDetailsMenu) {
            handledByMenuSpecific = handlePunishDetailsMenuActions(player, punishDetailsMenu, action, actionData);
        } else if (holder instanceof TimeSelectorMenu timeSelectorMenu) {
            handledByMenuSpecific = handleTimeSelectorMenuActions(player, timeSelectorMenu, action, actionData);
        } else if (holder instanceof HistoryMenu historyMenu) {
            handledByMenuSpecific = handleHistoryMenuActions(player, historyMenu, action, actionData);
        } else if (holder instanceof ProfileMenu profileMenu) {
            handledByMenuSpecific = handleProfileMenuActions(player, profileMenu, action, actionData);
        } else if (holder instanceof FullInventoryMenu fullInventoryMenu) {
            handledByMenuSpecific = handleFullInventoryMenuActions(player, fullInventoryMenu, action, actionData);
        } else if (holder instanceof EnderChestMenu enderChestMenu) {
            handledByMenuSpecific = handleEnderChestMenuActions(player, enderChestMenu, action, actionData);
        } else if (holder instanceof ReportsMenu reportsMenu) {
            handledByMenuSpecific = handleReportsMenuActions(player, reportsMenu, action, actionData, event);
        } else if (holder instanceof ReportDetailsMenu reportDetailsMenu) {
            handledByMenuSpecific = handleReportDetailsMenuActions(player, reportDetailsMenu, action, actionData,
                    event);
        }

        if (!handledByMenuSpecific) {
            switch (action) {
                case CONSOLE_COMMAND -> executeConsoleCommand(player, actionData, holder);
                case PLAYER_COMMAND, PLAYER_COMMAND_OP -> executeCommandAction(player, action, actionData, holder);
                case CLOSE_MENU -> player.closeInventory();
                case PLAY_SOUND -> executePlaySoundAction(player, actionData);
                case TITLE -> executeTitleAction(player, actionData, holder);
                case MESSAGE -> executeMessageAction(player, actionData, holder);
                case ACTIONBAR -> executeActionbarAction(player, actionData, holder);
                case PLAY_SOUND_TARGET -> executePlaySoundTargetAction(holder, actionData);
                case TITLE_TARGET -> executeTitleTargetAction(player, holder, actionData);
                case MESSAGE_TARGET -> executeMessageTargetAction(player, holder, actionData);
                case ACTIONBAR_TARGET -> executeActionbarTargetAction(player, holder, actionData);
                case GIVE_EFFECT_TARGET -> executeGiveEffectTargetAction(holder, actionData);
                case PLAY_SOUND_MODS -> executePlaySoundModsAction(actionData);
                case TITLE_MODS -> executeTitleModsAction(player, holder, actionData);
                case MESSAGE_MODS -> executeMessageModsAction(player, holder, actionData);
                case OPEN_AUDIT_LOG -> executeOpenAuditLogAction(player, actionData, holder);
                case OPEN_PROFILE_TARGET -> executeOpenProfileTargetAction(player, actionData, holder);
                case HISTORY_TARGET -> executeHistoryTargetAction(player, actionData, holder);
                case OPEN_REPORTS_MENU -> new ReportsMenu(plugin, player).open(player);
                default -> {
                    if (plugin.getConfigManager().isDebugEnabled() && action != ClickAction.NO_ACTION) {
                        plugin.getLogger().info("[DEBUG] Action " + action
                                + " was not handled by common handlers (expected if menu-specific).");
                    }
                }
            }
        }

        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] handleMenuItemClick - END - Action: " + action + ", ActionData: "
                    + Arrays.toString(actionData));
    }

    public void executeMenuItemAction(Player player, ClickAction action, String[] actionData, InventoryHolder holder) {
        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] executeMenuItemAction - START - Player: " + player.getName() + ", Action: "
                    + action + ", ActionData: " + Arrays.toString(actionData));

        String[] processedActionData = new String[actionData.length];
        for (int i = 0; i < actionData.length; i++) {
            processedActionData[i] = processAllPlaceholders(actionData[i], player, holder);
        }

        switch (action) {
            case CONSOLE_COMMAND -> executeConsoleCommand(player, processedActionData, holder);
            case PLAYER_COMMAND, PLAYER_COMMAND_OP -> executeCommandAction(player, action, processedActionData, holder);
            case PLAY_SOUND -> executePlaySoundAction(player, processedActionData);
            case TITLE -> executeTitleAction(player, processedActionData, holder);
            case MESSAGE -> executeMessageAction(player, processedActionData, holder);
            case ACTIONBAR -> executeActionbarAction(player, processedActionData, holder);
            default ->
                plugin.getLogger().warning("[WARNING] executeMenuItemAction called with context-dependent action ("
                        + action + ") without inventory context. Action skipped for player " + player.getName() + ".");
        }

        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger()
                    .info("[DEBUG] executeMenuItemAction - END - Player: " + player.getName() + ", Action: " + action);
    }

    public void executeMenuOpenActions(Player player, InventoryHolder holder) {
        List<MenuItem.ClickActionData> openActions;
        FileConfiguration config = null;
        String path = null;

        if (holder instanceof PunishMenu) {
            config = plugin.getConfigManager().getPunishMenuConfig().getConfig();
            path = "menu";
        } else if (holder instanceof PunishDetailsMenu detailsMenu) {
            config = plugin.getConfigManager().getPunishDetailsMenuConfig().getConfig();
            path = "menu.punish_details." + detailsMenu.getPunishmentType();
        } else if (holder instanceof TimeSelectorMenu) {
            config = plugin.getConfigManager().getTimeSelectorMenuConfig().getConfig();
            path = "menu";
        } else if (holder instanceof HistoryMenu) {
            config = plugin.getConfigManager().getHistoryMenuConfig().getConfig();
            path = "menu";
        } else if (holder instanceof ProfileMenu) {
            config = plugin.getConfigManager().getProfileMenuConfig().getConfig();
            path = "menu";
        } else if (holder instanceof FullInventoryMenu) {
            config = plugin.getConfigManager().getFullInventoryMenuConfig().getConfig();
            path = "menu";
        } else if (holder instanceof EnderChestMenu) {
            config = plugin.getConfigManager().getEnderChestMenuConfig().getConfig();
            path = "menu";
        } else if (holder instanceof ReportsMenu) {
            config = plugin.getConfigManager().getReportsMenuConfig().getConfig();
            path = "menu";
        } else if (holder instanceof ReportDetailsMenu) {
            config = plugin.getConfigManager().getReportDetailsMenuConfig().getConfig();
            path = "menu";
        }

        if (config != null) {
            openActions = plugin.getConfigManager().loadMenuOpenActions(config, path);
            if (!openActions.isEmpty() && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Executing " + openActions.size() + " open actions for "
                        + holder.getClass().getSimpleName());
            }
            for (MenuItem.ClickActionData actionData : openActions) {
                executeMenuItemAction(player, actionData.getAction(), actionData.getActionData(), holder);
            }
        } else if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info(
                    "[DEBUG] No valid config/path found for open actions for " + holder.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("deprecation")
    private void executePlaySoundAction(Player player, String[] soundArgs) {
        if (soundArgs == null || soundArgs.length == 0 || soundArgs[0] == null || soundArgs[0].isEmpty()) {
            plugin.getLogger().warning("PLAY_SOUND action requires a non-empty data string.");
            return;
        }
        String[] parts = soundArgs[0].split(":");
        if (parts.length < 1) {
            plugin.getLogger().warning("PLAY_SOUND action requires at least a sound name.");
            return;
        }
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            player.playSound(player.getLocation(), sound, volume, pitch);
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] PLAY_SOUND played '" + sound + "' for " + player.getName());
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND: " + Arrays.toString(parts));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND: " + parts[0]);
        }
    }

    private void executeTitleAction(Player player, String[] titleArgs, InventoryHolder holder) {
        if (titleArgs == null || titleArgs.length == 0 || titleArgs[0] == null || titleArgs[0].isEmpty()) {
            plugin.getLogger().warning("TITLE action requires a non-empty data string.");
            return;
        }
        String[] parts = titleArgs[0].split(":");
        if (parts.length < 3) {
            plugin.getLogger().warning("TITLE action requires at least title, subtitle, and time_seconds arguments.");
            return;
        }
        String titleText = MessageUtils.getColorMessage(processAllPlaceholders(parts[0], player, holder));
        String subtitleText = MessageUtils.getColorMessage(processAllPlaceholders(parts[1], player, holder));
        try {
            int timeSeconds = Integer.parseInt(parts[2]);
            int fadeInTicks = parts.length > 3 ? Integer.parseInt(parts[3]) : 10;
            int fadeOutTicks = parts.length > 4 ? Integer.parseInt(parts[4]) : 20;
            player.sendTitle(titleText, subtitleText, fadeInTicks, timeSeconds * 20, fadeOutTicks);
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] TITLE sent to " + player.getName() + ": " + titleText);
        } catch (NumberFormatException e) {
            plugin.getLogger()
                    .warning("Invalid number format for time/fade values in TITLE action: " + Arrays.toString(parts));
        }
    }

    private void executeMessageAction(Player player, String[] messageArgs, InventoryHolder holder) {
        String messageText = (messageArgs != null && messageArgs.length > 0) ? messageArgs[0] : null;

        if (messageText == null) {
            plugin.getLogger().warning("MESSAGE action requires a non-null message text argument.");
            return;
        }

        messageText = MessageUtils.getColorMessage(processAllPlaceholders(messageText, player, holder));

        player.sendMessage(messageText);
        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] MESSAGE sent to " + player.getName() + ": " + messageText);
    }

    private void executeActionbarAction(Player player, String[] messageArgs, InventoryHolder holder) {
        String messageText = (messageArgs != null && messageArgs.length > 0) ? messageArgs[0] : null;

        if (messageText == null) {
            plugin.getLogger().warning("ACTIONBAR action requires a non-null message text argument.");
            return;
        }
        messageText = MessageUtils.getColorMessage(processAllPlaceholders(messageText, player, holder));

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(messageText));
        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] ACTIONBAR sent to " + player.getName() + ": " + messageText);
    }

    @SuppressWarnings("deprecation")
    private void executePlaySoundTargetAction(InventoryHolder holder, String[] soundArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] PLAY_SOUND_TARGET skipped: Target offline or null.");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (targetPlayer == null)
            return;

        if (soundArgs == null || soundArgs.length == 0 || soundArgs[0] == null || soundArgs[0].isEmpty()) {
            plugin.getLogger().warning("PLAY_SOUND_TARGET action requires a non-empty data string.");
            return;
        }
        String[] parts = soundArgs[0].split(":");
        if (parts.length < 1) {
            plugin.getLogger().warning("PLAY_SOUND_TARGET action requires at least a sound name.");
            return;
        }
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            targetPlayer.playSound(targetPlayer.getLocation(), sound, volume, pitch);
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger()
                        .info("[DEBUG] PLAY_SOUND_TARGET played '" + sound + "' for " + targetPlayer.getName());
        } catch (NumberFormatException e) {
            plugin.getLogger()
                    .warning("Invalid volume or pitch format for PLAY_SOUND_TARGET: " + Arrays.toString(parts));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND_TARGET: " + parts[0]);
        }
    }

    private void executeTitleTargetAction(Player player, InventoryHolder holder, String[] titleArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] TITLE_TARGET skipped: Target offline or null.");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (targetPlayer == null)
            return;
        if (titleArgs == null || titleArgs.length == 0 || titleArgs[0] == null || titleArgs[0].isEmpty()) {
            plugin.getLogger().warning("TITLE_TARGET action requires a non-empty data string.");
            return;
        }
        String[] parts = titleArgs[0].split(":");
        if (parts.length < 3) {
            plugin.getLogger()
                    .warning("TITLE_TARGET action requires at least title, subtitle, and time_seconds arguments.");
            return;
        }

        String titleText = MessageUtils.getColorMessage(processAllPlaceholders(parts[0], player, holder));
        String subtitleText = MessageUtils.getColorMessage(processAllPlaceholders(parts[1], player, holder));
        try {
            int timeSeconds = Integer.parseInt(parts[2]);
            int fadeInTicks = parts.length > 3 ? Integer.parseInt(parts[3]) : 10;
            int fadeOutTicks = parts.length > 4 ? Integer.parseInt(parts[4]) : 20;
            targetPlayer.sendTitle(titleText, subtitleText, fadeInTicks, timeSeconds * 20, fadeOutTicks);
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] TITLE_TARGET sent to " + targetPlayer.getName() + ": " + titleText);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning(
                    "Invalid number format for time/fade values in TITLE_TARGET action: " + Arrays.toString(parts));
        }
    }

    private void executeMessageTargetAction(Player player, InventoryHolder holder, String[] messageArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] MESSAGE_TARGET skipped: Target offline or null.");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (targetPlayer == null)
            return;

        String messageText = (messageArgs != null && messageArgs.length > 0) ? messageArgs[0] : null;

        if (messageText == null) {
            plugin.getLogger().warning("MESSAGE_TARGET action requires a non-null message text argument.");
            return;
        }

        messageText = MessageUtils.getColorMessage(processAllPlaceholders(messageText, player, holder));

        targetPlayer.sendMessage(messageText);
        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] MESSAGE_TARGET sent to " + targetPlayer.getName() + ": " + messageText);
    }

    private void executeActionbarTargetAction(Player player, InventoryHolder holder, String[] messageArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] ACTIONBAR_TARGET skipped: Target offline or null.");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (targetPlayer == null)
            return;

        String messageText = (messageArgs != null && messageArgs.length > 0) ? messageArgs[0] : null;

        if (messageText == null) {
            plugin.getLogger().warning("ACTIONBAR_TARGET action requires a non-null message text argument.");
            return;
        }

        messageText = MessageUtils.getColorMessage(processAllPlaceholders(messageText, player, holder));

        targetPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(messageText));
        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] ACTIONBAR_TARGET sent to " + targetPlayer.getName() + ": " + messageText);
    }

    private void executeGiveEffectTargetAction(InventoryHolder holder, String[] effectArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] GIVE_EFFECT_TARGET skipped: Target offline or null.");
            return;
        }
        Player targetPlayer = target.getPlayer();
        if (targetPlayer == null)
            return;
        if (effectArgs == null || effectArgs.length == 0 || effectArgs[0] == null || effectArgs[0].isEmpty()) {
            plugin.getLogger().warning("GIVE_EFFECT_TARGET action requires a non-empty data string.");
            return;
        }
        String[] parts = effectArgs[0].split(":");
        if (parts.length < 3) {
            plugin.getLogger().warning(
                    "GIVE_EFFECT_TARGET action requires at least effect_type, duration_seconds, and amplifier arguments.");
            return;
        }
        try {
            NamespacedKey effectKey = NamespacedKey.minecraft(parts[0].toLowerCase());
            PotionEffectType effectType = PotionEffectType.getByKey(effectKey);
            if (effectType == null)
                effectType = PotionEffectType.getByName(parts[0].toUpperCase());
            if (effectType == null) {
                plugin.getLogger().warning(
                        "Invalid PotionEffectType configured: " + parts[0] + " for GIVE_EFFECT_TARGET action.");
                return;
            }
            int durationSeconds = Integer.parseInt(parts[1]);
            int amplifier = Integer.parseInt(parts[2]);
            boolean particles = parts.length <= 3 || Boolean.parseBoolean(parts[3]);
            PotionEffect effect = new PotionEffect(effectType, durationSeconds * 20, amplifier, false, particles);
            targetPlayer.addPotionEffect(effect);
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger()
                        .info("[DEBUG] GIVE_EFFECT_TARGET action executed for player: " + targetPlayer.getName()
                                + ", effect: " + effectType.getKey() + ", duration: " + durationSeconds
                                + "s, amplifier: " + amplifier);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning(
                    "Invalid duration or amplifier format for GIVE_EFFECT_TARGET action: " + Arrays.toString(parts));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("IllegalArgumentException in GIVE_EFFECT_TARGET action: " + e.getMessage()
                    + ", Args: " + Arrays.toString(parts));
        }
    }

    @SuppressWarnings("deprecation")
    private void executePlaySoundModsAction(String[] soundArgs) {
        if (soundArgs == null || soundArgs.length == 0 || soundArgs[0] == null || soundArgs[0].isEmpty()) {
            plugin.getLogger().warning("PLAY_SOUND_MODS action requires a non-empty data string.");
            return;
        }
        List<Player> mods = getMods();
        if (mods.isEmpty()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] PLAY_SOUND_MODS skipped: No online mods.");
            return;
        }
        String[] parts = soundArgs[0].split(":");
        if (parts.length < 1) {
            plugin.getLogger().warning("PLAY_SOUND_MODS action requires at least a sound name.");
            return;
        }
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            mods.forEach(mod -> mod.playSound(mod.getLocation(), sound, volume, pitch));
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger()
                        .info("[DEBUG] PLAY_SOUND_MODS played sound '" + sound + "' for " + mods.size() + " mods.");
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND_MODS: " + Arrays.toString(parts));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND_MODS: " + parts[0]);
        }
    }

    private void executeTitleModsAction(Player player, InventoryHolder holder, String[] titleArgs) {
        if (titleArgs == null || titleArgs.length == 0 || titleArgs[0] == null || titleArgs[0].isEmpty()) {
            plugin.getLogger().warning("TITLE_MODS action requires a non-empty data string.");
            return;
        }
        List<Player> mods = getMods();
        if (mods.isEmpty()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] TITLE_MODS skipped: No online mods.");
            return;
        }
        String[] parts = titleArgs[0].split(":");
        if (parts.length < 3) {
            plugin.getLogger()
                    .warning("TITLE_MODS action requires at least title, subtitle, and time_seconds arguments.");
            return;
        }

        String titleText = MessageUtils.getColorMessage(processAllPlaceholders(parts[0], player, holder));
        String subtitleText = MessageUtils.getColorMessage(processAllPlaceholders(parts[1], player, holder));
        try {
            int timeSeconds = Integer.parseInt(parts[2]);
            int fadeInTicks = parts.length > 3 ? Integer.parseInt(parts[3]) : 10;
            int fadeOutTicks = parts.length > 4 ? Integer.parseInt(parts[4]) : 20;
            final String finalTitle = titleText;
            final String finalSubtitle = subtitleText;
            mods.forEach(mod -> mod.sendTitle(finalTitle, finalSubtitle, fadeInTicks, timeSeconds * 20, fadeOutTicks));
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] TITLE_MODS sent to " + mods.size() + " mods. Title: " + finalTitle);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning(
                    "Invalid number format for time/fade values in TITLE_MODS action: " + Arrays.toString(parts));
        }
    }

    private void executeMessageModsAction(Player player, InventoryHolder holder, String[] messageArgs) {
        List<Player> mods = getMods();

        String baseMessage = (messageArgs != null && messageArgs.length > 0) ? messageArgs[0] : null;

        if (baseMessage == null) {
            plugin.getLogger().warning("MESSAGE_MODS action requires a non-null message text argument.");
            return;
        }

        final String finalMessage = MessageUtils.getColorMessage(processAllPlaceholders(baseMessage, player, holder));
        mods.forEach(mod -> mod.sendMessage(finalMessage));
        Bukkit.getConsoleSender().sendMessage(finalMessage);

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger()
                    .info("[DEBUG] MESSAGE_MODS sent to " + mods.size() + " mods and console: " + finalMessage);
        }
    }

    private void executeActionbarModsAction(Player player, InventoryHolder holder, String[] messageArgs) {
        List<Player> mods = getMods();
        if (mods.isEmpty()) {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] ACTIONBAR_MODS skipped: No online mods.");
            return;
        }

        String baseMessage = (messageArgs != null && messageArgs.length > 0) ? messageArgs[0] : null;

        if (baseMessage == null) {
            plugin.getLogger().warning("ACTIONBAR_MODS action requires a non-null message text argument.");
            return;
        }

        final String finalMessage = MessageUtils.getColorMessage(processAllPlaceholders(baseMessage, player, holder));
        mods.forEach(mod -> mod.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(finalMessage)));
        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] ACTIONBAR_MODS sent to " + mods.size() + " mods: " + finalMessage);
    }

    private void executeCommandAction(Player player, ClickAction action, String[] commandData, InventoryHolder holder) {
        if (commandData == null || commandData.length < 1 || commandData[0] == null || commandData[0].isEmpty()) {
            plugin.getLogger().warning("Invalid COMMAND action data: Command string is missing or empty.");
            return;
        }

        String commandToExecute = processAllPlaceholders(commandData[0], player, holder);
        commandToExecute = ColorUtils.translateRGBColors(commandToExecute);

        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] Executing COMMAND: " + action + " Command: " + commandToExecute);
        final String finalCommand = commandToExecute;

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (action) {
                case PLAYER_COMMAND -> player.performCommand(finalCommand);
                case PLAYER_COMMAND_OP -> {
                    boolean wasOp = player.isOp();
                    try {
                        player.setOp(true);
                        player.performCommand(finalCommand);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Error executing OP command '" + finalCommand + "' for player " + player.getName(), e);
                    } finally {
                        if (!wasOp)
                            player.setOp(false);
                    }
                }
                default -> plugin.getLogger().warning("executeCommandAction called with non-command action: " + action);
            }
        });
    }

    private void executeConsoleCommand(Player player, String[] commandData, InventoryHolder holder) {
        if (commandData == null || commandData.length < 1 || commandData[0] == null || commandData[0].isEmpty()) {
            plugin.getLogger().warning("Invalid CONSOLE_COMMAND action data: Command string is missing or empty.");
            return;
        }

        String commandToExecute = processAllPlaceholders(commandData[0], player, holder);
        commandToExecute = ColorUtils.translateRGBColors(commandToExecute);

        if (plugin.getConfigManager().isDebugEnabled())
            plugin.getLogger().info("[DEBUG] Executing CONSOLE_COMMAND: " + commandToExecute);
        final String finalCommand = commandToExecute;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }

    private void executeOpenAuditLogAction(Player player, String[] args, InventoryHolder holder) {
        OfflinePlayer target = null;
        if (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            target = Bukkit.getOfflinePlayer(args[0]);
        } else {
            target = getTargetForAction(holder);
        }

        if (target != null) {
            new AuditLogBook(plugin, target.getUniqueId(), player).openBook();
        } else {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] OPEN_AUDIT_LOG skipped: No target found.");
        }
    }

    private void executeOpenProfileTargetAction(Player player, String[] args, InventoryHolder holder) {
        OfflinePlayer target = null;
        if (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            target = Bukkit.getOfflinePlayer(args[0]);
        } else {
            target = getTargetForAction(holder);
        }

        if (target != null) {
            openProfileIfOnline(player, target.getUniqueId());
        } else {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] OPEN_PROFILE_TARGET skipped: No target found.");
        }
    }

    private void executeHistoryTargetAction(Player player, String[] args, InventoryHolder holder) {
        OfflinePlayer target = null;
        if (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            target = Bukkit.getOfflinePlayer(args[0]);
        } else {
            target = getTargetForAction(holder);
        }

        if (target != null) {
            new HistoryMenu(target.getUniqueId(), plugin).open(player);
        } else {
            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[DEBUG] HISTORY_TARGET skipped: No target found.");
        }
    }

    // ... (All original private handle...MenuActions methods remain here without
    // change)
    private boolean handleProfileMenuActions(Player player, ProfileMenu profileMenu, ClickAction action,
            String[] actionData) {
        UUID targetUUID = profileMenu.getTargetUUID();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;

        if (action == ClickAction.OPEN_MENU && firstArg != null) {
            switch (firstArg.toLowerCase()) {
                case "punish_menu" -> {
                    new PunishMenu(targetUUID, plugin).open(player);
                    return true;
                }
                case "full_inventory_menu" -> {
                    new FullInventoryMenu(targetUUID, plugin).open(player);
                    return true;
                }
                case "enderchest_menu" -> {
                    new EnderChestMenu(targetUUID, plugin).open(player);
                    return true;
                }
            }
        } else if (action == ClickAction.REQUEST_INPUT && firstArg != null) {
            if (firstArg.equalsIgnoreCase("change_target")) {
                player.closeInventory();
                requestNewTargetName(player, "profile_menu");
                return true;
            }
        }
        return false;
    }

    private boolean handleFullInventoryMenuActions(Player player, FullInventoryMenu fullInventoryMenu,
            ClickAction action, String[] actionData) {
        UUID targetUUID = fullInventoryMenu.getTargetUUID();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        if (action == ClickAction.OPEN_MENU && firstArg != null) {
            if ("profile_menu".equalsIgnoreCase(firstArg)) {
                new ProfileMenu(targetUUID, plugin).open(player);
                return true;
            }
        }
        return false;
    }

    private boolean handleEnderChestMenuActions(Player player, EnderChestMenu enderChestMenu, ClickAction action,
            String[] actionData) {
        UUID targetUUID = enderChestMenu.getTargetUUID();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        if (action == ClickAction.OPEN_MENU && firstArg != null) {
            if ("profile_menu".equalsIgnoreCase(firstArg)) {
                new ProfileMenu(targetUUID, plugin).open(player);
                return true;
            }
        }
        return false;
    }

    private boolean handlePunishMenuActions(Player player, PunishMenu punishMenu, ClickAction action,
            String[] actionData) {
        UUID targetUUID = punishMenu.getTargetUUID();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;

        switch (action) {
            case OPEN_MENU -> {
                if (firstArg != null) {
                    switch (firstArg.toLowerCase()) {
                        case "ban_details" -> {
                            if (!player.hasPermission(PUNISH_BAN_PERMISSION)) {
                                sendNoPermissionMenuMessage(player, "ban details");
                                return true;
                            }
                            new PunishDetailsMenu(targetUUID, plugin, BAN_PUNISHMENT_TYPE).open(player);
                            return true;
                        }
                        case "mute_details" -> {
                            if (!player.hasPermission(PUNISH_MUTE_PERMISSION)) {
                                sendNoPermissionMenuMessage(player, "mute details");
                                return true;
                            }
                            new PunishDetailsMenu(targetUUID, plugin, MUTE_PUNISHMENT_TYPE).open(player);
                            return true;
                        }
                        case "softban_details" -> {
                            if (!player.hasPermission(PUNISH_SOFTBAN_PERMISSION)) {
                                sendNoPermissionMenuMessage(player, "softban details");
                                return true;
                            }
                            new PunishDetailsMenu(targetUUID, plugin, SOFTBAN_PUNISHMENT_TYPE).open(player);
                            return true;
                        }
                        case "kick_details" -> {
                            if (!player.hasPermission(PUNISH_KICK_PERMISSION)) {
                                sendNoPermissionMenuMessage(player, "kick details");
                                return true;
                            }
                            new PunishDetailsMenu(targetUUID, plugin, KICK_PUNISHMENT_TYPE).open(player);
                            return true;
                        }
                        case "warn_details" -> {
                            if (!player.hasPermission(PUNISH_WARN_PERMISSION)) {
                                sendNoPermissionMenuMessage(player, "warn details");
                                return true;
                            }
                            new PunishDetailsMenu(targetUUID, plugin, WARN_PUNISHMENT_TYPE).open(player);
                            return true;
                        }
                        case "freeze_details" -> {
                            if (!player.hasPermission(PUNISH_FREEZE_PERMISSION)) {
                                sendNoPermissionMenuMessage(player, "freeze details");
                                return true;
                            }
                            new PunishDetailsMenu(targetUUID, plugin, FREEZE_PUNISHMENT_TYPE).open(player);
                            return true;
                        }
                        case "history_menu" -> {
                            if (!player.hasPermission(USE_PERMISSION)) {
                                sendNoPermissionMenuMessage(player, "history menu");
                                return true;
                            }
                            new HistoryMenu(targetUUID, plugin).open(player);
                            return true;
                        }
                        default -> {
                            return false;
                        }
                    }
                }
                return false;
            }

            case REQUEST_INPUT -> {
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("change_target")) {
                        if (!player.hasPermission(USE_PERMISSION)) {
                            sendNoPermissionMenuMessage(player, "change target action");
                            return true;
                        }
                        player.closeInventory();
                        requestNewTargetName(player, "punish_menu");
                        return true;
                    }
                }
                return false;
            }

            default -> {
                return false;
            }
        }
    }

    private boolean handlePunishDetailsMenuActions(Player player, PunishDetailsMenu punishDetailsMenu,
            ClickAction action, String[] actionData) {
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case OPEN_MENU -> {
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("time_selector")) {
                        if (punishDetailsMenu.isTimeRequired()) {
                            new TimeSelectorMenu(punishDetailsMenu, plugin).open(player);
                        } else {
                            sendConfigMessage(player, "messages.time_not_applicable");
                        }
                        return true;
                    } else if (firstArg.equalsIgnoreCase("punish_menu")) {
                        new PunishMenu(punishDetailsMenu.getTargetUUID(), plugin).open(player);
                        return true;
                    }
                }
                return false;
            }
            case REQUEST_INPUT -> {
                if (firstArg != null && firstArg.equalsIgnoreCase("reason_input")) {
                    requestReasonInput(player, punishDetailsMenu);
                    return true;
                }
                return false;
            }
            case TOGGLE_PUNISH_METHOD -> {
                if (plugin.getConfigManager().isIpPunishmentSupported(punishDetailsMenu.getPunishmentType())) {
                    punishDetailsMenu.togglePunishMethod();
                    punishDetailsMenu.open(player);
                } else {
                    playSound(player, "punish_error");
                    sendConfigMessage(player, "messages.punish_method_not_supported");
                }
                return true;
            }
            case CONFIRM_PUNISHMENT -> {
                handleConfirmButtonClick(player, punishDetailsMenu);
                return true;
            }
            case UN_SOFTBAN -> {
                if (!player.hasPermission(UNPUNISH_SOFTBAN_PERMISSION)) {
                    sendNoPermissionMenuMessage(player, "unsoftban");
                    return true;
                }
                handleUnsoftbanButtonClick(player, punishDetailsMenu);
                return true;
            }
            case UN_FREEZE -> {
                if (!player.hasPermission(UNPUNISH_FREEZE_PERMISSION)) {
                    sendNoPermissionMenuMessage(player, "unfreeze");
                    return true;
                }
                handleUnfreezeButtonClick(player, punishDetailsMenu);
                return true;
            }
            case UN_BAN -> {
                if (!player.hasPermission(UNPUNISH_BAN_PERMISSION)) {
                    sendNoPermissionMenuMessage(player, "unban");
                    return true;
                }
                executeUnbanAction(player, punishDetailsMenu,
                        plugin.getConfigManager().getDefaultUnpunishmentReason(BAN_PUNISHMENT_TYPE));
                return true;
            }
            case UN_MUTE -> {
                if (!player.hasPermission(UNPUNISH_MUTE_PERMISSION)) {
                    sendNoPermissionMenuMessage(player, "unmute");
                    return true;
                }
                executeUnmuteAction(player, punishDetailsMenu,
                        plugin.getConfigManager().getDefaultUnpunishmentReason(MUTE_PUNISHMENT_TYPE));
                return true;
            }
            case UN_WARN -> {
                if (!player.hasPermission(UNPUNISH_WARN_PERMISSION)) {
                    sendNoPermissionMenuMessage(player, "unwarn");
                    return true;
                }
                executeUnwarnAction(player, punishDetailsMenu,
                        plugin.getConfigManager().getDefaultUnpunishmentReason(WARN_PUNISHMENT_TYPE));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleTimeSelectorMenuActions(Player player, TimeSelectorMenu timeSelectorMenu, ClickAction action,
            String[] actionData) {
        PunishDetailsMenu detailsMenu = timeSelectorMenu.getPunishDetailsMenu();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case ADJUST_TIME -> {
                if (firstArg != null) {
                    int secondsToAdd = switch (firstArg.toLowerCase()) {
                        case "minus_5_min" -> -300;
                        case "minus_2_hour" -> -7200;
                        case "minus_1_day" -> -86400;
                        case "minus_5_day" -> -432000;
                        case "plus_15_min" -> 900;
                        case "plus_6_hour" -> 21600;
                        case "plus_1_day" -> 86400;
                        case "plus_7_day" -> 604800;
                        default -> 0;
                    };
                    if (secondsToAdd != 0) {
                        timeSelectorMenu.adjustTime(secondsToAdd);
                        timeSelectorMenu.updateTimeDisplayItem(player);
                    }
                    return true;
                }
                return false;
            }
            case REQUEST_INPUT -> {
                if (firstArg != null && firstArg.equalsIgnoreCase("custom_time_input")) {
                    requestCustomTimeInput(player, detailsMenu);
                    return true;
                }
                return false;
            }
            case SET_PUNISHMENT_TYPE -> {
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("permanent_time")) {
                        setPermanentTime(detailsMenu, player);
                        return true;
                    } else if (firstArg.equalsIgnoreCase("confirm_time")) {
                        handleTimeDisplayClick(timeSelectorMenu, detailsMenu, player);
                        return true;
                    }
                }
                return false;
            }
            case OPEN_MENU -> {
                if (firstArg != null && firstArg.equalsIgnoreCase("punish_details")) {
                    detailsMenu.open(player);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleHistoryMenuActions(Player player, HistoryMenu historyMenu, ClickAction action,
            String[] actionData) {
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case OPEN_MENU -> {
                if (firstArg != null && firstArg.equalsIgnoreCase("punish_menu")) {
                    new PunishMenu(historyMenu.getTargetUUID(), plugin).open(player);
                    return true;
                }
                return false;
            }
            case ADJUST_PAGE -> {
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("next_page"))
                        historyMenu.nextPage(player);
                    else if (firstArg.equalsIgnoreCase("previous_page"))
                        historyMenu.previousPage(player);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleReportsMenuActions(Player player, ReportsMenu menu, ClickAction action, String[] actionData,
            InventoryClickEvent event) {
        switch (action) {
            case ADJUST_PAGE -> {
                if (actionData != null && actionData.length > 0) {
                    if ("next_page".equalsIgnoreCase(actionData[0])) {
                        menu.nextPage();
                    } else if ("previous_page".equalsIgnoreCase(actionData[0])) {
                        menu.previousPage();
                    }
                }
                return true;
            }
            case FILTER_REPORTS_STATUS -> {
                menu.cycleFilterStatus();
                return true;
            }
            case FILTER_REPORTS_TYPE -> {
                menu.cycleReportTypeFilter();
                return true;
            }
            case FILTER_REPORTS_NAME -> {
                boolean byRequester = event.getClick().isRightClick();
                requestReportFilterInput(player, menu, byRequester);
                return true;
            }
            case FILTER_MY_REPORTS -> {
                menu.toggleMyReportsFilter();
                return true;
            }
            case OPEN_REPORT_DETAILS -> {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.hasItemMeta()) {
                    ItemMeta meta = clickedItem.getItemMeta();
                    String reportId = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "report_id"),
                            PersistentDataType.STRING);
                    if (reportId != null) {
                        new ReportDetailsMenu(plugin, player, reportId).open(player);
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void openProfileIfOnline(Player viewer, UUID targetUUID) {
        if (targetUUID == null)
            return;

        if (viewer.getUniqueId().equals(targetUUID)) {
            sendConfigMessage(viewer, "messages.profile_self_check_error");
            playSound(viewer, "punish_error");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        if (target.isOnline()) {
            new ProfileMenu(targetUUID, plugin).open(viewer);
        } else {
            sendConfigMessage(viewer, "messages.profile_offline_error", "{input}",
                    target.getName() != null ? target.getName() : "Unknown");
            playSound(viewer, "punish_error");
        }
    }

    private boolean handleReportDetailsMenuActions(Player player, ReportDetailsMenu menu, ClickAction action,
            String[] actionData, InventoryClickEvent event) {
        DatabaseManager.ReportEntry report = menu.getReportEntry();
        if (report == null)
            return true;

        switch (action) {
            case SET_REPORT_STATUS_TAKE -> {
                if (report.getModeratorUUID() != null && !report.getModeratorUUID().equals(player.getUniqueId())) {
                    executeReportStateChange(player, menu, action);
                } else {
                    plugin.getSoftBanDatabaseManager()
                            .updateReportStatus(report.getReportId(), ReportStatus.TAKEN, player.getUniqueId())
                            .thenAccept(success -> {
                                if (success) {
                                    MessageUtils.sendConfigMessage(plugin, player, "messages.report_status_changed",
                                            "{report_id}", report.getReportId(), "{status_color}",
                                            ReportStatus.TAKEN.getColor(), "{status}",
                                            ReportStatus.TAKEN.getDisplayName());
                                    menu.loadReportAndInitialize();
                                }
                            });
                }
                return true;
            }

            case SET_REPORT_STATUS_RESOLVED, SET_REPORT_STATUS_REJECTED, SET_REPORT_STATUS_PENDING -> {
                executeReportStateChange(player, menu, action);
                return true;
            }
            case ASSIGN_MODERATOR -> {
                if (!player.hasPermission(REPORT_ASSIGN_PERMISSION)) {
                    sendConfigMessage(player, "messages.no_permission_menu_action", "{action}", "assign reports");
                    playSound(player, "punish_error");
                    return true;
                }
                requestModeratorAssignmentInput(player, menu);
                return true;
            }
            case OPEN_MENU -> {
                if (actionData != null && actionData.length > 0) {
                    if ("punish_menu_target".equalsIgnoreCase(actionData[0]) && report.getTargetUUID() != null) {
                        new PunishMenu(report.getTargetUUID(), plugin).open(player);
                    } else if ("punish_menu_requester".equalsIgnoreCase(actionData[0])) {
                        new PunishMenu(report.getRequesterUUID(), plugin).open(player);
                    }
                }
                return true;
            }
            case OPEN_PROFILE_REQUESTER -> {
                openProfileIfOnline(player, report.getRequesterUUID());
                return true;
            }
            case OPEN_PROFILE_MODERATOR -> {
                if (report.getModeratorUUID() != null) {
                    openProfileIfOnline(player, report.getModeratorUUID());
                }
                return true;
            }
            case REPORTS_FILTER_TARGET -> {
                if (report.getTargetUUID() != null) {
                    new ReportsMenu(plugin, player, 1, null, Bukkit.getOfflinePlayer(report.getTargetUUID()).getName(),
                            false, false).open(player);
                }
                return true;
            }
            case REPORTS_FILTER_REQUESTER -> {
                new ReportsMenu(plugin, player, 1, null, Bukkit.getOfflinePlayer(report.getRequesterUUID()).getName(),
                        true, false).open(player);
                return true;
            }
            case HISTORY_REQUESTER -> {
                new HistoryMenu(report.getRequesterUUID(), plugin).open(player);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void executeReportStateChange(Player player, ReportDetailsMenu menu, ClickAction action) {
        String reportId = menu.getReportId();

        ReportStatus newStatus = null;
        UUID newModerator = player.getUniqueId();

        switch (action) {
            case SET_REPORT_STATUS_RESOLVED:
                newStatus = ReportStatus.RESOLVED;
                break;
            case SET_REPORT_STATUS_REJECTED:
                newStatus = ReportStatus.REJECTED;
                break;
            case SET_REPORT_STATUS_PENDING:
                newStatus = ReportStatus.PENDING;
                newModerator = null;
                break;
            case SET_REPORT_STATUS_TAKE:
                newStatus = ReportStatus.TAKEN;
                break;
        }

        if (newStatus == null)
            return;

        final ReportStatus finalStatus = newStatus;
        final UUID finalModerator = newModerator;

        plugin.getSoftBanDatabaseManager().updateReportStatus(reportId, finalStatus, finalModerator)
                .thenAccept(success -> {
                    if (success) {
                        MessageUtils.sendConfigMessage(plugin, player, "messages.report_status_changed", "{report_id}",
                                reportId, "{status_color}", finalStatus.getColor(), "{status}",
                                finalStatus.getDisplayName());
                        menu.loadReportAndInitialize();
                    }
                });
    }

    private void requestReportFilterInput(Player player, ReportsMenu menu, boolean byRequester) {
        player.closeInventory();
        String prompt = byRequester ? "messages.report_prompt_filter_requester"
                : "messages.report_prompt_filter_target";
        ReportInputType type = byRequester ? ReportInputType.FILTER_REQUESTER_NAME : ReportInputType.FILTER_TARGET_NAME;
        sendConfigMessage(player, prompt);
        pendingReportInputs.put(player.getUniqueId(), new ReportInputContext(menu, type, null));
        setupChatInputTimeout(player, "report_filter");
    }

    private void requestModeratorAssignmentInput(Player player, ReportDetailsMenu menu) {
        player.closeInventory();
        sendConfigMessage(player, "messages.report_prompt_assign_moderator");
        pendingReportInputs.put(player.getUniqueId(),
                new ReportInputContext(menu, ReportInputType.ASSIGN_MODERATOR, menu.getReportId()));
        setupChatInputTimeout(player, "report_assign");
    }

    private void handleReportChatInput(Player player, String input) {
        ReportInputContext context = pendingReportInputs.remove(player.getUniqueId());
        cancelExistingTimeout(player);
        if (context == null)
            return;

        if ("cancel".equalsIgnoreCase(input)) {
            sendConfigMessage(player, "messages.input_cancelled");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (context.menu() instanceof ReportsMenu rm)
                    rm.open(player);
                if (context.menu() instanceof ReportDetailsMenu rdm)
                    rdm.open(player);
            });
            return;
        }

        switch (context.type()) {
            case FILTER_TARGET_NAME:
            case FILTER_REQUESTER_NAME:
                if (context.menu() instanceof ReportsMenu reportsMenu) {
                    reportsMenu.setFilterName(input, context.type() == ReportInputType.FILTER_REQUESTER_NAME);
                    // Re-open the menu to show the loading state, the async task will populate it.
                    Bukkit.getScheduler().runTask(plugin, () -> reportsMenu.open(player));
                }
                break;
            case ASSIGN_MODERATOR:
                if (context.menu() instanceof ReportDetailsMenu detailsMenu) {
                    OfflinePlayer moderator = Bukkit.getOfflinePlayer(input);
                    if (!moderator.hasPlayedBefore() && !moderator.isOnline()) {
                        sendConfigMessage(player, "messages.never_played", "{input}", input);
                        Bukkit.getScheduler().runTask(plugin, () -> detailsMenu.open(player));
                        return;
                    }
                    detailsMenu.assignTo(moderator.getUniqueId());
                    sendConfigMessage(player, "messages.report_assigned", "{moderator}", moderator.getName(),
                            "{report_id}", detailsMenu.getReportId());
                }
                break;
        }
    }

    private void requestNewTargetName(Player player, String originMenu) {
        sendConfigMessage(player, "messages.prompt_new_target");
        storeInputData(player, setupChatInputTimeout(player, "change_target"), null, "change_target", originMenu);
    }

    private void requestReasonInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        String promptPath = "messages.prompt_" + punishDetailsMenu.getPunishmentType().toLowerCase() + "_reason";
        sendConfigMessage(player, promptPath);
        storeInputData(player, setupChatInputTimeout(player, "reason_input"), punishDetailsMenu, "reason_input", null);
    }

    private void requestCustomTimeInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        sendConfigMessage(player, "messages.prompt_custom_time");
        storeInputData(player, setupChatInputTimeout(player, "custom_time"), punishDetailsMenu, "custom_time", null);
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger()
                    .info("[DEBUG] Requested custom_time input from " + player.getName() + ". Timeout task stored.");
        }
    }

    private BukkitTask setupChatInputTimeout(Player player, String inputType) {
        cancelExistingTimeout(player);

        return new BukkitRunnable() {
            @Override
            public void run() {
                if (inputTypes.getOrDefault(player.getUniqueId(), "").equals(inputType)
                        || pendingReportInputs.containsKey(player.getUniqueId())) {
                    handleInputTimeout(player);
                }
            }
        }.runTaskLater(plugin, 400L);
    }

    private void storeInputData(Player player, BukkitTask task, PunishDetailsMenu menu, String inputType,
            String origin) {
        if (task != null) {
            inputTimeouts.put(player.getUniqueId(), task);
        } else {
            plugin.getLogger().warning(
                    "Attempted to store a null timeout task for " + player.getName() + ", inputType: " + inputType);
        }

        if (menu != null) {
            pendingDetailsMenus.put(player.getUniqueId(), menu);
        } else {
            pendingDetailsMenus.remove(player.getUniqueId());
        }
        inputTypes.put(player.getUniqueId(), inputType);
        if (origin != null) {
            inputOrigin.put(player.getUniqueId(), origin);
        }

        if (plugin.getConfigManager().isDebugEnabled() && task != null) {
            plugin.getLogger().info("[DEBUG] Stored input data for " + player.getName() + ": inputType=" + inputType
                    + ", menuContext=" + (menu != null) + ", taskID=" + task.getTaskId());
        }
    }

    private void cancelExistingTimeout(Player player) {
        BukkitTask existingTask = inputTimeouts.remove(player.getUniqueId());
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }
    }

    private void handleInputTimeout(Player player) {
        if (player != null && player.isOnline()) {
            if (inputTypes.containsKey(player.getUniqueId()) || pendingReportInputs.containsKey(player.getUniqueId())) {
                sendConfigMessage(player, "messages.input_timeout");
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[DEBUG] Input timed out for " + player.getName() + ". Type was: "
                            + inputTypes.get(player.getUniqueId()));
                }
            }
        }
        clearPlayerInputData(player);
    }

    private void handlePlayerInput(Player player, String input) {
        PunishDetailsMenu detailsMenu = pendingDetailsMenus.get(player.getUniqueId());
        String inputType = inputTypes.get(player.getUniqueId());
        String origin = inputOrigin.get(player.getUniqueId());

        if (inputType == null) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Received chat input from " + player.getName()
                        + " but no input type was stored (likely timed out or manually closed menu just before input). Input: "
                        + input);
            }
            return;
        }

        cancelExistingTimeout(player);

        if (input.equalsIgnoreCase("cancel")) {
            handleCancelInput(player, detailsMenu);
            return;
        }

        processValidInput(player, input, detailsMenu, inputType, origin);
    }

    private void handleCancelInput(Player player, PunishDetailsMenu detailsMenu) {
        sendConfigMessage(player, "messages.input_cancelled");
        if (detailsMenu != null) {
            reopenDetailsMenu(player, detailsMenu);
        }
        clearPlayerInputData(player);
    }

    private void processValidInput(Player player, String input, PunishDetailsMenu detailsMenu, String inputType,
            String origin) {
        switch (inputType.toLowerCase()) {
            case "change_target":
                handleNewTargetInput(player, input, origin);
                break;
            case "reason_input":
                if (detailsMenu != null) {
                    handleReasonInput(player, input, detailsMenu);
                } else {
                    plugin.getLogger()
                            .warning("Reason input received but no details menu context for " + player.getName());
                }
                break;
            case "custom_time":
                if (detailsMenu != null) {
                    handleCustomTimeInput(player, input, detailsMenu);
                } else {
                    plugin.getLogger()
                            .warning("Custom time input received but no details menu context for " + player.getName());
                }
                break;
            default:
                plugin.getLogger()
                        .warning("Unknown input type processed: " + inputType + " for player " + player.getName());
                break;
        }
        clearPlayerInputData(player);
    }

    private void handleNewTargetInput(Player player, String input, String origin) {
        OfflinePlayer newTarget = Bukkit.getOfflinePlayer(input);

        if ("profile_menu".equals(origin)) {
            if (newTarget.getUniqueId().equals(player.getUniqueId())) {
                sendConfigMessage(player, "messages.profile_self_check_error");
                return;
            }
            if (!newTarget.isOnline()) {
                sendConfigMessage(player, "messages.profile_offline_error", "{input}", input);
                return;
            }
        }

        if (!newTarget.hasPlayedBefore() && !newTarget.isOnline()) {
            sendConfigMessage(player, "messages.never_played", "{input}", input);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if ("profile_menu".equals(origin)) {
                    new ProfileMenu(newTarget.getUniqueId(), plugin).open(player);
                } else {
                    new PunishMenu(newTarget.getUniqueId(), plugin).open(player);
                }
            });
        }
    }

    private void handleReasonInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        detailsMenu.setBanReason(input);
        reopenDetailsMenu(player, detailsMenu);
    }

    private void handleCustomTimeInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        int seconds = TimeUtils.parseTime(input, plugin.getConfigManager());
        String permanentKeyword = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

        if (seconds > 0 || input.equalsIgnoreCase(permanentKeyword)) {
            String timeToSet = input.equalsIgnoreCase(permanentKeyword) ? permanentKeyword
                    : TimeUtils.formatTime(seconds, plugin.getConfigManager());
            detailsMenu.setBanTime(timeToSet);
            reopenDetailsMenu(player, detailsMenu);
        } else {
            sendConfigMessage(player, "messages.invalid_time_format_command", "{input}", input);
            reopenDetailsMenu(player, detailsMenu);
        }
    }

    private void reopenDetailsMenu(Player player, PunishDetailsMenu detailsMenu) {
        Bukkit.getScheduler().runTask(plugin, () -> detailsMenu.open(player));
    }

    private void clearPlayerInputData(Player player) {
        cancelExistingTimeout(player);
        pendingDetailsMenus.remove(player.getUniqueId());
        inputTypes.remove(player.getUniqueId());
        inputOrigin.remove(player.getUniqueId());
        pendingReportInputs.remove(player.getUniqueId()); // Also clear report inputs
    }

    private void handleConfirmButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        boolean timeMissing = punishDetailsMenu.isTimeRequired() && !punishDetailsMenu.isTimeSet();
        boolean reasonMissing = punishDetailsMenu.isReasonRequiredForConfirmation() && !punishDetailsMenu.isReasonSet();

        if (timeMissing || reasonMissing) {
            sendValidationMessages(player, timeMissing, reasonMissing);
        } else {
            confirmDynamicPunishment(player, punishDetailsMenu);
        }
    }

    private void handleUnsoftbanButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getSoftBanDatabaseManager().isSoftBanned(targetUUID)) {
            confirmUnsoftban(player, punishDetailsMenu);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            sendConfigMessage(player, "messages.no_active_softban", "{target}",
                    target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }

    private void handleUnfreezeButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getPluginFrozenPlayers().containsKey(targetUUID)) {
            confirmUnfreeze(player, punishDetailsMenu);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            sendConfigMessage(player, "messages.no_active_freeze", "{target}",
                    target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }

    private void confirmDynamicPunishment(Player player, PunishDetailsMenu punishDetailsMenu) {
        String type = punishDetailsMenu.getPunishmentType().toLowerCase();
        if (!checkPunishmentPermission(player, type)) {
            sendNoPermissionMenuMessage(player, type + " punishment");
            playSound(player, "punish_error");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(punishDetailsMenu.getTargetUUID());
        if (hasBypassPermission(target, type)) {
            sendBypassError(player, target, type);
            playSound(player, "punish_error");
            player.closeInventory();
            return;
        }

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
                sendConfigMessage(player, "messages.invalid_punishment_type", "{types}", "Known Types");
                playSound(player, "punish_error");
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void confirmStandardPunishment(Player player, PunishDetailsMenu detailsMenu) {
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String punishmentType = detailsMenu.getPunishmentType();
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(punishmentType);
        boolean byIp = detailsMenu.isByIp();
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand(punishmentType);
        String timeInput = detailsMenu.getBanTime() != null ? detailsMenu.getBanTime() : "permanent";
        String reason = detailsMenu.getBanReason() != null ? detailsMenu.getBanReason() : "No reason specified";

        String ipAddress = null;
        if (byIp) {
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null) {
                InetSocketAddress address = targetPlayer.getAddress();
                if (address != null) {
                    ipAddress = address.getAddress().getHostAddress();
                }
            } else {
                ipAddress = plugin.getSoftBanDatabaseManager().getLastKnownIp(targetUUID);
            }
            if (ipAddress == null) {
                sendConfigMessage(player, "messages.player_ip_not_found", "{target}", target.getName());
                return;
            }
        }
        final String finalIpAddress = ipAddress;

        long endTime = calculateEndTime(timeInput);
        String durationForLog = timeInput;
        if (endTime == Long.MAX_VALUE) {
            durationForLog = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        }

        final String finalDurationForLog = durationForLog;
        CompletableFuture<String> future = plugin.getSoftBanDatabaseManager()
                .executePunishmentAsync(targetUUID, target.getName(), punishmentType, reason, player.getName(), endTime,
                        durationForLog,
                        byIp, null);

        future.thenAccept(punishmentId -> {
            if (punishmentId == null)
                return;

            plugin.getSoftBanDatabaseManager().logPlayerInfoAsync(punishmentId, target, finalIpAddress);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (useInternal) {
                    if (punishmentType.equalsIgnoreCase(BAN_PUNISHMENT_TYPE)) {
                        Date expiration = (endTime == Long.MAX_VALUE) ? null : new Date(endTime);
                        if (byIp) {
                            Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(finalIpAddress, reason, expiration,
                                    player.getName());
                        } else {
                            if (target.getName() != null)
                                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), reason,
                                        expiration, player.getName());
                        }

                        if (target.isOnline()) {
                            Player onlineTarget = target.getPlayer();
                            if (onlineTarget != null) {
                                String kickMessage = MessageUtils.getKickMessage(
                                        plugin.getConfigManager().getBanScreen(), reason, finalDurationForLog,
                                        punishmentId, expiration, plugin.getConfigManager());
                                onlineTarget.kickPlayer(kickMessage);
                            }
                        }
                    } else if (punishmentType.equalsIgnoreCase(MUTE_PUNISHMENT_TYPE)) {
                        plugin.getMutedPlayersCache().put(targetUUID, endTime);
                        if (target.isOnline()) {
                            Player onlineTarget = target.getPlayer();
                            if (onlineTarget != null) {
                                String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted",
                                        "{time}", finalDurationForLog, "{reason}", reason, "{punishment_id}",
                                        punishmentId);
                                onlineTarget.sendMessage(MessageUtils.getColorMessage(muteMessage));
                            }
                        }
                    }
                } else {
                    String processedCommand = commandTemplate
                            .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                            .replace("{time}", timeInput)
                            .replace("{reason}", reason);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                }

                if (byIp) {
                    applyIpPunishmentToOnlinePlayers(punishmentType, finalIpAddress, endTime, reason,
                            finalDurationForLog, punishmentId, targetUUID);
                }

                playSound(player, "punish_confirm");
                sendPunishmentConfirmation(player, target, finalDurationForLog, reason, punishmentType, punishmentId);
                executeHookActions(player, target, punishmentType, finalDurationForLog, reason, false,
                        Collections.emptyList());
                player.closeInventory();
            });
        });
    }

    private void confirmSoftBan(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason();
        String timeInput = punishDetailsMenu.getBanTime() != null ? punishDetailsMenu.getBanTime() : "permanent";
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(SOFTBAN_PUNISHMENT_TYPE);
        boolean byIp = punishDetailsMenu.isByIp();
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand(SOFTBAN_PUNISHMENT_TYPE);

        String ipAddress = null;
        if (byIp) {
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null) {
                InetSocketAddress address = targetPlayer.getAddress();
                if (address != null) {
                    ipAddress = address.getAddress().getHostAddress();
                }
            } else {
                ipAddress = plugin.getSoftBanDatabaseManager().getLastKnownIp(targetUUID);
            }
            if (ipAddress == null) {
                sendConfigMessage(player, "messages.player_ip_not_found", "{target}", target.getName());
                return;
            }
        }
        final String finalIpAddress = ipAddress;

        long endTime = calculateEndTime(timeInput);
        String durationString = (endTime == Long.MAX_VALUE)
                ? plugin.getConfigManager().getMessage("placeholders.permanent_time_display")
                : timeInput;

        CompletableFuture<String> future = plugin.getSoftBanDatabaseManager()
                .executePunishmentAsync(targetUUID, target.getName(), SOFTBAN_PUNISHMENT_TYPE, reason, player.getName(),
                        endTime,
                        durationString, byIp, null);

        future.thenAccept(punishmentId -> {
            if (punishmentId == null)
                return;

            plugin.getSoftBanDatabaseManager().logPlayerInfoAsync(punishmentId, target, finalIpAddress);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (useInternal) {
                    plugin.getSoftBannedPlayersCache().put(targetUUID, endTime);
                    plugin.getSoftbannedCommandsCache().put(targetUUID, plugin.getConfigManager().getBlockedCommands());
                    if (target.isOnline()) {
                        Player onlineTarget = target.getPlayer();
                        if (onlineTarget != null) {
                            String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned",
                                    "{time}", durationString, "{reason}", reason, "{punishment_id}", punishmentId);
                            onlineTarget.sendMessage(MessageUtils.getColorMessage(softbanMessage));
                        }
                    }
                } else {
                    String processedCommand = commandTemplate
                            .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                            .replace("{time}", timeInput)
                            .replace("{reason}", reason);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                }

                if (byIp) {
                    applyIpPunishmentToOnlinePlayers(SOFTBAN_PUNISHMENT_TYPE, finalIpAddress, endTime, reason,
                            durationString, punishmentId, targetUUID);
                }

                playSound(player, "punish_confirm");
                sendPunishmentConfirmation(player, target, durationString, reason, SOFTBAN_PUNISHMENT_TYPE,
                        punishmentId);
                executeHookActions(player, target, SOFTBAN_PUNISHMENT_TYPE, durationString, reason, false,
                        Collections.emptyList());
                player.closeInventory();
            });
        });
    }

    private void confirmFreeze(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason();
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(FREEZE_PUNISHMENT_TYPE);
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand(FREEZE_PUNISHMENT_TYPE);
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        boolean byIp = punishDetailsMenu.isByIp();

        String ipAddress = null;
        if (byIp) {
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null) {
                InetSocketAddress address = targetPlayer.getAddress();
                if (address != null) {
                    ipAddress = address.getAddress().getHostAddress();
                }
            } else {
                ipAddress = plugin.getSoftBanDatabaseManager().getLastKnownIp(targetUUID);
            }
            if (ipAddress == null) {
                sendConfigMessage(player, "messages.player_ip_not_found", "{target}", target.getName());
                return;
            }
        }
        final String finalIpAddress = ipAddress;

        if (plugin.getPluginFrozenPlayers().containsKey(targetUUID)) {
            sendConfigMessage(player, "messages.already_frozen", "{target}",
                    target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
            return;
        }

        CompletableFuture<String> future = plugin.getSoftBanDatabaseManager()
                .executePunishmentAsync(targetUUID, target.getName(), FREEZE_PUNISHMENT_TYPE, reason, player.getName(),
                        Long.MAX_VALUE,
                        permanentDisplay, byIp, null);

        future.thenAccept(punishmentId -> {
            if (punishmentId == null)
                return;

            plugin.getSoftBanDatabaseManager().logPlayerInfoAsync(punishmentId, target, finalIpAddress);

            Bukkit.getScheduler().runTask(plugin, () -> {

                if (useInternal) {
                    plugin.getPluginFrozenPlayers().put(targetUUID, true);
                    Player onlineTarget = target.getPlayer();
                    if (onlineTarget != null) {
                        sendFreezeReceivedMessage(onlineTarget);
                        plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
                        plugin.getFreezeListener().startFreezeChatSession(player, onlineTarget, punishmentId);
                    }
                } else {
                    String processedCommand = commandTemplate
                            .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                            .replace("{reason}", reason);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                }

                if (byIp && finalIpAddress != null) {
                    applyIpPunishmentToOnlinePlayers(FREEZE_PUNISHMENT_TYPE, finalIpAddress, Long.MAX_VALUE, reason,
                            permanentDisplay, punishmentId, targetUUID);
                }

                playSound(player, "punish_confirm");
                sendPunishmentConfirmation(player, target, permanentDisplay, reason, FREEZE_PUNISHMENT_TYPE,
                        punishmentId);
                executeHookActions(player, target, FREEZE_PUNISHMENT_TYPE, permanentDisplay, reason, false,
                        Collections.emptyList());
                player.closeInventory();
            });
        });
    }

    @SuppressWarnings("deprecation")
    private void confirmKick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean byIp = punishDetailsMenu.isByIp();

        if (!byIp && !target.isOnline()) {
            sendConfigMessage(player, "messages.player_not_online", "{input}", target.getName());
            playSound(player, "punish_error");
            player.closeInventory();
            return;
        }

        String reason = punishDetailsMenu.getBanReason();
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(KICK_PUNISHMENT_TYPE);
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand(KICK_PUNISHMENT_TYPE);

        String finalIpAddress = null;
        if (byIp) {
            if (target.isOnline()) {
                Player targetPlayer = target.getPlayer();
                if (targetPlayer != null) {
                    InetSocketAddress address = targetPlayer.getAddress();
                    if (address != null) {
                        finalIpAddress = address.getAddress().getHostAddress();
                    }
                }
            } else {
                finalIpAddress = plugin.getSoftBanDatabaseManager().getLastKnownIp(targetUUID);
            }

            if (finalIpAddress == null) {
                sendConfigMessage(player, "messages.player_ip_not_found", "{target}", target.getName());
                return;
            }
        }

        final String ipForLog = finalIpAddress;
        CompletableFuture<String> future = plugin.getSoftBanDatabaseManager()
                .executePunishmentAsync(targetUUID, target.getName(), KICK_PUNISHMENT_TYPE, reason, player.getName(),
                        0L, "N/A", byIp,
                        null);

        future.thenAccept(punishmentId -> {
            if (punishmentId == null)
                return;

            plugin.getSoftBanDatabaseManager().logPlayerInfoAsync(punishmentId, target, ipForLog);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (useInternal) {
                    String kickMessage = MessageUtils.getKickMessage(plugin.getConfigManager().getKickScreen(), reason,
                            "N/A", punishmentId, null, plugin.getConfigManager());
                    if (target.isOnline()) {
                        Player onlineTarget = target.getPlayer();
                        if (onlineTarget != null) {
                            onlineTarget.kickPlayer(kickMessage);
                        }
                    }
                    if (byIp && ipForLog != null) {
                        applyIpPunishmentToOnlinePlayers(KICK_PUNISHMENT_TYPE, ipForLog, 0, reason, "N/A", punishmentId,
                                targetUUID);
                    }
                } else {
                    String processedCommand = commandTemplate
                            .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                            .replace("{reason}", reason);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                }

                playSound(player, "punish_confirm");
                sendPunishmentConfirmation(player, target, "N/A", reason, KICK_PUNISHMENT_TYPE, punishmentId);
                executeHookActions(player, target, KICK_PUNISHMENT_TYPE, "N/A", reason, false, Collections.emptyList());
                player.closeInventory();
            });
        });
    }

    private void confirmWarn(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason();
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(WARN_PUNISHMENT_TYPE);

        player.closeInventory();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (useInternal) {
                DatabaseManager dbManager = plugin.getSoftBanDatabaseManager();
                ActiveWarningEntry latestWarning = dbManager.getLatestActiveWarning(targetUUID);
                int nextWarnLevel = (latestWarning != null) ? latestWarning.getWarnLevel() + 1 : 1;

                WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(nextWarnLevel);

                if (levelConfig == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sendConfigMessage(player, "messages.no_warn_level_configured", "{level}",
                                String.valueOf(nextWarnLevel));
                        playSound(player, "punish_error");
                    });
                    return;
                }

                int durationSeconds = TimeUtils.parseTime(levelConfig.getExpiration(), plugin.getConfigManager());
                long endTime = (durationSeconds == -1) ? -1 : System.currentTimeMillis() + (durationSeconds * 1000L);
                String durationString = (endTime == -1) ? "Permanent"
                        : TimeUtils.formatTime(durationSeconds, plugin.getConfigManager());

                String punishmentId = dbManager.logPunishment(targetUUID,
                        target.getName() != null ? target.getName() : "Unknown", "warn", reason, player.getName(),
                        endTime, durationString, false, nextWarnLevel);

                if (punishmentId != null) {
                    dbManager.logPlayerInfoAsync(punishmentId, target, null); // Warns are not by IP
                }

                dbManager.addActiveWarning(targetUUID, punishmentId, nextWarnLevel, endTime).thenRun(() -> {
                    ActiveWarningEntry newWarning = dbManager.getActiveWarningByPunishmentId(punishmentId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (newWarning != null) {
                            executeHookActions(player, target, "warn", durationString, reason, false,
                                    levelConfig.getOnWarnActions(), newWarning);
                        }
                    });
                });
            } else {
                String commandTemplate = plugin.getConfigManager().getPunishmentCommand("warn");
                plugin.getSoftBanDatabaseManager()
                        .executePunishmentAsync(targetUUID, target.getName(), "warn", reason, player.getName(), 0L,
                                "N/A", false, null)
                        .thenAccept(punishmentId -> {
                            if (punishmentId == null)
                                return;

                            plugin.getSoftBanDatabaseManager().logPlayerInfoAsync(punishmentId, target, null);

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                String processedCommand = commandTemplate
                                        .replace("{target}",
                                                target.getName() != null ? target.getName() : targetUUID.toString())
                                        .replace("{reason}", reason);
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);

                                playSound(player, "punish_confirm");
                                sendPunishmentConfirmation(player, target, "N/A", reason, WARN_PUNISHMENT_TYPE,
                                        punishmentId);
                                executeHookActions(player, target, WARN_PUNISHMENT_TYPE, "N/A", reason, false,
                                        Collections.emptyList());
                            });
                        });
            }
        });
    }

    private void confirmUnsoftban(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = plugin.getConfigManager().getDefaultUnpunishmentReason(SOFTBAN_PUNISHMENT_TYPE);

        plugin.getSoftBanDatabaseManager()
                .executeUnpunishmentAsync(targetUUID, SOFTBAN_PUNISHMENT_TYPE, player.getName(), reason, null)
                .thenAccept(punishmentId -> {
                    if (punishmentId == null)
                        return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager()
                                .getPunishmentById(punishmentId);
                        if (entry != null && entry.wasByIp()) {
                            DatabaseManager.PlayerInfo pInfo = plugin.getSoftBanDatabaseManager()
                                    .getPlayerInfo(punishmentId);
                            if (pInfo != null && pInfo.getIp() != null) {
                                applyIpUnpunishmentToOnlinePlayers(SOFTBAN_PUNISHMENT_TYPE, pInfo.getIp());
                            }
                        }

                        plugin.getSoftBannedPlayersCache().remove(targetUUID);
                        plugin.getSoftbannedCommandsCache().remove(targetUUID);
                        playSound(player, "punish_confirm");
                        sendUnpunishConfirmation(player, target, SOFTBAN_PUNISHMENT_TYPE, punishmentId);
                        executeHookActions(player, target, SOFTBAN_PUNISHMENT_TYPE, "N/A", reason, true,
                                Collections.emptyList());
                        player.closeInventory();
                    });
                });
    }

    private void confirmUnfreeze(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = plugin.getConfigManager().getDefaultUnpunishmentReason(FREEZE_PUNISHMENT_TYPE);
        boolean removed = plugin.getPluginFrozenPlayers().containsKey(targetUUID);

        if (removed) {
            String originalPunishmentId = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentId(targetUUID,
                    FREEZE_PUNISHMENT_TYPE);

            plugin.getSoftBanDatabaseManager()
                    .executeUnpunishmentAsync(targetUUID, FREEZE_PUNISHMENT_TYPE, player.getName(), reason,
                            originalPunishmentId)
                    .thenAccept(punishmentId -> {
                        if (punishmentId == null)
                            return;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager()
                                    .getPunishmentById(punishmentId);
                            if (entry != null && entry.wasByIp()) {
                                DatabaseManager.PlayerInfo pInfo = plugin.getSoftBanDatabaseManager()
                                        .getPlayerInfo(punishmentId);
                                if (pInfo != null && pInfo.getIp() != null) {
                                    applyIpUnpunishmentToOnlinePlayers(FREEZE_PUNISHMENT_TYPE, pInfo.getIp());
                                }
                            }

                            plugin.getPluginFrozenPlayers().remove(targetUUID);
                            plugin.getFreezeListener().endFreezeChatSession(targetUUID);
                            playSound(player, "punish_confirm");
                            sendUnpunishConfirmation(player, target, FREEZE_PUNISHMENT_TYPE, punishmentId);
                            Player onlineTarget = target.getPlayer();
                            if (onlineTarget != null) {
                                plugin.getFreezeListener().stopFreezeActionsTask(targetUUID);
                                sendUnfreezeMessage(onlineTarget);
                            }
                            executeHookActions(player, target, FREEZE_PUNISHMENT_TYPE, "N/A", reason, true,
                                    Collections.emptyList());
                            player.closeInventory();
                        });
                    });
        } else {
            sendConfigMessage(player, "messages.no_active_freeze", "{target}",
                    target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }

    @SuppressWarnings("deprecation")
    private void executeUnbanAction(Player player, InventoryHolder holder, String reason) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(BAN_PUNISHMENT_TYPE);
        String commandTemplate = plugin.getConfigManager().getUnpunishCommand(BAN_PUNISHMENT_TYPE);

        plugin.getSoftBanDatabaseManager()
                .executeUnpunishmentAsync(targetUUID, BAN_PUNISHMENT_TYPE, player.getName(), reason, null)
                .thenAccept(punishmentId -> {
                    if (punishmentId == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> sendConfigMessage(player, "messages.not_banned"));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (useInternal) {
                            DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager()
                                    .getPunishmentById(punishmentId);
                            boolean wasByIp = entry != null && entry.wasByIp();
                            boolean pardoned = false;

                            if (wasByIp) {
                                DatabaseManager.PlayerInfo pInfo = plugin.getSoftBanDatabaseManager()
                                        .getPlayerInfo(punishmentId);
                                if (pInfo != null && pInfo.getIp() != null) {
                                    String ip = pInfo.getIp();
                                    if (Bukkit.getBanList(org.bukkit.BanList.Type.IP).isBanned(ip)) {
                                        Bukkit.getBanList(org.bukkit.BanList.Type.IP).pardon(ip);
                                        pardoned = true;
                                    }
                                }
                            }
                            if (!pardoned && target.getName() != null
                                    && Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(target.getName())) {
                                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(target.getName());
                            }
                        } else {
                            String processedCommand = commandTemplate.replace("{target}",
                                    target.getName() != null ? target.getName() : targetUUID.toString());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                        }

                        playSound(player, "punish_confirm");
                        sendUnpunishConfirmation(player, target, BAN_PUNISHMENT_TYPE, punishmentId);
                        executeHookActions(player, target, BAN_PUNISHMENT_TYPE, "N/A", reason, true,
                                Collections.emptyList());
                        player.closeInventory();
                    });
                });
    }

    private void executeUnmuteAction(Player player, InventoryHolder holder, String reason) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(MUTE_PUNISHMENT_TYPE);
        String commandTemplate = plugin.getConfigManager().getUnpunishCommand(MUTE_PUNISHMENT_TYPE);

        plugin.getSoftBanDatabaseManager()
                .executeUnpunishmentAsync(targetUUID, MUTE_PUNISHMENT_TYPE, player.getName(), reason, null)
                .thenAccept(punishmentId -> {
                    if (punishmentId == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> sendConfigMessage(player, "messages.not_muted"));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager()
                                .getPunishmentById(punishmentId);
                        if (entry != null && entry.wasByIp()) {
                            DatabaseManager.PlayerInfo pInfo = plugin.getSoftBanDatabaseManager()
                                    .getPlayerInfo(punishmentId);
                            if (pInfo != null && pInfo.getIp() != null) {
                                applyIpUnpunishmentToOnlinePlayers(MUTE_PUNISHMENT_TYPE, pInfo.getIp());
                            }
                        }

                        plugin.getMutedPlayersCache().remove(targetUUID);

                        if (!useInternal) {
                            String processedCommand = commandTemplate.replace("{target}",
                                    target.getName() != null ? target.getName() : targetUUID.toString());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                        }

                        playSound(player, "punish_confirm");
                        sendUnpunishConfirmation(player, target, MUTE_PUNISHMENT_TYPE, punishmentId);
                        executeHookActions(player, target, MUTE_PUNISHMENT_TYPE, "N/A", reason, true,
                                Collections.emptyList());
                        player.closeInventory();
                    });
                });
    }

    private void executeUnwarnAction(Player player, InventoryHolder holder, String reason) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(WARN_PUNISHMENT_TYPE);

        player.closeInventory();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (useInternal) {
                DatabaseManager dbManager = plugin.getSoftBanDatabaseManager();
                ActiveWarningEntry activeWarning = dbManager.getLatestActiveWarning(targetUUID);

                if (activeWarning == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sendConfigMessage(player, "messages.no_active_warn", "{target}", target.getName());
                        playSound(player, "punish_error");
                    });
                    return;
                }

                String punishmentId = activeWarning.getPunishmentId();
                String finalReason = reason
                        .equals(plugin.getConfigManager().getDefaultUnpunishmentReason(WARN_PUNISHMENT_TYPE))
                                ? reason.replace("{player}", player.getName()) + " (ID: " + punishmentId + ")"
                                : reason;

                dbManager.removeActiveWarning(targetUUID, punishmentId, player.getName(), finalReason);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendUnpunishConfirmation(player, target, WARN_PUNISHMENT_TYPE, punishmentId);
                    playSound(player, "punish_confirm");
                });
            } else {
                plugin.getSoftBanDatabaseManager()
                        .executeUnpunishmentAsync(targetUUID, WARN_PUNISHMENT_TYPE, player.getName(), reason, null)
                        .thenAccept(punishmentId -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (punishmentId == null) {
                                    sendConfigMessage(player, "messages.no_active_warn", "{target}", target.getName());
                                    return;
                                }
                                String commandTemplate = plugin.getConfigManager().getUnpunishCommand("warn");
                                if (!commandTemplate.isEmpty()) {
                                    String processedCommand = commandTemplate.replace("{target}",
                                            target.getName() != null ? target.getName() : targetUUID.toString());
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                                }
                                playSound(player, "punish_confirm");
                                sendUnpunishConfirmation(player, target, WARN_PUNISHMENT_TYPE, punishmentId);
                                executeHookActions(player, target, WARN_PUNISHMENT_TYPE, "N/A", reason, true,
                                        Collections.emptyList());
                            });
                        });
            }
        });
    }

    private void setPermanentTime(PunishDetailsMenu detailsMenu, Player player) {
        String permanentDisplayString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        detailsMenu.setBanTime(permanentDisplayString);
        reopenDetailsMenu(player, detailsMenu);
    }

    private void handleTimeDisplayClick(TimeSelectorMenu timeSelectorMenu, PunishDetailsMenu detailsMenu,
            Player player) {
        if (timeSelectorMenu.getCurrentTimeSeconds() > 0) {
            String formattedTime = timeSelectorMenu.getFormattedTime();
            detailsMenu.setBanTime(formattedTime);
            reopenDetailsMenu(player, detailsMenu);
        } else {
            sendConfigMessage(player, "messages.set_valid_time_confirm");
            playSound(player, "punish_error");
        }
    }

    private MenuItem getMenuItemClicked(int slot, InventoryHolder holder) {
        if (holder instanceof PunishMenu menu)
            return getPunishMenuItem(slot, menu);
        if (holder instanceof PunishDetailsMenu menu)
            return getPunishDetailsMenuItem(slot, menu);
        if (holder instanceof TimeSelectorMenu menu)
            return getTimeSelectorMenuItem(slot, menu);
        if (holder instanceof HistoryMenu menu)
            return getHistoryMenuItem(slot, menu);
        if (holder instanceof ProfileMenu)
            return getProfileMenuItem(slot);
        if (holder instanceof FullInventoryMenu)
            return getFullInventoryMenuItem(slot);
        if (holder instanceof EnderChestMenu)
            return getEnderChestMenuItem(slot);
        if (holder instanceof ReportsMenu)
            return getReportsMenuItem(slot); // ADDED
        if (holder instanceof ReportDetailsMenu)
            return getReportDetailsMenuItem(slot); // ADDED
        if (holder instanceof LockerMenu) {
            // Check static slots from config
            for (String key : List.of("next_page", "previous_page", "clear_locker")) {
                MenuItem item = plugin.getConfigManager().getLockerMenuItemConfig(key);
                if (item != null && item.getSlots().contains(slot))
                    return item;
            }
        }
        return null;
    }

    private MenuItem getPunishMenuItem(int slot, PunishMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getPunishMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot))
                return menuItem;
        }
        return null;
    }

    private MenuItem getPunishDetailsMenuItem(int slot, PunishDetailsMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        String punishmentType = menu.getPunishmentType();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getDetailsMenuItemConfig(punishmentType, itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot))
                return menuItem;
        }
        return null;
    }

    private MenuItem getTimeSelectorMenuItem(int slot, TimeSelectorMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getTimeSelectorItemKeys()) {
            MenuItem menuItem = configManager.getTimeSelectorMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot))
                return menuItem;
        }
        return null;
    }

    private MenuItem getHistoryMenuItem(int slot, HistoryMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getHistoryMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot))
                return menuItem;
        }
        for (MenuItem historyEntryItem : menu.getHistoryEntryItems()) {
            if (historyEntryItem != null && historyEntryItem.getSlots() != null
                    && historyEntryItem.getSlots().contains(slot))
                return historyEntryItem;
        }
        return null;
    }

    private MenuItem getProfileMenuItem(int slot) {
        for (String key : plugin.getConfigManager().getProfileMenuItemKeys()) {
            MenuItem item = plugin.getConfigManager().getProfileMenuItemConfig(key);
            if (item != null && item.getSlots() != null && item.getSlots().contains(slot)) {
                return item;
            }
        }
        return null;
    }

    private MenuItem getFullInventoryMenuItem(int slot) {
        for (String key : plugin.getConfigManager().getFullInventoryMenuItemKeys()) {
            MenuItem item = plugin.getConfigManager().getFullInventoryMenuItemConfig(key);
            if (item != null && item.getSlots() != null && item.getSlots().contains(slot)) {
                return item;
            }
        }
        return null;
    }

    private MenuItem getEnderChestMenuItem(int slot) {
        for (String key : plugin.getConfigManager().getEnderChestMenuItemKeys()) {
            MenuItem item = plugin.getConfigManager().getEnderChestMenuItemConfig(key);
            if (item != null && item.getSlots() != null && item.getSlots().contains(slot)) {
                return item;
            }
        }
        return null;
    }

    // ADDED START
    private MenuItem getReportsMenuItem(int slot) {
        for (String key : plugin.getConfigManager().getReportsMenuItemKeys()) {
            MenuItem item = plugin.getConfigManager().getReportsMenuItemConfig(key);
            if (item != null && item.getSlots() != null && item.getSlots().contains(slot)) {
                return item;
            }
        }
        // Also check for dynamically placed report entries
        if (slot >= 10 && slot <= 43) { // Assuming these are the slots for entries
            return plugin.getConfigManager().getReportsMenuItemConfig("report_entry");
        }
        return null;
    }

    private MenuItem getReportDetailsMenuItem(int slot) {
        for (String key : plugin.getConfigManager().getReportDetailsMenuItemKeys()) {
            MenuItem item = plugin.getConfigManager().getReportDetailsMenuItemConfig(key);
            if (item != null && item.getSlots() != null && item.getSlots().contains(slot)) {
                return item;
            }
        }
        return null;
    }
    // ADDED END

    private OfflinePlayer getTargetForAction(InventoryHolder holder) {
        if (holder == null)
            return null;

        UUID targetUUID = null;
        if (holder instanceof PunishMenu menu)
            targetUUID = menu.getTargetUUID();
        else if (holder instanceof PunishDetailsMenu menu)
            targetUUID = menu.getTargetUUID();
        else if (holder instanceof TimeSelectorMenu menu)
            targetUUID = menu.getPunishDetailsMenu().getTargetUUID();
        else if (holder instanceof HistoryMenu menu)
            targetUUID = menu.getTargetUUID();
        else if (holder instanceof ProfileMenu menu)
            targetUUID = menu.getTargetUUID();
        else if (holder instanceof FullInventoryMenu menu)
            targetUUID = menu.getTargetUUID();
        else if (holder instanceof EnderChestMenu menu)
            targetUUID = menu.getTargetUUID();
        else if (holder instanceof ReportDetailsMenu menu) {
            DatabaseManager.ReportEntry entry = menu.getReportEntry();
            if (entry != null) {
                targetUUID = entry.getTargetUUID();
            }
        } else if (holder instanceof TempHolder temp)
            targetUUID = temp.getTargetUUID();

        return targetUUID != null ? Bukkit.getOfflinePlayer(targetUUID) : null;
    }

    private List<Player> getMods() {
        return Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(MOD_PERMISSION))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("deprecation")
    private void playSound(Player player, String soundKey) {
        String soundName = plugin.getConfigManager().getSoundName(soundKey);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound configured for key '" + soundKey + "': " + soundName);
            }
        }
    }

    private void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        MessageUtils.sendConfigMessage(plugin, sender, path, replacements);
    }

    private void sendNoPermissionMenuMessage(Player player, String actionName) {
        sendConfigMessage(player, "messages.no_permission_menu_action", "{action}", actionName);
    }

    private void sendValidationMessages(Player player, boolean timeMissing, boolean reasonMissing) {
        if (timeMissing && reasonMissing)
            sendConfigMessage(player, "messages.set_time_reason_before_confirm");
        else if (timeMissing)
            sendConfigMessage(player, "messages.set_time_before_confirm");
        else if (reasonMissing)
            sendConfigMessage(player, "messages.set_reason_before_confirm");
        playSound(player, "punish_error");
    }

    private void sendPunishmentConfirmation(Player player, OfflinePlayer target, String timeValue, String reason,
            String punishmentType, String punishmentId) {
        player.closeInventory();
        boolean byIp = plugin.getConfigManager().isPunishmentByIp(punishmentType) && target.isOnline();
        String messageKey = byIp ? "messages.punishment_confirmed_ip" : "messages.punishment_confirmed";
        String punishmentActionVerb = plugin.getConfigManager().getPunishmentDisplayForm(punishmentType, true);
        sendConfigMessage(player, messageKey,
                "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString(),
                "{time}", timeValue,
                "{reason}", reason,
                "{punishment_action_verb}", punishmentActionVerb,
                "{punishment_id}", punishmentId);
    }

    private void sendUnpunishConfirmation(Player player, OfflinePlayer target, String punishType, String punishmentId) {
        player.closeInventory();
        sendConfigMessage(player, "messages.direct_unpunishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString(),
                "{punishment_type}", punishType,
                "{punishment_id}", punishmentId);
    }

    private void sendFreezeReceivedMessage(Player player) {
        sendConfigMessage(player, "messages.you_are_frozen");
    }

    private void sendUnfreezeMessage(Player player) {
        sendConfigMessage(player, "messages.you_are_unfrozen");
    }

    private long calculateEndTime(String timeInput) {
        if (timeInput == null)
            return 0L;
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        if (timeInput.equalsIgnoreCase(permanentDisplay))
            return Long.MAX_VALUE;
        int seconds = TimeUtils.parseTime(timeInput, plugin.getConfigManager());
        if (seconds <= 0)
            return 0L;
        return System.currentTimeMillis() + (seconds * 1000L);
    }

    private boolean hasBypassPermission(OfflinePlayer target, String punishmentType) {
        if (!(target instanceof Player onlineTarget))
            return false;
        String bypassPerm = "crown.bypass." + punishmentType.toLowerCase();
        return onlineTarget.hasPermission(bypassPerm);
    }

    private void sendBypassError(Player punisher, OfflinePlayer target, String punishmentType) {
        String messageKey = "messages.bypass_error_" + punishmentType.toLowerCase();
        sendConfigMessage(punisher, messageKey, "{target}",
                target.getName() != null ? target.getName() : target.getUniqueId().toString());
    }

    private boolean checkPunishmentPermission(Player executor, String punishmentType) {
        String perm = "crown.punish." + punishmentType.toLowerCase();
        return executor.hasPermission(perm);
    }

    public void executeHookActions(CommandSender executor, OfflinePlayer target, String punishmentType, String time,
            String reason, boolean isUnpunish, List<ClickActionData> actions) {
        executeHookActions(executor, target, punishmentType, time, reason, isUnpunish, actions, null);
    }

    public void executeHookActions(CommandSender executor, OfflinePlayer target, String punishmentType, String time,
            String reason, boolean isUnpunish, List<ClickActionData> actions, ActiveWarningEntry warningContext) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        String hookType = isUnpunish ? "on_unpunish" : "on_punish";
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Preparing to execute " + actions.size() + " hook actions for " + hookType
                    + "." + punishmentType);
        }

        final String finalTime = (time != null) ? time : "N/A";
        final String finalReason = (reason != null) ? reason : "N/A";

        final Pattern placeholderPattern = Pattern.compile("\\{associated_punishment_id:([a-zA-Z]+)}");

        for (ClickActionData actionData : actions) {
            String[] originalArgs = actionData.getActionData();
            String[] processedHookArgs = new String[originalArgs.length];

            for (int i = 0; i < originalArgs.length; i++) {
                if (originalArgs[i] != null) {
                    String currentArg = originalArgs[i];
                    Matcher matcher = placeholderPattern.matcher(currentArg);
                    if (warningContext != null && matcher.find()) {
                        String type = matcher.group(1);
                        String associatedIds = warningContext.getAssociatedPunishmentIds();
                        String replacementId = "NOT_FOUND";
                        if (associatedIds != null && !associatedIds.isEmpty()) {
                            for (String pair : associatedIds.split(";")) {
                                String[] parts = pair.split(":");
                                if (parts.length == 2 && parts[0].equalsIgnoreCase(type)) {
                                    replacementId = parts[1];
                                    break;
                                }
                            }
                        }
                        currentArg = matcher.replaceAll(replacementId);
                    }

                    if (target != null) {
                        currentArg = processAllPlaceholders(currentArg, executor, new TempHolder(target.getUniqueId()));
                    }

                    currentArg = currentArg.replace("{reason}", finalReason);
                    currentArg = currentArg.replace("{time}", finalTime);
                    currentArg = currentArg.replace("{punishment_type}", punishmentType);
                    processedHookArgs[i] = MessageUtils.getColorMessage(currentArg);
                } else {
                    processedHookArgs[i] = null;
                }
            }

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] -> Executing Hook Action: " + actionData.getAction()
                        + " with Processed Args: " + Arrays.toString(processedHookArgs));
            }

            executeSpecificHookAction(executor, target, actionData.getAction(), processedHookArgs, plugin,
                    warningContext);
        }
    }

    @SuppressWarnings("deprecation")
    private void executeSpecificHookAction(CommandSender executor, OfflinePlayer target, ClickAction action,
            String[] actionArgs, Crown plugin, ActiveWarningEntry warningContext) {
        if (action == ClickAction.NO_ACTION)
            return;

        Player playerExecutor = (executor instanceof Player) ? (Player) executor : null;

        if (plugin.getConfigManager().isDebugEnabled()) {
            String executorName = (executor instanceof Player) ? executor.getName() : "Console";
            String targetName = (target != null && target.getName() != null) ? target.getName() : "Unknown";
            plugin.getLogger().info("[DEBUG] executeSpecificHookAction - Action: " + action + " | Executor: "
                    + executorName + " | Target: " + targetName + " | Using Args: " + Arrays.toString(actionArgs));
        }

        switch (action) {
            case CONSOLE_COMMAND:
                if (actionArgs.length >= 1 && actionArgs[0] != null) {
                    final String commandToRun = actionArgs[0];
                    Bukkit.getScheduler().runTask(plugin,
                            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun));
                } else {
                    logInvalidArgs(action, actionArgs, plugin);
                }
                break;

            case PLAYER_COMMAND:
            case PLAYER_COMMAND_OP:
                if (playerExecutor == null) {
                    plugin.getLogger().warning(
                            "Skipping PLAYER_COMMAND/PLAYER_COMMAND_OP hook action: Executor is not a player.");
                    return;
                }
                if (actionArgs.length >= 1 && actionArgs[0] != null) {
                    final String commandToRun = actionArgs[0];
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (action == ClickAction.PLAYER_COMMAND_OP) {
                            boolean wasOp = playerExecutor.isOp();
                            try {
                                playerExecutor.setOp(true);
                                playerExecutor.performCommand(commandToRun);
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.SEVERE, "Error executing OP command '" + commandToRun
                                        + "' for player " + playerExecutor.getName(), e);
                            } finally {
                                if (!wasOp) {
                                    playerExecutor.setOp(false);
                                }
                            }
                        } else {
                            playerExecutor.performCommand(commandToRun);
                        }
                    });
                } else {
                    logInvalidArgs(action, actionArgs, plugin);
                }
                break;

            case APPLY_SOFTBAN:
            case APPLY_MUTE:
            case APPLY_BAN:
            case APPLY_KICK:
                if (warningContext == null) {
                    plugin.getLogger().warning("Cannot execute " + action
                            + " hook action: This action requires a warning context but was called without one.");
                    return;
                }
                if (target == null) {
                    logTargetMissing(action, plugin);
                    return;
                }
                if (actionArgs.length == 0 || actionArgs[0] == null) {
                    logInvalidArgs(action, actionArgs, plugin);
                    return;
                }

                boolean expectsDuration = action != ClickAction.APPLY_KICK;
                HookPunishmentArgs parsedArgs = parseHookPunishmentArgs(actionArgs[0], expectsDuration);
                if (parsedArgs == null) {
                    logInvalidArgs(action, actionArgs, plugin);
                    return;
                }

                String duration = parsedArgs.duration();
                String punishmentReason = parsedArgs.reason();
                String executorName = (executor instanceof Player) ? executor.getName() : "Console";
                DatabaseManager dbManager = plugin.getSoftBanDatabaseManager();

                String type = "";
                List<String> customCommands = null;
                switch (action) {
                    case APPLY_SOFTBAN:
                        type = "softban";
                        WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(warningContext.getWarnLevel());
                        customCommands = (levelConfig != null) ? levelConfig.getSoftbanBlockedCommands()
                                : Collections.emptyList();
                        break;
                    case APPLY_MUTE:
                        type = "mute";
                        break;
                    case APPLY_BAN:
                        type = "ban";
                        break;
                    case APPLY_KICK:
                        type = "kick";
                        break;
                }

                boolean byIp = parsedArgs.byIpOverride() != null ? parsedArgs.byIpOverride()
                        : plugin.getConfigManager().isPunishmentByIp(type);
                String ipAddress = null;
                if (byIp) {
                    ipAddress = resolveTargetIp(target);
                    if (ipAddress == null) {
                        plugin.getLogger().warning("Unable to resolve IP for " + action + " hook action on target: "
                                + target.getUniqueId());
                        return;
                    }
                } else if (action == ClickAction.APPLY_KICK && !target.isOnline()) {
                    plugin.getLogger().warning(
                            "Cannot execute " + action + " hook action: Target is offline and punishment is local.");
                    return;
                }

                long endTime;
                String durationForLog;
                if (action == ClickAction.APPLY_KICK) {
                    endTime = 0L;
                    durationForLog = "N/A";
                } else if ("permanent".equalsIgnoreCase(duration)) {
                    endTime = Long.MAX_VALUE;
                    durationForLog = duration;
                } else {
                    int seconds = TimeUtils.parseTime(duration, plugin.getConfigManager());
                    if (seconds <= 0) {
                        plugin.getLogger().warning("Invalid duration '" + duration + "' for " + action
                                + " action. Punishment not applied.");
                        return;
                    }
                    endTime = System.currentTimeMillis() + (seconds * 1000L);
                    durationForLog = duration;
                }

                final String finalType = type;
                final List<String> finalCustomCommands = customCommands;
                final boolean finalByIp = byIp;
                final String finalIpAddress = ipAddress;
                final String finalDurationForLog = durationForLog;
                final long finalEndTime = endTime;
                dbManager
                        .executePunishmentAsync(target.getUniqueId(), target.getName(), finalType, punishmentReason,
                                executorName,
                                endTime, durationForLog, byIp, finalCustomCommands, warningContext.getWarnLevel())
                        .thenAccept(punishmentId -> {
                            if (punishmentId != null) {
                                dbManager.addAssociatedPunishmentId(warningContext.getPunishmentId(), finalType,
                                        punishmentId);
                                dbManager.logPlayerInfoAsync(punishmentId, target, finalByIp ? finalIpAddress : null);

                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    switch (finalType) {
                                        case "softban":
                                            plugin.getSoftBannedPlayersCache().put(target.getUniqueId(), finalEndTime);
                                            plugin.getSoftbannedCommandsCache().put(target.getUniqueId(),
                                                    finalCustomCommands.isEmpty()
                                                            ? plugin.getConfigManager().getBlockedCommands()
                                                            : finalCustomCommands);
                                            if (finalByIp && finalIpAddress != null) {
                                                applyIpPunishmentToOnlinePlayers(finalType, finalIpAddress,
                                                        finalEndTime, punishmentReason, finalDurationForLog,
                                                        punishmentId, target.getUniqueId());
                                            }
                                            break;
                                        case "mute":
                                            plugin.getMutedPlayersCache().put(target.getUniqueId(), finalEndTime);
                                            if (finalByIp && finalIpAddress != null) {
                                                applyIpPunishmentToOnlinePlayers(finalType, finalIpAddress,
                                                        finalEndTime, punishmentReason, finalDurationForLog,
                                                        punishmentId, target.getUniqueId());
                                            }
                                            break;
                                        case "ban":
                                            if (finalByIp && finalIpAddress != null) {
                                                Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(finalIpAddress,
                                                        punishmentReason,
                                                        finalEndTime == Long.MAX_VALUE ? null : new Date(finalEndTime),
                                                        executorName);
                                            } else if (target.getName() != null) {
                                                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(),
                                                        punishmentReason,
                                                        finalEndTime == Long.MAX_VALUE ? null : new Date(finalEndTime),
                                                        executorName);
                                            }
                                            if (target.isOnline()) {
                                                Player onlineTarget = target.getPlayer();
                                                if (onlineTarget != null) {
                                                    onlineTarget.kickPlayer(MessageUtils.getKickMessage(
                                                            plugin.getConfigManager().getBanScreen(), punishmentReason,
                                                            finalDurationForLog, punishmentId,
                                                            finalEndTime == Long.MAX_VALUE ? null
                                                                    : new Date(finalEndTime),
                                                            plugin.getConfigManager()));
                                                }
                                            }
                                            if (finalByIp && finalIpAddress != null) {
                                                applyIpPunishmentToOnlinePlayers(finalType, finalIpAddress,
                                                        finalEndTime, punishmentReason, finalDurationForLog,
                                                        punishmentId, target.getUniqueId());
                                            }
                                            break;
                                        case "kick":
                                            if (target.isOnline()) {
                                                Player onlineTarget = target.getPlayer();
                                                if (onlineTarget != null) {
                                                    String kickMessage = MessageUtils.getKickMessage(
                                                            plugin.getConfigManager().getKickScreen(), punishmentReason,
                                                            finalDurationForLog, punishmentId, null,
                                                            plugin.getConfigManager());
                                                    onlineTarget.kickPlayer(kickMessage);
                                                }
                                            }
                                            if (finalByIp && finalIpAddress != null) {
                                                applyIpPunishmentToOnlinePlayers(finalType, finalIpAddress, 0L,
                                                        punishmentReason, finalDurationForLog, punishmentId,
                                                        target.getUniqueId());
                                            }
                                            break;
                                    }
                                });
                            }
                        });
                break;

            case PLAY_SOUND:
                if (playerExecutor != null) {
                    executePlaySoundAction(playerExecutor, actionArgs);
                }
                break;
            case PLAY_SOUND_TARGET:
                if (target != null) {
                    executePlaySoundTargetAction(new TempHolder(target.getUniqueId()), actionArgs);
                } else {
                    logTargetMissing(action, plugin);
                }
                break;
            case PLAY_SOUND_MODS:
                executePlaySoundModsAction(actionArgs);
                break;
            case TITLE:
                if (playerExecutor != null) {
                    executeTitleAction(playerExecutor, actionArgs,
                            new TempHolder(target != null ? target.getUniqueId() : null));
                }
                break;
            case TITLE_TARGET:
                if (target != null) {
                    executeTitleTargetAction(playerExecutor, new TempHolder(target.getUniqueId()), actionArgs);
                } else {
                    logTargetMissing(action, plugin);
                }
                break;
            case TITLE_MODS:
                executeTitleModsAction(playerExecutor, new TempHolder(target != null ? target.getUniqueId() : null),
                        actionArgs);
                break;
            case MESSAGE:
                executor.sendMessage(actionArgs.length > 0 ? actionArgs[0] : "");
                break;
            case MESSAGE_TARGET:
                if (target != null) {
                    executeMessageTargetAction(playerExecutor, new TempHolder(target.getUniqueId()), actionArgs);
                } else {
                    logTargetMissing(action, plugin);
                }
                break;
            case MESSAGE_MODS:
                executeMessageModsAction(playerExecutor, new TempHolder(target != null ? target.getUniqueId() : null),
                        actionArgs);
                break;
            case ACTIONBAR:
                if (playerExecutor != null) {
                    executeActionbarAction(playerExecutor, actionArgs, null);
                }
                break;
            case ACTIONBAR_TARGET:
                if (target != null) {
                    executeActionbarTargetAction(playerExecutor, new TempHolder(target.getUniqueId()), actionArgs);
                } else {
                    logTargetMissing(action, plugin);
                }
                break;
            case ACTIONBAR_MODS:
                executeActionbarModsAction(playerExecutor, new TempHolder(target != null ? target.getUniqueId() : null),
                        actionArgs);
                break;
            case GIVE_EFFECT_TARGET:
                if (target != null) {
                    executeGiveEffectTargetAction(new TempHolder(target.getUniqueId()), actionArgs);
                } else {
                    logTargetMissing(action, plugin);
                }
                break;

            default:
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[DEBUG] Skipping unsupported action type for hook execution: " + action);
                }
                break;
        }
    }

    private void logTargetMissing(ClickAction action, Crown plugin) {
        plugin.getLogger().warning("Cannot execute hook action " + action + ": Target player context is missing.");
    }

    private void logInvalidArgs(ClickAction action, String[] args, Crown plugin) {
        plugin.getLogger().warning("Invalid arguments for hook action " + action + ": " + Arrays.toString(args));
    }

    private record HookPunishmentArgs(String duration, String reason, Boolean byIpOverride) {
    }

    private HookPunishmentArgs parseHookPunishmentArgs(String rawArgs, boolean expectsDuration) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return null;
        }

        String[] parts = rawArgs.split(":");
        if (expectsDuration) {
            if (parts.length < 2) {
                return null;
            }
            String duration = parts[0].trim();
            if (duration.isEmpty()) {
                return null;
            }
            int endIndex = parts.length;
            Boolean override = parseByIpOverride(parts[parts.length - 1]);
            if (override != null && parts.length >= 3) {
                endIndex--;
            } else {
                override = null;
            }
            if (endIndex <= 1) {
                return null;
            }
            String reason = String.join(":", Arrays.copyOfRange(parts, 1, endIndex)).trim();
            if (reason.isEmpty()) {
                return null;
            }
            return new HookPunishmentArgs(duration, reason, override);
        }

        int endIndex = parts.length;
        Boolean override = parseByIpOverride(parts[parts.length - 1]);
        if (override != null && parts.length >= 2) {
            endIndex--;
        } else {
            override = null;
        }
        String reason = String.join(":", Arrays.copyOfRange(parts, 0, endIndex)).trim();
        if (reason.isEmpty()) {
            return null;
        }
        return new HookPunishmentArgs(null, reason, override);
    }

    private Boolean parseByIpOverride(String token) {
        if (token == null) {
            return null;
        }
        if ("ip".equalsIgnoreCase(token)) {
            return true;
        }
        if ("local".equalsIgnoreCase(token)) {
            return false;
        }
        return null;
    }

    private String resolveTargetIp(OfflinePlayer target) {
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null && onlineTarget.getAddress() != null
                    && onlineTarget.getAddress().getAddress() != null) {
                return onlineTarget.getAddress().getAddress().getHostAddress();
            }
        }
        return plugin.getSoftBanDatabaseManager().getLastKnownIp(target.getUniqueId());
    }

    @SuppressWarnings("deprecation")
    public void applyIpPunishmentToOnlinePlayers(String punishmentType, String ipAddress, long endTime, String reason,
            String durationForLog, String punishmentId, UUID originalTargetUUID) {
        String lowerCasePunishType = punishmentType.toLowerCase();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(originalTargetUUID)) {
                continue;
            }

            InetSocketAddress playerAddress = onlinePlayer.getAddress();
            if (playerAddress != null && playerAddress.getAddress() != null
                    && playerAddress.getAddress().getHostAddress().equals(ipAddress)) {

                switch (lowerCasePunishType) {
                    case "ban":
                    case "kick":
                        Date expiration = (endTime == Long.MAX_VALUE || lowerCasePunishType.equals("kick")) ? null
                                : new Date(endTime);
                        List<String> screenLines = lowerCasePunishType.equals("ban")
                                ? plugin.getConfigManager().getBanScreen()
                                : plugin.getConfigManager().getKickScreen();
                        String kickMessage = MessageUtils.getKickMessage(screenLines, reason, durationForLog,
                                punishmentId, expiration, plugin.getConfigManager());
                        onlinePlayer.kickPlayer(kickMessage);
                        break;

                    case "mute":
                        plugin.getMutedPlayersCache().put(onlinePlayer.getUniqueId(), endTime);
                        String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}",
                                durationForLog, "{reason}", reason, "{punishment_id}", punishmentId);
                        onlinePlayer.sendMessage(MessageUtils.getColorMessage(muteMessage));
                        break;

                    case "softban":
                        plugin.getSoftBannedPlayersCache().put(onlinePlayer.getUniqueId(), endTime);
                        plugin.getSoftbannedCommandsCache().put(onlinePlayer.getUniqueId(),
                                plugin.getConfigManager().getBlockedCommands());
                        String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned",
                                "{time}", durationForLog, "{reason}", reason, "{punishment_id}", punishmentId);
                        onlinePlayer.sendMessage(MessageUtils.getColorMessage(softbanMessage));
                        break;

                    case "freeze":
                        plugin.getPluginFrozenPlayers().put(onlinePlayer.getUniqueId(), true);
                        plugin.getFreezeListener().startFreezeActionsTask(onlinePlayer);
                        onlinePlayer.sendMessage(MessageUtils
                                .getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_frozen")));
                        break;
                }
            }
        }
    }

    public void applyIpUnpunishmentToOnlinePlayers(String punishmentType, String ipAddress) {
        applyIpUnpunishmentToOnlinePlayers(punishmentType, ipAddress, null);
    }

    public void applyIpUnpunishmentToOnlinePlayers(String punishmentType, String ipAddress, UUID originalTargetUUID) {
        String lowerCaseType = punishmentType.toLowerCase();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (originalTargetUUID != null && onlinePlayer.getUniqueId().equals(originalTargetUUID)) {
                continue;
            }
            InetSocketAddress playerAddress = onlinePlayer.getAddress();
            if (playerAddress != null && playerAddress.getAddress() != null
                    && playerAddress.getAddress().getHostAddress().equals(ipAddress)) {
                switch (lowerCaseType) {
                    case "mute":
                        plugin.getMutedPlayersCache().remove(onlinePlayer.getUniqueId());
                        sendConfigMessage(onlinePlayer, "messages.unmute_notification");
                        break;
                    case "softban":
                        plugin.getSoftBannedPlayersCache().remove(onlinePlayer.getUniqueId());
                        plugin.getSoftbannedCommandsCache().remove(onlinePlayer.getUniqueId());
                        sendConfigMessage(onlinePlayer, "messages.unsoftban_notification");
                        break;
                    case "freeze":
                        if (plugin.getPluginFrozenPlayers().remove(onlinePlayer.getUniqueId()) != null) {
                            plugin.getFreezeListener().stopFreezeActionsTask(onlinePlayer.getUniqueId());
                            plugin.getFreezeListener().endFreezeChatSession(onlinePlayer.getUniqueId());
                            sendConfigMessage(onlinePlayer, "messages.you_are_unfrozen");
                        }
                        break;
                }
            }
        }
    }

    private record TempHolder(UUID targetUUID) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(null, 9);
        }

        public UUID getTargetUUID() {
            return targetUUID;
        }
    }
}

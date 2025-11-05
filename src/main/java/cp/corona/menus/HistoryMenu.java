// src/main/java/cp/corona/menus/HistoryMenu.java
// MODIFIED: No code changes were necessary here. The generic item loading mechanism correctly
// processes the new active punishment count placeholders from history_menu.yml via MainConfigManager.
package cp.corona.menus;

import cp.corona.crown.Crown;
import cp.corona.database.ActiveWarningEntry;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class HistoryMenu implements InventoryHolder {

    private final Inventory inventory;
    private final UUID targetUUID;
    private final Crown plugin;
    private int page = 1;
    private final int entriesPerPage = 28;
    private final List<MenuItem> historyEntryItems = new ArrayList<>();
    private final Set<String> menuItemKeys = new HashSet<>();
    private List<DatabaseManager.PunishmentEntry> allHistoryEntries;


    private static final String BACK_BUTTON_KEY = "back_button";
    private static final String NEXT_PAGE_BUTTON_KEY = "next_page_button";
    private static final String PREVIOUS_PAGE_BUTTON_KEY = "previous_page_button";
    private static final String HISTORY_ENTRY_ITEM_KEY = "history_entry";
    private static final String BACKGROUND_FILL_KEY = "background_fill";

    private static final List<Integer> validSlots = List.of(
            10, 11, 12, 13, 14, 15, 16,   // Row 1
            19, 20, 21, 22, 23, 24, 25,   // Row 2
            28, 29, 30, 31, 32, 33, 34,   // Row 3
            37, 38, 39, 40, 41, 42, 43    // Row 4
    );

    public HistoryMenu(UUID targetUUID, Crown plugin) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getHistoryMenuTitle(target);
        inventory = Bukkit.createInventory(this, 54, title);
        loadMenuItems();
        loadAndProcessAllHistory();
        initializeItems(target);
    }

    private void loadAndProcessAllHistory() {
        allHistoryEntries = plugin.getSoftBanDatabaseManager().getPunishmentHistory(targetUUID, 1, Integer.MAX_VALUE);
        Map<String, ActiveWarningEntry> activeWarningsMap = plugin.getSoftBanDatabaseManager().getAllActiveAndPausedWarnings(targetUUID)
                .stream().collect(Collectors.toMap(ActiveWarningEntry::getPunishmentId, w -> w));

        for (DatabaseManager.PunishmentEntry entry : allHistoryEntries) {
            String type = entry.getType().toLowerCase();
            String status;

            boolean isSystemExpired = !entry.isActive() && "System".equals(entry.getRemovedByName())
                    && ("Expired".equalsIgnoreCase(entry.getRemovedReason()) || "Superseded by new warning.".equalsIgnoreCase(entry.getRemovedReason()));


            if (type.equals("warn")) {
                ActiveWarningEntry activeWarning = activeWarningsMap.get(entry.getPunishmentId());
                if (activeWarning != null) {
                    status = activeWarning.isPaused() ? plugin.getConfigManager().getMessage("placeholders.status_paused")
                            : plugin.getConfigManager().getMessage("placeholders.status_active");
                } else {
                    status = isSystemExpired ? plugin.getConfigManager().getMessage("placeholders.status_expired")
                            : plugin.getConfigManager().getMessage("placeholders.status_removed");
                }
            } else if (type.equals("kick")) {
                status = "";
            } else {
                if (entry.isActive()) {
                    status = (entry.getEndTime() > System.currentTimeMillis() || entry.getEndTime() == Long.MAX_VALUE)
                            ? plugin.getConfigManager().getMessage("placeholders.status_active")
                            : plugin.getConfigManager().getMessage("placeholders.status_expired");
                } else {
                    status = isSystemExpired ? plugin.getConfigManager().getMessage("placeholders.status_expired")
                            : plugin.getConfigManager().getMessage("placeholders.status_removed");
                }
            }
            entry.setStatus(status);
        }
    }


    private void initializeItems(OfflinePlayer target) {
        setItemInMenu(BACK_BUTTON_KEY,
                plugin.getConfigManager().getHistoryMenuItemConfig(BACK_BUTTON_KEY),
                target, 53);

        loadHistoryPage(target, page);

        updatePageButtons(target);

        fillEmptySlotsWithBackground(target);

        for (String itemKey : menuItemKeys) {
            if (!itemKey.equals(BACK_BUTTON_KEY) && !itemKey.equals(NEXT_PAGE_BUTTON_KEY) && !itemKey.equals(PREVIOUS_PAGE_BUTTON_KEY) && !itemKey.equals(BACKGROUND_FILL_KEY)) {
                ItemStack itemStack = getItemStack(itemKey, target);
                if (itemStack != null) {
                    setItemInMenu(itemKey, plugin.getConfigManager().getHistoryMenuItemConfig(itemKey), target);
                }
            }
        }
    }

    private void loadHistoryPage(OfflinePlayer target, int page) {
        clearHistoryEntries();
        historyEntryItems.clear();

        int start = (page - 1) * entriesPerPage;
        int end = Math.min(start + entriesPerPage, allHistoryEntries.size());
        List<DatabaseManager.PunishmentEntry> historyForPage = (start < allHistoryEntries.size()) ? allHistoryEntries.subList(start, end) : Collections.emptyList();

        Map<String, ActiveWarningEntry> activeWarningsMap = plugin.getSoftBanDatabaseManager().getAllActiveAndPausedWarnings(targetUUID)
                .stream().collect(Collectors.toMap(ActiveWarningEntry::getPunishmentId, w -> w));

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        int index = 0;
        for (DatabaseManager.PunishmentEntry entry : historyForPage) {
            if (index >= validSlots.size()) break;
            int slot = validSlots.get(index);

            String entryTypeConfigName = entry.getType().toLowerCase() + "_history_entry";
            MenuItem historyItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(entryTypeConfigName);
            MenuItem baseItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(HISTORY_ENTRY_ITEM_KEY);

            if (historyItemConfig == null) {
                historyItemConfig = baseItemConfig;
                if (historyItemConfig == null) continue;
            }

            MenuItem historyEntryItem = new MenuItem();
            historyEntryItem.setMaterial(historyItemConfig.getMaterial());
            historyEntryItem.setPlayerHead(historyItemConfig.getPlayerHead());

            String status = entry.getStatus() != null ? entry.getStatus() : "";
            String method = entry.wasByIp() ? plugin.getConfigManager().getMessage("placeholders.by_ip") : plugin.getConfigManager().getMessage("placeholders.by_local");

            String nameTemplate = Optional.ofNullable(historyItemConfig.getName())
                    .orElse(baseItemConfig != null ? baseItemConfig.getName() : "");

            String entryName = nameTemplate
                    .replace("{punishment_type}", entry.getType())
                    .replace("{method}", method)
                    .replace("{status}", status);
            historyEntryItem.setName(MessageUtils.getColorMessage(entryName));

            List<String> lore = new ArrayList<>();
            String originalPunishmentId = entry.getPunishmentId();
            String reason = entry.getReason();

            List<String> configLore = plugin.getConfigManager().getHistoryMenuItemLore(HISTORY_ENTRY_ITEM_KEY, target);
            for (String line : configLore) {
                String processedLine = line.replace("{punishment_id}", originalPunishmentId)
                        .replace("{reason}", reason)
                        .replace("{date}", dateFormat.format(entry.getTimestamp()))
                        .replace("{punisher}", entry.getPunisherName())
                        .replace("{duration}", getDurationDisplay(entry));
                lore.add(processedLine);
            }

            if (status.equals(plugin.getConfigManager().getMessage("placeholders.status_active"))) {
                String expiresAt = (entry.getPunishmentTime() == Long.MAX_VALUE) ? "Never" : dateFormat.format(new Date(entry.getPunishmentTime()));
                long remainingMillis = entry.getPunishmentTime() - System.currentTimeMillis();
                String timeLeft = (entry.getPunishmentTime() == Long.MAX_VALUE) ? "Permanent" : TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());
                lore.add("&7Expires: &b" + expiresAt + " (" + timeLeft + ")");
            } else if (status.equals(plugin.getConfigManager().getMessage("placeholders.status_removed"))) {
                if (entry.getRemovedByName() != null && entry.getRemovedAt() != null) {
                    lore.add("&cRemoved by: &e" + entry.getRemovedByName());
                    lore.add("&cAt: &e" + dateFormat.format(entry.getRemovedAt()));
                    if (entry.getRemovedReason() != null && !entry.getRemovedReason().isEmpty()) {
                        lore.add("&cReason: &e" + entry.getRemovedReason());
                    }
                }
            } else if (entry.getType().equalsIgnoreCase("warn")) {
                ActiveWarningEntry activeWarning = activeWarningsMap.get(entry.getPunishmentId());
                if (activeWarning != null) {
                    lore.add("&7Level: &b" + activeWarning.getWarnLevel());
                    String timeLeft;
                    if (activeWarning.isPaused()) {
                        timeLeft = TimeUtils.formatTime((int) (activeWarning.getRemainingTimeOnPause() / 1000), plugin.getConfigManager());
                        lore.add("&7Time Left: &e" + timeLeft);
                    } else if (activeWarning.getEndTime() != -1) {
                        timeLeft = TimeUtils.formatTime((int) ((activeWarning.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                        lore.add("&7Expires in: &e" + timeLeft);
                    }
                } else if (!entry.isActive() && entry.getRemovedByName() != null) { // Restored logic
                    lore.add("&cRemoved by: &e" + entry.getRemovedByName());
                    lore.add("&cAt: &e" + dateFormat.format(entry.getRemovedAt()));
                    if (entry.getRemovedReason() != null && !entry.getRemovedReason().isEmpty()) {
                        lore.add("&cReason: &e" + entry.getRemovedReason());
                    }
                }
            }


            historyEntryItem.setLore(lore);
            historyEntryItem.setSlots(List.of(slot));

            List<MenuItem.ClickActionData> leftClickActions = historyItemConfig.getLeftClickActions();
            if ((leftClickActions == null || leftClickActions.isEmpty()) && baseItemConfig != null) {
                leftClickActions = baseItemConfig.getLeftClickActions();
            }

            List<MenuItem.ClickActionData> rightClickActions = historyItemConfig.getRightClickActions();
            if ((rightClickActions == null || rightClickActions.isEmpty()) && baseItemConfig != null) {
                rightClickActions = baseItemConfig.getRightClickActions();
            }

            historyEntryItem.setLeftClickActions(processActions(leftClickActions, originalPunishmentId));
            historyEntryItem.setRightClickActions(processActions(rightClickActions, originalPunishmentId));

            setItemInMenu(HISTORY_ENTRY_ITEM_KEY, historyEntryItem, target, slot);
            historyEntryItems.add(historyEntryItem);
            index++;
        }
        updatePageButtons(target);
    }

    private List<MenuItem.ClickActionData> processActions(List<MenuItem.ClickActionData> actions, String punishmentId) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }

        List<MenuItem.ClickActionData> processedActions = new ArrayList<>();
        for (MenuItem.ClickActionData action : actions) {
            String[] processedArgs = Arrays.stream(action.getActionData())
                    .map(arg -> arg.replace("{punishment_id}", punishmentId))
                    .toArray(String[]::new);
            processedActions.add(new MenuItem.ClickActionData(action.getAction(), processedArgs));
        }
        return processedActions;
    }


    private String getDurationDisplay(DatabaseManager.PunishmentEntry entry) {
        String type = entry.getType().toLowerCase();
        if (type.equals("kick") || type.equals("freeze") ||
                (entry.getDurationString() != null && entry.getDurationString().equalsIgnoreCase("permanent"))) {
            return plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        } else if (entry.getDurationString() != null && !entry.getDurationString().isEmpty()) {
            return entry.getDurationString();
        }
        return "N/A";
    }


    private void updatePageButtons(OfflinePlayer target) {
        int totalCount = allHistoryEntries.size();
        boolean hasNextPage = totalCount > (page * entriesPerPage);

        if (page <= 1) {
            clearPageButton(51);
        } else {
            setItemInMenu(PREVIOUS_PAGE_BUTTON_KEY,
                    plugin.getConfigManager().getHistoryMenuItemConfig(PREVIOUS_PAGE_BUTTON_KEY),
                    target, 51);
        }

        if (!hasNextPage) {
            clearPageButton(52);
        } else {
            setItemInMenu(NEXT_PAGE_BUTTON_KEY,
                    plugin.getConfigManager().getHistoryMenuItemConfig(NEXT_PAGE_BUTTON_KEY),
                    target, 52);
        }
    }

    private void clearPageButton(int slot) {
        inventory.clear(slot);
    }

    private void fillEmptySlotsWithBackground(OfflinePlayer target) {
        MenuItem backgroundItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(BACKGROUND_FILL_KEY);
        if (backgroundItemConfig != null) {
            ItemStack backgroundItemStack = backgroundItemConfig.toItemStack(target, plugin.getConfigManager());
            if (backgroundItemStack != null) {
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    if (inventory.getItem(slot) == null && !validSlots.contains(slot) && !isButtonSlot(slot)) {
                        inventory.setItem(slot, backgroundItemStack.clone());
                    }
                }
            }
        }
    }

    private boolean isButtonSlot(int slot) {
        return slot == 51 || slot == 52 || slot == 53;
    }

    private ItemStack getItemStack(String itemKey, OfflinePlayer target) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().log(Level.INFO, "[HistoryMenu] getItemStack called for itemKey: " + itemKey);
        }
        MenuItem menuItemConfig = plugin.getConfigManager().getHistoryMenuItemConfig(itemKey);
        if (menuItemConfig != null) {
            return menuItemConfig.toItemStack(target, plugin.getConfigManager());
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.WARNING, "[HistoryMenu] getItemStack - No MenuItem config found for itemKey: " + itemKey);
            }
            return null;
        }
    }

    private void clearHistoryEntries() {
        for (int slot : validSlots) {
            inventory.clear(slot);
        }
    }

    private void setItemInMenu(String itemKey, MenuItem menuItemConfig, OfflinePlayer target, int slot) {
        if (menuItemConfig != null) {
            ItemStack itemStack = menuItemConfig.toItemStack(target, plugin.getConfigManager());
            if (itemStack != null) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    private void setItemInMenu(String itemKey, MenuItem menuItemConfig, OfflinePlayer target) {
        if (menuItemConfig != null) {
            ItemStack itemStack = menuItemConfig.toItemStack(target, plugin.getConfigManager());
            if (itemStack != null && menuItemConfig.getSlots() != null) {
                for (int slot : menuItemConfig.getSlots()) {
                    inventory.setItem(slot, itemStack);
                }
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public void nextPage(Player player) {
        int totalCount = allHistoryEntries.size();
        if (totalCount <= (page * entriesPerPage)) {
            return;
        }
        page++;
        loadHistoryPage(Bukkit.getOfflinePlayer(targetUUID), page);
        fillEmptySlotsWithBackground(Bukkit.getOfflinePlayer(targetUUID));
        player.updateInventory();
    }

    public void previousPage(Player player) {
        if (page > 1) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] HistoryMenu - previousPage() called. Current page: " + page);
            page--;
            loadHistoryPage(Bukkit.getOfflinePlayer(targetUUID), page);
            fillEmptySlotsWithBackground(Bukkit.getOfflinePlayer(targetUUID));
            player.updateInventory();
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] HistoryMenu - Navigated to previous page. New page: " + page);
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] HistoryMenu - previousPage() called, but already on first page.");
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.history_menu_first_page")));
        }
    }

    public List<MenuItem> getHistoryEntryItems() {
        return historyEntryItems;
    }

    public Set<String> getMenuItemKeys() {
        return menuItemKeys;
    }

    private void loadMenuItems() {
        menuItemKeys.clear();
        Set<String> configKeys = plugin.getConfigManager().getHistoryMenuConfig().getConfig().getConfigurationSection("menu.items").getKeys(false);
        menuItemKeys.addAll(configKeys);
    }
}
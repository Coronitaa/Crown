package cp.corona.menus.punish;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HistoryMenu implements InventoryHolder {

    private final Inventory inventory;
    private final UUID targetUUID;
    private final Crown plugin;
    private int page = 1;
    private final int entriesPerPage = 28;
    private final Set<String> menuItemKeys = new HashSet<>();
    private List<DatabaseManager.PunishmentEntry> allHistoryEntries;
    private boolean isLoadingPage = false;
    private final List<MenuItem> historyEntryItemsCache = Collections.synchronizedList(new ArrayList<>()); // MODIFIED: Added cache

    private static final String LOADING_ITEM_KEY = "loading_item";
    private static final String BACK_BUTTON_KEY = "back_button";
    private static final String NEXT_PAGE_BUTTON_KEY = "next_page_button";
    private static final String PREVIOUS_PAGE_BUTTON_KEY = "previous_page_button";
    private static final String HISTORY_ENTRY_ITEM_KEY = "history_entry";
    private static final String BACKGROUND_FILL_KEY = "background_fill";
    private static final List<Integer> validSlots = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    public HistoryMenu(UUID targetUUID, Crown plugin) {
        this.targetUUID = targetUUID;
        this.plugin = plugin;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String title = plugin.getConfigManager().getHistoryMenuTitle(target);
        inventory = Bukkit.createInventory(this, 54, title);

        loadMenuItems();
        initializeLoadingState(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            loadAndProcessAllHistory();
            loadPageAsync(1, (Player) inventory.getViewers().stream().findFirst().orElse(null));
        });
    }

    private void initializeLoadingState(OfflinePlayer target) {
        inventory.clear();
        setItemInMenu(LOADING_ITEM_KEY, plugin.getConfigManager().getHistoryMenuItemConfig(LOADING_ITEM_KEY), target, 22);
        fillEmptySlotsWithBackground(target);
    }

    private void loadAndProcessAllHistory() {
        allHistoryEntries = plugin.getSoftBanDatabaseManager().getPunishmentHistory(targetUUID, 1, Integer.MAX_VALUE);
        Map<String, ActiveWarningEntry> activeWarningsMap = plugin.getSoftBanDatabaseManager().getAllActiveAndPausedWarnings(targetUUID)
                .stream().collect(Collectors.toMap(ActiveWarningEntry::getPunishmentId, w -> w));

        for (DatabaseManager.PunishmentEntry entry : allHistoryEntries) {
            String type = entry.getType().toLowerCase();
            String status;
            boolean isInternal = plugin.getConfigManager().isPunishmentInternal(type);
            boolean isSystemExpired = !entry.isActive() && "System".equals(entry.getRemovedByName())
                    && ("Expired".equalsIgnoreCase(entry.getRemovedReason()) || "Superseded by new warning.".equalsIgnoreCase(entry.getRemovedReason()));

            if (isInternal) {
                if (type.equals("warn")) {
                    ActiveWarningEntry activeWarning = activeWarningsMap.get(entry.getPunishmentId());
                    if (activeWarning != null) {
                        status = activeWarning.isPaused() ?
                                plugin.getConfigManager().getMessage("placeholders.status_paused")
                                : plugin.getConfigManager().getMessage("placeholders.status_active");
                    } else {
                        status = isSystemExpired ?
                                plugin.getConfigManager().getMessage("placeholders.status_expired")
                                : plugin.getConfigManager().getMessage("placeholders.status_removed");
                    }
                } else if (type.equals("kick")) {
                    status = "N/A";
                } else {
                    boolean isPaused = !entry.isActive() && "Paused by new warning".equalsIgnoreCase(entry.getRemovedReason());
                    if (isPaused) {
                        status = plugin.getConfigManager().getMessage("placeholders.status_paused");
                    } else if (entry.isActive() && entry.getEndTime() < System.currentTimeMillis() && entry.getEndTime() != Long.MAX_VALUE) {
                        status = plugin.getConfigManager().getMessage("placeholders.status_expired");
                    } else if (entry.isActive()) {
                        status = plugin.getConfigManager().getMessage("placeholders.status_active");
                    } else {
                        status = isSystemExpired ?
                                plugin.getConfigManager().getMessage("placeholders.status_expired")
                                : plugin.getConfigManager().getMessage("placeholders.status_removed");
                    }
                }
            } else {
                // --- EXTERNAL (NON-INTERNAL) PUNISHMENT STATUS LOGIC ---
                if (type.equals("kick")) {
                    status = "N/A";
                } else if (type.equals("warn")) {
                    status = entry.isActive() ? plugin.getConfigManager().getMessage("placeholders.status_active")
                            : plugin.getConfigManager().getMessage("placeholders.status_removed");
                } else { // For ban, mute, softban, freeze
                    if (!entry.isActive()) {
                        status = isSystemExpired ? plugin.getConfigManager().getMessage("placeholders.status_expired")
                                : plugin.getConfigManager().getMessage("placeholders.status_removed");
                    } else if (entry.getEndTime() < System.currentTimeMillis() && entry.getEndTime() != Long.MAX_VALUE) {
                        status = plugin.getConfigManager().getMessage("placeholders.status_expired");
                    } else {
                        status = plugin.getConfigManager().getMessage("placeholders.status_active");
                    }
                }
            }
            entry.setStatus(status);
        }
    }


    private void loadPageAsync(int newPage, Player viewer) {
        if (isLoadingPage) return;
        isLoadingPage = true;
        this.page = newPage;

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        // Give immediate feedback that the page is changing
        Bukkit.getScheduler().runTask(plugin, () -> initializeLoadingState(target));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Map<Integer, ItemStack> pageItems = preparePageItems(target, newPage);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer == null || !viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder() != this) {
                    isLoadingPage = false;
                    return; // Player closed the menu, abort update
                }

                inventory.clear();

                // Place static items
                placeStaticItems(target);

                // Place the asynchronously prepared items
                pageItems.forEach(inventory::setItem);

                updatePageButtons(target);
                fillEmptySlotsWithBackground(target);
                isLoadingPage = false;
            });
        });
    }

    private Map<Integer, ItemStack> preparePageItems(OfflinePlayer target, int pageToLoad) {
        historyEntryItemsCache.clear(); // MODIFIED: Clear cache before populating
        Map<Integer, ItemStack> items = new ConcurrentHashMap<>();
        int start = (pageToLoad - 1) * entriesPerPage;
        int end = Math.min(start + entriesPerPage, allHistoryEntries.size());
        List<DatabaseManager.PunishmentEntry> historyForPage = (start < allHistoryEntries.size()) ? allHistoryEntries.subList(start, end) : Collections.emptyList();

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

            if (entry.getType().equalsIgnoreCase("warn") && entry.getWarnLevel() > 0) {
                entryName = entryName.replace("Warn", "Warn (Lvl " + entry.getWarnLevel() + ")");
            }
            historyEntryItem.setName(MessageUtils.getColorMessage(entryName));

            List<String> lore = new ArrayList<>();
            List<String> configLore = plugin.getConfigManager().getHistoryMenuItemLore(HISTORY_ENTRY_ITEM_KEY, target);
            for (String line : configLore) {
                String processedLine = line.replace("{punishment_id}", entry.getPunishmentId())
                        .replace("{reason}", entry.getReason())
                        .replace("{date}", dateFormat.format(entry.getTimestamp()))
                        .replace("{punisher}", entry.getPunisherName())
                        .replace("{duration}", getDurationDisplay(entry));
                lore.add(processedLine);
            }

            // Append dynamic status lore
            if (status.equals(plugin.getConfigManager().getMessage("placeholders.status_active"))) {
                String expiresAt = (entry.getPunishmentTime() == Long.MAX_VALUE) ?
                        "Never" : dateFormat.format(new Date(entry.getPunishmentTime()));
                long remainingMillis = entry.getPunishmentTime() - System.currentTimeMillis();
                String timeLeft = (entry.getPunishmentTime() == Long.MAX_VALUE) ?
                        "Permanent" : TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());
                lore.add("&7Expires: &b" + expiresAt + " (" + timeLeft + ")");
            } else if (status.equals(plugin.getConfigManager().getMessage("placeholders.status_removed")) || status.equals(plugin.getConfigManager().getMessage("placeholders.status_paused"))) {
                if (entry.getRemovedByName() != null && entry.getRemovedAt() != null) {
                    lore.add("&c" + (status.contains("Paused") ? "Paused" : "Removed") + " by: &e" + entry.getRemovedByName());
                    lore.add("&cAt: &e" + dateFormat.format(entry.getRemovedAt()));
                    if (entry.getRemovedReason() != null && !entry.getRemovedReason().isEmpty()) {
                        lore.add("&cReason: &e" + entry.getRemovedReason());
                    }
                }
            }

            historyEntryItem.setLore(lore);
            historyEntryItem.setSlots(List.of(slot));

            // Set actions
            List<MenuItem.ClickActionData> leftClickActions = Optional.ofNullable(historyItemConfig.getLeftClickActions()).filter(list -> !list.isEmpty()).orElse(baseItemConfig != null ? baseItemConfig.getLeftClickActions() : Collections.emptyList());
            List<MenuItem.ClickActionData> rightClickActions = Optional.ofNullable(historyItemConfig.getRightClickActions()).filter(list -> !list.isEmpty()).orElse(baseItemConfig != null ? baseItemConfig.getRightClickActions() : Collections.emptyList());

            historyEntryItem.setLeftClickActions(processActions(leftClickActions, entry.getPunishmentId()));
            historyEntryItem.setRightClickActions(processActions(rightClickActions, entry.getPunishmentId()));

            items.put(slot, historyEntryItem.toItemStack(target, plugin.getConfigManager()));
            historyEntryItemsCache.add(historyEntryItem); // MODIFIED: Add the full MenuItem to cache
            index++;
        }
        return items;
    }

    private void placeStaticItems(OfflinePlayer target) {
        for (String itemKey : menuItemKeys) {
            if (!itemKey.startsWith(HISTORY_ENTRY_ITEM_KEY.substring(0, 4)) &&
                    !itemKey.equals(NEXT_PAGE_BUTTON_KEY) &&
                    !itemKey.equals(PREVIOUS_PAGE_BUTTON_KEY) &&
                    !itemKey.equals(LOADING_ITEM_KEY) &&
                    !itemKey.equals(BACKGROUND_FILL_KEY)) {
                setItemInMenu(itemKey, plugin.getConfigManager().getHistoryMenuItemConfig(itemKey), target);
            }
        }
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
                    // FIX: Only fill if the slot is empty AND not a designated entry/button slot
                    if (inventory.getItem(slot) == null && !validSlots.contains(slot) && !isButtonSlot(slot)) {
                        inventory.setItem(slot, backgroundItemStack.clone());
                    }
                }
            }
        }
    }

    private boolean isButtonSlot(int slot) {
        // Includes the static items like punishment_counts_item
        MenuItem countsItem = plugin.getConfigManager().getHistoryMenuItemConfig("punishment_counts_item");
        List<Integer> staticSlots = new ArrayList<>(Arrays.asList(51, 52, 53));
        if(countsItem != null && countsItem.getSlots() != null){
            staticSlots.addAll(countsItem.getSlots());
        }
        return staticSlots.contains(slot);
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
        if (totalCount > (page * entriesPerPage)) {
            loadPageAsync(page + 1, player);
        }
    }

    public void previousPage(Player player) {
        if (page > 1) {
            loadPageAsync(page - 1, player);
        }
    }

    /**
     * MODIFIED: Returns the cached list of MenuItems for the current page.
     * This avoids rebuilding from ItemStacks and preserves click actions.
     */
    public List<MenuItem> getHistoryEntryItems() {
        return historyEntryItemsCache;
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
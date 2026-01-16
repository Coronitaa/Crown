package cp.corona.menus.report;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.items.MenuItem;
import cp.corona.report.ReportStatus;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class ReportsMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Crown plugin;
    private final Player viewer;
    private int page = 1;
    private final int entriesPerPage = 28;
    private int totalPages = 1;
    private ReportStatus filterStatus = null;
    private String filterName = null;
    private boolean filterAsRequester = false;
    private boolean filterAssignedToMe = false;
    private String reportTypeFilter = null; // ADDED

    private static final List<Integer> REPORT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    public ReportsMenu(Crown plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        String title = plugin.getConfigManager().getReportsMenuConfig().getConfig().getString("menu.title", "&c&lReports Menu ({page}/{max_pages})")
                .replace("{page}", String.valueOf(page))
                .replace("{max_pages}", String.valueOf(totalPages));
        this.inventory = Bukkit.createInventory(this, 54, MessageUtils.getColorMessage(title));
        loadPageAsync();
    }

    public ReportsMenu(Crown plugin, Player viewer, int page, ReportStatus status, String name, boolean asRequester, boolean assignedToMe) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = page;
        this.filterStatus = status;
        this.filterName = name;
        this.filterAsRequester = asRequester;
        this.filterAssignedToMe = assignedToMe;
        String title = plugin.getConfigManager().getReportsMenuConfig().getConfig().getString("menu.title", "&c&lReports Menu ({page}/{max_pages})")
                .replace("{page}", String.valueOf(page))
                .replace("{max_pages}", String.valueOf(totalPages));
        this.inventory = Bukkit.createInventory(this, 54, MessageUtils.getColorMessage(title));
        loadPageAsync();
    }

    // Overload for reports filter
    public ReportsMenu(Crown plugin, Player viewer, int page, ReportStatus status, String name, boolean asRequester, boolean assignedToMe, String reportType) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = page;
        this.filterStatus = status;
        this.filterName = name;
        this.filterAsRequester = asRequester;
        this.filterAssignedToMe = assignedToMe;
        this.reportTypeFilter = reportType;
        String title = plugin.getConfigManager().getReportsMenuConfig().getConfig().getString("menu.title", "&c&lReports Menu ({page}/{max_pages})")
                .replace("{page}", String.valueOf(page))
                .replace("{max_pages}", String.valueOf(totalPages));
        this.inventory = Bukkit.createInventory(this, 54, MessageUtils.getColorMessage(title));
        loadPageAsync();
    }

    public void loadPageAsync() {
        inventory.clear();
        MenuItem loadingItem = plugin.getConfigManager().getHistoryMenuItemConfig("loading_item");
        if (loadingItem != null) {
            inventory.setItem(22, loadingItem.toItemStack(null, plugin.getConfigManager()));
        }

        UUID assignedTo = filterAssignedToMe ? viewer.getUniqueId() : null;

        plugin.getSoftBanDatabaseManager().countReports(filterStatus, filterName, filterAsRequester, assignedTo, reportTypeFilter).thenAcceptBothAsync(
                plugin.getSoftBanDatabaseManager().getReports(page, entriesPerPage, filterStatus, filterName, filterAsRequester, assignedTo, reportTypeFilter),
                (totalCount, reportEntries) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer == null || !viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder() != this) return;

                    this.totalPages = (int) Math.ceil((double) totalCount / (double) entriesPerPage);
                    if (this.totalPages == 0) this.totalPages = 1;
                    if (this.page > this.totalPages) this.page = this.totalPages;

                    String title = plugin.getConfigManager().getReportsMenuConfig().getConfig().getString("menu.title", "&c&lReports Menu ({page}/{max_pages})")
                            .replace("{page}", String.valueOf(page))
                            .replace("{max_pages}", String.valueOf(totalPages));
                    title = MessageUtils.getColorMessage(title);

                    if (!viewer.getOpenInventory().getTitle().equals(title)) {
                        Inventory newInv = Bukkit.createInventory(this, 54, title);
                        inventory.setContents(newInv.getContents());
                        viewer.openInventory(inventory);
                        initializeItems(reportEntries);
                    } else {
                        initializeItems(reportEntries);
                        viewer.updateInventory();
                    }
                })
        );
    }

    private void initializeItems(List<DatabaseManager.ReportEntry> reports) {
        inventory.clear();
        placeStaticItems();

        MenuItem reportItemConfig = plugin.getConfigManager().getReportsMenuItemConfig("report_entry");
        if (reportItemConfig == null) return;

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (int i = 0; i < reports.size(); i++) {
            if (i >= REPORT_SLOTS.size()) break;
            DatabaseManager.ReportEntry entry = reports.get(i);
            int slot = REPORT_SLOTS.get(i);

            ItemStack item = reportItemConfig.toItemStack(null, plugin.getConfigManager());
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String statusColor = entry.getStatus().getColor();
            String statusName = entry.getStatus().getDisplayName();
            String requesterName = Bukkit.getOfflinePlayer(entry.getRequesterUUID()).getName();

            String moderatorName = "N/A";
            if (entry.getModeratorUUID() != null) {
                OfflinePlayer mod = Bukkit.getOfflinePlayer(entry.getModeratorUUID());
                if (mod.getName() != null) {
                    moderatorName = mod.getName();
                }
            }

            meta.setDisplayName(MessageUtils.getColorMessage(meta.getDisplayName()
                    .replace("{report_id}", entry.getReportId())
                    .replace("{target}", entry.getTargetName())));

            List<String> lore = new ArrayList<>();
            for(String line : meta.getLore()) {
                String resolutionTime = "";
                if (entry.getResolvedAt() != null && (entry.getStatus() == ReportStatus.RESOLVED || entry.getStatus() == ReportStatus.REJECTED)) {
                    Duration duration = Duration.between(entry.getTimestamp().toInstant(), entry.getResolvedAt().toInstant());
                    resolutionTime = "&8Resolved in: &7" + formatDuration(duration);
                }

                lore.add(MessageUtils.getColorMessage(line
                        .replace("{requester}", requesterName != null ? requesterName : entry.getRequesterUUID().toString())
                        .replace("{category}", entry.getCategory())
                        .replace("{reason}", entry.getReason())
                        .replace("{details}", entry.getDetails() != null ? entry.getDetails() : "None")
                        .replace("{moderator}", moderatorName)
                        .replace("{date}", dateFormat.format(entry.getTimestamp()))
                        .replace("{status_color}", statusColor)
                        .replace("{status}", statusName)
                        .replace("{resolution_time}", resolutionTime)
                ));
            }
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "report_id"), PersistentDataType.STRING, entry.getReportId());
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }

    private void placeStaticItems() {
        for (String key : plugin.getConfigManager().getReportsMenuItemKeys()) {
            if (key.equals("report_entry")) continue;
            MenuItem itemConfig = plugin.getConfigManager().getReportsMenuItemConfig(key);
            if (itemConfig != null) {
                ItemStack item = itemConfig.toItemStack(null, plugin.getConfigManager());
                ItemMeta meta = item.getItemMeta();
                
                String statusDisplay = filterStatus != null ? filterStatus.getColor() + filterStatus.getDisplayName() : "&bAll";
                String nameDisplay = filterName != null ? (filterAsRequester ? "Requester: " : "Target: ") + filterName : "None";
                String requesterName = (filterName != null && filterAsRequester) ? filterName : "None";
                String targetName = (filterName != null && !filterAsRequester) ? filterName : "None";

                if (meta != null) {
                    if (meta.hasDisplayName()) {
                        meta.setDisplayName(MessageUtils.getColorMessage(meta.getDisplayName()
                                .replace("{filter_status}", statusDisplay)
                                .replace("{filter_name}", nameDisplay)
                                .replace("{filter_type}", reportTypeFilter != null ? reportTypeFilter : "All")
                                .replace("{requester}", requesterName)
                                .replace("{target}", targetName)
                        ));
                    }

                    if (meta.hasLore()) {
                        List<String> newLore = new ArrayList<>();
                        for (String line : meta.getLore()) {
                            newLore.add(MessageUtils.getColorMessage(line
                                    .replace("{filter_status}", statusDisplay)
                                    .replace("{filter_name}", nameDisplay)
                                    .replace("{filter_type}", reportTypeFilter != null ? reportTypeFilter : "All")
                                    .replace("{requester}", requesterName)
                                    .replace("{target}", targetName)
                            ));
                        }

                        if (key.equals("filter_my_reports")) {
                            newLore.add("");
                            newLore.add(MessageUtils.getColorMessage(filterAssignedToMe ? "&a&lACTIVE" : "&c&lINACTIVE"));
                        }

                        meta.setLore(newLore);
                    }
                    item.setItemMeta(meta);
                }

                for (int slot : itemConfig.getSlots()) {
                    inventory.setItem(slot, item);
                }
            }
        }
        if (page >= totalPages) {
            MenuItem nextButton = plugin.getConfigManager().getReportsMenuItemConfig("next_page_button");
            if(nextButton != null) nextButton.getSlots().forEach(inventory::clear);
        }
        if (page <= 1) {
            MenuItem prevButton = plugin.getConfigManager().getReportsMenuItemConfig("previous_page_button");
            if(prevButton != null) prevButton.getSlots().forEach(inventory::clear);
        }
    }

    public void nextPage() {
        if (page < totalPages) {
            page++;
            loadPageAsync();
        }
    }

    public void previousPage() {
        if (page > 1) {
            page--;
            loadPageAsync();
        }
    }

    public void cycleFilterStatus() {
        // this.filterAssignedToMe = false; // REMOVED: Allow simultaneous filters
        // this.reportTypeFilter = null; // REMOVED: Allow simultaneous filters
        if (filterStatus == null) {
            filterStatus = ReportStatus.PENDING;
        } else {
            int nextOrdinal = filterStatus.ordinal() + 1;
            if (nextOrdinal >= ReportStatus.values().length) {
                filterStatus = null;
            } else {
                filterStatus = ReportStatus.values()[nextOrdinal];
            }
        }
        // this.filterName = null; // REMOVED: Allow simultaneous filters
        page = 1;
        loadPageAsync();
    }

    public void cycleReportTypeFilter() {
        // this.filterAssignedToMe = false; // REMOVED: Allow simultaneous filters
        // this.filterStatus = null; // REMOVED: Allow simultaneous filters
        // this.filterName = null; // REMOVED: Allow simultaneous filters

        if (reportTypeFilter == null) {
            reportTypeFilter = "PLAYER";
        } else if ("PLAYER".equals(reportTypeFilter)) {
            reportTypeFilter = "CLAN";
        } else if ("CLAN".equals(reportTypeFilter)) {
            reportTypeFilter = "SERVER";
        } else {
            reportTypeFilter = null; // Back to ALL
        }
        page = 1;
        loadPageAsync();
    }

    public void setFilterName(String name, boolean asRequester) {
        this.filterName = name;
        this.filterAsRequester = asRequester;
        // this.filterAssignedToMe = false; // REMOVED: Allow simultaneous filters
        // this.filterStatus = null; // REMOVED: Allow simultaneous filters
        // this.reportTypeFilter = null; // REMOVED: Allow simultaneous filters
        this.page = 1;
        loadPageAsync();
    }

    public void toggleMyReportsFilter() {
        this.filterAssignedToMe = !this.filterAssignedToMe;
        // this.filterName = null; // REMOVED: Allow simultaneous filters
        // this.filterStatus = null; // REMOVED: Allow simultaneous filters
        // this.reportTypeFilter = null; // REMOVED: Allow simultaneous filters
        this.page = 1;
        loadPageAsync();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
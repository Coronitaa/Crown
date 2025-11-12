// --- FILE: ReportDetailsMenu.java ---
// --- PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\menus\ReportDetailsMenu.java ---
package cp.corona.menus;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.items.MenuItem;
import cp.corona.report.ReportStatus;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;

public class ReportDetailsMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Crown plugin;
    private final Player viewer;
    private final String reportId;
    private DatabaseManager.ReportEntry reportEntry;

    // Helper class to hold fetched stats
    private static class PlayerRecordStats {
        HashMap<String, Integer> punishmentCounts = new HashMap<>();
        int reportsReceived = 0;
        int reportsSent = 0;
    }

    public ReportDetailsMenu(Crown plugin, Player viewer, String reportId) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.reportId = reportId;
        this.inventory = Bukkit.createInventory(this, 54, "Report #" + reportId);
        loadReportAndInitialize();
    }

    public void loadReportAndInitialize() {
        plugin.getSoftBanDatabaseManager().getReportById(reportId).thenAcceptAsync(entry -> {
            this.reportEntry = entry;
            if (entry == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    viewer.closeInventory();
                    MessageUtils.sendConfigMessage(plugin, viewer, "messages.report_not_found", "{id}", reportId);
                });
                return;
            }

            // Fetch all required stats asynchronously
            CompletableFuture<PlayerRecordStats> targetStatsFuture = getPlayerStats(entry.getTargetUUID());
            CompletableFuture<PlayerRecordStats> requesterStatsFuture = getPlayerStats(entry.getRequesterUUID());

            CompletableFuture.allOf(targetStatsFuture, requesterStatsFuture).thenRun(() -> {
                PlayerRecordStats targetStats = targetStatsFuture.join();
                PlayerRecordStats requesterStats = requesterStatsFuture.join();
                Bukkit.getScheduler().runTask(plugin, () -> initializeItems(targetStats, requesterStats));
            });
        });
    }

    private CompletableFuture<PlayerRecordStats> getPlayerStats(UUID playerUUID) {
        if (playerUUID == null) {
            return CompletableFuture.completedFuture(new PlayerRecordStats());
        }
        CompletableFuture<HashMap<String, Integer>> punishments = CompletableFuture.supplyAsync(() -> plugin.getSoftBanDatabaseManager().getPunishmentCounts(playerUUID));
        CompletableFuture<Integer> received = plugin.getSoftBanDatabaseManager().countReportsAsTarget(playerUUID);
        CompletableFuture<Integer> sent = plugin.getSoftBanDatabaseManager().countReportsAsRequester(playerUUID);

        return CompletableFuture.allOf(punishments, received, sent).thenApply(v -> {
            PlayerRecordStats stats = new PlayerRecordStats();
            stats.punishmentCounts = punishments.join();
            stats.reportsReceived = received.join();
            stats.reportsSent = sent.join();
            return stats;
        });
    }

    private void initializeItems(PlayerRecordStats targetStats, PlayerRecordStats requesterStats) {
        String title = MessageUtils.getColorMessage(plugin.getConfigManager().getReportDetailsMenuConfig().getConfig().getString("menu.title", "&c&lReport Details: #{report_id}").replace("{report_id}", reportId));
        if (!viewer.getOpenInventory().getTitle().equals(title)) {
            Inventory newInv = Bukkit.createInventory(this, 54, title);
            inventory.setContents(newInv.getContents());
            viewer.openInventory(inventory);
        }
        inventory.clear();

        OfflinePlayer target = (reportEntry.getTargetUUID() != null) ? Bukkit.getOfflinePlayer(reportEntry.getTargetUUID()) : null;
        OfflinePlayer requester = Bukkit.getOfflinePlayer(reportEntry.getRequesterUUID());
        OfflinePlayer moderator = (reportEntry.getModeratorUUID() != null) ? Bukkit.getOfflinePlayer(reportEntry.getModeratorUUID()) : null;

        ReportStatus status = reportEntry.getStatus();
        boolean isAssignedToViewer = viewer.getUniqueId().equals(reportEntry.getModeratorUUID());
        boolean isClosed = (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED);

        for (String key : plugin.getConfigManager().getReportDetailsMenuItemKeys()) {
            MenuItem itemConfig = plugin.getConfigManager().getReportDetailsMenuItemConfig(key);
            if (itemConfig == null) continue;

            // Conditional item visibility logic
            if (isClosed && (key.equals("take_report") || key.equals("resolve_report") || key.equals("reject_report") || key.equals("assign_moderator"))) {
                continue;
            }
            if (key.equals("take_report") && isAssignedToViewer) {
                continue;
            }
            if ((key.equals("resolve_report") || key.equals("reject_report")) && !isAssignedToViewer) {
                continue;
            }
            if (key.equals("mark_as_pending_button") && status == ReportStatus.PENDING) {
                continue;
            }
            if ((key.equals("punish_target") || key.equals("target_info") || key.equals("target_summary")) && target == null) {
                continue;
            }
            if (key.equals("moderator_info") && moderator == null) {
                continue;
            }

            ItemStack item = itemConfig.toItemStack(target, plugin.getConfigManager());
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if(meta == null) continue;

            PlayerRecordStats currentStats = (key.contains("requester") || key.equals("punish_requester")) ? requesterStats : targetStats;

            if (meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                if (key.equals("assign_moderator") && moderator != null) {
                    name = name.replace("Assign", "Reassign");
                }
                meta.setDisplayName(replacePlaceholders(name, target, requester, moderator, currentStats));
            }

            if (meta.hasLore()) {
                List<String> newLore = new ArrayList<>();
                if (key.equals("collected_data")) {
                    newLore.addAll(buildCollectedDataLore());
                } else {
                    for (String line : meta.getLore()) {
                        newLore.add(replacePlaceholders(line, target, requester, moderator, currentStats));
                    }
                }
                meta.setLore(newLore);
            }

            item.setItemMeta(meta);
            for (int slot : itemConfig.getSlots()) inventory.setItem(slot, item);
        }
        viewer.updateInventory();
    }

    private List<String> buildCollectedDataLore() {
        List<String> lore = new ArrayList<>();
        String data = reportEntry.getCollectedData();
        if (data == null || data.isEmpty()) {
            lore.add(MessageUtils.getColorMessage("&7No data was collected."));
            return lore;
        }

        String[] pairs = data.split(", ");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1].replace(",", ", ");
                lore.add(MessageUtils.getColorMessage("&7" + key + ": &b" + value));
            }
        }
        return lore;
    }

    private String replacePlaceholders(String text, OfflinePlayer target, OfflinePlayer requester, OfflinePlayer moderator, PlayerRecordStats stats) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String targetName = (target != null && target.getName() != null) ? target.getName() : reportEntry.getTargetName();
        String requesterName = (requester != null && requester.getName() != null) ? requester.getName() : reportEntry.getRequesterUUID().toString();
        String moderatorName = (moderator != null && moderator.getName() != null) ? moderator.getName() : "None";

        return MessageUtils.getColorMessage(text
                .replace("{report_id}", reportEntry.getReportId())
                .replace("{target}", targetName)
                .replace("{requester}", requesterName)
                .replace("{moderator}", moderatorName)
                .replace("{category}", reportEntry.getCategory())
                .replace("{reason}", reportEntry.getReason())
                .replace("{details}", reportEntry.getDetails() != null ? reportEntry.getDetails() : "None")
                .replace("{date}", dateFormat.format(reportEntry.getTimestamp()))
                .replace("{status_color}", reportEntry.getStatus().getColor())
                .replace("{status}", reportEntry.getStatus().getDisplayName())
                .replace("{ban_count}", String.valueOf(stats.punishmentCounts.getOrDefault("ban", 0)))
                .replace("{mute_count}", String.valueOf(stats.punishmentCounts.getOrDefault("mute", 0)))
                .replace("{warn_count}", String.valueOf(stats.punishmentCounts.getOrDefault("warn", 0)))
                .replace("{kick_count}", String.valueOf(stats.punishmentCounts.getOrDefault("kick", 0)))
                .replace("{reports_received}", String.valueOf(stats.reportsReceived))
                .replace("{reports_sent}", String.valueOf(stats.reportsSent))
        );
    }

    public void cycleStatus() {
        if (reportEntry == null) return;
        ReportStatus newStatus = reportEntry.getStatus().next();
        UUID moderator = (newStatus == ReportStatus.PENDING) ? null : viewer.getUniqueId();

        plugin.getSoftBanDatabaseManager().updateReportStatus(reportId, newStatus, moderator)
                .thenAccept(success -> {
                    if (success) {
                        MessageUtils.sendConfigMessage(plugin, viewer, "messages.report_status_changed", "{report_id}", reportId, "{status_color}", newStatus.getColor(), "{status}", newStatus.getDisplayName());
                        loadReportAndInitialize();
                    }
                });
    }

    public void assignTo(UUID moderatorUUID) {
        if (reportEntry == null) return;
        plugin.getSoftBanDatabaseManager().updateReportStatus(reportId, ReportStatus.ASSIGNED, moderatorUUID)
                .thenAccept(success -> {
                    if (success) {
                        loadReportAndInitialize();
                    }
                });
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public DatabaseManager.ReportEntry getReportEntry() {
        return reportEntry;
    }

    public String getReportId() {
        return reportId;
    }
}
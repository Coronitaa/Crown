package cp.corona.report;

import cp.corona.config.MainConfigManager;
import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReportBookManager {

    private final Crown plugin;
    private final Map<UUID, ReportBuilder> reportSessions = new ConcurrentHashMap<>();

    public ReportBookManager(Crown plugin) {
        this.plugin = plugin;
    }

    public void startReportProcess(Player player) {
        reportSessions.put(player.getUniqueId(), new ReportBuilder(player.getUniqueId()));
        String initialPageKey = plugin.getConfigManager().getReportInitialPageKey();
        openBookForPage(player, initialPageKey);
    }

    public void startReportProcess(Player player, OfflinePlayer target) {
        ReportBuilder builder = new ReportBuilder(player.getUniqueId());
        builder.targetUUID = target.getUniqueId();
        builder.targetName = target.getName();
        builder.reportType = "PLAYER";
        reportSessions.put(player.getUniqueId(), builder);
        openBookForPage(player, "player_categories"); // Assumes 'player_categories' is the key for the next step
    }

    // FIXED: Reworked to handle multiple actions in a single command string.
    public void handleBookCommand(Player player, String[] args) {
        ReportBuilder builder = reportSessions.get(player.getUniqueId());
        if (builder == null) return;

        // Iterate through args in pairs (action, value)
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) continue; // Ensure there's a value for the action

            String action = args[i];
            String value = args[i + 1].replace("_SPACE_", " "); // Decode spaces

            switch (action) {
                case "GOTO":
                    openBookForPage(player, value);
                    break;
                case "REQUEST_INPUT":
                    player.closeInventory();
                    builder.inputState = value;
                    // The next page key is now the third parameter for this specific action
                    if (args.length > i + 2) {
                        builder.nextPageKey = args[i + 2];
                    }

                    String messageKey = "messages.report_prompt_custom_reason"; // Default
                    if ("PLAYER".equalsIgnoreCase(value)) messageKey = "messages.report_prompt_player_name";
                    else if ("CLAN".equalsIgnoreCase(value)) messageKey = "messages.report_prompt_clan_name";

                    MessageUtils.sendConfigMessage(plugin, player, messageKey);
                    return; // Stop processing further actions as we are now awaiting chat input
                case "SELECT_REASON":
                    player.closeInventory();
                    builder.reason = value;
                    builder.inputState = "DETAILS";
                    MessageUtils.sendConfigMessage(plugin, player, "messages.report_prompt_additional_details");
                    return; // Stop processing, await chat input
                case "SET_TYPE":
                    builder.reportType = value;
                    break;
                case "SET_TARGET":
                    builder.targetName = value;
                    break;
                case "SET_CATEGORY":
                    builder.category = value;
                    break;
            }
        }
    }


    public void handleChatInput(Player player, String input) {
        ReportBuilder builder = reportSessions.get(player.getUniqueId());
        if (builder == null || builder.inputState == null) return;

        if ("cancel".equalsIgnoreCase(input)) {
            reportSessions.remove(player.getUniqueId());
            MessageUtils.sendConfigMessage(plugin, player, "messages.input_cancelled");
            return;
        }

        String state = builder.inputState;
        builder.inputState = null;

        switch (state) {
            case "PLAYER":
                OfflinePlayer target = Bukkit.getOfflinePlayer(input);
                if (player.getUniqueId().equals(target.getUniqueId())) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.report_self_error");
                    reportSessions.remove(player.getUniqueId());
                    return;
                }
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.never_played", "{input}", input);
                    reportSessions.remove(player.getUniqueId());
                    return;
                }
                builder.targetUUID = target.getUniqueId();
                builder.targetName = target.getName();
                builder.reportType = "PLAYER";
                if (builder.nextPageKey != null) openBookForPage(player, builder.nextPageKey);
                break;
            case "CLAN":
                builder.targetName = input;
                builder.reportType = "CLAN";
                if (builder.nextPageKey != null) openBookForPage(player, builder.nextPageKey);
                break;
            case "CUSTOM_REASON":
                builder.category = "Custom"; // FIXED: Ensure category is set
                builder.reason = "Custom";
                builder.details = input;
                submitReport(player, builder);
                break;
            case "DETAILS":
                builder.details = input;
                submitReport(player, builder);
                break;
        }
    }

    public void openBookForPage(Player player, String pageKey) {
        ReportBuilder builder = reportSessions.get(player.getUniqueId());
        if (builder == null) return;

        MainConfigManager.ReportPage pageConfig = plugin.getConfigManager().getReportPage(pageKey);
        if (pageConfig == null) {
            plugin.getLogger().warning("Report page with key '" + pageKey + "' not found in reports.yml.");
            return;
        }

        Component pageContent = createTitle(pageConfig.title());

        for (MainConfigManager.ReportOption option : pageConfig.options()) {
            pageContent = pageContent.append(createClickableOption(option)).append(Component.newline()).append(Component.newline());
        }

        openBook(player, pageConfig.title(), pageContent);
    }

    // FIXED: Command generation logic to support multi-actions cleanly.
    private Component createClickableOption(MainConfigManager.ReportOption option) {
        Component component = MessageUtils.getColorComponent(option.text());

        StringBuilder commandBuilder = new StringBuilder("/crown report_internal ");
        String[] actions = option.action().split(";");
        for(String singleAction : actions) {
            String[] parts = singleAction.split(":", 2);
            String actionName = parts[0];
            commandBuilder.append(actionName).append(" ");
            if (parts.length > 1) {
                // For REQUEST_INPUT, the structure is ACTION:TYPE:NEXT_PAGE
                if ("REQUEST_INPUT".equals(actionName)) {
                    String[] requestParts = parts[1].split(":", 2);
                    commandBuilder.append(requestParts[0]).append(" "); // Append TYPE
                    if (requestParts.length > 1) {
                        commandBuilder.append(requestParts[1]).append(" "); // Append NEXT_PAGE
                    }
                } else {
                    commandBuilder.append(parts[1].replace(" ", "_SPACE_")).append(" ");
                }
            }
        }

        component = component.clickEvent(ClickEvent.runCommand(commandBuilder.toString().trim()));

        if (option.hover() != null && !option.hover().isEmpty()) {
            component = component.hoverEvent(HoverEvent.showText(MessageUtils.getColorComponent(option.hover())));
        }
        return component;
    }

    private void openBook(Player player, String title, Component page) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(MessageUtils.getColorMessage(plugin.getConfigManager().getReportBookTitle()));
        meta.setAuthor("Crown System");
        meta.addPages(page);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private Component createTitle(String text) {
        return MessageUtils.getColorComponent(text + "\n\n").decorate(TextDecoration.BOLD);
    }

    public void createDirectReport(Player player, OfflinePlayer target, String reason) {
        String collectedData = "";
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            collectedData = String.format("HP:%.1f, HUNGER:%d, XP:%d, LOC:%s %d,%d,%d",
                    onlineTarget.getHealth(),
                    onlineTarget.getFoodLevel(),
                    onlineTarget.getLevel(),
                    onlineTarget.getWorld().getName(),
                    onlineTarget.getLocation().getBlockX(),
                    onlineTarget.getLocation().getBlockY(),
                    onlineTarget.getLocation().getBlockZ());
        }

        plugin.getSoftBanDatabaseManager().createReport(
                player.getUniqueId(), target.getUniqueId(), target.getName(),
                "PLAYER", "Direct", reason, "N/A", collectedData
        ).thenAccept(reportId -> {
            if (reportId != null) {
                recordReportTimestamp(player.getUniqueId());
                MessageUtils.sendConfigMessage(plugin, player, "messages.report_submitted", "{report_id}", reportId);
                String notifyMessage = plugin.getConfigManager().getMessage("messages.report_staff_notification",
                        "{requester}", player.getName(), "{target}", target.getName(), "{reason}", reason);
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("crown.report.view"))
                        .forEach(staff -> staff.sendMessage(MessageUtils.getColorMessage(notifyMessage)));
            } else {
                MessageUtils.sendConfigMessage(plugin, player, "messages.report_submit_failed");
            }
        });
    }

    private void submitReport(Player player, ReportBuilder builder) {
        reportSessions.remove(player.getUniqueId());
        if(player.getOpenInventory().getTitle().equals("Book")) {
            player.closeInventory();
        }

        String collectedData = "";
        if (builder.targetUUID != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(builder.targetUUID);
            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                collectedData = String.format("HP:%.1f, HUNGER:%d, XP:%d, LOC:%s %d,%d,%d",
                        onlineTarget.getHealth(),
                        onlineTarget.getFoodLevel(),
                        onlineTarget.getLevel(),
                        onlineTarget.getWorld().getName(),
                        onlineTarget.getLocation().getBlockX(),
                        onlineTarget.getLocation().getBlockY(),
                        onlineTarget.getLocation().getBlockZ());
            }
        }

        plugin.getSoftBanDatabaseManager().createReport(
                builder.requesterUUID, builder.targetUUID, builder.targetName,
                builder.reportType, builder.category, builder.reason, builder.details, collectedData
        ).thenAccept(reportId -> {
            if (reportId != null) {
                recordReportTimestamp(player.getUniqueId());
                MessageUtils.sendConfigMessage(plugin, player, "messages.report_submitted", "{report_id}", reportId);
                String notifyMessage = plugin.getConfigManager().getMessage("messages.report_staff_notification",
                        "{requester}", player.getName(), "{target}", builder.targetName, "{reason}", builder.reason);
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("crown.report.view"))
                        .forEach(staff -> staff.sendMessage(MessageUtils.getColorMessage(notifyMessage)));
            } else {
                MessageUtils.sendConfigMessage(plugin, player, "messages.report_submit_failed");
            }
        });
    }

    public boolean isAwaitingInput(Player player) {
        ReportBuilder builder = reportSessions.get(player.getUniqueId());
        return builder != null && builder.inputState != null;
    }

    public boolean checkReportCooldown(Player player) {
        if (player.hasPermission("crown.report.bypasscooldown")) {
            return true;
        }

        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        List<Long> timestamps = plugin.getPlayerReportTimestamps().getOrDefault(playerUUID, new ArrayList<>());

        int cooldownSeconds = plugin.getConfigManager().getReportCooldown();
        if (cooldownSeconds > 0 && !timestamps.isEmpty()) {
            long lastReportTime = timestamps.get(timestamps.size() - 1);
            long timeSinceLast = (currentTime - lastReportTime) / 1000;
            if (timeSinceLast < cooldownSeconds) {
                long timeLeft = cooldownSeconds - timeSinceLast;
                MessageUtils.sendConfigMessage(plugin, player, "messages.report_cooldown", "{time}", String.valueOf(timeLeft));
                return false;
            }
        }

        if (plugin.getConfigManager().isReportRateLimitEnabled()) {
            int limitAmount = plugin.getConfigManager().getReportRateLimitAmount();
            int limitPeriod = plugin.getConfigManager().getReportRateLimitPeriod();
            long periodMillis = limitPeriod * 1000L;

            long recentReports = timestamps.stream()
                    .filter(timestamp -> (currentTime - timestamp) < periodMillis)
                    .count();

            if (recentReports >= limitAmount) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.report_rate_limit");
                return false;
            }
        }

        return true;
    }

    public void recordReportTimestamp(UUID playerUUID) {
        long currentTime = System.currentTimeMillis();
        List<Long> timestamps = plugin.getPlayerReportTimestamps().computeIfAbsent(playerUUID, k -> new ArrayList<>());

        long periodMillis = plugin.getConfigManager().getReportRateLimitPeriod() * 1000L;
        timestamps.removeIf(timestamp -> (currentTime - timestamp) > periodMillis);

        timestamps.add(currentTime);
    }

    private static class ReportBuilder {
        final UUID requesterUUID;
        UUID targetUUID;
        String targetName;
        String reportType;
        String category;
        String reason;
        String details;
        String inputState;
        String nextPageKey;

        ReportBuilder(UUID requesterUUID) {
            this.requesterUUID = requesterUUID;
        }
    }
}
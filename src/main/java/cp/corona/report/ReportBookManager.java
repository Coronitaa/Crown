package cp.corona.report;

import cp.corona.config.MainConfigManager;
import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

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

        ComponentBuilder pageBuilder = new ComponentBuilder();
        pageBuilder.append(createTitle(pageConfig.title()));

        for (MainConfigManager.ReportOption option : pageConfig.options()) {
            pageBuilder.append(createClickableOption(option)).append("\n\n");
        }

        openBook(player, pageConfig.title(), pageBuilder.create());
    }

    // FIXED: Command generation logic to support multi-actions cleanly.
    private TextComponent createClickableOption(MainConfigManager.ReportOption option) {
        TextComponent component = new TextComponent(MessageUtils.getColorMessage(option.text()));

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

        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandBuilder.toString().trim()));

        if (option.hover() != null && !option.hover().isEmpty()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(MessageUtils.getColorMessage(option.hover()))));
        }
        return component;
    }

    private void openBook(Player player, String title, BaseComponent[] page) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(MessageUtils.getColorMessage(plugin.getConfigManager().getReportBookTitle()));
        meta.setAuthor("Crown System");
        meta.spigot().addPage(page);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private TextComponent createTitle(String text) {
        TextComponent title = new TextComponent(text + "\n\n");
        title.setBold(true);
        return title;
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
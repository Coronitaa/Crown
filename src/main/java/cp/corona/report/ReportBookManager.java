package cp.corona.report;

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

    private enum InputState {
        AWAITING_PLAYER_NAME,
        AWAITING_CLAN_NAME,
        AWAITING_CUSTOM_REASON,
        AWAITING_ADDITIONAL_DETAILS // ADDED
    }

    public ReportBookManager(Crown plugin) {
        this.plugin = plugin;
    }

    public void startReportProcess(Player player) {
        reportSessions.put(player.getUniqueId(), new ReportBuilder(player.getUniqueId()));
        openBook(player, "Report Menu", getInitialView());
    }

    public void startReportProcess(Player player, OfflinePlayer target) {
        ReportBuilder builder = new ReportBuilder(player.getUniqueId());
        builder.targetUUID = target.getUniqueId();
        builder.targetName = target.getName();
        builder.reportType = "PLAYER";
        reportSessions.put(player.getUniqueId(), builder);
        openBook(player, "Report Categories", getCategoriesView("Player"));
    }

    public void handleBookCommand(Player player, String[] args) {
        if (args.length < 2) return;
        ReportBuilder builder = reportSessions.get(player.getUniqueId());
        if (builder == null) return;

        String action = args[0];
        String value = args[1];

        switch (action) {
            case "select_target_type":
                builder.reportType = value;
                if ("PLAYER".equals(builder.reportType)) {
                    openBook(player, "Report a Player/Clan", getPlayerOrClanView());
                } else {
                    builder.targetName = "Server";
                    openBook(player, "Report a Server Issue", getCategoriesView("Server"));
                }
                break;
            case "select_player_type":
                player.closeInventory();
                if ("PLAYER".equals(value)) {
                    builder.inputState = InputState.AWAITING_PLAYER_NAME;
                    MessageUtils.sendConfigMessage(plugin, player, "messages.report_prompt_player_name");
                } else {
                    builder.inputState = InputState.AWAITING_CLAN_NAME;
                    MessageUtils.sendConfigMessage(plugin, player, "messages.report_prompt_clan_name");
                }
                break;
            case "select_category":
                builder.category = value;
                openBook(player, "Select Reason", getReasonsView(builder.category));
                break;
            case "select_reason":
                player.closeInventory();
                if ("CUSTOM".equals(value)) {
                    builder.category = "Custom";
                    builder.inputState = InputState.AWAITING_CUSTOM_REASON;
                    MessageUtils.sendConfigMessage(plugin, player, "messages.report_prompt_custom_reason");
                } else {
                    builder.reason = value;
                    builder.inputState = InputState.AWAITING_ADDITIONAL_DETAILS;
                    MessageUtils.sendConfigMessage(plugin, player, "messages.report_prompt_additional_details");
                }
                break;
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

        InputState state = builder.inputState;
        builder.inputState = null;

        switch (state) {
            case AWAITING_PLAYER_NAME:
                OfflinePlayer target = Bukkit.getOfflinePlayer(input);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.never_played", "{input}", input);
                    reportSessions.remove(player.getUniqueId());
                    return;
                }
                builder.targetUUID = target.getUniqueId();
                builder.targetName = target.getName();
                builder.reportType = "PLAYER";
                openBook(player, "Report Categories", getCategoriesView("Player"));
                break;
            case AWAITING_CLAN_NAME:
                builder.targetName = input;
                builder.reportType = "CLAN";
                openBook(player, "Report Categories", getCategoriesView("Clan"));
                break;
            case AWAITING_CUSTOM_REASON:
                builder.reason = "Custom";
                builder.details = input;
                submitReport(player, builder);
                break;
            case AWAITING_ADDITIONAL_DETAILS: // ADDED
                builder.details = input;
                submitReport(player, builder);
                break;
        }
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
                "PLAYER", "Direct Command", "Direct Command", reason, collectedData
        ).thenAccept(reportId -> {
            if (reportId != null) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.report_submitted", "{report_id}", reportId);
                String notifyMessage = plugin.getConfigManager().getMessage("messages.report_staff_notification",
                        "{requester}", player.getName(), "{target}", target.getName(), "{reason}", "Direct Command");
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

    private void openBook(Player player, String title, BaseComponent[] page) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(title);
        meta.setAuthor("Crown System");
        meta.spigot().addPage(page);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private BaseComponent[] getInitialView() {
        return new ComponentBuilder()
                .append(createTitle("Create a Report"))
                .append(createClickableOption("Report a Player or Clan", "/crown report_internal select_target_type PLAYER", "Report a user or group of users."))
                .append("\n\n")
                .append(createClickableOption("Report a Server Issue", "/crown report_internal select_target_type SERVER", "Report bugs, lag, or other server problems."))
                .create();
    }

    private BaseComponent[] getPlayerOrClanView() {
        return new ComponentBuilder()
                .append(createTitle("Report Type"))
                .append(createClickableOption("Report a specific Player", "/crown report_internal select_player_type PLAYER", "Report a single user."))
                .append("\n\n")
                .append(createClickableOption("Report a Clan", "/crown report_internal select_player_type CLAN", "Report a group of players by their clan name."))
                .create();
    }

    private BaseComponent[] getCategoriesView(String type) {
        ComponentBuilder builder = new ComponentBuilder().append(createTitle("Select a Category"));
        if ("Server".equals(type)) {
            builder.append(createClickableOption("Bug or Glitch", "/crown report_internal select_category Bug", "Report an exploit or unintended behavior."))
                    .append("\n\n")
                    .append(createClickableOption("Server Performance", "/crown report_internal select_category Performance", "Report lag, crashes, or TPS drops."));
        } else {
            builder.append(createClickableOption("Disruptive Gameplay", "/crown report_internal select_category Disruptive", "Cheating, hacking, exploiting."))
                    .append("\n\n")
                    .append(createClickableOption("Bad Behavior", "/crown report_internal select_category Verbal", "Insults, harassment, spam."))
                    .append("\n\n")
                    .append(createClickableOption("Abusive Gameplay", "/crown report_internal select_category Abusive", "Griefing, spawnkilling, scamming."));
        }
        builder.append("\n\n\n").append(createClickableOption("Custom Reason", "/crown report_internal select_reason CUSTOM", "Write your own report reason in chat."));
        return builder.create();
    }

    private BaseComponent[] getReasonsView(String category) {
        ComponentBuilder builder = new ComponentBuilder().append(createTitle("Select a Reason"));
        switch (category) {
            case "Disruptive" -> builder.append(createClickableOption("Kill Aura / Aimbot", "/crown report_internal select_reason Killaura", ""))
                    .append("\n\n")
                    .append(createClickableOption("Fly / Speed Hacks", "/crown report_internal select_reason FlySpeed", ""))
                    .append("\n\n")
                    .append(createClickableOption("X-Ray / Resource Cheats", "/crown report_internal select_reason XRay", ""));
            case "Verbal" -> builder.append(createClickableOption("Chat Spam", "/crown report_internal select_reason Spam", ""))
                    .append("\n\n")
                    .append(createClickableOption("Harassment / Insults", "/crown report_internal select_reason Harassment", ""))
                    .append("\n\n")
                    .append(createClickableOption("Inappropriate Content", "/crown report_internal select_reason Inappropriate", ""));
            case "Abusive" -> builder.append(createClickableOption("Griefing / Raiding", "/crown report_internal select_reason Griefing", ""))
                    .append("\n\n")
                    .append(createClickableOption("Scamming Items / Money", "/crown report_internal select_reason Scamming", ""))
                    .append("\n\n")
                    .append(createClickableOption("Spawnkilling / Trapping", "/crown report_internal select_reason Trapping", ""));
            case "Bug" -> builder.append(createClickableOption("Duplication Glitch", "/crown report_internal select_reason Duplication", ""))
                    .append("\n\n")
                    .append(createClickableOption("Map Exploit", "/crown report_internal select_reason MapExploit", ""));
            case "Performance" -> builder.append(createClickableOption("Lag Machine", "/crown report_internal select_reason LagMachine", ""))
                    .append("\n\n")
                    .append(createClickableOption("Server Crash", "/crown report_internal select_reason Crash", ""));
            default -> builder.append(createClickableOption("General Report", "/crown report_internal select_reason General", ""));
        }
        builder.append("\n\n\n").append(createClickableOption("Custom Reason", "/crown report_internal select_reason CUSTOM", "Write your own report reason in chat."));
        return builder.create();
    }

    private TextComponent createTitle(String text) {
        TextComponent title = new TextComponent(text + "\n\n");
        title.setBold(true);
        return title;
    }

    private TextComponent createClickableOption(String text, String command, String hoverText) {
        TextComponent component = new TextComponent("§3§l[§b" + text + "§3§l]");
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        if (hoverText != null && !hoverText.isEmpty()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7" + hoverText)));
        }
        return component;
    }

    private static class ReportBuilder {
        final UUID requesterUUID;
        UUID targetUUID;
        String targetName;
        String reportType;
        String category;
        String reason;
        String details;
        InputState inputState;

        ReportBuilder(UUID requesterUUID) {
            this.requesterUUID = requesterUUID;
        }
    }
}
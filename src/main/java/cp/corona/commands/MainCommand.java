package cp.corona.commands;

import cp.corona.config.WarnLevel;
import cp.corona.crown.Crown;
import cp.corona.database.ActiveWarningEntry;
import cp.corona.database.DatabaseManager;
import cp.corona.listeners.FreezeListener;
import cp.corona.listeners.MenuListener;
import cp.corona.menus.mod.LockerMenu;
import cp.corona.menus.punish.HistoryMenu;
import cp.corona.menus.profile.ProfileMenu;
import cp.corona.menus.profile.AuditLogBook;
import cp.corona.menus.punish.PunishDetailsMenu;
import cp.corona.menus.punish.PunishMenu;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import cp.corona.menus.report.ReportDetailsMenu;
import cp.corona.menus.report.ReportsMenu;
import cp.corona.report.ReportBookManager;
import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final Crown plugin;

    // Command and subcommand names constants for maintainability
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String PUNISH_SUBCOMMAND = "punish";
    private static final String UNPUNISH_SUBCOMMAND = "unpunish";
    private static final String CHECK_SUBCOMMAND = "check";
    private static final String HISTORY_SUBCOMMAND = "history";
    private static final String PROFILE_SUBCOMMAND = "profile";
    private static final String LOG_SUBCOMMAND = "log";
    private static final String HELP_SUBCOMMAND = "help";
    private static final String REPORT_COMMAND = "report";
    private static final String REPORTS_COMMAND = "reports";
    private static final String REPORT_INTERNAL_SUBCOMMAND = "report_internal";
    private static final String PROFILE_COMMAND_ALIAS = "profile";
    private static final String LOCKER_SUBCOMMAND = "locker";


    // Added constants for unpunish aliases and check alias
    private static final String SOFTBAN_COMMAND_ALIAS = "softban";
    private static final String FREEZE_COMMAND_ALIAS = "freeze";
    private static final String BAN_COMMAND_ALIAS = "ban";
    private static final String MUTE_COMMAND_ALIAS = "mute";
    private static final String KICK_COMMAND_ALIAS = "kick";
    private static final String WARN_COMMAND_ALIAS = "warn";

    private static final String UNBAN_COMMAND_ALIAS = "unban";
    private static final String UNMUTE_COMMAND_ALIAS = "unmute";
    private static final String UNWARN_COMMAND_ALIAS = "unwarn";
    private static final String UNSOFTBAN_COMMAND_ALIAS = "unsoftban";
    private static final String UNFREEZE_COMMAND_ALIAS = "unfreeze";
    private static final String CHECK_COMMAND_ALIAS = "c"; // From plugin.yml
    private static final String FREEZE_CHAT_COMMAND_ALIAS = "fchat";

    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final String USE_PERMISSION = "crown.use";
    private static final String PROFILE_PERMISSION = "crown.profile";
    private static final String CHECK_PERMISSION = "crown.check";
    private static final String HISTORY_PERMISSION = "crown.history";
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
    private static final String REPORT_CREATE_PERMISSION = "crown.report.create";
    private static final String REPORT_VIEW_PERMISSION = "crown.report.view";
    private static final String MOD_USE_PERMISSION = "crown.mod.use";
    private static final String MOD_CHAT_PERMISSION = "crown.mod";
    private static final String PROFILE_EDIT_INVENTORY_PERMISSION = "crown.profile.editinventory";
    private static final String LOCKER_ADMIN_PERMISSION = "crown.locker.admin";

    private static final List<String> PUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "kick", "warn", "freeze");
    private static final List<String> UNPUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "warn", "freeze");

    // Added constants for tab completion
    private static final List<String> UNPUNISH_ALIASES = Arrays.asList(
            UNBAN_COMMAND_ALIAS, UNMUTE_COMMAND_ALIAS, UNWARN_COMMAND_ALIAS,
            UNSOFTBAN_COMMAND_ALIAS, UNFREEZE_COMMAND_ALIAS
    );
    private static final List<String> IP_FLAGS = Arrays.asList("-ip", "-i", "-local", "-l");
    private static final List<String> CHECK_ACTIONS = Arrays.asList("info", "repunish", "unpunish");
    private static final List<String> ID_SUGGESTION = Collections.singletonList("<ID: XXXXXX>");
    private static final List<String> REASON_SUGGESTION = Collections.singletonList("<reason>");


    public MainCommand(Crown plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String commandLabel = command.getName().toLowerCase();

        boolean isReportCommand = commandLabel.equals(REPORT_COMMAND);
        boolean isReportsCommand = commandLabel.equals(REPORTS_COMMAND);
        boolean isInternalReportSubcommand = commandLabel.equals("crown") && args.length > 0 && args[0].equalsIgnoreCase(REPORT_INTERNAL_SUBCOMMAND);
        boolean isFreezeChatCommand = commandLabel.equals(FREEZE_CHAT_COMMAND_ALIAS);

        if (!isReportCommand && !isReportsCommand && !isInternalReportSubcommand && !isFreezeChatCommand) {
            if (!sender.hasPermission(USE_PERMISSION)) {
                sendConfigMessage(sender, "messages.no_permission_command");
                return true;
            }
        }

        switch (commandLabel) {
            case "crown" -> {
                return handleCrownBaseCommand(sender, args);
            }
            case LOCKER_SUBCOMMAND -> {
                return handleLockerCommand(sender, args);
            }
            case PUNISH_SUBCOMMAND -> {
                return handlePunishCommand(sender, args);
            }
            case UNPUNISH_SUBCOMMAND -> {
                return handleUnpunishCommand(sender, args);
            }
            case CHECK_SUBCOMMAND, CHECK_COMMAND_ALIAS -> { // Handle alias from plugin.yml
                return handleCheckCommand(sender, args);
            }
            case HISTORY_SUBCOMMAND -> {
                return handleHistoryCommand(sender, args);
            }
            case PROFILE_SUBCOMMAND -> {
                return handleProfileCommand(sender, args);
            }
            case REPORT_COMMAND -> {
                return handleReportCommand(sender, args);
            }
            case REPORTS_COMMAND -> {
                return handleReportsCommand(sender, args);
            }
            case SOFTBAN_COMMAND_ALIAS, FREEZE_COMMAND_ALIAS, BAN_COMMAND_ALIAS, MUTE_COMMAND_ALIAS, KICK_COMMAND_ALIAS, WARN_COMMAND_ALIAS -> {
                return handlePunishmentTypeAlias(sender, commandLabel, args);
            }
            case UNBAN_COMMAND_ALIAS, UNMUTE_COMMAND_ALIAS, UNWARN_COMMAND_ALIAS, UNSOFTBAN_COMMAND_ALIAS, UNFREEZE_COMMAND_ALIAS -> {
                return handleUnpunishmentTypeAlias(sender, commandLabel, args);
            }
            case FREEZE_CHAT_COMMAND_ALIAS -> {
                return handleFreezeChatCommand(sender, args);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleCrownBaseCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender, 1);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subcommand) {
            case RELOAD_SUBCOMMAND:
                return handleReloadCommand(sender);
            case PUNISH_SUBCOMMAND:
                return handlePunishCommand(sender, subArgs);
            case UNPUNISH_SUBCOMMAND:
                return handleUnpunishCommand(sender, subArgs);
            case CHECK_SUBCOMMAND:
                return handleCheckCommand(sender, subArgs);
            case HISTORY_SUBCOMMAND:
                return handleHistoryCommand(sender, subArgs);
            case PROFILE_SUBCOMMAND:
                return handleProfileCommand(sender, subArgs);
            case LOG_SUBCOMMAND:
                return handleLogCommand(sender, subArgs);
            case LOCKER_SUBCOMMAND:
                return handleLockerCommand(sender, subArgs);
            case REPORT_INTERNAL_SUBCOMMAND:
                if (sender instanceof Player) {
                    plugin.getReportBookManager().handleBookCommand((Player) sender, subArgs);
                }
                return true;
            case HELP_SUBCOMMAND:
                int page = 1;
                if (subArgs.length > 0) {
                    try {
                        page = Integer.parseInt(subArgs[0]);
                    } catch (NumberFormatException ignored) {}
                }
                help(sender, page);
                return true;
            default:
                help(sender, 1);
                return true;
        }
    }

    private boolean handleLockerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendConfigMessage(sender, "messages.player_only");
            return true;
        }

        if (!player.hasPermission(MOD_USE_PERMISSION) && !player.hasPermission(PROFILE_EDIT_INVENTORY_PERMISSION)) {
            sendConfigMessage(player, "messages.no_permission");
            return true;
        }

        if (args.length > 0) {
            if (!player.hasPermission(LOCKER_ADMIN_PERMISSION)) {
                sendConfigMessage(player, "messages.no_permission");
                return true;
            }

            if (args[0].equalsIgnoreCase("all")) {
                // Global Locker
                new LockerMenu(plugin, player, null, 1).open();
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sendConfigMessage(player, "messages.never_played", "{input}", args[0]);
                return true;
            }

            // Check if the target has permission to have a locker (i.e., is a moderator)
            Player targetPlayer = target.getPlayer();
            boolean hasModPerm = false;
            if (targetPlayer != null) {
                hasModPerm = targetPlayer.hasPermission(MOD_USE_PERMISSION) || targetPlayer.hasPermission(PROFILE_EDIT_INVENTORY_PERMISSION);
            } else {
                // If offline, check if they have any confiscated items. If so, they effectively have a locker.
                // This is a fallback since we can't check permissions for offline players easily.
                plugin.getSoftBanDatabaseManager().hasConfiscatedItems(target.getUniqueId()).thenAccept(hasItems -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!hasItems) {
                            // If they don't have items, we can't be sure if they are staff or not without Vault.
                            // But the requirement is to check if they "ever opened their own locker" (which implies having items or permission).
                            // Since we can't track "opened locker", checking for items is the closest proxy for "has used locker features".
                            // However, the prompt says "check if they ever opened their own locker". We don't log that specifically.
                            // But if they have items, they definitely have a locker.
                            // If they don't have items, we might deny access to avoid creating empty lockers for random players.
                            sendConfigMessage(player, "messages.locker_target_no_permission", "{target}", target.getName());
                        } else {
                            sendConfigMessage(player, "messages.locker_opened_other", "{target}", target.getName());
                            new LockerMenu(plugin, player, target.getUniqueId(), 1).open();
                        }
                    });
                });
                return true;
            }

            if (targetPlayer != null) {
                if (!hasModPerm) {
                    sendConfigMessage(player, "messages.locker_target_no_permission", "{target}", target.getName());
                    return true;
                }
            }

            sendConfigMessage(player, "messages.locker_opened_other", "{target}", target.getName());
            new LockerMenu(plugin, player, target.getUniqueId(), 1).open();
        } else {
            // Self Locker
            new LockerMenu(plugin, player, player.getUniqueId(), 1).open();
        }
        return true;
    }

    private boolean handlePunishmentTypeAlias(CommandSender sender, String punishmentType, String[] args) {
        List<String> newArgsList = new ArrayList<>();
        if (args.length > 0) {
            newArgsList.add(args[0]);
        } else {
            // Show usage if no player specified
            return handlePunishCommand(sender, new String[]{});
        }
        newArgsList.add(punishmentType);
        if (args.length > 1) {
            newArgsList.addAll(Arrays.asList(args).subList(1, args.length));
        }
        return handlePunishCommand(sender, newArgsList.toArray(new String[0]));
    }

    private boolean handleUnpunishmentTypeAlias(CommandSender sender, String unpunishmentCommand, String[] args) {
        String punishmentType = unpunishmentCommand.substring(2); // "unban" -> "ban"
        String[] newArgs;

        if (args.length == 0) {
            return handleUnpunishCommand(sender, new String[0]);
        }

        // Handle unpunish by ID for aliases
        if (args[0].startsWith("#")) {
            newArgs = new String[args.length];
            newArgs[0] = args[0]; // The ID
            if (args.length > 1) {
                System.arraycopy(args, 1, newArgs, 1, args.length - 1); // The reason
            }
            return handleUnpunishCommand(sender, newArgs, punishmentType);
        }

        // Handle unpunish by player name for aliases
        newArgs = new String[args.length + 1];
        newArgs[0] = args[0]; // player name
        newArgs[1] = punishmentType;

        if (args.length > 1) {
            System.arraycopy(args, 1, newArgs, 2, args.length - 1);
        }

        return handleUnpunishCommand(sender, newArgs);
    }


    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission");
            return true;
        }
        plugin.getConfigManager().loadConfig();
        sendConfigMessage(sender, "messages.reload_success");
        return true;
    }

    private boolean handleHistoryCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(HISTORY_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_command");
            return true;
        }
        if (!(sender instanceof Player)) {
            sendConfigMessage(sender, "messages.player_only");
            return true;
        }
        if (args.length == 0) {
            sendConfigMessage(sender, "messages.history_usage", "{usage}", "/history <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", args[0]);
            return true;
        }

        new HistoryMenu(target.getUniqueId(), plugin).open((Player) sender);
        return true;
    }

    private boolean handleProfileCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PROFILE_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_command");
            return true;
        }
        if (!(sender instanceof Player playerSender)) {
            sendConfigMessage(sender, "messages.player_only");
            return true;
        }
        if (args.length == 0) {
            sendConfigMessage(sender, "messages.profile_usage", "{usage}", "/profile <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (playerSender.getUniqueId().equals(target.getUniqueId())) {
            sendConfigMessage(sender, "messages.profile_self_check_error");
            return true;
        }

        // MODIFIED: Use a more specific error message for offline players
        if (!target.isOnline()) {
            sendConfigMessage(sender, "messages.profile_offline_error", "{input}", args[0]);
            return true;
        }

        new ProfileMenu(target.getUniqueId(), plugin).open(playerSender);
        return true;
    }

    private boolean handleLogCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PROFILE_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_command");
            return true;
        }
        if (!(sender instanceof Player playerSender)) {
            sendConfigMessage(sender, "messages.player_only");
            return true;
        }
        if (args.length == 0) {
            sendConfigMessage(sender, "messages.log_usage", "{usage}", "/crown log <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", args[0]);
            return true;
        }

        new AuditLogBook(plugin, target.getUniqueId(), playerSender).openBook();
        return true;
    }

    private boolean handleCheckCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(CHECK_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_command");
            return true;
        }
        if (args.length == 0) {
            sendConfigMessage(sender, "messages.check_usage");
            return true;
        }

        String punishmentId = args[0];
        if (punishmentId.startsWith("#")) {
            punishmentId = punishmentId.substring(1);
        }
        DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager().getPunishmentById(punishmentId);

        if (entry == null) {
            sendConfigMessage(sender, "messages.punishment_not_found", "{id}", punishmentId);
            return true;
        }

        String action = "info";
        if (args.length > 1) {
            action = args[1].toLowerCase();
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getPlayerUUID());

        switch (action) {
            case "info":
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String type = entry.getType().toLowerCase();
                String status;
                String timeLeft = "N/A";
                boolean isInternal = plugin.getConfigManager().isPunishmentInternal(type);

                // --- STATUS LOGIC REWORK ---
                if (!entry.isActive()) {
                    boolean isSystemExpired = "System".equals(entry.getRemovedByName()) && ("Expired".equalsIgnoreCase(entry.getRemovedReason()) || "Superseded by new warning.".equalsIgnoreCase(entry.getRemovedReason()));
                    boolean isPaused = "Paused by new warning".equalsIgnoreCase(entry.getRemovedReason());
                    if (isPaused) {
                        status = plugin.getConfigManager().getMessage("placeholders.status_paused");
                    } else if (isSystemExpired) {
                        status = plugin.getConfigManager().getMessage("placeholders.status_expired");
                    } else {
                        status = plugin.getConfigManager().getMessage("placeholders.status_removed");
                    }
                } else if (entry.getEndTime() < System.currentTimeMillis() && entry.getEndTime() != Long.MAX_VALUE) {
                    status = plugin.getConfigManager().getMessage("placeholders.status_expired");
                } else {
                    status = plugin.getConfigManager().getMessage("placeholders.status_active");
                }


                if (isInternal) {
                    // --- INTERNAL PUNISHMENT TIMELEFT LOGIC ---
                    if (type.equals("warn")) {
                        ActiveWarningEntry activeWarning = plugin.getSoftBanDatabaseManager().getActiveWarningByPunishmentId(punishmentId);
                        if (activeWarning != null) {
                            sendConfigMessage(sender, "messages.check_info_warn_level", "{level}", String.valueOf(activeWarning.getWarnLevel()));
                            if (activeWarning.isPaused()) {
                                timeLeft = TimeUtils.formatTime((int) (activeWarning.getRemainingTimeOnPause() / 1000), plugin.getConfigManager());
                            } else {
                                if (activeWarning.getEndTime() != -1) {
                                    timeLeft = TimeUtils.formatTime((int) ((activeWarning.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                                } else {
                                    timeLeft = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
                                }
                            }
                        }
                    } else if (type.equals("kick")) {
                        timeLeft = "N/A";
                    } else { // For internal ban, mute, softban, freeze
                        if (status.equals(plugin.getConfigManager().getMessage("placeholders.status_active"))) {
                            if (entry.getEndTime() != Long.MAX_VALUE) {
                                timeLeft = TimeUtils.formatTime((int) ((entry.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                            } else {
                                timeLeft = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
                            }
                        }
                    }
                } else {
                    // --- EXTERNAL PUNISHMENT TIMELEFT LOGIC ---
                    if (type.equals("kick") || type.equals("warn")) {
                        timeLeft = "N/A";
                    } else { // For ban, mute, softban, freeze
                        if (status.equals(plugin.getConfigManager().getMessage("placeholders.status_active"))) {
                            if (entry.getEndTime() != Long.MAX_VALUE) {
                                timeLeft = TimeUtils.formatTime((int) ((entry.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                            } else {
                                timeLeft = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
                            }
                        }
                    }
                }


                String method = entry.wasByIp() ? plugin.getConfigManager().getMessage("placeholders.by_ip") : plugin.getConfigManager().getMessage("placeholders.by_local");


                sendConfigMessage(sender, "messages.check_info_header", "{id}", punishmentId);
                sendConfigMessage(sender, "messages.check_info_player", "{player}", target.getName(), "{uuid}", target.getUniqueId().toString());
                sendConfigMessage(sender, "messages.check_info_type", "{type}", entry.getType(), "{method}", method);
                sendConfigMessage(sender, "messages.check_info_status", "{status}", status);
                sendConfigMessage(sender, "messages.check_info_reason", "{reason}", entry.getReason());
                sendConfigMessage(sender, "messages.check_info_punisher", "{punisher}", entry.getPunisherName());
                sendConfigMessage(sender, "messages.check_info_date", "{date}", dateFormat.format(entry.getTimestamp()));
                sendConfigMessage(sender, "messages.check_info_duration", "{duration}", entry.getDurationString());
                sendConfigMessage(sender, "messages.check_info_expires", "{time_left}", timeLeft);

                DatabaseManager.PlayerInfo playerInfo = plugin.getSoftBanDatabaseManager().getPlayerInfo(punishmentId);
                if (playerInfo != null) {
                    sendConfigMessage(sender, "messages.check_info_extra_header");
                    sendConfigMessage(sender, "messages.check_info_ip", "{ip}", playerInfo.getIp());
                    sendConfigMessage(sender, "messages.check_info_location", "{location}", playerInfo.getLocation());
                    sendConfigMessage(sender, "messages.check_info_gamemode", "{gamemode}", playerInfo.getGamemode());
                    sendConfigMessage(sender, "messages.check_info_health", "{health}", String.valueOf(playerInfo.getHealth()));
                    sendConfigMessage(sender, "messages.check_info_hunger", "{hunger}", String.valueOf(playerInfo.getHunger()));
                    sendConfigMessage(sender, "messages.check_info_exp_level", "{exp_level}", String.valueOf(playerInfo.getExpLevel()));
                    sendConfigMessage(sender, "messages.check_info_playtime", "{playtime}", TimeUtils.formatTime((int) (playerInfo.getPlaytime() / 20), plugin.getConfigManager()));
                    sendConfigMessage(sender, "messages.check_info_ping", "{ping}", String.valueOf(playerInfo.getPing()));
                    sendConfigMessage(sender, "messages.check_info_first_joined", "{first_joined}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(playerInfo.getFirstJoined())));
                    sendConfigMessage(sender, "messages.check_info_last_joined", "{last_joined}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(playerInfo.getLastJoined())));
                }
                List<String> chatHistory = plugin.getSoftBanDatabaseManager().getChatHistory(target.getUniqueId(), 10);
                if (!chatHistory.isEmpty()) {
                    sendConfigMessage(sender, "messages.check_info_chat_history_header");
                    for (String msg : chatHistory) {
                        sendConfigMessage(sender, "messages.check_info_chat_history_entry", "{message}", msg);
                    }
                }

                if (playerInfo != null) {
                    List<String> associatedAccounts = plugin.getSoftBanDatabaseManager().getPlayersByIp(playerInfo.getIp());
                    if (!associatedAccounts.isEmpty()) {
                        sendConfigMessage(sender, "messages.check_info_associated_accounts_header");
                        sendConfigMessage(sender, "messages.check_info_associated_accounts_entry", "{accounts}", String.join(", ", associatedAccounts));
                    }
                }

                boolean isManuallyRemoved = !entry.isActive() && !("System".equals(entry.getRemovedByName()) && "Expired".equalsIgnoreCase(entry.getRemovedReason()));
                if (isManuallyRemoved) {
                    sendConfigMessage(sender, "messages.check_info_removed", "{remover}", entry.getRemovedByName(), "{remove_date}", dateFormat.format(entry.getRemovedAt()), "{remove_reason}", entry.getRemovedReason());
                }

                if (sender instanceof Player) {
                    Component repunishButton = MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.check_info_repunish_button"))
                            .clickEvent(ClickEvent.runCommand("/check " + punishmentId + " repunish"))
                            .hoverEvent(HoverEvent.showText(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.check_info_repunish_hover"))));

                    Component unpunishButton = MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.check_info_unpunish_button"))
                            .clickEvent(ClickEvent.runCommand("/check " + punishmentId + " unpunish"))
                            .hoverEvent(HoverEvent.showText(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.check_info_unpunish_hover"))));

                    Component separator = MessageUtils.getColorComponent(" &7| ");

                    Component actionMessage = Component.empty()
                            .append(repunishButton)
                            .append(separator)
                            .append(unpunishButton);

                    sender.sendMessage(actionMessage);
                }

                break;
            case "repunish":
                if (sender instanceof Player) {
                    PunishDetailsMenu detailsMenu = new PunishDetailsMenu(target.getUniqueId(), plugin, entry.getType());
                    detailsMenu.setBanReason(entry.getReason());
                    detailsMenu.setBanTime(entry.getDurationString());
                    detailsMenu.setByIp(entry.wasByIp());
                    detailsMenu.open((Player) sender);
                } else {
                    sendConfigMessage(sender, "messages.player_only");
                }
                break;
            case "unpunish":
                boolean isPaused = "Paused by new warning".equalsIgnoreCase(entry.getRemovedReason());
                if (!entry.isActive() && !isPaused) {
                    sendConfigMessage(sender, "messages.punishment_not_active", "{id}", entry.getPunishmentId());
                    return true;
                }

                if (entry.getType().equalsIgnoreCase("warn")) {
                    boolean isInternalWarn = plugin.getConfigManager().isPunishmentInternal("warn");
                    String reason = plugin.getConfigManager().getDefaultUnpunishmentReason("warn");
                    // If internal, pass the specific ID. If external, pass null to remove the latest one.
                    confirmDirectUnpunish(sender, target, "warn", reason, isInternalWarn ? entry.getPunishmentId() : null);
                } else {
                    confirmDirectUnpunish(sender, target, entry.getType(), plugin.getConfigManager().getDefaultUnpunishmentReason(entry.getType()), entry.getPunishmentId());
                }
                break;
            default:
                sendConfigMessage(sender, "messages.check_usage");
                break;
        }

        return true;
    }


    private boolean handlePunishCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender, 1);
            return true;
        }

        String targetName = args[0];

        if (targetName.length() < 3 || targetName.length() > 16) {
            sendConfigMessage(sender, "messages.invalid_player_name");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sendConfigMessage(sender, "messages.player_only");
                return true;
            }
            new PunishMenu(target.getUniqueId(), plugin).open((Player) sender);
        } else {
            String punishType = args[1].toLowerCase();
            if (!PUNISHMENT_TYPES.contains(punishType)) {
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return true;
            }

            if (!checkPunishCommandPermission(sender, punishType)) {
                sendNoPermissionCommandMessage(sender, punishType);
                return true;
            }

            List<String> argsList = new ArrayList<>(Arrays.asList(args));
            argsList.removeFirst(); // remove target
            argsList.removeFirst(); // remove type

            Boolean byIpOverride = null;
            if (!argsList.isEmpty() && plugin.getConfigManager().isIpPunishmentSupported(punishType)) {
                String firstArg = argsList.getFirst();
                if (firstArg.equalsIgnoreCase("-IP") || firstArg.equalsIgnoreCase("-i")) {
                    byIpOverride = true;
                    argsList.removeFirst();
                } else if (firstArg.equalsIgnoreCase("-LOCAL") || firstArg.equalsIgnoreCase("-L")) {
                    byIpOverride = false;
                    argsList.removeFirst();
                }
            }

            String timeForPunishment;
            String reason;

            if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) {
                if (!argsList.isEmpty() && TimeUtils.isValidTimeFormat(argsList.getFirst(), plugin.getConfigManager())) {
                    timeForPunishment = argsList.getFirst();
                    argsList.removeFirst();
                } else {
                    timeForPunishment = "permanent";
                }
            } else {
                timeForPunishment = "permanent"; // Not applicable for kick, warn, freeze
            }

            reason = !argsList.isEmpty() ? String.join(" ", argsList) : plugin.getConfigManager().getDefaultPunishmentReason(punishType);


            if (plugin.getConfigManager().isDebugEnabled())
                plugin.getLogger().info("[MainCommand] Direct punishment confirmed for " + target.getName() + ", type: " + punishType);
            confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason, byIpOverride);
        }
        return true;
    }

    private boolean handleUnpunishCommand(CommandSender sender, String[] args) {
        return handleUnpunishCommand(sender, args, null);
    }

    private boolean handleUnpunishCommand(CommandSender sender, String[] args, String expectedType) {
        if (args.length == 0) {
            String commandLabel = (sender instanceof Player) ? "unpunish" : "crown unpunish";
            sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/" + commandLabel + " <player|#id> <type|reason> [reason]");
            return true;
        }

        String firstArg = args[0];
        if (firstArg.startsWith("#")) {
            // Unpunish by ID
            String punishmentId = firstArg.substring(1);
            DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager().getPunishmentById(punishmentId);

            if (entry == null) {
                sendConfigMessage(sender, "messages.punishment_not_found", "{id}", punishmentId);
                return true;
            }

            if (expectedType != null && !entry.getType().equalsIgnoreCase(expectedType)) {
                sendConfigMessage(sender, "messages.punishment_type_mismatch", "{id}", punishmentId, "{actual_type}", entry.getType().toUpperCase(), "{expected_type}", expectedType.toUpperCase());
                return true;
            }

            boolean isPaused = "Paused by new warning".equalsIgnoreCase(entry.getRemovedReason());
            boolean isInternal = plugin.getConfigManager().isPunishmentInternal(entry.getType());

            if (!entry.isActive() && !(isInternal && isPaused)) {
                sendConfigMessage(sender, "messages.punishment_not_active", "{id}", punishmentId);
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getPlayerUUID());
            String reason = (args.length > 1) ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : plugin.getConfigManager().getDefaultUnpunishmentReason(entry.getType());

            if (lacksUnpunishPermission(sender, entry.getType())) {
                sendNoPermissionUnpunishMessage(sender, entry.getType());
                return true;
            }

            if (entry.getType().equalsIgnoreCase("warn")) {
                // If internal, pass the specific ID. If external, pass null to remove the latest one.
                confirmDirectUnpunish(sender, target, "warn", reason, isInternal ? entry.getPunishmentId() : null);
            } else {
                confirmDirectUnpunish(sender, target, entry.getType(), reason, entry.getPunishmentId());
            }
            return true;
        } else {
            // Unpunish by player name
            if (args.length < 2) {
                String commandLabel = (sender instanceof Player) ? "unpunish" : "crown unpunish";
                sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/" + commandLabel + " <player> <type> [reason]");
                return true;
            }

            String targetName = args[0];
            if (targetName.length() < 3 || targetName.length() > 16) {
                sendConfigMessage(sender, "messages.invalid_player_name", "{input}", targetName);
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
                return true;
            }

            String punishType = args[1].toLowerCase();
            if (!UNPUNISHMENT_TYPES.contains(punishType)) {
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
                return true;
            }

            if (lacksUnpunishPermission(sender, punishType)) {
                sendNoPermissionUnpunishMessage(sender, punishType);
                return true;
            }

            String reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : plugin.getConfigManager().getDefaultUnpunishmentReason(punishType);
            confirmDirectUnpunish(sender, target, punishType, reason, null);
            return true;
        }
    }


    private void executePunishmentCommand(CommandSender sender, String commandTemplate, OfflinePlayer target, String time, String reason) {
        if (commandTemplate == null || commandTemplate.isEmpty()) {
            return;
        }

        String processedCommand = commandTemplate
                .replace("{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString())
                .replace("{time}", time)
                .replace("{reason}", reason);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                if (!success) {
                    sendConfigMessage(sender, "messages.command_not_found", "{command}", processedCommand);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "An error occurred while dispatching command: " + processedCommand, e);
                sendConfigMessage(sender, "messages.command_dispatch_error", "{command}", processedCommand);
            }
        });
    }


    private void confirmDirectPunishment(final CommandSender sender, final OfflinePlayer target, final String punishType, final String time, final String reason, final Boolean byIpOverride) {
        boolean byIp = byIpOverride != null ? byIpOverride : plugin.getConfigManager().isPunishmentByIp(punishType);

        // Pre-emptive check for local kicks on offline players
        if (punishType.equalsIgnoreCase("kick") && !byIp && !target.isOnline()) {
            sendConfigMessage(sender, "messages.player_not_online", "{input}", target.getName());
            return;
        }

        if (target instanceof Player playerTarget) {
            if (punishType.equalsIgnoreCase("softban") && playerTarget.hasPermission("crown.bypass.softban")) {
                sendConfigMessage(sender, "messages.bypass_error_softban", "{target}", target.getName());
                return;
            }
            if (punishType.equalsIgnoreCase("freeze") && playerTarget.hasPermission("crown.bypass.freeze")) {
                sendConfigMessage(sender, "messages.bypass_error_freeze", "{target}", target.getName());
                return;
            }
            if (punishType.equalsIgnoreCase("ban") && playerTarget.hasPermission("crown.bypass.ban")) {
                sendConfigMessage(sender, "messages.bypass_error_ban", "{target}", target.getName());
                return;
            }
            if (punishType.equalsIgnoreCase("mute") && playerTarget.hasPermission("crown.bypass.mute")) {
                sendConfigMessage(sender, "messages.bypass_error_mute", "{target}", target.getName());
                return;
            }
            if (punishType.equalsIgnoreCase("kick") && playerTarget.hasPermission("crown.bypass.kick")) {
                sendConfigMessage(sender, "messages.bypass_error_kick", "{target}", target.getName());
                return;
            }
            if (punishType.equalsIgnoreCase("warn") && playerTarget.hasPermission("crown.bypass.warn")) {
                sendConfigMessage(sender, "messages.bypass_error_warn", "{target}", target.getName());
                return;
            }
        }

        String commandTemplate = plugin.getConfigManager().getPunishmentCommand(punishType);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(punishType);

        String ipAddress = null;
        if (byIp) {
            Player playerTarget = target.getPlayer();
            if (playerTarget != null) {
                InetSocketAddress address = playerTarget.getAddress();
                if (address != null && address.getAddress() != null) {
                    ipAddress = address.getAddress().getHostAddress();
                }
            } else {
                ipAddress = plugin.getSoftBanDatabaseManager().getLastKnownIp(target.getUniqueId());
            }

            if (ipAddress == null) {
                sendConfigMessage(sender, "messages.player_ip_not_found", "{target}", target.getName());
                return;
            }
        }
        final String finalIpAddress = ipAddress;


        if (!useInternal) {
            if (commandTemplate != null && !commandTemplate.isEmpty()) {
                String baseCommand = commandTemplate.split(" ")[0].toLowerCase();
                if (plugin.getRegisteredCommands().contains(baseCommand)) {
                    sendConfigMessage(sender, "messages.command_loop_error", "{command}", baseCommand);
                    return; // Stop execution to prevent loop
                }
            }
        }

        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

        long punishmentEndTime = 0L;
        String durationForLog = "N/A";

        switch (punishType.toLowerCase()) {
            case "ban":
            case "mute":
            case "softban":
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay)) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = permanentDisplay;
                } else {
                    durationForLog = time;
                }
                break;
            case "freeze":
                punishmentEndTime = Long.MAX_VALUE;
                durationForLog = permanentDisplay;
                break;
            case "kick":
            case "warn":
                if (!useInternal) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = permanentDisplay;
                }
                // For internal warns, duration is handled by level config.
                // For kicks, there is no end time.
                break;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return;
            }

        if (punishType.equalsIgnoreCase("warn") && useInternal) {
            // Warn logic is complex and involves multiple DB reads/writes, handle it separately.
            handleInternalWarn(sender, target, reason);
            return;
        }

        final long finalPunishmentEndTime = punishmentEndTime;
        final String finalDurationForLog = durationForLog;
        CompletableFuture<String> punishmentFuture = plugin.getSoftBanDatabaseManager()
                .executePunishmentAsync(target.getUniqueId(), punishType, reason, sender.getName(), finalPunishmentEndTime, finalDurationForLog, byIp, null);

        punishmentFuture.thenAccept(punishmentId -> {
            if (punishmentId == null) {
                // Error already logged by DatabaseManager
                return;
            }

            // Log player info immediately after getting the punishment ID
            plugin.getSoftBanDatabaseManager().logPlayerInfoAsync(punishmentId, target, finalIpAddress);

            // All Bukkit API calls must be in a sync task
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (useInternal) {
                    handleInternalPunishmentPostAction(sender, target, punishType, reason, finalIpAddress, time, punishmentId, finalPunishmentEndTime);
                } else {
                    executePunishmentCommand(sender, commandTemplate, target, time, reason);
                }

                if (byIp) {
                    applyIpPunishmentToOnlinePlayers(punishType, finalIpAddress, finalPunishmentEndTime, reason, finalDurationForLog, punishmentId, target.getUniqueId());
                }

                String messageKey = byIp ? "messages.direct_punishment_confirmed_ip" : "messages.direct_punishment_confirmed";
                sendConfigMessage(sender, messageKey, "{target}", target.getName(), "{time}", finalDurationForLog, "{reason}", reason, "{punishment_type}", punishType, "{punishment_id}", punishmentId);

                MenuListener menuListener = plugin.getMenuListener();
                if (menuListener != null) {
                    menuListener.executeHookActions(sender, target, punishType, finalDurationForLog, reason, false, Collections.emptyList());
                } else {
                    plugin.getLogger().warning("MenuListener instance is null, cannot execute punishment hooks.");
                }
            });
        });
    }

    private void applyIpPunishmentToOnlinePlayers(String punishmentType, String ipAddress, long endTime, String reason, String durationForLog, String punishmentId, UUID originalTargetUUID) {
        String lowerCasePunishType = punishmentType.toLowerCase();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(originalTargetUUID)) {
                continue; // Skip the original target, they are already handled
            }

            InetSocketAddress playerAddress = onlinePlayer.getAddress();
            if (playerAddress != null && playerAddress.getAddress() != null && playerAddress.getAddress().getHostAddress().equals(ipAddress)) {

                switch (lowerCasePunishType) {
                    case "ban", "kick" -> {
                        Date expiration = (endTime == Long.MAX_VALUE || lowerCasePunishType.equals("kick")) ? null : new Date(endTime);
                        List<String> screenLines = lowerCasePunishType.equals("ban") ? plugin.getConfigManager().getBanScreen() : plugin.getConfigManager().getKickScreen();
                        String kickMessage = MessageUtils.getKickMessage(screenLines, reason, durationForLog, punishmentId, expiration, plugin.getConfigManager());
                        onlinePlayer.kick(MessageUtils.getColorComponent(kickMessage));
                    }
                    case "mute" -> {
                        plugin.getMutedPlayersCache().put(onlinePlayer.getUniqueId(), endTime);
                        String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}", durationForLog, "{reason}", reason, "{punishment_id}", punishmentId);
                        onlinePlayer.sendMessage(MessageUtils.getColorMessage(muteMessage));
                    }
                    case "softban" -> {
                        plugin.getSoftBannedPlayersCache().put(onlinePlayer.getUniqueId(), endTime);
                        plugin.getSoftbannedCommandsCache().put(onlinePlayer.getUniqueId(), plugin.getConfigManager().getBlockedCommands());
                        String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned", "{time}", durationForLog, "{reason}", reason, "{punishment_id}", punishmentId);
                        onlinePlayer.sendMessage(MessageUtils.getColorMessage(softbanMessage));
                    }
                    case "freeze" -> {
                        plugin.getPluginFrozenPlayers().put(onlinePlayer.getUniqueId(), true);
                        plugin.getFreezeListener().startFreezeActionsTask(onlinePlayer);
                        onlinePlayer.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_frozen")));
                    }
                }
            }
        }
    }

    private void handleInternalPunishmentPostAction(CommandSender sender, OfflinePlayer target, String punishType, String reason, String ipAddress, String timeInput, String punishmentId, long punishmentEndTime) {
        switch (punishType.toLowerCase()) {
            case "ban":
                long banDuration = TimeUtils.parseTime(timeInput, plugin.getConfigManager());
                Date expiration = (banDuration > 0) ? new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(banDuration)) : null;

                boolean isByIp = ipAddress != null;

                if (isByIp) {
                    try {
                        InetAddress addr = InetAddress.getByName(ipAddress);
                        Bukkit.getBanList(BanListType.IP).addBan(addr, reason, expiration, sender.getName());
                    } catch (UnknownHostException e) {
                        plugin.getLogger().warning("Invalid IP address for ban: " + ipAddress);
                    }
                } else {
                    Bukkit.getBanList(BanListType.PROFILE).addBan(target.getPlayerProfile(), reason, expiration, sender.getName());
                }

                Player playerTarget = target.getPlayer();
                if (playerTarget != null) {
                    String kickMessage = MessageUtils.getKickMessage(plugin.getConfigManager().getBanScreen(), reason, timeInput, punishmentId, expiration, plugin.getConfigManager());
                    playerTarget.kick(MessageUtils.getColorComponent(kickMessage));
                }
                break;
            case "mute":
                plugin.getMutedPlayersCache().put(target.getUniqueId(), punishmentEndTime);
                Player playerTargetMute = target.getPlayer();
                if (playerTargetMute != null) {
                    String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}", timeInput, "{reason}", reason, "{punishment_id}", punishmentId);
                    playerTargetMute.sendMessage(MessageUtils.getColorMessage(muteMessage));
                }
                break;
            case "softban":
                plugin.getSoftBannedPlayersCache().put(target.getUniqueId(), punishmentEndTime);
                plugin.getSoftbannedCommandsCache().put(target.getUniqueId(), plugin.getConfigManager().getBlockedCommands());
                Player playerTargetSoftban = target.getPlayer();
                if (playerTargetSoftban != null) {
                    String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned", "{time}", timeInput, "{reason}", reason, "{punishment_id}", punishmentId);
                    playerTargetSoftban.sendMessage(MessageUtils.getColorMessage(softbanMessage));
                }
                break;
            case "kick":
                Player playerTargetKick = target.getPlayer();
                if (playerTargetKick != null) {
                    String kickMessage = MessageUtils.getKickMessage(plugin.getConfigManager().getKickScreen(), reason, "N/A", punishmentId, null, plugin.getConfigManager());
                    playerTargetKick.kick(MessageUtils.getColorComponent(kickMessage));
                }
                break;
            case "freeze":
                plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true);
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null && !onlineTarget.hasPermission("crown.bypass.freeze")) {
                    sendConfigMessage(onlineTarget, "messages.you_are_frozen");
                    if (plugin.getConfigManager().isDebugEnabled())
                        plugin.getLogger().info("[MainCommand] Starting FreezeActionsTask for player " + onlineTarget.getName() + " after direct freeze command.");
                    plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
                    plugin.getFreezeListener().startFreezeChatSession(sender, onlineTarget, punishmentId);
                }
                break;
        }
    }

    private void handleInternalWarn(CommandSender sender, OfflinePlayer target, String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager dbManager = plugin.getSoftBanDatabaseManager();
            ActiveWarningEntry latestWarning = dbManager.getLatestActiveWarning(target.getUniqueId());
            int nextWarnLevel = (latestWarning != null) ? latestWarning.getWarnLevel() + 1 : 1;

            WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(nextWarnLevel);

            if (levelConfig == null) {
                Bukkit.getScheduler().runTask(plugin, () -> sendConfigMessage(sender, "messages.no_warn_level_configured", "{level}", String.valueOf(nextWarnLevel)));
                return;
            }

            int durationSeconds = TimeUtils.parseTime(levelConfig.getExpiration(), plugin.getConfigManager());
            long endTime = (durationSeconds == -1) ? -1 : System.currentTimeMillis() + (durationSeconds * 1000L);
            String durationForLog = (endTime == -1) ? "Permanent" : TimeUtils.formatTime(durationSeconds, plugin.getConfigManager());

            String punishmentId = dbManager.logPunishment(target.getUniqueId(), "warn", reason, sender.getName(), endTime, durationForLog, false, nextWarnLevel);

            if (punishmentId != null) {
                dbManager.logPlayerInfoAsync(punishmentId, target, null);
            }

            dbManager.addActiveWarning(target.getUniqueId(), punishmentId, nextWarnLevel, endTime).thenRun(() -> {
                ActiveWarningEntry newWarning = dbManager.getActiveWarningByPunishmentId(punishmentId);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.getMenuListener() != null && newWarning != null) {
                        plugin.getMenuListener().executeHookActions(sender, target, "warn", durationForLog, reason, false, levelConfig.getOnWarnActions(), newWarning);
                    } else if (newWarning == null) {
                        plugin.getLogger().severe("Failed to retrieve new warning context for " + punishmentId + " after adding it!");
                    }
                });
            });
        });
    }


    private void confirmDirectUnpunish(final CommandSender sender, final OfflinePlayer target, final String punishType, final String reason, String punishmentId) {
        String commandTemplate = plugin.getConfigManager().getUnpunishCommand(punishType);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(punishType);

        if (!UNPUNISHMENT_TYPES.contains(punishType)) {
            sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
            return;
        }

        if (punishType.equalsIgnoreCase("kick")) {
            sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType);
            return;
        }

        if (!useInternal && commandTemplate != null && !commandTemplate.isEmpty()) {
            String baseCommand = commandTemplate.split(" ")[0].toLowerCase();
            if (plugin.getRegisteredCommands().contains(baseCommand)) {
                sendConfigMessage(sender, "messages.command_loop_error", "{command}", baseCommand);
                return;
            }
        }

        // Handle warn unpunishment separately due to its complexity
        if (useInternal && punishType.equalsIgnoreCase("warn")) {
            handleInternalUnwarn(sender, target, reason, punishmentId); // MODIFIED: Pass punishmentId
            return;
        }

        // Handle freeze unpunishment on the main thread because it involves plugin's internal map
        if (useInternal && punishType.equalsIgnoreCase("freeze")) {
            handleInternalUnfreeze(sender, target, reason, punishmentId);
            return;
        }

        plugin.getSoftBanDatabaseManager()
                .executeUnpunishmentAsync(target.getUniqueId(), punishType, sender.getName(), reason, punishmentId)
                .thenAccept(unpunishedId -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (unpunishedId == null) {
                        sendConfigMessage(sender, "messages.no_active_" + punishType, "{target}", target.getName());
                        return;
                    }

                    DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager().getPunishmentById(unpunishedId);
                    if (entry != null && entry.wasByIp()) {
                        DatabaseManager.PlayerInfo pInfo = plugin.getSoftBanDatabaseManager().getPlayerInfo(unpunishedId);
                        if (pInfo != null && pInfo.getIp() != null) {
                            applyIpUnpunishmentToOnlinePlayers(punishType, pInfo.getIp(), target.getUniqueId()); // MODIFIED
                        }
                    }

                    if (useInternal) {
                        handleInternalUnpunishmentPostAction(sender, target, punishType, unpunishedId);
                    } else {
                        executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                    }

                    sendConfigMessage(sender, "messages.direct_unpunishment_confirmed", "{target}", target.getName(), "{punishment_type}", punishType, "{punishment_id}", unpunishedId);

                    MenuListener menuListener = plugin.getMenuListener();
                    if (menuListener != null) {
                        menuListener.executeHookActions(sender, target, punishType, "N/A", reason, true, Collections.emptyList());
                    } else {
                        plugin.getLogger().warning("MenuListener instance is null, cannot execute unpunishment hooks.");
                    }
                }));
    }

    private void applyIpUnpunishmentToOnlinePlayers(String punishmentType, String ipAddress, UUID originalTargetUUID) { // MODIFIED
        String lowerCaseType = punishmentType.toLowerCase();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(originalTargetUUID)) { // MODIFIED
                continue; // MODIFIED
            }
            InetSocketAddress playerAddress = onlinePlayer.getAddress();
            if (playerAddress != null && playerAddress.getAddress() != null && playerAddress.getAddress().getHostAddress().equals(ipAddress)) {
                switch (lowerCaseType) {
                    case "mute" -> {
                        plugin.getMutedPlayersCache().remove(onlinePlayer.getUniqueId());
                        sendConfigMessage(onlinePlayer, "messages.unmute_notification");
                    }
                    case "softban" -> {
                        plugin.getSoftBannedPlayersCache().remove(onlinePlayer.getUniqueId());
                        plugin.getSoftbannedCommandsCache().remove(onlinePlayer.getUniqueId());
                        sendConfigMessage(onlinePlayer, "messages.unsoftban_notification");
                    }
                    case "freeze" -> {
                        if (plugin.getPluginFrozenPlayers().remove(onlinePlayer.getUniqueId()) != null) {
                            plugin.getFreezeListener().stopFreezeActionsTask(onlinePlayer.getUniqueId());
                            plugin.getFreezeListener().endFreezeChatSession(onlinePlayer.getUniqueId());
                            sendConfigMessage(onlinePlayer, "messages.you_are_unfrozen");
                        }
                    }
                }
            }
        }
    }

    private void handleInternalUnpunishmentPostAction(CommandSender sender, OfflinePlayer target, String punishType, String punishmentId) {
        String lowerCasePunishType = punishType.toLowerCase();

        switch (lowerCasePunishType) {
            case "ban":
                DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager().getPunishmentById(punishmentId);
                boolean wasByIp = entry != null && entry.wasByIp();
                boolean pardoned = false;

                if (wasByIp) {
                    DatabaseManager.PlayerInfo playerInfo = plugin.getSoftBanDatabaseManager().getPlayerInfo(punishmentId);
                    if (playerInfo != null && playerInfo.getIp() != null) {
                        String ip = playerInfo.getIp();
                        try {
                            InetAddress addr = InetAddress.getByName(ip);
                            if (Bukkit.getBanList(BanListType.IP).isBanned(addr)) {
                                Bukkit.getBanList(BanListType.IP).pardon(addr);
                                pardoned = true;
                            }
                        } catch (UnknownHostException e) {
                            // ignore
                        }
                    }
                }

                if (!pardoned && target.getName() != null && Bukkit.getBanList(BanListType.PROFILE).isBanned(target.getPlayerProfile())) {
                    Bukkit.getBanList(BanListType.PROFILE).pardon(target.getPlayerProfile());
                    pardoned = true;
                }

                if (!pardoned) {
                    sendConfigMessage(sender, "messages.not_banned", "{target}", target.getName());
                }
                break;
            case "mute":
                plugin.getMutedPlayersCache().remove(target.getUniqueId());
                break;
            case "softban":
                plugin.getSoftBannedPlayersCache().remove(target.getUniqueId());
                plugin.getSoftbannedCommandsCache().remove(target.getUniqueId());
                break;
        }
    }

    private void handleInternalUnfreeze(CommandSender sender, OfflinePlayer target, String reason, String punishmentId) {
        boolean removed = plugin.getPluginFrozenPlayers().remove(target.getUniqueId()) != null;
        if (!removed) {
            sendConfigMessage(sender, "messages.no_active_freeze", "{target}", target.getName());
            return;
        }

        plugin.getFreezeListener().endFreezeChatSession(target.getUniqueId());
        plugin.getSoftBanDatabaseManager().executeUnpunishmentAsync(target.getUniqueId(), "freeze", sender.getName(), reason, punishmentId)
                .thenAccept(unpunishedId -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (unpunishedId == null) return; // Should not happen if removed was true

                    DatabaseManager.PunishmentEntry entry = plugin.getSoftBanDatabaseManager().getPunishmentById(unpunishedId);
                    if (entry != null && entry.wasByIp()) {
                        DatabaseManager.PlayerInfo pInfo = plugin.getSoftBanDatabaseManager().getPlayerInfo(unpunishedId);
                        if (pInfo != null && pInfo.getIp() != null) {
                            applyIpUnpunishmentToOnlinePlayers("freeze", pInfo.getIp(), target.getUniqueId()); // MODIFIED
                        }
                    }

                    Player onlineTargetUnfreeze = target.getPlayer();
                    if (onlineTargetUnfreeze != null) {
                        sendConfigMessage(onlineTargetUnfreeze, "messages.you_are_unfrozen");
                        plugin.getFreezeListener().stopFreezeActionsTask(target.getUniqueId());
                    }

                    sendConfigMessage(sender, "messages.direct_unpunishment_confirmed", "{target}", target.getName(), "{punishment_type}", "freeze", "{punishment_id}", unpunishedId);

                    MenuListener menuListener = plugin.getMenuListener();
                    if (menuListener != null) {
                        menuListener.executeHookActions(sender, target, "freeze", "N/A", reason, true, Collections.emptyList());
                    }
                }));
    }

    private void handleInternalUnwarn(CommandSender sender, OfflinePlayer target, String reason, String punishmentId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager dbManager = plugin.getSoftBanDatabaseManager();
            ActiveWarningEntry warningToRemove;

            if (punishmentId != null) {
                warningToRemove = dbManager.getActiveWarningByPunishmentId(punishmentId);
            } else {
                warningToRemove = dbManager.getLatestActiveWarning(target.getUniqueId());
            }

            if (warningToRemove == null) {
                Bukkit.getScheduler().runTask(plugin, () -> sendConfigMessage(sender, "messages.no_active_warn", "{target}", target.getName()));
                return;
            }

            final String finalPunishmentId = warningToRemove.getPunishmentId();
            String finalReason = reason;
            if (reason.equals(plugin.getConfigManager().getDefaultUnpunishmentReason("warn"))) {
                finalReason = reason.replace("{player}", sender.getName()) + " (ID: " + finalPunishmentId + ")";
            }

            dbManager.removeActiveWarning(target.getUniqueId(), finalPunishmentId, sender.getName(), finalReason);

            Bukkit.getScheduler().runTask(plugin, () -> sendConfigMessage(sender, "messages.direct_unpunishment_confirmed", "{target}", target.getName(), "{punishment_type}", "warn", "{punishment_id}", finalPunishmentId));
        });
    }


    private void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        String message = plugin.getConfigManager().getMessage(path, replacements);
        sender.sendMessage(MessageUtils.getColorMessage(message));
    }

    private boolean handleFreezeChatCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendConfigMessage(sender, "messages.player_only");
            return true;
        }

        if (!player.hasPermission(MOD_CHAT_PERMISSION)) {
            sendConfigMessage(player, "messages.no_permission");
            return true;
        }

        if (args.length < 1) {
            String leftTarget = plugin.getFreezeListener().leaveModeratorChat(player);
            if (leftTarget != null) {
                sendConfigMessage(player, "messages.fchat_left", "{target}", leftTarget);
            } else {
                help(player, 1);
            }
            return true;
        }

        FreezeListener.FreezeChatToggleResult result = plugin.getFreezeListener().toggleModeratorChat(player, args[0]);
        switch (result) {
            case JOINED -> sendConfigMessage(player, "messages.fchat_joined", "{target}", args[0]);
            case LEFT -> sendConfigMessage(player, "messages.fchat_left", "{target}", args[0]);
            case NOT_ACTIVE -> sendConfigMessage(player, "messages.fchat_not_active", "{target}", args[0]);
            case NOT_FOUND -> sendConfigMessage(player, "messages.fchat_not_found", "{target}", args[0]);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // Allow tab complete for /report even without crown.use
        if (!command.getName().equalsIgnoreCase("report") && !command.getName().equalsIgnoreCase(FREEZE_CHAT_COMMAND_ALIAS) && !sender.hasPermission(USE_PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        final List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());

        String commandLabel = command.getName().toLowerCase();

        if (commandLabel.equals("crown")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList(PUNISH_SUBCOMMAND, UNPUNISH_SUBCOMMAND, CHECK_SUBCOMMAND, HISTORY_SUBCOMMAND, PROFILE_SUBCOMMAND, LOG_SUBCOMMAND, HELP_SUBCOMMAND, RELOAD_SUBCOMMAND, LOCKER_SUBCOMMAND), completions);
            } else if (args.length > 1) {
                String subcommand = args[0].toLowerCase();
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

                switch (subcommand) {
                    case PUNISH_SUBCOMMAND -> handlePunishTab(subArgs, completions, playerNames, PUNISH_SUBCOMMAND);
                    case UNPUNISH_SUBCOMMAND -> handleUnpunishTab(subArgs, completions, playerNames, UNPUNISH_SUBCOMMAND);
                    case CHECK_SUBCOMMAND -> handleCheckTab(subArgs, completions);
                    case HISTORY_SUBCOMMAND, PROFILE_SUBCOMMAND, LOG_SUBCOMMAND -> {
                        if (subArgs.length == 1) {
                            StringUtil.copyPartialMatches(subArgs[0], playerNames, completions);
                        }
                    }
                    case LOCKER_SUBCOMMAND -> {
                        if (subArgs.length == 1) {
                            if (sender.hasPermission(LOCKER_ADMIN_PERMISSION)) {
                                List<String> suggestions = new ArrayList<>();
                                suggestions.add("all");
                                suggestions.addAll(playerNames);
                                StringUtil.copyPartialMatches(subArgs[0], suggestions, completions);
                            } else {
                                StringUtil.copyPartialMatches(subArgs[0], playerNames, completions);
                            }
                        }
                    }
                }
            }
        } else if (commandLabel.equals(LOCKER_SUBCOMMAND)) {
            if (args.length == 1) {
                if (sender.hasPermission(LOCKER_ADMIN_PERMISSION)) {
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("all");
                    suggestions.addAll(playerNames);
                    StringUtil.copyPartialMatches(args[0], suggestions, completions);
                } else {
                    StringUtil.copyPartialMatches(args[0], playerNames, completions);
                }
            }
        } else if (commandLabel.equals(PUNISH_SUBCOMMAND)) {
            handlePunishTab(args, completions, playerNames, PUNISH_SUBCOMMAND);
        } else if (PUNISHMENT_TYPES.contains(commandLabel)) {
            handlePunishTab(args, completions, playerNames, commandLabel);
        } else if (commandLabel.equals(UNPUNISH_SUBCOMMAND)) {
            handleUnpunishTab(args, completions, playerNames, UNPUNISH_SUBCOMMAND);
        } else if (UNPUNISH_ALIASES.contains(commandLabel)) {
            handleUnpunishTab(args, completions, playerNames, commandLabel);
        } else if (commandLabel.equals(CHECK_SUBCOMMAND) || commandLabel.equals(CHECK_COMMAND_ALIAS)) {
            handleCheckTab(args, completions);
        } else if (commandLabel.equals(HISTORY_SUBCOMMAND) || commandLabel.equals(PROFILE_COMMAND_ALIAS)) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            }
        } else if (commandLabel.equals(REPORT_COMMAND)) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            }
        } else if (commandLabel.equals(REPORTS_COMMAND)) {
            if (args.length == 1) {
                String currentArg = args[0];
                if (currentArg.startsWith("!")) {
                    String partialName = currentArg.substring(1);
                    playerNames.stream()
                        .filter(name -> name.toLowerCase().startsWith(partialName.toLowerCase()))
                        .map(name -> "!" + name)
                        .forEach(completions::add);
                } else if (!currentArg.startsWith("#")) {
                    StringUtil.copyPartialMatches(currentArg, playerNames, completions);
                    if ("!".startsWith(currentArg.toLowerCase())) {
                        completions.add("!");
                    }
                    if ("#".startsWith(currentArg.toLowerCase())) {
                        completions.add("#");
                    }
                }
            }
        } else if (commandLabel.equals(FREEZE_CHAT_COMMAND_ALIAS)) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], plugin.getFreezeListener().getFreezeChatSuggestions(), completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }

    private void handlePunishTab(String[] args, List<String> completions, List<String> playerNames, String commandLabel) {
        if (args.length == 0) return;

        List<String> currentArgs = new ArrayList<>(Arrays.asList(args));
        String currentArg = currentArgs.getLast();

        if (commandLabel.equals("punish")) {
            if (currentArgs.size() == 1) {
                StringUtil.copyPartialMatches(currentArg, playerNames, completions);
                return;
            }
            if (currentArgs.size() == 2) {
                StringUtil.copyPartialMatches(currentArg, PUNISHMENT_TYPES, completions);
                return;
            }

            String punishType = currentArgs.get(1).toLowerCase();
            boolean ipSupported = plugin.getConfigManager().isIpPunishmentSupported(punishType);
            boolean timeSupported = punishType.equals("ban") || punishType.equals("mute") || punishType.equals("softban");

            if (currentArgs.size() == 3) {
                List<String> suggestions = new ArrayList<>();
                if (ipSupported) suggestions.addAll(IP_FLAGS);
                if (timeSupported) suggestions.addAll(getTimeSuggestions());
                if (currentArg.isEmpty()) {
                    suggestions.addAll(REASON_SUGGESTION);
                }
                StringUtil.copyPartialMatches(currentArg, suggestions, completions);
                return;
            }

            if (currentArgs.size() == 4) {
                String thirdArg = currentArgs.get(2);
                boolean isIpFlag = IP_FLAGS.stream().anyMatch(flag -> flag.equalsIgnoreCase(thirdArg));
                if (isIpFlag && ipSupported) {
                    List<String> suggestions = new ArrayList<>();
                    if (timeSupported) suggestions.addAll(getTimeSuggestions());
                    if (currentArg.isEmpty()) {
                        suggestions.addAll(REASON_SUGGESTION);
                    }
                    StringUtil.copyPartialMatches(currentArg, suggestions, completions);
                } else if (timeSupported) {
                    if (currentArg.isEmpty()) {
                        completions.addAll(REASON_SUGGESTION);
                    }
                }
                return;
            }
        } else { // It's an alias like /ban
            boolean ipSupported = plugin.getConfigManager().isIpPunishmentSupported(commandLabel);
            boolean timeSupported = commandLabel.equals("ban") || commandLabel.equals("mute") || commandLabel.equals("softban");

            if (currentArgs.size() == 1) {
                StringUtil.copyPartialMatches(currentArg, playerNames, completions);
                return;
            }

            if (currentArgs.size() == 2) {
                List<String> suggestions = new ArrayList<>();
                if (ipSupported) suggestions.addAll(IP_FLAGS);
                if (timeSupported) suggestions.addAll(getTimeSuggestions());
                if (currentArg.isEmpty()) {
                    suggestions.addAll(REASON_SUGGESTION);
                }
                StringUtil.copyPartialMatches(currentArg, suggestions, completions);
                return;
            }

            if (currentArgs.size() == 3) {
                String secondArg = currentArgs.get(1);
                boolean isIpFlag = IP_FLAGS.stream().anyMatch(flag -> flag.equalsIgnoreCase(secondArg));

                if (isIpFlag && ipSupported) {
                    List<String> suggestions = new ArrayList<>();
                    if (timeSupported) suggestions.addAll(getTimeSuggestions());
                    if (currentArg.isEmpty()) {
                        suggestions.addAll(REASON_SUGGESTION);
                    }
                    StringUtil.copyPartialMatches(currentArg, suggestions, completions);
                } else if (timeSupported) {
                    if (currentArg.isEmpty()) {
                        completions.addAll(REASON_SUGGESTION);
                    }
                }
            }
        }

        if (currentArgs.size() >= 3) {
            if (currentArg.isEmpty()) {
                completions.addAll(REASON_SUGGESTION);
            }
        }
    }

    private void handleCheckTab(String[] args, List<String> completions) {
        if (args.length == 1) {
            String currentArg = args[0];
            if (currentArg.isEmpty()) {
                completions.addAll(ID_SUGGESTION);
            } else if (currentArg.startsWith("#")) {
                List<String> activePunishmentIds = plugin.getSoftBanDatabaseManager().getAllActivePunishmentIds();
                StringUtil.copyPartialMatches(currentArg, activePunishmentIds, completions);
            } else {
                if ("#".startsWith(currentArg.toLowerCase())) {
                    completions.add("#");
                }
                StringUtil.copyPartialMatches(currentArg, ID_SUGGESTION, completions);
            }
        } else if (args.length == 2) {
            StringUtil.copyPartialMatches(args[1], CHECK_ACTIONS, completions);
        }
    }

    private List<String> getTimeSuggestions() {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        String secondsUnit = plugin.getConfigManager().getSecondsTimeUnit();
        String minutesUnit = plugin.getConfigManager().getMinutesTimeUnit();
        String hoursUnit = plugin.getConfigManager().getHoursTimeUnit();
        String dayUnit = plugin.getConfigManager().getDayTimeUnit();
        String monthsUnit = plugin.getConfigManager().getMonthsTimeUnit();
        String yearsUnit = plugin.getConfigManager().getYearsTimeUnit();

        if (!secondsUnit.isEmpty()) suggestions.add("1" + secondsUnit);
        if (!minutesUnit.isEmpty()) suggestions.add("1" + minutesUnit);
        if (!hoursUnit.isEmpty()) suggestions.add("1" + hoursUnit);
        if (!dayUnit.isEmpty()) suggestions.add("1" + dayUnit);
        if (!monthsUnit.isEmpty()) suggestions.add("1" + monthsUnit);
        if (!yearsUnit.isEmpty()) suggestions.add("1" + yearsUnit);
        suggestions.add("permanent");

        return new ArrayList<>(suggestions);
    }

    private void handleUnpunishTab(String[] args, List<String> completions, List<String> playerNames, String commandLabel) {
        if (args.length == 0) return;

        String currentArg = args[args.length - 1];

        if (args.length == 1) {
            if (currentArg.startsWith("#")) {
                List<String> activePunishmentIds = plugin.getSoftBanDatabaseManager().getAllActivePunishmentIds();
                StringUtil.copyPartialMatches(currentArg, activePunishmentIds, completions);
            } else {
                StringUtil.copyPartialMatches(currentArg, playerNames, completions);
                if ("#".startsWith(currentArg.toLowerCase())) {
                    completions.add("#");
                }
            }
            return;
        }

        boolean isById = args[0].startsWith("#");

        if (isById) {
            if (args.length == 2) {
                if (currentArg.isEmpty()) {
                    completions.addAll(REASON_SUGGESTION);
                }
            }
        } else {
            // By player name
            if (commandLabel.equals(UNPUNISH_SUBCOMMAND)) {
                if (args.length == 2) {
                    StringUtil.copyPartialMatches(currentArg, UNPUNISHMENT_TYPES, completions);
                } else if (args.length == 3) {
                    if (currentArg.isEmpty()) {
                        completions.addAll(REASON_SUGGESTION);
                    }
                }
            } else { // Alias like /unban
                if (args.length == 2) {
                    if (currentArg.isEmpty()) {
                        completions.addAll(REASON_SUGGESTION);
                    }
                }
            }
        }
    }

    private boolean handleReportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendConfigMessage(sender, "messages.player_only");
            return true;
        }

        if (plugin.getConfigManager().isReportPermissionRequired() && !player.hasPermission(REPORT_CREATE_PERMISSION)) {
            sendConfigMessage(player, "messages.report_no_permission_create");
            return true;
        }

        ReportBookManager reportManager = plugin.getReportBookManager();

        if (!reportManager.checkReportCooldown(player)) {
            return true;
        }

        if (args.length >= 2) { // Direct report: /report <player> <reason...>
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

            if (player.getUniqueId().equals(target.getUniqueId())) {
                sendConfigMessage(player, "messages.report_self_error");
                return true;
            }

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sendConfigMessage(player, "messages.never_played", "{input}", args[0]);
                return true;
            }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            reportManager.createDirectReport(player, target, reason);

        } else if (args.length == 1) { // Open book pre-filled with target
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

            if (player.getUniqueId().equals(target.getUniqueId())) {
                sendConfigMessage(player, "messages.report_self_error");
                return true;
            }

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sendConfigMessage(player, "messages.never_played", "{input}", args[0]);
                return true;
            }
            reportManager.startReportProcess(player, target);
        } else { // Open book from scratch
            reportManager.startReportProcess(player);
        }
        return true;
    }

    private boolean handleReportsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendConfigMessage(sender, "messages.player_only");
            return true;
        }
        if (!player.hasPermission(REPORT_VIEW_PERMISSION)) {
            sendConfigMessage(player, "messages.report_no_permission_view");
            return true;
        }

        if (args.length > 0) {
            String argument = args[0];
            if (argument.startsWith("#")) {
                if (argument.length() > 1) {
                    String reportId = argument.substring(1);
                    new ReportDetailsMenu(plugin, player, reportId).open(player);
                } else {
                    new ReportsMenu(plugin, player).open(player);
                }
                return true;
            } else if (argument.startsWith("!")) {
                if (argument.length() > 1) {
                    String playerName = argument.substring(1);
                    new ReportsMenu(plugin, player, 1, null, playerName, true, false, null).open(player);
                } else {
                    new ReportsMenu(plugin, player).open(player);
                }
                return true;
            } else {
                new ReportsMenu(plugin, player, 1, null, argument, false, false, null).open(player);
                return true;
            }
        }

        new ReportsMenu(plugin, player).open(player);
        return true;
    }

    private void sendNoPermissionUnpunishMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_unpunish_command_type", "{punishment_type}", punishType);
    }

    private void sendNoPermissionCommandMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_punish_command_type", "{punishment_type}", punishType);
    }

    private boolean lacksUnpunishPermission(CommandSender sender, String punishType) {
        return switch (punishType.toLowerCase()) {
            case "ban" -> !sender.hasPermission(UNPUNISH_BAN_PERMISSION);
            case "mute" -> !sender.hasPermission(UNPUNISH_MUTE_PERMISSION);
            case "softban" -> !sender.hasPermission(UNPUNISH_SOFTBAN_PERMISSION);
            case "warn" -> !sender.hasPermission(UNPUNISH_WARN_PERMISSION);
            case "freeze" -> !sender.hasPermission(UNPUNISH_FREEZE_PERMISSION);
            default -> true;
        };
    }

    private boolean checkPunishCommandPermission(CommandSender sender, String punishType) {
        return switch (punishType.toLowerCase()) {
            case "ban" -> sender.hasPermission(PUNISH_BAN_PERMISSION);
            case "mute" -> sender.hasPermission(PUNISH_MUTE_PERMISSION);
            case "softban" -> sender.hasPermission(PUNISH_SOFTBAN_PERMISSION);
            case "kick" -> sender.hasPermission(PUNISH_KICK_PERMISSION);
            case "warn" -> sender.hasPermission(PUNISH_WARN_PERMISSION);
            case "freeze" -> sender.hasPermission(PUNISH_FREEZE_PERMISSION);
            default -> false;
        };
    }

    private void help(CommandSender sender, int page) {
        Map<String, List<HelpEntry>> categories = new LinkedHashMap<>();
        categories.put("punishment", new ArrayList<>());
        categories.put("unpunishment", new ArrayList<>());
        categories.put("utility", new ArrayList<>());
        categories.put("admin", new ArrayList<>());

        List<HelpEntry> punishCmds = categories.get("punishment");
        punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_punish"), "/crown punish"));
        punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_punish_extended"), "/crown punish"));
        punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_punish_alias"), "/punish"));
        if (plugin.getConfigManager().isCommandEnabled("ban")) {
            punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_ban_command"), "/ban"));
        }
        if (plugin.getConfigManager().isCommandEnabled("mute")) {
            punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_mute_command"), "/mute"));
        }
        if (plugin.getConfigManager().isCommandEnabled("softban")) {
            punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_softban_command"), "/softban"));
        }
        if (plugin.getConfigManager().isCommandEnabled("kick")) {
            punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_kick_command"), "/kick"));
        }
        if (plugin.getConfigManager().isCommandEnabled("warn")) {
            punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_warn_command"), "/warn"));
        }
        if (plugin.getConfigManager().isCommandEnabled("freeze")) {
            punishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_freeze_command"), "/freeze"));
        }

        List<HelpEntry> unpunishCmds = categories.get("unpunishment");
        unpunishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_unpunish"), "/crown unpunish"));
        unpunishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_unpunish_alias"), "/unpunish"));
        if (plugin.getConfigManager().isCommandEnabled("unban")) {
            unpunishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_unban_command"), "/unban"));
        }
        if (plugin.getConfigManager().isCommandEnabled("unmute")) {
            unpunishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_unmute_command"), "/unmute"));
        }
        if (plugin.getConfigManager().isCommandEnabled("unsoftban")) {
            unpunishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_unsoftban_command"), "/unsoftban"));
        }
        if (plugin.getConfigManager().isCommandEnabled("unwarn")) {
            unpunishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_unwarn_command"), "/unwarn"));
        }
        if (plugin.getConfigManager().isCommandEnabled("unfreeze")) {
            unpunishCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_unfreeze_command"), "/unfreeze"));
        }

        List<HelpEntry> utilityCmds = categories.get("utility");
        if (sender.hasPermission(PROFILE_PERMISSION)) {
            if (plugin.getConfigManager().isCommandEnabled("profile")) {
                utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_profile_command"), "/profile"));
            } else {
                utilityCmds.add(new HelpEntry(replaceHelpCommand("messages.help_profile_command", "/crown profile"), "/crown profile"));
            }
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_log_command"), "/crown log"));
        }
        if (sender.hasPermission(HISTORY_PERMISSION)) {
            if (plugin.getConfigManager().isCommandEnabled("history")) {
                utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_history_command"), "/history"));
            } else {
                utilityCmds.add(new HelpEntry(replaceHelpCommand("messages.help_history_command", "/crown history"), "/crown history"));
            }
        }
        if (sender.hasPermission(CHECK_PERMISSION)) {
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_check_command"), "/check"));
        }
        if (plugin.getConfigManager().isCommandEnabled("report")
                && (sender.hasPermission(REPORT_CREATE_PERMISSION) || !plugin.getConfigManager().isReportPermissionRequired())) {
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_report_command"), "/report"));
        }
        if (plugin.getConfigManager().isCommandEnabled("report") && sender.hasPermission(REPORT_VIEW_PERMISSION)) {
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_reports_command"), "/reports"));
        }
        if (sender.hasPermission(MOD_USE_PERMISSION)) {
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_mod_command"), "/mod"));
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_mod_target_command"), "/mod target"));
        }
        if (sender.hasPermission(MOD_CHAT_PERMISSION)) {
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_fchat_command"), "/fchat"));
        }
        if (sender.hasPermission(MOD_USE_PERMISSION) || sender.hasPermission(PROFILE_EDIT_INVENTORY_PERMISSION)) {
            utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_locker_command"), "/crown locker"));
            if (plugin.getConfigManager().isCommandEnabled("locker")) {
                utilityCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_locker_alias"), "/locker"));
            }
        }

        List<HelpEntry> adminCmds = categories.get("admin");
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            adminCmds.add(new HelpEntry(plugin.getConfigManager().getMessage("messages.help_reload"), "/crown reload"));
        }

        categories.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        List<String> categoryKeys = new ArrayList<>(categories.keySet());
        int totalPages = categoryKeys.size();

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String currentCategoryKey = categoryKeys.get(page - 1);
        List<HelpEntry> currentMessages = categories.get(currentCategoryKey);

        sender.sendMessage(MessageUtils.getColorMessage(""));
        sender.sendMessage(MessageUtils.getColorMessage(""));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_header")));
        sender.sendMessage(MessageUtils.getColorMessage(""));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_category_" + currentCategoryKey)));
        sender.sendMessage(MessageUtils.getColorMessage(""));
        
        for (HelpEntry entry : currentMessages) {
            if (sender instanceof Player player) {
                Component line = MessageUtils.getColorComponent(entry.message())
                        .clickEvent(ClickEvent.suggestCommand(entry.command()));
                player.sendMessage(line);
            } else {
                sender.sendMessage(MessageUtils.getColorMessage(entry.message()));
            }
        }

        if (sender instanceof Player) {
            sender.sendMessage(MessageUtils.getColorMessage(""));
            sender.sendMessage(MessageUtils.getColorMessage(""));
            Component footer = Component.empty();
            if (page > 1) {
                footer = footer.append(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.help_previous_page"))
                        .clickEvent(ClickEvent.runCommand("/crown help " + (page - 1)))
                        .hoverEvent(HoverEvent.showText(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.help_previous_page_hover")))));
            } else {
                footer = footer.append(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.help_no_previous_page")));
            }

            footer = footer.append(MessageUtils.getColorComponent(" &7| "));

            if (page < totalPages) {
                footer = footer.append(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.help_next_page"))
                        .clickEvent(ClickEvent.runCommand("/crown help " + (page + 1)))
                        .hoverEvent(HoverEvent.showText(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.help_next_page_hover")))));
            } else {
                footer = footer.append(MessageUtils.getColorComponent(plugin.getConfigManager().getMessage("messages.help_no_next_page")));
            }
            sender.sendMessage(footer);
        } else {
            sender.sendMessage(MessageUtils.getColorMessage(""));
            sender.sendMessage(MessageUtils.getColorMessage(""));
            sender.sendMessage(MessageUtils.getColorMessage("&7Page " + page + "/" + totalPages));
        }
    }

    private String replaceHelpCommand(String messageKey, String newCommand) {
        String message = plugin.getConfigManager().getMessage(messageKey);
        if (message == null || message.isEmpty()) {
            return message;
        }
        return message.replaceFirst("/\\S+", newCommand);
    }

    private record HelpEntry(String message, String command) {}
}

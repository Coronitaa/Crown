// src/main/java/cp/corona/commands/MainCommand.java
package cp.corona.commands;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.listeners.MenuListener;
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.PunishMenu;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final Crown plugin;

    // Command and subcommand names constants for maintainability
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String PUNISH_SUBCOMMAND = "punish";
    private static final String UNPUNISH_SUBCOMMAND = "unpunish";
    private static final String CHECK_SUBCOMMAND = "check";
    private static final String HELP_SUBCOMMAND = "help";
    private static final String SOFTBAN_COMMAND_ALIAS = "softban";
    private static final String FREEZE_COMMAND_ALIAS = "freeze";
    private static final String BAN_COMMAND_ALIAS = "ban";
    private static final String MUTE_COMMAND_ALIAS = "mute";
    private static final String KICK_COMMAND_ALIAS = "kick";
    private static final String WARN_COMMAND_ALIAS = "warn";
    private static final String UNBAN_COMMAND_ALIAS = "unban";
    private static final String UNMUTE_COMMAND_ALIAS = "unmute";
    private static final String UNWARN_COMMAND_ALIAS = "unwarn";
    private static final String CHECK_COMMAND_ALIAS = "check";
    private static final String UNSOFTBAN_COMMAND_ALIAS = "unsoftban";
    private static final String UNFREEZE_COMMAND_ALIAS = "unfreeze";
    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final String USE_PERMISSION = "crown.use";
    private static final String CHECK_PERMISSION = "crown.check";
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
    private static final List<String> PUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "kick", "warn", "freeze");
    private static final List<String> UNPUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "warn", "freeze");

    public MainCommand(Crown plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(USE_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_command");
            return true;
        }

        String commandLabel = command.getName().toLowerCase();

        switch (commandLabel) {
            case "crown":
                return handleCrownBaseCommand(sender, args);
            case "punish":
                return handlePunishCommand(sender, args);
            case "unpunish":
                return handleUnpunishCommand(sender, args);
            case "check":
                return handleCheckCommand(sender, args);
            case "softban":
            case "freeze":
            case "ban":
            case "mute":
            case "kick":
            case "warn":
                return handlePunishmentTypeAlias(sender, commandLabel, args);
            case "unban":
            case "unmute":
            case "unwarn":
            case "unsoftban":
            case "unfreeze":
                return handleUnpunishmentTypeAlias(sender, commandLabel, args);
            default:
                return false;
        }
    }

    private boolean handleCrownBaseCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
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
            case HELP_SUBCOMMAND:
            default:
                help(sender);
                return true;
        }
    }

    private boolean handlePunishmentTypeAlias(CommandSender sender, String punishmentType, String[] args) {
        String[] newArgs = new String[args.length + 1];
        if (args.length > 0) {
            newArgs[0] = args[0]; // Player name
        } else {
            // If just /ban, show usage from punish command
            return handlePunishCommand(sender, new String[0]);
        }
        newArgs[1] = punishmentType; // The alias is the punishment type
        if (args.length > 1) {
            System.arraycopy(args, 1, newArgs, 2, args.length - 1);
        }
        return handlePunishCommand(sender, newArgs);
    }

    private boolean handleUnpunishmentTypeAlias(CommandSender sender, String unpunishmentCommand, String[] args) {
        String punishmentType = unpunishmentCommand.substring(2);
        String[] newArgs;

        if (args.length == 0) {
            return handleUnpunishCommand(sender, new String[0]);
        }

        newArgs = new String[args.length + 1];
        newArgs[0] = args[0]; // player name
        newArgs[1] = punishmentType;

        if (args.length > 1) {
            System.arraycopy(args, 1, newArgs, 2, args.length - 1); // reason
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
                if (type.equals("kick") || type.equals("freeze")) {
                    status = "N/A";
                } else {
                    status = entry.isActive() ? (entry.getEndTime() > System.currentTimeMillis() || entry.getEndTime() == Long.MAX_VALUE ? "&a(Active)" : "&c(Expired)") : "&7(Removed)";
                }

                String timeLeft = "N/A";
                if (!type.equals("kick") && !type.equals("freeze")) {
                    timeLeft = entry.isActive() && entry.getEndTime() != Long.MAX_VALUE ? TimeUtils.formatTime((int) ((entry.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager()) : "N/A";
                }


                sendConfigMessage(sender, "messages.check_info_header", "{id}", punishmentId);
                sendConfigMessage(sender, "messages.check_info_player", "{player}", target.getName(), "{uuid}", target.getUniqueId().toString());
                sendConfigMessage(sender, "messages.check_info_type", "{type}", entry.getType());
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

                if (!entry.isActive()) {
                    sendConfigMessage(sender, "messages.check_info_removed", "{remover}", entry.getRemovedByName(), "{remove_date}", dateFormat.format(entry.getRemovedAt()), "{remove_reason}", entry.getRemovedReason());
                }

                if (sender instanceof Player) {
                    TextComponent repunishButton = new TextComponent(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.check_info_repunish_button")));
                    repunishButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/check " + punishmentId + " repunish"));
                    repunishButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to repunish")));

                    TextComponent unpunishButton = new TextComponent(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.check_info_unpunish_button")));
                    unpunishButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/check " + punishmentId + " unpunish"));
                    unpunishButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to unpunish")));

                    TextComponent separator = new TextComponent(MessageUtils.getColorMessage(" &7| "));

                    TextComponent actionMessage = new TextComponent("");
                    actionMessage.addExtra(repunishButton);
                    actionMessage.addExtra(separator);
                    actionMessage.addExtra(unpunishButton);

                    sender.spigot().sendMessage(actionMessage);
                }

                break;
            case "repunish":
                if (sender instanceof Player) {
                    PunishDetailsMenu detailsMenu = new PunishDetailsMenu(target.getUniqueId(), plugin, entry.getType());
                    detailsMenu.setBanReason(entry.getReason());
                    detailsMenu.setBanTime(entry.getDurationString());
                    detailsMenu.open((Player) sender);
                } else {
                    sendConfigMessage(sender, "messages.player_only");
                }
                break;
            case "unpunish":
                confirmDirectUnpunish(sender, target, entry.getType(), plugin.getConfigManager().getDefaultUnpunishmentReason(entry.getType()));
                break;
            default:
                sendConfigMessage(sender, "messages.check_usage");
                break;
        }

        return true;
    }


    private boolean handlePunishCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
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
        } else if (args.length >= 2) {
            String punishType = args[1].toLowerCase();
            if (!PUNISHMENT_TYPES.contains(punishType)) {
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return true;
            }

            if (!checkPunishCommandPermission(sender, punishType)) {
                sendNoPermissionCommandMessage(sender, punishType);
                return true;
            }

            String timeForPunishment;
            String reason;
            int reasonStartIndex;

            if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) {
                if (args.length > 2 && TimeUtils.isValidTimeFormat(args[2], plugin.getConfigManager())) {
                    timeForPunishment = args[2];
                    reasonStartIndex = 3;
                } else {
                    timeForPunishment = "permanent";
                    reasonStartIndex = 2;
                }
                reason = (args.length > reasonStartIndex) ? String.join(" ", Arrays.copyOfRange(args, reasonStartIndex, args.length)) : plugin.getConfigManager().getDefaultPunishmentReason(punishType);
            } else {
                timeForPunishment = "permanent"; // Not applicable for kick, warn, freeze
                reasonStartIndex = 2;
                reason = (args.length > reasonStartIndex) ? String.join(" ", Arrays.copyOfRange(args, reasonStartIndex, args.length)) : plugin.getConfigManager().getDefaultPunishmentReason(punishType);
            }

            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Direct punishment confirmed for " + target.getName() + ", type: " + punishType);
            confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason);
        }
        return true;
    }

    private boolean handleUnpunishCommand(CommandSender sender, String[] args) {
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

        String punishType = args[1].toLowerCase();

        if (!UNPUNISHMENT_TYPES.contains(punishType)) {
            sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
            return true;
        }

        if (!checkUnpunishPermission(sender, punishType)) {
            sendNoPermissionUnpunishMessage(sender, punishType);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        String reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : plugin.getConfigManager().getDefaultUnpunishmentReason(punishType);
        confirmDirectUnpunish(sender, target, punishType, reason);
        return true;
    }


    private void executePunishmentCommand(CommandSender sender, String commandTemplate, OfflinePlayer target, String time, String reason) {
        if (commandTemplate == null || commandTemplate.isEmpty()) {
            return;
        }

        String processedCommand = commandTemplate
                .replace("{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString())
                .replace("{time}", time)
                .replace("{reason}", reason);

        // Loop check is now performed before calling this method.

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                if (!success) {
                    sendConfigMessage(sender, "messages.command_not_found", "{command}", processedCommand);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("An error occurred while dispatching command: " + processedCommand);
                e.printStackTrace();
                sendConfigMessage(sender, "messages.command_dispatch_error", "{command}", processedCommand);
            }
        });
    }


    private void confirmDirectPunishment(final CommandSender sender, final OfflinePlayer target, final String punishType, final String time, final String reason) {
        if (target instanceof Player) {
            Player playerTarget = (Player) target;
            if (punishType.equalsIgnoreCase("softban") && playerTarget.hasPermission("crown.bypass.softban")) {
                sendConfigMessage(sender, "messages.bypass_error_softban", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("freeze") && playerTarget.hasPermission("crown.bypass.freeze")) {
                sendConfigMessage(sender, "messages.bypass_error_freeze", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("ban") && playerTarget.hasPermission("crown.bypass.ban")) {
                sendConfigMessage(sender, "messages.bypass_error_ban", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("mute") && playerTarget.hasPermission("crown.bypass.mute")) {
                sendConfigMessage(sender, "messages.bypass_error_mute", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("kick") && playerTarget.hasPermission("crown.bypass.kick")) {
                sendConfigMessage(sender, "messages.bypass_error_kick", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("warn") && playerTarget.hasPermission("crown.bypass.warn")) {
                sendConfigMessage(sender, "messages.bypass_error_warn", "{target}", target.getName()); return;
            }
        }

        String commandTemplate = plugin.getConfigManager().getPunishmentCommand(punishType);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(punishType);

        if (!useInternal) {
            if (commandTemplate != null && !commandTemplate.isEmpty()) {
                String baseCommand = commandTemplate.split(" ")[0].toLowerCase();
                if (plugin.getRegisteredCommands().contains(baseCommand)) {
                    sendConfigMessage(sender, "messages.command_loop_error", "{command}", baseCommand);
                    return; // Stop execution to prevent loop
                }
            }
        }

        String durationForLog = time;
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        String punishmentId = null;


        switch (punishType.toLowerCase()) {
            case "ban":
                long punishmentEndTimeBan = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay)) {
                    punishmentEndTimeBan = Long.MAX_VALUE;
                    durationForLog = permanentDisplay;
                }
                punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), punishmentEndTimeBan, durationForLog);
                if (useInternal) {
                    long banDuration = TimeUtils.parseTime(time, plugin.getConfigManager());
                    Date expiration = (banDuration > 0) ? new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(banDuration)) : null;
                    Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), reason, expiration, sender.getName());
                    if (target.isOnline()) {
                        String kickMessage = getKickMessage(plugin.getConfigManager().getBanScreen(), reason, durationForLog, punishmentId, expiration);
                        target.getPlayer().kickPlayer(kickMessage);
                    }
                } else {
                    executePunishmentCommand(sender, commandTemplate, target, time, reason);
                }
                break;
            case "mute":
                boolean isPermanentMute = time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay);
                long punishmentEndTimeMute = isPermanentMute ? Long.MAX_VALUE : (TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis());
                durationForLog = isPermanentMute ? permanentDisplay : time;
                if (useInternal) {
                    punishmentId = plugin.getSoftBanDatabaseManager().mutePlayer(target.getUniqueId(), punishmentEndTimeMute, reason, sender.getName());
                    if (target.isOnline()) {
                        String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}", durationForLog, "{reason}", reason, "{punishment_id}", punishmentId);
                        target.getPlayer().sendMessage(MessageUtils.getColorMessage(muteMessage));
                    }
                } else {
                    punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), punishmentEndTimeMute, durationForLog);
                    executePunishmentCommand(sender, commandTemplate, target, time, reason);
                }
                break;
            case "softban":
                boolean isPermanentSoftban = time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay);
                long punishmentEndTimeSoftban = isPermanentSoftban ? Long.MAX_VALUE : (TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis());
                if (!isPermanentSoftban && punishmentEndTimeSoftban > 0) {
                    punishmentEndTimeSoftban += 1000L;
                }
                if(useInternal) {
                    punishmentId = plugin.getSoftBanDatabaseManager().softBanPlayer(target.getUniqueId(), punishmentEndTimeSoftban, reason, sender.getName());
                } else {
                    punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), punishmentEndTimeSoftban, durationForLog);
                    executePunishmentCommand(sender, commandTemplate, target, time, reason);
                }
                break;
            case "kick":
                durationForLog = "N/A";
                punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), 0L, durationForLog);
                if (useInternal) {
                    if (target.isOnline()) {
                        String kickMessage = getKickMessage(plugin.getConfigManager().getKickScreen(), reason, "N/A", punishmentId, null);
                        target.getPlayer().kickPlayer(kickMessage);
                    }
                } else {
                    executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                }
                break;
            case "warn":
                durationForLog = "N/A";
                punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), 0L, durationForLog);
                if (useInternal) {
                    if (target.isOnline()) {
                        target.getPlayer().sendMessage(MessageUtils.getColorMessage(reason));
                    }
                } else {
                    executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                }
                break;
            case "freeze":
                durationForLog = permanentDisplay;
                punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), Long.MAX_VALUE, durationForLog);
                if(useInternal) {
                    plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true);
                    Player onlineTarget = target.getPlayer();
                    if (onlineTarget != null && !onlineTarget.hasPermission("crown.bypass.freeze")) {
                        sendConfigMessage(onlineTarget, "messages.you_are_frozen");
                        if (plugin.getConfigManager().isDebugEnabled())
                            plugin.getLogger().info("[MainCommand] Starting FreezeActionsTask for player " + onlineTarget.getName() + " after direct freeze command.");
                        plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
                    }
                } else {
                    executePunishmentCommand(sender, commandTemplate, target, "permanent", reason);
                }
                break;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return;
        }

        sendConfigMessage(sender, "messages.direct_punishment_confirmed", "{target}", target.getName(), "{time}", durationForLog, "{reason}", reason, "{punishment_type}", punishType, "{punishment_id}", punishmentId);

        MenuListener menuListener = plugin.getMenuListener();
        if (menuListener != null) {
            menuListener.executeHookActions(sender, target, punishType, durationForLog, reason, false);
        } else {
            plugin.getLogger().warning("MenuListener instance is null, cannot execute punishment hooks.");
        }
    }

    private void confirmDirectUnpunish(final CommandSender sender, final OfflinePlayer target, final String punishType, final String reason) {
        String commandTemplate = plugin.getConfigManager().getUnpunishCommand(punishType);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(punishType);
        String punishmentId = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentId(target.getUniqueId(), punishType);

        if (!UNPUNISHMENT_TYPES.contains(punishType)) {
            sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
            return;
        }

        if (punishmentId == null && useInternal) {
            sendConfigMessage(sender, "messages.no_active_" + punishType, "{target}", target.getName());
            return;
        }

        if (!useInternal && commandTemplate != null && !commandTemplate.isEmpty()) {
            String baseCommand = commandTemplate.split(" ")[0].toLowerCase();
            if (plugin.getRegisteredCommands().contains(baseCommand)) {
                sendConfigMessage(sender, "messages.command_loop_error", "{command}", baseCommand);
                return;
            }
        }

        String logReason = reason;
        if (reason.equals(plugin.getConfigManager().getDefaultUnpunishmentReason(punishType))) {
            logReason = reason.replace("{player}", sender.getName()) + " (ID: " + punishmentId + ")";
        }

        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "un" + punishType, logReason, sender.getName(), 0L, "N/A");


        switch (punishType.toLowerCase()) {
            case "ban":
                if (useInternal) {
                    if (!target.isBanned()) {
                        sendConfigMessage(sender, "messages.not_banned", "{target}", target.getName());
                        return;
                    }
                    Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
                } else {
                    executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                }
                plugin.getSoftBanDatabaseManager().updatePunishmentAsRemoved(punishmentId, sender.getName(), logReason);
                break;
            case "mute":
                if (useInternal) {
                    if (!plugin.getSoftBanDatabaseManager().isMuted(target.getUniqueId())) {
                        sendConfigMessage(sender, "messages.not_muted", "{target}", target.getName());
                        return;
                    }
                    plugin.getSoftBanDatabaseManager().unmutePlayer(target.getUniqueId(), sender.getName(), reason);
                } else {
                    plugin.getSoftBanDatabaseManager().updatePunishmentAsRemoved(punishmentId, sender.getName(), logReason);
                    executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                }
                break;
            case "softban":
                if (!plugin.getSoftBanDatabaseManager().isSoftBanned(target.getUniqueId())) {
                    sendConfigMessage(sender, "messages.no_active_softban", "{target}", target.getName());
                    return;
                }
                if (useInternal) {
                    plugin.getSoftBanDatabaseManager().unSoftBanPlayer(target.getUniqueId(), sender.getName(), reason);
                } else {
                    plugin.getSoftBanDatabaseManager().updatePunishmentAsRemoved(punishmentId, sender.getName(), logReason);
                    executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                }
                break;
            case "warn":
                plugin.getSoftBanDatabaseManager().updatePunishmentAsRemoved(punishmentId, sender.getName(), logReason);
                if (useInternal) {
                    plugin.getLogger().warning("Unwarn command is empty, internal unwarn is not supported.");
                } else {
                    executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                }
                break;
            case "freeze":
                if (useInternal) {
                    boolean removed = plugin.getPluginFrozenPlayers().remove(target.getUniqueId()) != null;
                    if (!removed) {
                        sendConfigMessage(sender, "messages.no_active_freeze", "{target}", target.getName());
                        return;
                    }
                    plugin.getSoftBanDatabaseManager().updatePunishmentAsRemoved(punishmentId, sender.getName(), logReason);
                    Player onlineTargetUnfreeze = target.getPlayer();
                    if (onlineTargetUnfreeze != null) {
                        sendConfigMessage(onlineTargetUnfreeze, "messages.you_are_unfrozen");
                        plugin.getFreezeListener().stopFreezeActionsTask(target.getUniqueId());
                    }
                } else {
                    plugin.getSoftBanDatabaseManager().updatePunishmentAsRemoved(punishmentId, sender.getName(), logReason);
                    executePunishmentCommand(sender, commandTemplate, target, "N/A", reason);
                }
                break;
            case "kick":
                sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType);
                return;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
                return;
        }

        sendConfigMessage(sender, "messages.direct_unpunishment_confirmed", "{target}", target.getName(), "{punishment_type}", punishType, "{punishment_id}", punishmentId);

        MenuListener menuListener = plugin.getMenuListener();
        if (menuListener != null) {
            menuListener.executeHookActions(sender, target, punishType, "N/A", logReason, true);
        } else {
            plugin.getLogger().warning("MenuListener instance is null, cannot execute unpunishment hooks.");
        }
    }


    private void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        String message = plugin.getConfigManager().getMessage(path, replacements);
        sender.sendMessage(MessageUtils.getColorMessage(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(USE_PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        final List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());

        if (alias.equalsIgnoreCase("crown")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList(PUNISH_SUBCOMMAND, UNPUNISH_SUBCOMMAND, HELP_SUBCOMMAND, RELOAD_SUBCOMMAND, CHECK_SUBCOMMAND), completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) {
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) {
                StringUtil.copyPartialMatches(args[2], PUNISHMENT_TYPES, completions);
            } else if (args.length == 4 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) {
                String punishType = args[2].toLowerCase();
                if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) {
                    StringUtil.copyPartialMatches(args[3], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
                }
            } else if (args.length >= 5 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) {
                completions.add("reason here...");
            } else if (args.length == 2 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) {
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) {
                StringUtil.copyPartialMatches(args[2], UNPUNISHMENT_TYPES, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase(CHECK_SUBCOMMAND)) {
                completions.add("<punishment_id>");
            } else if (args.length == 3 && args[0].equalsIgnoreCase(CHECK_SUBCOMMAND)) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("info", "repunish", "unpunish"), completions);
            }
        }

        if (alias.equalsIgnoreCase("punish")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], PUNISHMENT_TYPES, completions);
            } else if (args.length == 3) {
                String punishType = args[1].toLowerCase();
                if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) {
                    StringUtil.copyPartialMatches(args[2], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
                }
            } else if (args.length >= 4) {
                completions.add("reason here...");
            }
        }
        if (alias.equalsIgnoreCase("check")) {
            if (args.length == 1) {
                completions.add("<punishment_id>");
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("info", "repunish", "unpunish"), completions);
            }
        }


        if (alias.equalsIgnoreCase("unpunish") || alias.equalsIgnoreCase(UNBAN_COMMAND_ALIAS) || alias.equalsIgnoreCase(UNMUTE_COMMAND_ALIAS) || alias.equalsIgnoreCase(UNWARN_COMMAND_ALIAS) || alias.equalsIgnoreCase(UNSOFTBAN_COMMAND_ALIAS) || alias.equalsIgnoreCase(UNFREEZE_COMMAND_ALIAS)) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            } else if (args.length == 2 && alias.equalsIgnoreCase("unpunish")) {
                StringUtil.copyPartialMatches(args[1], UNPUNISHMENT_TYPES, completions);
            } else if (args.length >= 3) {
                completions.add("reason here...");
            }
        }

        if (alias.equalsIgnoreCase("softban") || alias.equalsIgnoreCase("ban") || alias.equalsIgnoreCase("mute")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
            } else if (args.length >= 3) {
                completions.add("reason here...");
            }
        }

        if (alias.equalsIgnoreCase("freeze") || alias.equalsIgnoreCase("kick") || alias.equalsIgnoreCase("warn")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            } else if (args.length >= 2) {
                completions.add("reason here...");
            }
        }

        Collections.sort(completions);
        return completions;
    }
    private String getKickMessage(List<String> lines, String reason, String timeLeft, String punishmentId, Date expiration) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = dateFormat.format(new Date());
        String dateUntil = expiration != null ? dateFormat.format(expiration) : "Never";

        return lines.stream()
                .map(MessageUtils::getColorMessage)
                .map(line -> line.replace("{reason}", reason))
                .map(line -> line.replace("{time_left}", timeLeft))
                .map(line -> line.replace("{punishment_id}", punishmentId))
                .map(line -> line.replace("{date}", date))
                .map(line -> line.replace("{date_until}", dateUntil))
                .map(line -> line.replace("{support_link}", plugin.getConfigManager().getSupportLink()))
                .collect(Collectors.joining("\n"));
    }
    private boolean checkPunishDetailsPermission(CommandSender sender, String punishType) {
        switch (punishType.toLowerCase()) {
            case "ban": return sender.hasPermission(PUNISH_BAN_PERMISSION);
            case "mute": return sender.hasPermission(PUNISH_MUTE_PERMISSION);
            case "softban": return sender.hasPermission(PUNISH_SOFTBAN_PERMISSION);
            case "kick": return sender.hasPermission(PUNISH_KICK_PERMISSION);
            case "warn": return sender.hasPermission(PUNISH_WARN_PERMISSION);
            case "freeze": return sender.hasPermission(PUNISH_FREEZE_PERMISSION);
            default: return false;
        }
    }

    private void sendNoPermissionUnpunishMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_unpunish_command_type", "{punishment_type}", punishType);
    }

    private void sendNoPermissionCommandMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_punish_command_type", "{punishment_type}", punishType);
    }

    private boolean checkUnpunishPermission(CommandSender sender, String punishType) {
        switch (punishType.toLowerCase()) {
            case "ban": return sender.hasPermission(UNPUNISH_BAN_PERMISSION);
            case "mute": return sender.hasPermission(UNPUNISH_MUTE_PERMISSION);
            case "softban": return sender.hasPermission(UNPUNISH_SOFTBAN_PERMISSION);
            case "warn": return sender.hasPermission(UNPUNISH_WARN_PERMISSION);
            case "freeze": return sender.hasPermission(UNPUNISH_FREEZE_PERMISSION);
            default: return false;
        }
    }

    private void sendNoPermissionDetailsMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_details_menu", "{punishment_type}", punishType);
    }

    private boolean checkPunishCommandPermission(CommandSender sender, String punishType) {
        switch (punishType.toLowerCase()) {
            case "ban": return sender.hasPermission(PUNISH_BAN_PERMISSION);
            case "mute": return sender.hasPermission(PUNISH_MUTE_PERMISSION);
            case "softban": return sender.hasPermission(PUNISH_SOFTBAN_PERMISSION);
            case "kick": return sender.hasPermission(PUNISH_KICK_PERMISSION);
            case "warn": return sender.hasPermission(PUNISH_WARN_PERMISSION);
            case "freeze": return sender.hasPermission(PUNISH_FREEZE_PERMISSION);
            default: return false;
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_header")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish_extended")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish_alias")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish_alias")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_softban_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_freeze_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_ban_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_mute_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_kick_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_warn_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unban_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unmute_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unwarn_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unsoftban_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unfreeze_command")));
        if (sender.hasPermission(CHECK_PERMISSION)) {
            sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_check_command")));
        }
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
        }
    }
}
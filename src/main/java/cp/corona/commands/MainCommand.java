// MainCommand.java
package cp.corona.commands;

import cp.corona.crown.Crown;
import cp.corona.listeners.MenuListener;
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.PunishMenu;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final Crown plugin;

    // Command and subcommand names constants for maintainability
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String PUNISH_SUBCOMMAND = "punish";
    private static final String UNPUNISH_SUBCOMMAND = "unpunish";
    private static final String HELP_SUBCOMMAND = "help";
    private static final String SOFTBAN_COMMAND_ALIAS = "softban";
    private static final String FREEZE_COMMAND_ALIAS = "freeze";
    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final String USE_PERMISSION = "crown.use";
    private static final String PUNISH_BAN_PERMISSION = "crown.punish.ban";
    private static final String UNPUNISH_BAN_PERMISSION = "crown.unpunish.ban";
    private static final String PUNISH_MUTE_PERMISSION = "crown.punish.mute";
    private static final String UNPUNISH_MUTE_PERMISSION = "crown.unpunish.mute";
    private static final String UNPUNISH_WARN_PERMISSION = "crown.unpunish.warn";
    private static final String PUNISH_SOFTBAN_PERMISSION = "crown.punish.softban";
    private static final String UNPUNISH_SOFTBAN_PERMISSION = "crown.unpunish.softban";
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

        if (alias.equalsIgnoreCase("crown")) {
            if (args.length == 0) {
                help(sender);
                return true;
            }

            String subcommand = args[0].toLowerCase();
            switch (subcommand) {
                case RELOAD_SUBCOMMAND:
                    return handleReloadCommand(sender);
                case PUNISH_SUBCOMMAND:
                    return handlePunishCommand(sender, Arrays.copyOfRange(args, 1, args.length));
                case UNPUNISH_SUBCOMMAND:
                    return handleUnpunishCommand(sender, Arrays.copyOfRange(args, 1, args.length));
                case HELP_SUBCOMMAND:
                    help(sender);
                    return true;
                default:
                    help(sender);
                    return true;
            }
        }

        if (alias.equalsIgnoreCase("punish")) {
            return handlePunishCommand(sender, args);
        }

        if (alias.equalsIgnoreCase("unpunish")) {
            return handleUnpunishCommand(sender, args);
        }

        if (alias.equalsIgnoreCase("softban")) {
            return handleSoftbanCommand(sender, args);
        }

        if (alias.equalsIgnoreCase("freeze")) {
            return handleFreezeCommand(sender, args);
        }

        return false;
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

    private boolean handlePunishCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length < 2) {
                sendConfigMessage(sender, "messages.player_only_console_punish");
                return true;
            }
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        String targetName = args[0];
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

            if (args.length == 2) {
                if (!(sender instanceof Player)) {
                    sendConfigMessage(sender, "messages.player_only");
                    return true;
                }
                if (!checkPunishDetailsPermission(sender, punishType)) {
                    sendNoPermissionDetailsMessage(sender, punishType);
                    return true;
                }
                new PunishDetailsMenu(target.getUniqueId(), plugin, punishType).open((Player) sender);
            } else {
                if (!checkPunishCommandPermission(sender, punishType)) {
                    sendNoPermissionCommandMessage(sender, punishType);
                    return true;
                }
                String timeForPunishment = "permanent";
                String reason;

                if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) {
                    if (args.length < 3) {
                        sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/" + (sender instanceof Player ? "punish" : "crown punish") + " " + targetName + " " + punishType + " <time> [reason]");
                        return true;
                    }
                    timeForPunishment = args[2];
                    reason = (args.length > 3) ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "No reason specified.";
                } else {
                    reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason specified.";
                }

                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Direct punishment confirmed for " + target.getName() + ", type: " + punishType);
                confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason);
            }
        }
        return true;
    }

    private boolean handleUnpunishCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            String commandLabel = (sender instanceof Player) ? "unpunish" : "crown unpunish";
            sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/" + commandLabel + " <player> <type>");
            return true;
        }

        String targetName = args[0];
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

        confirmDirectUnpunish(sender, target, punishType);
        return true;
    }

    private boolean handleSoftbanCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PUNISH_SOFTBAN_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_punish_command_type", "{punishment_type}", "softban");
            return true;
        }

        if (args.length < 1) {
            sendConfigMessage(sender, "messages.softban_usage", "{usage}", "/softban <player> [time] [reason]");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        if (target instanceof Player && ((Player) target).hasPermission("crown.bypass.softban")) {
            sendConfigMessage(sender, "messages.bypass_error_softban", "{target}", targetName);
            return true;
        }

        String time = "permanent";
        String reason = "Softbanned by moderator";

        if (args.length >= 2) {
            time = args[1];
            if (TimeUtils.parseTime(time, plugin.getConfigManager()) == 0 && !time.equalsIgnoreCase("permanent") && !time.equalsIgnoreCase(plugin.getConfigManager().getMessage("placeholders.permanent_time_display"))) {
                sendConfigMessage(sender, "messages.invalid_time_format_command", "{input}", time);
                return true;
            }
        }
        if (args.length >= 3) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        confirmDirectPunishment(sender, target, SOFTBAN_COMMAND_ALIAS, time, reason);
        return true;
    }

    private boolean handleFreezeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PUNISH_FREEZE_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_punish_command_type", "{punishment_type}", "freeze");
            return true;
        }

        if (args.length < 1) {
            sendConfigMessage(sender, "messages.freeze_usage", "{usage}", "/freeze <player> [reason]");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        if (target instanceof Player && ((Player) target).hasPermission("crown.bypass.freeze")) {
            sendConfigMessage(sender, "messages.bypass_error_freeze", "{target}", targetName);
            return true;
        }

        if (plugin.getPluginFrozenPlayers().containsKey(target.getUniqueId())) {
            sendConfigMessage(sender, "messages.already_frozen", "{target}", targetName);
            return true;
        }

        String reason = "Frozen by moderator";
        if (args.length >= 2) {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        confirmDirectPunishment(sender, target, FREEZE_COMMAND_ALIAS, "permanent", reason);
        return true;
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

        String commandToExecute = "";
        long punishmentEndTime = 0L;
        String durationForLog = time;
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

        switch (punishType.toLowerCase()) {
            case "ban":
                commandToExecute = plugin.getConfigManager().getBanCommand();
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay)) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = permanentDisplay;
                }
                if (commandToExecute.isEmpty()) {
                    long banDuration = TimeUtils.parseTime(time, plugin.getConfigManager());
                    Date expiration = (banDuration > 0) ? new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(banDuration)) : null;
                    Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), reason, expiration, sender.getName());
                    if (target.isOnline()) {
                        target.getPlayer().kickPlayer(reason);
                    }
                } else {
                    commandToExecute = commandToExecute.replace("{target}", target.getName()).replace("{time}", time).replace("{reason}", reason);
                }
                break;
            case "mute":
                commandToExecute = plugin.getConfigManager().getMuteCommand();
                boolean isPermanentMute = time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay);
                punishmentEndTime = isPermanentMute ? Long.MAX_VALUE : (TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis());
                durationForLog = isPermanentMute ? permanentDisplay : time;
                if (commandToExecute.isEmpty()) {
                    plugin.getSoftBanDatabaseManager().mutePlayer(target.getUniqueId(), punishmentEndTime, reason, sender.getName());
                    if (target.isOnline()) {
                        String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}", durationForLog, "{reason}", reason);
                        target.getPlayer().sendMessage(MessageUtils.getColorMessage(muteMessage));
                    }
                } else {
                    commandToExecute = commandToExecute.replace("{target}", target.getName()).replace("{time}", time).replace("{reason}", reason);
                }
                break;
            case "softban":
                boolean isPermanentSoftban = time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay);
                punishmentEndTime = isPermanentSoftban ? Long.MAX_VALUE : (TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis());
                if (!isPermanentSoftban && punishmentEndTime > 0) {
                    punishmentEndTime += 1000L;
                }
                durationForLog = isPermanentSoftban ? permanentDisplay : time;
                plugin.getSoftBanDatabaseManager().softBanPlayer(target.getUniqueId(), punishmentEndTime, reason, sender.getName());
                break;
            case "kick":
                commandToExecute = plugin.getConfigManager().getKickCommand();
                durationForLog = "N/A";
                punishmentEndTime = 0L;
                if (commandToExecute.isEmpty()) {
                    if (target.isOnline()) {
                        target.getPlayer().kickPlayer(reason);
                    }
                } else {
                    commandToExecute = commandToExecute.replace("{target}", target.getName()).replace("{reason}", reason);
                }
                break;
            case "warn":
                commandToExecute = plugin.getConfigManager().getWarnCommand();
                durationForLog = "N/A";
                punishmentEndTime = 0L;
                if (commandToExecute.isEmpty()) {
                    if (target.isOnline()) {
                        target.getPlayer().sendMessage(MessageUtils.getColorMessage(reason));
                    }
                } else {
                    commandToExecute = commandToExecute.replace("{target}", target.getName()).replace("{reason}", reason);
                }
                break;
            case "freeze":
                plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true);
                durationForLog = permanentDisplay;
                punishmentEndTime = Long.MAX_VALUE;
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null && !onlineTarget.hasPermission("crown.bypass.freeze")) {
                    sendConfigMessage(onlineTarget, "messages.you_are_frozen");
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Starting FreezeActionsTask for player " + onlineTarget.getName() + " after direct freeze command.");
                    plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
                }
                break;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return;
        }

        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), punishmentEndTime, durationForLog);
        sendConfigMessage(sender, "messages.direct_punishment_confirmed", "{target}", target.getName(), "{time}", durationForLog, "{reason}", reason, "{punishment_type}", punishType);

        if (!commandToExecute.isEmpty()) {
            final String finalCommandToExecute = commandToExecute;
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
        }

        MenuListener menuListener = plugin.getMenuListener();
        if (menuListener != null) {
            menuListener.executeHookActions(sender, target, punishType, durationForLog, reason, false);
        } else {
            plugin.getLogger().warning("MenuListener instance is null, cannot execute punishment hooks.");
        }
    }

    private void confirmDirectUnpunish(final CommandSender sender, final OfflinePlayer target, final String punishType) {
        String commandToExecute = "";
        String logReason = "Unpunished";

        switch (punishType.toLowerCase()) {
            case "ban":
                commandToExecute = plugin.getConfigManager().getUnbanCommand();
                if (commandToExecute.isEmpty()) {
                    if (!target.isBanned()) {
                        sendConfigMessage(sender, "messages.not_banned");
                        return;
                    }
                    Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
                } else {
                    commandToExecute = commandToExecute.replace("{target}", target.getName());
                }
                logReason = "Unbanned";
                break;
            case "mute":
                commandToExecute = plugin.getConfigManager().getUnmuteCommand();
                if (commandToExecute.isEmpty()) {
                    if (!plugin.getSoftBanDatabaseManager().isMuted(target.getUniqueId())) {
                        sendConfigMessage(sender, "messages.not_muted");
                        return;
                    }
                    plugin.getSoftBanDatabaseManager().unmutePlayer(target.getUniqueId(), sender.getName());
                } else {
                    commandToExecute = commandToExecute.replace("{target}", target.getName());
                }
                logReason = "Unmuted";
                break;
            case "softban":
                plugin.getSoftBanDatabaseManager().unSoftBanPlayer(target.getUniqueId(), sender.getName());
                logReason = "Un-softbanned";
                break;
            case "warn":
                String unwarnCommand = plugin.getConfigManager().getUnwarnCommand();
                if (unwarnCommand != null && !unwarnCommand.isEmpty()) {
                    commandToExecute = unwarnCommand.replace("{target}", target.getName());
                } else {
                    plugin.getLogger().warning("Unwarn command is empty, internal unwarn is not supported.");
                }
                logReason = "Unwarned";
                break;
            case "freeze":
                boolean removed = plugin.getPluginFrozenPlayers().remove(target.getUniqueId()) != null;
                if (!removed) {
                    sendConfigMessage(sender, "messages.no_active_freeze", "{target}", target.getName());
                    return;
                }
                logReason = "Unfrozen";
                Player onlineTargetUnfreeze = target.getPlayer();
                if (onlineTargetUnfreeze != null) {
                    sendConfigMessage(onlineTargetUnfreeze, "messages.you_are_unfrozen");
                    plugin.getFreezeListener().stopFreezeActionsTask(target.getUniqueId());
                }
                break;
            case "kick":
                sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType);
                return;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
                return;
        }

        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "un" + punishType, logReason, sender.getName(), 0L, "N/A");
        sendConfigMessage(sender, "messages.direct_unpunishment_confirmed", "{target}", target.getName(), "{punishment_type}", punishType);

        if (!commandToExecute.isEmpty()) {
            final String finalCommandToExecute = commandToExecute;
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
        }

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

        if (alias.equalsIgnoreCase("crown")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList(PUNISH_SUBCOMMAND, UNPUNISH_SUBCOMMAND, HELP_SUBCOMMAND, RELOAD_SUBCOMMAND), completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) {
                StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
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
                StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) {
                StringUtil.copyPartialMatches(args[2], UNPUNISHMENT_TYPES, completions);
            }
        }

        if (alias.equalsIgnoreCase("punish")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
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

        if (alias.equalsIgnoreCase("unpunish")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], UNPUNISHMENT_TYPES, completions);
            }
        }

        if (alias.equalsIgnoreCase("softban")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
            } else if (args.length >= 3) {
                completions.add("reason here...");
            }
        }

        if (alias.equalsIgnoreCase("freeze")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length >= 2) {
                completions.add("reason here...");
            }
        }

        Collections.sort(completions);
        return completions;
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
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
        }
    }
}
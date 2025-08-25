// MainCommand.java
package cp.corona.commands;

import cp.corona.crown.Crown;
import cp.corona.listeners.MenuListener; // Import MenuListener
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.PunishMenu;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ////////////////////////////////////////////////
 * //             Crown             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Handles the main command and subcommands for the Crown plugin.
 * Implements CommandExecutor and TabCompleter for command handling and tab completion,
 * treating /crown, /punish, /softban, and /unpunish as COMPLETELY SEPARATE top-level commands.
 * This version includes robust input validation for time arguments and a correction for the softban time calculation bug.
 * Top-level /softban and /unpunish commands are now fully separate, and /punish mirrors /crown punish.
 *
 * **NEW:** Added handling for the "freeze" punishment type.
 * **MODIFIED:** Integrated calls to execute post-action hooks after punishment/unpunishment.
 * **MODIFIED:** Added a global 'crown.use' permission check at the beginning of onCommand and onTabComplete.
 *              If the sender lacks this permission, no command execution or tab suggestions will be provided
 *              for any of the plugin's commands.
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    private final Crown plugin;

    // Command and subcommand names constants for maintainability
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String PUNISH_SUBCOMMAND = "punish";
    private static final String UNPUNISH_SUBCOMMAND = "unpunish";
    private static final String HELP_SUBCOMMAND = "help";
    private static final String SOFTBAN_COMMAND_ALIAS = "softban"; // Constant for softban command alias
    private static final String FREEZE_COMMAND_ALIAS = "freeze"; // Constant for freeze command alias - NEW
    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final String USE_PERMISSION = "crown.use"; // Base permission for all plugin commands
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


    /**
     * Constructor for MainCommand.
     * @param plugin Instance of the main plugin class.
     */
    public MainCommand(Crown plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes commands when players type them in-game.
     * **NEW:** A global 'crown.use' permission check is performed at the beginning.
     *          If the sender lacks this permission, a "no permission" message is sent,
     *          and no further command processing occurs for any plugin command.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        //
        // Global Permission Check: 'crown.use'
        // --------------------------------------------------------------------
        // If the sender does not have the base 'crown.use' permission,
        // they are denied access to ALL commands handled by this plugin.
        // A generic "no permission" message is sent, and the command is
        // considered handled (by being denied). This prevents leaking command
        // existence or functionality to unauthorized users.
        // --------------------------------------------------------------------
        if (!sender.hasPermission(USE_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission_command"); // Generic no permission message
            return true; // Command handled (by denying access)
        }

        // Handling for /crown command and its subcommands
        if (alias.equalsIgnoreCase("crown")) {
            // No USE_PERMISSION check needed here, already done globally
            if (args.length == 0) { // /crown with no arguments: show help
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
                    help(sender); // Show help for invalid subcommands
                    return true;
            }
        }

        // Handling for /punish command (alias for /crown punish)
        if (alias.equalsIgnoreCase("punish")) {
            // No USE_PERMISSION check needed here
            return handlePunishCommand(sender, args);
        }

        // Handling for /unpunish command as a SEPARATE top-level command
        if (alias.equalsIgnoreCase("unpunish")) {
            // No USE_PERMISSION check needed here
            return handleUnpunishCommand(sender, args);
        }

        // Handling for /softban command as a SEPARATE top-level command
        if (alias.equalsIgnoreCase("softban")) {
            // No USE_PERMISSION check needed here
            return handleSoftbanCommand(sender, args);
        }

        // Handling for /freeze command as a SEPARATE top-level command
        if (alias.equalsIgnoreCase("freeze")) {
            // No USE_PERMISSION check needed here
            return handleFreezeCommand(sender, args);
        }

        return false; // Command or alias not handled by this executor
    }


    /**
     * Handles the reload subcommand to reload plugin configurations.
     * Checks for admin permission before reloading.
     *
     * @param sender CommandSender who sent the command.
     * @return true if the command was handled successfully.
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) { // Specific permission for reload
            sendConfigMessage(sender, "messages.no_permission"); // Use specific 'no_permission' as this is for an admin action
            return true;
        }
        plugin.getConfigManager().loadConfig();
        sendConfigMessage(sender, "messages.reload_success");
        return true;
    }

    /**
     * Handles the punish subcommand, opening the punishment menu or executing direct punishment.
     * Accessible via /crown punish ... or /punish ... (alias).
     * The base 'crown.use' permission is already checked before this method is called.
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments.
     * @return true if the command was handled successfully.
     */
    private boolean handlePunishCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length < 2) { // Console needs at least <target> <type> for direct punishment
                sendConfigMessage(sender, "messages.player_only_console_punish");
                return true; // Changed to true as message is sent
            }
        }
        // Note: The global USE_PERMISSION check handles general access.
        // Specific permissions for punish types are checked deeper.

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
                sendConfigMessage(sender, "messages.player_only"); // Console needs more args to open menu (not possible)
                return true;
            }
            // Permission for punish menu itself is covered by global USE_PERMISSION.
            // Specific punish type permissions are checked when opening details or direct punishing.
            new PunishMenu(target.getUniqueId(), plugin).open((Player) sender);
        } else if (args.length >= 2) {
            String punishType = args[1].toLowerCase();
            if (!PUNISHMENT_TYPES.contains(punishType)) {
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return true;
            }

            if (args.length == 2) { // Open details menu
                if (!(sender instanceof Player)) {
                    sendConfigMessage(sender, "messages.player_only");
                    return true;
                }
                // Check permission to open this specific details menu
                if (!checkPunishDetailsPermission(sender, punishType)) {
                    sendNoPermissionDetailsMessage(sender, punishType);
                    return true;
                }
                new PunishDetailsMenu(target.getUniqueId(), plugin, punishType).open((Player) sender);
            } else { // Direct punishment: args.length >= 3
                // Check permission for direct command usage of this punishment type
                if (!checkPunishCommandPermission(sender, punishType)) {
                    sendNoPermissionCommandMessage(sender, punishType);
                    return true;
                }
                // Time is arg 2 for ban, mute, softban. Reason starts after time or after type.
                String timeForPunishment = "permanent"; // Default
                String reason;

                if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) {
                    if (args.length < 3) { // <target> <type> needs <time>
                        sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/" + (sender instanceof Player ? "punish" : "crown punish") + " " + targetName + " " + punishType + " <time> [reason]");
                        return true;
                    }
                    timeForPunishment = args[2];
                    reason = (args.length > 3) ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "No reason specified.";
                } else { // kick, warn, freeze
                    reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason specified.";
                }

                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Direct punishment confirmed for " + target.getName() + ", type: " + punishType);
                confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason);
            }
        }
        return true;
    }

    /**
     * Handles the unpunish command, removing a punishment from a player.
     * The base 'crown.use' permission is already checked.
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments.
     * @return true if the command was handled successfully.
     */
    private boolean handleUnpunishCommand(CommandSender sender, String[] args) {
        // Base USE_PERMISSION already checked globally.

        if (args.length == 0) {
            help(sender);
            return true;
        }

        if (args.length < 2) { // /unpunish <player> <type> is minimum required
            String commandLabel = (sender instanceof Player) ? "unpunish" : "crown unpunish"; // Adjust based on context
            sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/" + commandLabel + " <player> <type>");
            return true;
        }

        String targetName = args[0];
        String punishType = args[1].toLowerCase();

        if (!UNPUNISHMENT_TYPES.contains(punishType)) {
            sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
            return true;
        }

        // Check permission for this specific unpunish type
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

    /**
     * Handles the softban command, directly executing a softban punishment.
     * The base 'crown.use' permission is already checked.
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments for /softban (player, time, reason...).
     * @return true if the command was handled successfully.
     */
    private boolean handleSoftbanCommand(CommandSender sender, String[] args) {
        // Base USE_PERMISSION already checked. Now check specific softban permission.
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

        // Bypass check: specific to softban
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
                return true; // Changed to true as message is sent
            }
        }
        if (args.length >= 3) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        confirmDirectPunishment(sender, target, SOFTBAN_COMMAND_ALIAS, time, reason);
        return true;
    }

    /**
     * Handles the freeze command, directly executing a freeze punishment.
     * The base 'crown.use' permission is already checked.
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments for /freeze (player, reason...).
     * @return true if the command was handled successfully.
     */
    private boolean handleFreezeCommand(CommandSender sender, String[] args) {
        // Base USE_PERMISSION already checked. Now check specific freeze permission.
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

        // Bypass check: specific to freeze
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


    /**
     * Confirms and executes a direct punishment command (ban, mute, softban, kick, warn, freeze).
     * Includes bypass checks.
     * **MODIFIED:** Executes post-punishment hooks after completion.
     *
     * @param sender Command sender.
     * @param target Target player.
     * @param punishType Type of punishment (lowercase).
     * @param time Punishment time string.
     * @param reason Punishment reason.
     */
    private void confirmDirectPunishment(final CommandSender sender, final OfflinePlayer target, final String punishType, final String time, final String reason) {
        // Bypass check moved here - MODIFIED
        if (target instanceof Player) {
            Player playerTarget = (Player) target;
            // Combine permission check with bypass check for clarity
            // The sender's permission for the specific punishType is already checked by the handle<PunishType>Command methods
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
        boolean handledInternally = false;
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

        switch (punishType.toLowerCase()) {
            case "ban":
                commandToExecute = plugin.getConfigManager().getBanCommand()
                        .replace("{target}", target.getName())
                        .replace("{time}", time)
                        .replace("{reason}", reason);
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay)) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = permanentDisplay;
                }
                break;
            case "mute":
                commandToExecute = plugin.getConfigManager().getMuteCommand()
                        .replace("{target}", target.getName())
                        .replace("{time}", time)
                        .replace("{reason}", reason);
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay)) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = permanentDisplay;
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
                sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                        "{target}", target.getName(),
                        "{time}", durationForLog,
                        "{reason}", reason,
                        "{punishment_type}", punishType);
                handledInternally = true;
                break;
            case "kick":
                commandToExecute = plugin.getConfigManager().getKickCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "N/A";
                punishmentEndTime = 0L;
                break;
            case "warn":
                commandToExecute = plugin.getConfigManager().getWarnCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "N/A";
                punishmentEndTime = 0L;
                break;
            case "freeze":
                plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true);
                durationForLog = permanentDisplay;
                punishmentEndTime = Long.MAX_VALUE;
                sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                        "{target}", target.getName(),
                        "{time}", durationForLog,
                        "{reason}", reason,
                        "{punishment_type}", punishType);
                plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), punishmentEndTime, durationForLog);
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null && !onlineTarget.hasPermission("crown.bypass.freeze")) {
                    sendConfigMessage(onlineTarget, "messages.you_are_frozen");
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Starting FreezeActionsTask for player " + onlineTarget.getName() + " after direct freeze command.");
                    plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
                }
                handledInternally = true;
                break;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return;
        }

        if (!handledInternally && !commandToExecute.isEmpty()) {
            final String finalCommandToExecute = commandToExecute;
            final long finalPunishmentEndTime = punishmentEndTime;
            final String finalDurationForLog = durationForLog;

            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
            sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                    "{target}", target.getName(),
                    "{time}", finalDurationForLog,
                    "{reason}", reason,
                    "{punishment_type}", punishType);
            plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), finalPunishmentEndTime, finalDurationForLog);
        } else if (!handledInternally) {
            if (commandToExecute.isEmpty() && !(punishType.equalsIgnoreCase("softban") || punishType.equalsIgnoreCase("freeze"))) {
                plugin.getLogger().warning("Punishment execution path error for type: " + punishType + " - No command and not handled internally.");
                return;
            }
        }

        MenuListener menuListener = plugin.getMenuListener();
        if (menuListener != null) {
            menuListener.executeHookActions(sender, target, punishType, durationForLog, reason, false);
        } else {
            plugin.getLogger().warning("MenuListener instance is null, cannot execute punishment hooks.");
        }
    }


    /**
     * Confirms and executes a direct unpunish command (unban, unmute, unsoftban, unwarn, unfreeze).
     * **MODIFIED:** Executes post-unpunishment hooks after completion.
     *
     * @param sender Command sender.
     * @param target Target player.
     * @param punishType Type of punishment to remove (lowercase).
     */
    private void confirmDirectUnpunish(final CommandSender sender, final OfflinePlayer target, final String punishType) {
        String commandToExecute = "";
        boolean handledInternally = false;
        String logReason = "Unpunished";

        switch (punishType.toLowerCase()) {
            case "ban":
                commandToExecute = plugin.getConfigManager().getUnbanCommand().replace("{target}", target.getName());
                logReason = "Unbanned";
                break;
            case "mute":
                commandToExecute = plugin.getConfigManager().getUnmuteCommand().replace("{target}", target.getName());
                logReason = "Unmuted";
                break;
            case "softban":
                plugin.getSoftBanDatabaseManager().unSoftBanPlayer(target.getUniqueId(), sender.getName());
                sendConfigMessage(sender, "messages.direct_unsoftban_confirmed", "{target}", target.getName());
                handledInternally = true;
                logReason = "Un-softbanned";
                break;
            case "warn":
                String unwarnCommand = plugin.getConfigManager().getUnwarnCommand();
                if (unwarnCommand != null && !unwarnCommand.isEmpty()) {
                    commandToExecute = unwarnCommand.replace("{target}", target.getName());
                    logReason = "Unwarned";
                } else {
                    sendConfigMessage(sender, "messages.unpunish_command_not_configured", "{punishment_type}", punishType);
                    return;
                }
                break;
            case "freeze":
                boolean removed = plugin.getPluginFrozenPlayers().remove(target.getUniqueId()) != null;
                if (!removed) {
                    sendConfigMessage(sender, "messages.no_active_freeze", "{target}", target.getName());
                    return;
                }
                logReason = "Unfrozen";
                sendConfigMessage(sender, "messages.direct_unfreeze_confirmed", "{target}", target.getName());
                Player onlineTargetUnfreeze = target.getPlayer();
                if (onlineTargetUnfreeze != null) { sendConfigMessage(onlineTargetUnfreeze, "messages.you_are_unfrozen"); }
                plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "unfreeze", logReason, sender.getName(), 0L, "N/A");
                handledInternally = true;
                if (onlineTargetUnfreeze != null) {
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

        if (!handledInternally && !commandToExecute.isEmpty()) {
            final String finalCommandToExecute = commandToExecute;
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
            sendConfigMessage(sender, "messages.direct_unpunishment_confirmed",
                    "{target}", target.getName(),
                    "{punishment_type}", punishType);
            plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "un" + punishType, logReason, sender.getName(), 0L, "N/A");
        } else if (!handledInternally) {
            if (commandToExecute.isEmpty() && !(punishType.equalsIgnoreCase("softban") || punishType.equalsIgnoreCase("freeze"))) {
                plugin.getLogger().warning("Unpunishment execution path error for type: " + punishType + " - No command and not handled internally.");
                return;
            }
        }

        MenuListener menuListener = plugin.getMenuListener();
        if (menuListener != null) {
            menuListener.executeHookActions(sender, target, punishType, "N/A", logReason, true);
        } else {
            plugin.getLogger().warning("MenuListener instance is null, cannot execute unpunishment hooks.");
        }
    }


    /**
     * Sends a message from the configuration to the command sender, with optional replacements.
     * @param sender Command sender.
     * @param path Path to the message in messages.yml.
     * @param replacements Placeholders to replace in the message.
     */
    private void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        String message = plugin.getConfigManager().getMessage(path, replacements);
        sender.sendMessage(MessageUtils.getColorMessage(message));
    }

    /**
     * Provides tab completion options for commands.
     * **NEW:** If the sender lacks the 'crown.use' permission, no suggestions are provided.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        //
        // Global Permission Check for Tab Completion
        // --------------------------------------------------------------------
        // If the sender does not have the base 'crown.use' permission,
        // do not provide any tab completion suggestions for any plugin commands.
        // This prevents leaking command structure or subcommands.
        // --------------------------------------------------------------------
        if (!sender.hasPermission(USE_PERMISSION)) {
            return Collections.emptyList(); // No suggestions
        }

        List<String> completions = new ArrayList<>();

        // Tab completion for /crown command and its subcommands
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

        // Tab completion for /punish command (alias)
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

        // Tab completion for /unpunish command
        if (alias.equalsIgnoreCase("unpunish")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], UNPUNISHMENT_TYPES, completions);
            }
        }

        // Tab completion for /softban command
        if (alias.equalsIgnoreCase("softban")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
            } else if (args.length >= 3) {
                completions.add("reason here...");
            }
        }

        // Tab completion for /freeze command
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

    /**
     * Checks if the sender has permission to access punish details menu for a specific punishment type.
     * The base 'crown.use' permission is implicitly required to reach this point.
     * @param sender Command sender.
     * @param punishType Punishment type.
     * @return true if has permission, false otherwise.
     */
    private boolean checkPunishDetailsPermission(CommandSender sender, String punishType) {
        // The global USE_PERMISSION check in onCommand already covers base access.
        // This method checks for the *specific* type permission.
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

    /**
     * Sends a no permission message for unpunish command based on punishment type.
     * @param sender Command sender.
     * @param punishType Punishment type.
     */
    private void sendNoPermissionUnpunishMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_unpunish_command_type", "{punishment_type}", punishType);
    }

    /**
     * Sends a no permission message for direct punish command based on punishment type.
     * @param sender Command sender.
     * @param punishType Punishment type.
     */
    private void sendNoPermissionCommandMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_punish_command_type", "{punishment_type}", punishType);
    }

    /**
     * Checks if the sender has permission to use unpunish command for a specific punishment type.
     * The base 'crown.use' permission is implicitly required.
     * @param sender CommandSender.
     * @param punishType Punishment type.
     * @return true if has permission, false otherwise.
     */
    private boolean checkUnpunishPermission(CommandSender sender, String punishType) {
        // Base USE_PERMISSION is already handled globally.
        switch (punishType.toLowerCase()) {
            case "ban": return sender.hasPermission(UNPUNISH_BAN_PERMISSION);
            case "mute": return sender.hasPermission(UNPUNISH_MUTE_PERMISSION);
            case "softban": return sender.hasPermission(UNPUNISH_SOFTBAN_PERMISSION);
            case "warn": return sender.hasPermission(UNPUNISH_WARN_PERMISSION);
            case "freeze": return sender.hasPermission(UNPUNISH_FREEZE_PERMISSION);
            default: return false;
        }
    }

    /**
     * Sends a no permission message for punish details menu based on punishment type.
     * @param sender Command sender.
     * @param punishType Punishment type.
     */
    private void sendNoPermissionDetailsMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_details_menu", "{punishment_type}", punishType);
    }


    /**
     * Checks if the sender has permission to use direct punish command for a specific punishment type.
     * The base 'crown.use' permission is implicitly required.
     * @param sender CommandSender.
     * @param punishType Punishment type.
     * @return true if has permission, false otherwise.
     */
    private boolean checkPunishCommandPermission(CommandSender sender, String punishType) {
        // Base USE_PERMISSION is already handled globally.
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

    /**
     * Sends the help message to the command sender.
     * Retrieves help messages from messages.yml for customization and sends them to the sender.
     *
     * @param sender CommandSender to send the help message to.
     */
    private void help(CommandSender sender) {
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_header")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish_extended")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish_alias")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish_alias")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_softban_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_freeze_command")));
        // Only show reload help if they have admin permission
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
        }
    }
}
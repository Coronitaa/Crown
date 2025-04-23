// MainCommand.java
package cp.corona.commands;

import cp.corona.crownpunishments.CrownPunishments;
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
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Handles the main command and subcommands for the CrownPunishments plugin.
 * Implements CommandExecutor and TabCompleter for command handling and tab completion,
 * treating /crown, /punish, /softban, and /unpunish as COMPLETELY SEPARATE top-level commands.
 * This version includes robust input validation for time arguments and a correction for the softban time calculation bug.
 * Top-level /softban and /unpunish commands are now fully separate, and /punish mirrors /crown punish.
 *
 * **NEW:** Added handling for the "freeze" punishment type.
 * **MODIFIED:** Integrated calls to execute post-action hooks after punishment/unpunishment.
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    private final CrownPunishments plugin;

    // Command and subcommand names constants for maintainability
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String PUNISH_SUBCOMMAND = "punish";
    private static final String UNPUNISH_SUBCOMMAND = "unpunish";
    private static final String HELP_SUBCOMMAND = "help";
    private static final String SOFTBAN_COMMAND_ALIAS = "softban"; // Constant for softban command alias
    private static final String FREEZE_COMMAND_ALIAS = "freeze"; // Constant for freeze command alias - NEW
    private static final String ADMIN_PERMISSION = "crown.admin"; // ADDED ADMIN_PERMISSION constant here
    private static final String USE_PERMISSION = "crown.use"; // Replaced PUNISH_PERMISSION with USE_PERMISSION - MODIFIED
    private static final String PUNISH_BAN_PERMISSION = "crown.punish.ban"; // Permission for ban related actions
    private static final String UNPUNISH_BAN_PERMISSION = "crown.unpunish.ban"; // Permission for unban related actions
    private static final String PUNISH_MUTE_PERMISSION = "crown.punish.mute"; // Permission for mute related actions
    private static final String UNPUNISH_MUTE_PERMISSION = "crown.unpunish.mute"; // Permission for unmute related actions
    private static final String UNPUNISH_WARN_PERMISSION = "crown.unpunish.warn"; // Permission for unmute related actions
    private static final String PUNISH_SOFTBAN_PERMISSION = "crown.punish.softban"; // Permission for softban related actions
    private static final String UNPUNISH_SOFTBAN_PERMISSION = "crown.unpunish.softban"; // Permission for unsoftban related actions
    private static final String PUNISH_KICK_PERMISSION = "crown.punish.kick"; // Permission for kick related actions
    private static final String PUNISH_WARN_PERMISSION = "crown.punish.warn"; // Permission for warn related actions
    private static final String PUNISH_FREEZE_PERMISSION = "crown.punish.freeze"; // Permission for freeze related actions - NEW
    private static final String UNPUNISH_FREEZE_PERMISSION = "crown.unpunish.freeze"; // Permission for unfreeze related actions - NEW
    private static final List<String> PUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "kick", "warn", "freeze"); // Registered punishment types - ADDED FREEZE
    private static final List<String> UNPUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "warn", "freeze"); // Registered unpunishment types, including warn and freeze - ADDED FREEZE


    /**
     * Constructor for MainCommand.
     * @param plugin Instance of the main plugin class.
     */
    public MainCommand(CrownPunishments plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes commands when players type them in-game, handling:
     * - /crown base command and its subcommands (punish, reload, help, unpunish).
     * - /punish alias command to /crown punish subcommand.
     * - /softban top-level command, now with its own dedicated handler and argument validation.
     * - /unpunish top-level command, now with its own dedicated handler and correct tab completion.
     * - /freeze top-level command, new handler for the freeze punishment. - NEW
     * Each command is handled in a separate block for clarity and to avoid complex argument processing.
     * Top-level /softban and /unpunish commands are now treated separately, and /punish is a direct alias for /crown punish.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Handling for /crown command and its subcommands
        if (alias.equalsIgnoreCase("crown")) {
            if (!sender.hasPermission(USE_PERMISSION)) { // Replaced permission check with USE_PERMISSION - MODIFIED
                sendConfigMessage(sender, "messages.no_permission_command"); // Generic no permission message
                return true;
            }
            if (args.length == 0) { // /crown with no arguments: show help
                help(sender);
                return true;
            }

            String subcommand = args[0].toLowerCase();
            switch (subcommand) {
                case RELOAD_SUBCOMMAND:
                    return handleReloadCommand(sender);
                case PUNISH_SUBCOMMAND:
                    return handlePunishCommand(sender, Arrays.copyOfRange(args, 1, args.length)); // Pass arguments without the subcommand
                case UNPUNISH_SUBCOMMAND:
                    return handleUnpunishCommand(sender, Arrays.copyOfRange(args, 1, args.length)); // Pass arguments without the subcommand
                case HELP_SUBCOMMAND:
                    help(sender);
                    return true;
                default:
                    help(sender); // Show help for invalid subcommands
                    return true;
            }
        }

        // Handling for /punish command (alias for /crown punish) - SEPARATE HANDLING BLOCK
        if (alias.equalsIgnoreCase("punish")) {
            if (!sender.hasPermission(USE_PERMISSION)) { // Replaced permission check with USE_PERMISSION - MODIFIED
                sendConfigMessage(sender, "messages.no_permission_command"); // Generic no permission message
                return true;
            }
            // Directly call handlePunishCommand, treating /punish as if it was /crown punish
            return handlePunishCommand(sender, args); // Pass all arguments directly to handlePunishCommand for /punish
        }

        // Handling for /unpunish command as a SEPARATE top-level command - SEPARATE HANDLING BLOCK
        if (alias.equalsIgnoreCase("unpunish")) {
            if (!sender.hasPermission(USE_PERMISSION)) { // Replaced permission check with USE_PERMISSION - MODIFIED
                sendConfigMessage(sender, "messages.no_permission_command"); // Generic no permission message
                return true;
            }
            return handleUnpunishCommand(sender, args); // Directly handle /unpunish command
        }

        // Handling for /softban command as a SEPARATE top-level command - SEPARATE HANDLING BLOCK
        if (alias.equalsIgnoreCase("softban")) {
            if (!sender.hasPermission(USE_PERMISSION)) { // Replaced permission check with USE_PERMISSION - MODIFIED
                sendConfigMessage(sender, "messages.no_permission_command"); // Generic no permission message
                return true;
            }
            return handleSoftbanCommand(sender, args); // Call dedicated handleSoftbanCommand for /softban
        }

        // Handling for /freeze command as a SEPARATE top-level command - SEPARATE HANDLING BLOCK - NEW
        if (alias.equalsIgnoreCase("freeze")) {
            if (!sender.hasPermission(USE_PERMISSION)) { // Replaced permission check with USE_PERMISSION - MODIFIED
                sendConfigMessage(sender, "messages.no_permission_command"); // Generic no permission message
                return true;
            }
            return handleFreezeCommand(sender, args); // Call dedicated handleFreezeCommand for /freeze
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
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission");
            return true;
        }
        plugin.getConfigManager().loadConfig();
        sendConfigMessage(sender, "messages.reload_success");
        return true;
    }

    /**
     * Handles the punish subcommand, opening the punishment menu or executing direct punishment.
     * Accessible via /crown punish ... or /punish ... (alias).
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments.
     * @return true if the command was handled successfully.
     */
    private boolean handlePunishCommand(CommandSender sender, String[] args) {
        // Ensure the command sender is a player if not providing enough arguments for console direct punishment.
        if (!(sender instanceof Player)) {
            if (args.length < 2) {
                sendConfigMessage(sender, "messages.player_only_console_punish");
                return false;
            }
        }

        // Check if the sender has general punish permission to use punish features.
        if (!sender.hasPermission(USE_PERMISSION)) { // Replaced permission check with USE_PERMISSION - MODIFIED
            sendConfigMessage(sender, "messages.no_permission_punish_menu"); // Specific permission message for punish menu
            return true;
        }

        // Show help if no arguments are provided after /crown punish or /punish.
        if (args.length == 0) { // /crown punish or /punish with no arguments: show help
            help(sender);
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        // Check if the target player has ever played before.
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        // Bypass checks are now handled in confirmDirectPunishment to allow menu access - MODIFIED (Bypass checks removed here)


        // Handle different argument lengths for punish command.
        if (args.length == 1) { // /crown punish <target> or /punish <target>: Open main menu
            if (!(sender instanceof Player)) {
                sendConfigMessage(sender, "messages.player_only");
                return true;
            }
            new PunishMenu(target.getUniqueId(), plugin).open((Player) sender); // /crown punish <target> - Open main menu
        } else if (args.length >= 2) {
            String punishType = args[1].toLowerCase();
            if (!PUNISHMENT_TYPES.contains(punishType)) {
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return true;
            }
            // Permission check for each punishment type DETAIL MENU access - NEW: Permission check before opening details menu
            if (!checkPunishDetailsPermission(sender, punishType)) {
                sendNoPermissionDetailsMessage(sender, punishType); // Send specific no permission message
                return true;
            }

            if (args.length == 2) { // /crown punish <target> <type> or /punish <target> <type>: Open details menu
                if (!(sender instanceof Player)) {
                    sendConfigMessage(sender, "messages.player_only");
                    return true;
                }
                new PunishDetailsMenu(target.getUniqueId(), plugin, punishType).open((Player) sender); // /crown punish <target> <type> - Open details menu
            } else if (args.length >= 3) { // Reason is always from arg 3 onwards, time might be arg 2 for ban, mute, softban, or not present for kick, warn, freeze
                // Permission check for direct punishment command usage - NEW: Permission check before direct punishment
                if (!checkPunishCommandPermission(sender, punishType)) {
                    sendNoPermissionCommandMessage(sender, punishType); // Send specific no permission message for direct command
                    return true;
                }

                String reason = String.join(" ", Arrays.copyOfRange(args, (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? 3 : 2, args.length)); // /crown punish <target> <type> <time> <reason...> or /crown punish <target> <type> <reason...>
                String timeForPunishment = (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? args[2] : "permanent"; // Time is arg 2 for ban, mute, softban, or permanent for kick/warn/freeze if not specified in direct command
                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Direct punishment confirmed for " + target.getName() + ", type: " + punishType); // Debug log for direct punishment
                confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason);
            }
        }
        return true;
    }

    /**
     * Handles the unpunish command, removing a punishment from a player.
     * Accessible via both /crown unpunish ... and /unpunish ... as SEPARATE commands.
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments.
     * @return true if the command was handled successfully.
     */
    private boolean handleUnpunishCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("crown.use")) { // Check for base crown.use permission first
            sendConfigMessage(sender, "messages.no_permission_command"); // Generic no permission message
            return true;
        }

        if (args.length == 0) { // Only /crown unpunish -> help message (consider specific help)
            help(sender); // Reusing main help, consider specific /crown unpunish help
            return true;
        }

        if (args.length < 2) { // /crown unpunish <player> <type> is minimum required
            sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/crown unpunish <player> <type>");
            return true;
        }

        String targetName = args[0];
        String punishType = args[1].toLowerCase();

        if (!UNPUNISHMENT_TYPES.contains(punishType)) { // Validate against UNPUNISHMENT_TYPES
            sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
            return true;
        }

        // Permission check for each unpunishment type - NEW: Permission check for unpunish command
        if (!checkUnpunishPermission(sender, punishType)) {
            sendNoPermissionUnpunishMessage(sender, punishType); // Send specific no permission message for unpunish
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
     * This is a SEPARATE handler specifically for the top-level /softban command.
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments for /softban (player, time, reason...).
     * @return true if the command was handled successfully.
     */
    private boolean handleSoftbanCommand(CommandSender sender, String[] args) {
        // Check if the sender has the necessary permission to perform softbans.
        if (!sender.hasPermission(PUNISH_SOFTBAN_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission");
            return true;
        }

        // Ensure there is at least a target player specified.
        if (args.length < 1) {
            sendConfigMessage(sender, "messages.softban_usage", "{usage}", "/softban <player> [time] [reason]"); // Ensure you have a message for softban_usage in messages.yml
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        // Check if the target player has ever played on the server.
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        // Bypass check: if the target player has 'crown.bypass.softban' permission, do not softban.
        if (target instanceof Player && ((Player) target).hasPermission("crown.bypass.softban")) {
            sendConfigMessage(sender, "messages.bypass_error_softban", "{target}", targetName); // Inform the sender about the bypass.
            return true; // Stop command execution.
        }

        String time = "permanent"; // Default time to permanent if not specified
        String reason = "Softbanned by moderator"; // Default reason if not specified

        if (args.length >= 2) {
            time = args[1];
            // [NEW] Validate time format for /softban command
            if (TimeUtils.parseTime(time, plugin.getConfigManager()) == 0 && !time.equalsIgnoreCase("permanent") && !time.equalsIgnoreCase(plugin.getConfigManager().getMessage("placeholders.permanent_time_display"))) { // Check against display name too
                sendConfigMessage(sender, "messages.invalid_time_format_command", "{input}", time); // Specific message for invalid time format in commands
                return false; // Stop command execution if time format is invalid
            }
        }
        if (args.length >= 3) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        confirmDirectPunishment(sender, target, SOFTBAN_COMMAND_ALIAS, time, reason); // Call confirmDirectPunishment with SOFTBAN_COMMAND_ALIAS and parsed args
        return true;
    }

    /**
     * Handles the freeze command, directly executing a freeze punishment. - NEW
     * This is a SEPARATE handler specifically for the top-level /freeze command.
     *
     * @param sender CommandSender who sent the command.
     * @param args Command arguments for /freeze (player, reason...).
     * @return true if the command was handled successfully.
     */
    private boolean handleFreezeCommand(CommandSender sender, String[] args) {
        // Check if the sender has permission to use the freeze command.
        if (!sender.hasPermission(PUNISH_FREEZE_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission");
            return true;
        }

        // Ensure a target player is specified.
        if (args.length < 1) {
            sendConfigMessage(sender, "messages.freeze_usage", "{usage}", "/freeze <player> [reason]"); // Ensure you have a message for freeze_usage in messages.yml
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        // Check if the target player has ever played before.
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        // Bypass check: prevent freezing players with 'crown.bypass.freeze' permission.
        if (target instanceof Player && ((Player) target).hasPermission("crown.bypass.freeze")) {
            sendConfigMessage(sender, "messages.bypass_error_freeze", "{target}", targetName); // Inform the sender about the bypass.
            return true; // Stop command execution.
        }

        // Check if player is already frozen - NEW: Check before applying freeze via command
        if (plugin.getPluginFrozenPlayers().containsKey(target.getUniqueId())) {
            sendConfigMessage(sender, "messages.already_frozen", "{target}", targetName); // Send message if already frozen - NEW
            return true; // Prevent further command execution - NEW
        }

        String reason = "Frozen by moderator"; // Default reason if not specified
        if (args.length >= 2) {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); // Get reason from arguments if provided
        }

        confirmDirectPunishment(sender, target, FREEZE_COMMAND_ALIAS, "permanent", reason); // Call confirmDirectPunishment with FREEZE_COMMAND_ALIAS and default reason
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
            if (punishType.equalsIgnoreCase("softban") && playerTarget.hasPermission("crown.bypass.softban") && sender.hasPermission(PUNISH_SOFTBAN_PERMISSION)) {
                sendConfigMessage(sender, "messages.bypass_error_softban", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("freeze") && playerTarget.hasPermission("crown.bypass.freeze") && sender.hasPermission(PUNISH_FREEZE_PERMISSION)) {
                sendConfigMessage(sender, "messages.bypass_error_freeze", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("ban") && playerTarget.hasPermission("crown.bypass.ban") && sender.hasPermission(PUNISH_BAN_PERMISSION)) {
                sendConfigMessage(sender, "messages.bypass_error_ban", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("mute") && playerTarget.hasPermission("crown.bypass.mute") && sender.hasPermission(PUNISH_MUTE_PERMISSION)) {
                sendConfigMessage(sender, "messages.bypass_error_mute", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("kick") && playerTarget.hasPermission("crown.bypass.kick") && sender.hasPermission(PUNISH_KICK_PERMISSION)) {
                sendConfigMessage(sender, "messages.bypass_error_kick", "{target}", target.getName()); return;
            }
            if (punishType.equalsIgnoreCase("warn") && playerTarget.hasPermission("crown.bypass.warn") && sender.hasPermission(PUNISH_WARN_PERMISSION)) {
                sendConfigMessage(sender, "messages.bypass_error_warn", "{target}", target.getName()); return;
            }
        }

        String commandToExecute = "";
        long punishmentEndTime = 0L; // Default punishment end time for logging
        String durationForLog = time; // Store duration string for logging
        boolean handledInternally = false; // Flag to check if handled by plugin (softban/freeze)
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display"); // Cache permanent display string

        switch (punishType.toLowerCase()) { // Use lowercase for switch
            case "ban":
                commandToExecute = plugin.getConfigManager().getBanCommand()
                        .replace("{target}", target.getName())
                        .replace("{time}", time)
                        .replace("{reason}", reason);
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay)) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = permanentDisplay; // Use configured permanent display
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
                    durationForLog = permanentDisplay; // Use configured permanent display
                }
                break;
            case "softban":
                // Need to handle if 'time' is the permanent display string
                boolean isPermanentSoftban = time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay);
                punishmentEndTime = isPermanentSoftban ? Long.MAX_VALUE : (TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis());

                // [BUGFIX] Add 1 second to softban duration to correct the 1-second-short issue only if not permanent
                if (!isPermanentSoftban && punishmentEndTime > 0) { // Check > 0 in case parseTime failed
                    punishmentEndTime += 1000L; // Add 1 second in milliseconds
                }

                durationForLog = isPermanentSoftban ? permanentDisplay : time; // Use display string or original time for logs/hooks

                plugin.getSoftBanDatabaseManager().softBanPlayer(target.getUniqueId(), punishmentEndTime, reason, sender.getName()); // Logs internally
                sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                        "{target}", target.getName(),
                        "{time}", durationForLog, // Use durationForLog here for consistency
                        "{reason}", reason,
                        "{punishment_type}", punishType);
                handledInternally = true; // Mark as handled
                break; // NO return, allow hooks
            case "kick":
                commandToExecute = plugin.getConfigManager().getKickCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "N/A"; // Kick duration is not applicable
                punishmentEndTime = 0L; // Instantaneous
                break;
            case "warn":
                commandToExecute = plugin.getConfigManager().getWarnCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "N/A"; // Warn duration is not applicable
                punishmentEndTime = 0L; // Instantaneous
                break;
            case "freeze":
                plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true); // Mark player as frozen
                durationForLog = permanentDisplay; // Freeze is permanent
                punishmentEndTime = Long.MAX_VALUE;
                sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                        "{target}", target.getName(),
                        "{time}", durationForLog, // Use permanent display
                        "{reason}", reason,
                        "{punishment_type}", punishType);
                plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), punishmentEndTime, durationForLog); // Log freeze as permanent
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null && !onlineTarget.hasPermission("crown.bypass.freeze")) {
                    sendConfigMessage(onlineTarget, "messages.you_are_frozen"); // Inform the frozen player
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Starting FreezeActionsTask for player " + onlineTarget.getName() + " after direct freeze command.");
                    plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
                }
                handledInternally = true; // Mark as handled
                break; // NO return, allow hooks
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return;
        }

        // --- Execute External Command if Needed ---
        if (!handledInternally && !commandToExecute.isEmpty()) {
            final String finalCommandToExecute = commandToExecute; // Final for lambda
            final long finalPunishmentEndTime = punishmentEndTime; // Final for lambda
            final String finalDurationForLog = durationForLog; // Final for lambda

            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
            sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                    "{target}", target.getName(),
                    "{time}", durationForLog, // Use durationForLog
                    "{reason}", reason,
                    "{punishment_type}", punishType);
            // Log non-internal punishments here
            plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), finalPunishmentEndTime, finalDurationForLog);
        } else if (!handledInternally) {
            // This case is now expected for softban/freeze, only log error if command was empty unexpectedly
            if (commandToExecute.isEmpty() && !(punishType.equalsIgnoreCase("softban") || punishType.equalsIgnoreCase("freeze"))) {
                plugin.getLogger().warning("Punishment execution path error for type: " + punishType + " - No command and not handled internally.");
                return; // Don't execute hooks if there was an error
            }
        }

        // --- Execute Post-Punishment Hooks --- // NEW
        MenuListener menuListener = plugin.getMenuListener(); // Get listener instance
        if (menuListener != null) {
            // Use durationForLog which holds the correct display string ("Permanent", "N/A", or time input)
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
        boolean handledInternally = false; // Flag for internal handling
        String logReason = "Unpunished"; // Default log reason

        switch (punishType.toLowerCase()) { // Use lowercase for switch
            case "ban":
                commandToExecute = plugin.getConfigManager().getUnbanCommand().replace("{target}", target.getName());
                logReason = "Unbanned";
                break;
            case "mute":
                commandToExecute = plugin.getConfigManager().getUnmuteCommand().replace("{target}", target.getName());
                logReason = "Unmuted";
                break;
            case "softban":
                plugin.getSoftBanDatabaseManager().unSoftBanPlayer(target.getUniqueId(), sender.getName()); // Logs internally
                sendConfigMessage(sender, "messages.direct_unsoftban_confirmed", "{target}", target.getName());
                handledInternally = true;
                logReason = "Un-softbanned"; // Specific reason for hooks if needed
                break; // NO return, allow hooks
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
                if (!removed) { // Check if player was actually frozen
                    sendConfigMessage(sender, "messages.no_active_freeze", "{target}", target.getName());
                    return; // Exit if not frozen
                }
                logReason = "Unfrozen"; // Set reason before logging
                sendConfigMessage(sender, "messages.direct_unfreeze_confirmed", "{target}", target.getName());
                Player onlineTargetUnfreeze = target.getPlayer();
                if (onlineTargetUnfreeze != null) { sendConfigMessage(onlineTargetUnfreeze, "messages.you_are_unfrozen"); }
                // Log unfreeze action
                plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "unfreeze", logReason, sender.getName(), 0L, "N/A");
                handledInternally = true;
                // Stop freeze task AFTER logging and sending messages
                if (onlineTargetUnfreeze != null) {
                    plugin.getFreezeListener().stopFreezeActionsTask(target.getUniqueId());
                }
                break; // NO return, allow hooks
            case "kick": // Cannot un-kick
                sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType);
                return;
            default: // Invalid type
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
                return;
        }

        // --- Execute External Command if Needed ---
        if (!handledInternally && !commandToExecute.isEmpty()) {
            final String finalCommandToExecute = commandToExecute; // Final for lambda
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
            sendConfigMessage(sender, "messages.direct_unpunishment_confirmed",
                    "{target}", target.getName(),
                    "{punishment_type}", punishType);
            // Log non-internal unpunishments here
            plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "un" + punishType, logReason, sender.getName(), 0L, "N/A"); // Use N/A for duration
        } else if (!handledInternally) {
            // This case is now expected for softban/freeze, only log error if command was empty unexpectedly
            if (commandToExecute.isEmpty() && !(punishType.equalsIgnoreCase("softban") || punishType.equalsIgnoreCase("freeze"))) {
                plugin.getLogger().warning("Unpunishment execution path error for type: " + punishType + " - No command and not handled internally.");
                return; // Don't execute hooks if there was an error
            }
        }

        // --- Execute Post-Unpunishment Hooks --- // NEW
        MenuListener menuListener = plugin.getMenuListener();
        if (menuListener != null) {
            // Pass the original punishment type (e.g., "ban", not "unban")
            // Pass the specific reason determined in the switch case
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
     * Provides tab completion options for commands, handling:
     * - /crown base command and its subcommands.
     * - /punish alias command to /crown punish subcommand.
     * - /softban top-level command mirroring /crown punish softban - [CORRECTED TAB COMPLETE FOR /SOFTBAN!]
     * - /unpunish top-level command as a SEPARATE command - [CORRECTED TAB COMPLETE FOR /crown unpunish!]
     * - /freeze top-level command mirroring /crown punish freeze - [NEW TAB COMPLETE FOR /FREEZE!] - NEW
     * Tab completion for all commands is now handled in separate blocks for clarity and correctness.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // Tab completion for /crown command and its subcommands - SEPARATE BLOCK
        if (alias.equalsIgnoreCase("crown")) {
            if (args.length == 1) { // Subcommands for /crown
                StringUtil.copyPartialMatches(args[0], Arrays.asList(PUNISH_SUBCOMMAND, UNPUNISH_SUBCOMMAND, HELP_SUBCOMMAND, RELOAD_SUBCOMMAND), completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Player names for /crown punish <player>
                StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Punishment types for /crown punish <player> <type>
                StringUtil.copyPartialMatches(args[2], PUNISHMENT_TYPES, completions);
            } else if (args.length == 4 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Time suggestions for /crown punish <player> <type>
                String punishType = args[2].toLowerCase();
                if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) { // Time relevant for ban, mute, softban
                    StringUtil.copyPartialMatches(args[3], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
                }
            } else if (args.length >= 5 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Reason suggestion for /crown punish <player> <type> <time>
                completions.add("reason here...");
            } else if (args.length == 2 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) { // Player names for /crown unpunish <player>
                StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) { // Unpunishment types for /crown unpunish <player> <type>
                StringUtil.copyPartialMatches(args[2], UNPUNISHMENT_TYPES, completions);
            }
        }

        // Tab completion for /punish command (alias) - SEPARATE BLOCK - [CORRECTED TAB COMPLETE FOR /PUNISH!]
        if (alias.equalsIgnoreCase("punish")) {
            if (args.length == 1) { // Player names for /punish <player>
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) { // Punishment types for /punish <player> <type>
                StringUtil.copyPartialMatches(args[1], PUNISHMENT_TYPES, completions);
            } else if (args.length == 3) { // Time suggestions for /punish <player> <type> <time>
                String punishType = args[1].toLowerCase(); // Use args[1] here, as args[0] is player name
                if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) { // Time relevant for ban, mute, softban
                    StringUtil.copyPartialMatches(args[2], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
                }
            } else if (args.length >= 4) { // Reason suggestion for /punish <player> <type> <time> <reason...>
                completions.add("reason here...");
            }
        }


        // Tab completion for /unpunish command - SEPARATE BLOCK - [CORRECTED TAB COMPLETE for /crown unpunish AND /unpunish - NOW CORRECT!]
        if (alias.equalsIgnoreCase("unpunish")) {
            if (args.length == 1) { // Player name completion for /unpunish <player>
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) { // Unpunishment types for /unpunish <player> <type>
                StringUtil.copyPartialMatches(args[1], UNPUNISHMENT_TYPES, completions); // Use UNPUNISHMENT_TYPES for /unpunish
            }
        }

        // Tab completion for /softban command - SEPARATE BLOCK - [CORRECTED TAB COMPLETE FOR /SOFTBAN - NOW CORRECT!]
        if (alias.equalsIgnoreCase("softban")) {
            if (args.length == 1) { // Player name completion for /softban <player>
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) { // Time suggestions for /softban <player> <time>
                StringUtil.copyPartialMatches(args[1], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
            } else if (args.length >= 3) { // Reason suggestion for /softban <player> <time> <reason...>
                completions.add("reason here...");
            }
        }

        // Tab completion for /freeze command - SEPARATE BLOCK - [NEW TAB COMPLETE FOR /FREEZE!] - NEW
        if (alias.equalsIgnoreCase("freeze")) {
            if (args.length == 1) { // Player name completion for /freeze <player>
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length >= 2) { // Reason suggestion for /freeze <player> <reason...>
                completions.add("reason here...");
            }
        }


        Collections.sort(completions);
        return completions;
    }

    /**
     * Checks if the sender has permission to access punish details menu for a specific punishment type. - NEW
     * @param sender Command sender.
     * @param punishType Punishment type.
     * @return true if has permission, false otherwise.
     */
    private boolean checkPunishDetailsPermission(CommandSender sender, String punishType) {
        // Check for base punish permission first
        if (!sender.hasPermission(USE_PERMISSION)) {
            return false;
        }
        switch (punishType.toLowerCase()) {
            case "ban": return sender.hasPermission(PUNISH_BAN_PERMISSION);
            case "mute": return sender.hasPermission(PUNISH_MUTE_PERMISSION);
            case "softban": return sender.hasPermission(PUNISH_SOFTBAN_PERMISSION);
            case "kick": return sender.hasPermission(PUNISH_KICK_PERMISSION);
            case "warn": return sender.hasPermission(PUNISH_WARN_PERMISSION);
            case "freeze": return sender.hasPermission(PUNISH_FREEZE_PERMISSION); // NEW: Freeze permission check
            default: return false; // Or handle as needed, perhaps return sender.hasPermission(USE_PERMISSION); for default access
        }
    }

    /**
     * Sends a no permission message for unpunish command based on punishment type. - NEW
     * @param sender Command sender.
     * @param punishType Punishment type.
     */
    private void sendNoPermissionUnpunishMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_unpunish_command_type", "{punishment_type}", punishType);
    }

    /**
     * Sends a no permission message for direct punish command based on punishment type. - NEW
     * @param sender Command sender.
     * @param punishType Punishment type.
     */
    private void sendNoPermissionCommandMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_punish_command_type", "{punishment_type}", punishType);
    }

    /**
     * Checks if the sender has permission to use unpunish command for a specific punishment type. - NEW
     * @param sender CommandSender.
     * @param punishType Punishment type.
     * @return true if has permission, false otherwise.
     */
    private boolean checkUnpunishPermission(CommandSender sender, String punishType) {
        // Check for base crown.use permission first - NEW: Added base permission check
        if (!sender.hasPermission(USE_PERMISSION)) {
            return false;
        }
        switch (punishType.toLowerCase()) {
            case "ban": return sender.hasPermission(UNPUNISH_BAN_PERMISSION);
            case "mute": return sender.hasPermission(UNPUNISH_MUTE_PERMISSION);
            case "softban": return sender.hasPermission(UNPUNISH_SOFTBAN_PERMISSION);
            case "warn": return sender.hasPermission(UNPUNISH_WARN_PERMISSION); // Warn unpunish defaults to admin permission if no specific permission
            case "freeze": return sender.hasPermission(UNPUNISH_FREEZE_PERMISSION); // NEW: Freeze unpunish permission check
            default: return false; // Or handle as needed, perhaps return sender.hasPermission(ADMIN_PERMISSION); for default access
        }
    }

    /**
     * Sends a no permission message for punish details menu based on punishment type. - NEW
     * @param sender Command sender.
     * @param punishType Punishment type.
     */
    private void sendNoPermissionDetailsMessage(CommandSender sender, String punishType) {
        sendConfigMessage(sender, "messages.no_permission_details_menu", "{punishment_type}", punishType);
    }


    /**
     * Checks if the sender has permission to use direct punish command for a specific punishment type. - NEW
     * @param sender CommandSender.
     * @param punishType Punishment type.
     * @return true if has permission, false otherwise.
     */
    private boolean checkPunishCommandPermission(CommandSender sender, String punishType) {
        // Check for base punish permission first
        if (!sender.hasPermission(USE_PERMISSION)) {
            return false;
        }
        switch (punishType.toLowerCase()) {
            case "ban": return sender.hasPermission(PUNISH_BAN_PERMISSION);
            case "mute": return sender.hasPermission(PUNISH_MUTE_PERMISSION);
            case "softban": return sender.hasPermission(PUNISH_SOFTBAN_PERMISSION);
            case "kick": return sender.hasPermission(PUNISH_KICK_PERMISSION);
            case "warn": return sender.hasPermission(PUNISH_WARN_PERMISSION);
            case "freeze": return sender.hasPermission(PUNISH_FREEZE_PERMISSION); // NEW: Freeze permission check
            default: return false; // Or handle as needed, perhaps return sender.hasPermission(USE_PERMISSION); for default access
        }
    }

    /**
     * Sends the help message to the command sender.
     * Retrieves help messages from messages.yml for customization and sends them to the sender.
     *
     * @param sender CommandSender to send the help message to.
     */
    private void help(CommandSender sender) {
        // Help messages are loaded from messages.yml for easy customization
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_header")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish_extended")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish_alias")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish_alias"))); // Help for /unpunish command
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_softban_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_freeze_command"))); // Help for /freeze command - NEW
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
    }
}
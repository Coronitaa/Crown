// MainCommand.java
package cp.corona.commands;

import cp.corona.crownpunishments.CrownPunishments;
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
import java.util.logging.Level;
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
 * This approach prioritizes clarity and robustness over minimal code duplication for command handling.
 * Top-level /softban and /unpunish commands are now treated separately, and /punish mirrors /crown punish.
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    private final CrownPunishments plugin;

    // Command and subcommand names constants for maintainability
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String PUNISH_SUBCOMMAND = "punish";
    private static final String UNPUNISH_SUBCOMMAND = "unpunish";
    private static final String HELP_SUBCOMMAND = "help";
    private static final String SOFTBAN_COMMAND = "softban"; // Constant for softban command alias
    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final List<String> PUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "kick", "warn"); // Registered punishment types
    private static final List<String> UNPUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "warn"); // Registered unpunishment types, including warn

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
     * - /punish command (alias for /crown punish).
     * - /softban top-level command.
     * - /unpunish top-level command.
     * Each command is handled in a separate block for clarity and to avoid complex argument processing.
     * Top-level /softban and /unpunish commands are now treated separately, and /punish is a direct alias for /crown punish.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Handling for /crown command and its subcommands
        if (alias.equalsIgnoreCase("crown")) {
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
            // Directly call handlePunishCommand, treating /punish as if it was /crown punish
            return handlePunishCommand(sender, args); // Pass all arguments directly to handlePunishCommand for /punish
        }


        // Handling for /unpunish command as a SEPARATE top-level command
        if (alias.equalsIgnoreCase("unpunish")) {
            return handleUnpunishCommand(sender, args); // Directly handle /unpunish command
        }

        // Handling for /softban command as a SEPARATE top-level command
        if (alias.equalsIgnoreCase("softban")) {
            // Process /softban command directly - arguments are already in correct order for handlePunishCommand
            return handlePunishCommand(sender, new String[]{SOFTBAN_COMMAND, args.length > 0 ? args[0] : "", args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : ""});
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
        // Check for console usage without player specified
        if (!(sender instanceof Player) && args.length < 2) {
            sendConfigMessage(sender, "messages.player_only_console_punish");
            return false;
        }

        if (args.length == 0) { // /crown punish or /punish with no arguments: show help
            help(sender);
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        // Check if the target player has ever played on the server
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        if (args.length == 1) { // /crown punish <target> or /punish <target>: Open main menu
            if (!(sender instanceof Player)) {
                sendConfigMessage(sender, "messages.player_only");
                return true;
            }
            new PunishMenu(target.getUniqueId(), plugin).open((Player) sender);
        } else if (args.length >= 2) {
            String punishType = args[1].toLowerCase();
            // Validate the punishment type
            if (!PUNISHMENT_TYPES.contains(punishType)) {
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return true;
            }
            if (args.length == 2) { // /crown punish <target> <type> or /punish <target> <type>: Open details menu
                if (!(sender instanceof Player)) {
                    sendConfigMessage(sender, "messages.player_only");
                    return true;
                }
                new PunishDetailsMenu(target.getUniqueId(), plugin, punishType).open((Player) sender);
            } else if (args.length >= 3) {
                String time = args[2];
                // /crown punish <target> <type> <time> or /punish <target> <type> <time>: Open details menu with time pre-set for ban, mute, softban
                if (args.length == 3 && (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban"))) {
                    if (!(sender instanceof Player)) {
                        sendConfigMessage(sender, "messages.player_only");
                        return true;
                    }
                    PunishDetailsMenu detailsMenu = new PunishDetailsMenu(target.getUniqueId(), plugin, punishType);
                    detailsMenu.setBanTime(time);
                    detailsMenu.open((Player) sender);
                } else if (args.length >= 3) { // Direct punishment command execution
                    // /crown punish <target> <type> <time> <reason...> or /punish <target> <type> <reason...>
                    String reason = String.join(" ", Arrays.copyOfRange(args, (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? 3 : 2, args.length));
                    String timeForPunishment = (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? time : "permanent";
                    confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason);
                }
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
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission");
            return true;
        }

        if (args.length == 0) { // /crown unpunish or /unpunish with no arguments: show help
            help(sender);
            return true;
        }

        if (args.length < 2) { // /crown unpunish <player> <type> or /unpunish <player> <type> minimum arguments
            sendConfigMessage(sender, "messages.unpunish_usage", "{usage}", "/crown unpunish <player> <type>");
            return true;
        }

        String targetName = args[0];
        String punishType = args[1].toLowerCase();

        // Validate the unpunishment type
        if (!UNPUNISHMENT_TYPES.contains(punishType)) {
            sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        // Check if the target player has ever played on the server
        if (!target.hasPlayedBefore()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        confirmDirectUnpunish(sender, target, punishType);
        return true;
    }


    /**
     * Confirms and executes a direct punishment command (ban, mute, softban, kick, warn).
     * Executes the corresponding server command based on punishment type, and logs the punishment.
     *
     * @param sender CommandSender who initiated the command.
     * @param target OfflinePlayer target of the punishment.
     * @param punishType Type of punishment to apply.
     * @param time Duration of the punishment (if applicable).
     * @param reason Reason for the punishment.
     */
    private void confirmDirectPunishment(final CommandSender sender, final OfflinePlayer target, final String punishType, final String time, final String reason) {
        String commandToExecute = "";
        long punishmentEndTime = 0L; // Default punishment end time for logging, 0 for permanent or instant punishments
        String durationForLog = time; // Store duration string for logging purposes

        switch (punishType) {
            case "ban":
                commandToExecute = plugin.getConfigManager().getBanCommand()
                        .replace("{target}", target.getName())
                        .replace("{time}", time)
                        .replace("{reason}", reason);
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent")) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = "permanent";
                }
                break;
            case "mute":
                commandToExecute = plugin.getConfigManager().getMuteCommand()
                        .replace("{target}", target.getName())
                        .replace("{time}", time)
                        .replace("{reason}", reason);
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent")) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = "permanent";
                }
                break;
            case "softban": // Softban is handled internally
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                if (time.equalsIgnoreCase("permanent")) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = "permanent";
                }
                plugin.getSoftBanDatabaseManager().softBanPlayer(target.getUniqueId(), punishmentEndTime, reason, sender.getName());
                sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                        "{target}", target.getName(),
                        "{time}", time,
                        "{reason}", reason,
                        "{punishment_type}", punishType);
                return; // Return to prevent further command execution for softban
            case "kick":
                commandToExecute = plugin.getConfigManager().getKickCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "permanent"; // Kick is considered permanent for duration logging
                break;
            case "warn":
                commandToExecute = plugin.getConfigManager().getWarnCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "permanent"; // Warn is considered permanent for duration logging
                break;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return;
        }

        final String finalCommandToExecute = commandToExecute; // Final variable for lambda expression
        final long finalPunishmentEndTime = punishmentEndTime; // Final variable for lambda expression
        final String finalDurationForLog = durationForLog; // Final variable for lambda expression
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute)); // Execute command in main thread
        sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                "{target}", target.getName(),
                "{time}", time,
                "{reason}", reason,
                "{punishment_type}", punishType);
        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), finalPunishmentEndTime, finalDurationForLog); // Log the punishment
    }

    /**
     * Confirms and executes a direct unpunish command (unban, unmute, unsoftban, unwarn).
     * Executes the corresponding server command to remove the punishment and logs the action.
     *
     * @param sender CommandSender who initiated the command.
     * @param target OfflinePlayer to be unpunished.
     * @param punishType Type of punishment to remove.
     */
    private void confirmDirectUnpunish(final CommandSender sender, final OfflinePlayer target, final String punishType) {
        String commandToExecute = "";
        switch (punishType) {
            case "ban":
                commandToExecute = plugin.getConfigManager().getUnbanCommand()
                        .replace("{target}", target.getName());
                break;
            case "mute":
                commandToExecute = plugin.getConfigManager().getUnmuteCommand()
                        .replace("{target}", target.getName());
                break;
            case "softban": // Softban unpunish is handled internally
                plugin.getSoftBanDatabaseManager().unSoftBanPlayer(target.getUniqueId(), sender.getName());
                sendConfigMessage(sender, "messages.direct_unsoftban_confirmed", "{target}", target.getName());
                return; // Return to prevent further command execution for softban
            case "warn": // Handle warn unpunish - execute unwarn command if configured
                String unwarnCommand = plugin.getConfigManager().getUnwarnCommand();
                if (unwarnCommand != null && !unwarnCommand.isEmpty()) {
                    commandToExecute = unwarnCommand.replace("{target}", target.getName());
                } else {
                    sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType); // Message if unwarn command is not set
                    return; // Exit if no unwarn command configured
                }
                break;
            case "kick":
                sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType); // Kick unpunish is not supported
                return;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
                return;
        }

        final String finalCommandToExecute = commandToExecute; // Final variable for lambda expression
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute)); // Execute command in main thread
        sendConfigMessage(sender, "messages.direct_unpunishment_confirmed",
                "{target}", target.getName(),
                "{punishment_type}", punishType);
        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "un" + punishType, "Unpunished", sender.getName(), 0L, "permanent"); // Log unpunishment action
    }

    /**
     * Sends a message from the configuration to the command sender, with optional replacements.
     * Uses MessageUtils to colorize the message, and handles placeholders.
     *
     * @param sender CommandSender to send the message to.
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
     * - /softban top-level command mirroring /crown punish softban.
     * - /unpunish top-level command as a SEPARATE command.
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
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
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
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) { // Unpunishment types for /crown unpunish <player> <type>
                StringUtil.copyPartialMatches(args[2], UNPUNISHMENT_TYPES, completions);
            }
        }

        // Tab completion for /punish command (alias) - SEPARATE BLOCK - [CORRECTED TAB COMPLETE FOR /PUNISH!]
        if (alias.equalsIgnoreCase("punish")) {
            if (args.length == 1) { // Player names for /punish <player>
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
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


        // Tab completion for /unpunish command - SEPARATE BLOCK - [CORRECTED TAB COMPLETE for /crown unpunish AND /unpunish]
        if (alias.equalsIgnoreCase("unpunish")) {
            if (args.length == 1) { // Player name completion for /unpunish <player>
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) { // Unpunishment types for /unpunish <player> <type>
                StringUtil.copyPartialMatches(args[1], UNPUNISHMENT_TYPES, completions); // Use UNPUNISHMENT_TYPES for /unpunish
            }
        }

        // Tab completion for /softban command - SEPARATE BLOCK
        if (alias.equalsIgnoreCase("softban")) {
            if (args.length == 1) { // Player name completion for /softban <player>
                StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
            } else if (args.length == 2) { // Time suggestions for /softban <player> <time>
                StringUtil.copyPartialMatches(args[1], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
            } else if (args.length >= 3) { // Reason suggestion for /softban <player> <time> <reason...>
                completions.add("reason here...");
            }
        }


        Collections.sort(completions);
        return completions;
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
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish_command"))); // Help for /unpunish command
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_softban_command")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
    }
}
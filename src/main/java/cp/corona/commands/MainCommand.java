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
 * This version includes robust input validation for time arguments and a correction for the softban time calculation bug.
 * Top-level /softban and /unpunish commands are now fully separate, and /punish mirrors /crown punish.
 *
 * **NEW:** Added handling for the "freeze" punishment type.
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
    private static final String ADMIN_PERMISSION = "crown.admin";
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

        // Handling for /unpunish command as a SEPARATE top-level command - SEPARATE HANDLING BLOCK
        if (alias.equalsIgnoreCase("unpunish")) {
            return handleUnpunishCommand(sender, args); // Directly handle /unpunish command
        }

        // Handling for /softban command as a SEPARATE top-level command - SEPARATE HANDLING BLOCK
        if (alias.equalsIgnoreCase("softban")) {
            return handleSoftbanCommand(sender, args); // Call dedicated handleSoftbanCommand for /softban
        }

        // Handling for /freeze command as a SEPARATE top-level command - SEPARATE HANDLING BLOCK - NEW
        if (alias.equalsIgnoreCase("freeze")) {
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

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

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
            if (args.length == 2) { // /crown punish <target> <type> or /punish <target> <type>: Open details menu
                if (!(sender instanceof Player)) {
                    sendConfigMessage(sender, "messages.player_only");
                    return true;
                }
                new PunishDetailsMenu(target.getUniqueId(), plugin, punishType).open((Player) sender); // /crown punish <target> <type> - Open details menu
            } else if (args.length >= 3) { // Reason is always from arg 3 onwards, time might be arg 2 for ban, mute, softban, or not present for kick, warn, freeze
                String reason = String.join(" ", Arrays.copyOfRange(args, (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? 3 : 2, args.length)); // /crown punish <target> <type> <time> <reason...> or /crown punish <target> <type> <reason...>
                String timeForPunishment = (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? args[2] : "permanent"; // Time is arg 2 for ban, mute, softban, or permanent for kick/warn/freeze if not specified in direct command
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
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sendConfigMessage(sender, "messages.no_permission");
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

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore()) {
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
        if (!sender.hasPermission(ADMIN_PERMISSION)) { // Assuming softban also requires admin permission
            sendConfigMessage(sender, "messages.no_permission");
            return true;
        }

        if (args.length < 1) { // /softban with no arguments: show usage
            sendConfigMessage(sender, "messages.softban_usage", "{usage}", "/softban <player> [time] [reason]"); // Ensure you have a message for softban_usage in messages.yml
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        String time = "permanent"; // Default time to permanent if not specified
        String reason = "Softbanned by moderator"; // Default reason if not specified

        if (args.length >= 2) {
            time = args[1];
            // [NEW] Validate time format for /softban command
            if (TimeUtils.parseTime(time, plugin.getConfigManager()) == 0 && !time.equalsIgnoreCase("permanent")) {
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
        if (!sender.hasPermission(ADMIN_PERMISSION)) { // Assuming freeze also requires admin permission
            sendConfigMessage(sender, "messages.no_permission");
            return true;
        }

        if (args.length < 1) { // /freeze requires at least one argument: player name
            sendConfigMessage(sender, "messages.freeze_usage", "{usage}", "/freeze <player> [reason]"); // Ensure you have a message for freeze_usage in messages.yml
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
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
     * Confirms and executes a direct punishment command (ban, mute, softban, kick, warn, freeze). - MODIFIED: Added freeze
     *
     * @param sender Command sender.
     * @param target Target player.
     * @param punishType Type of punishment.
     * @param time Punishment time.
     * @param reason Punishment reason.
     */
    private void confirmDirectPunishment(final CommandSender sender, final OfflinePlayer target, final String punishType, final String time, final String reason) {
        String commandToExecute = "";
        long punishmentEndTime = 0L; // Default punishment end time for logging
        String durationForLog = time; // Store duration string for logging

        switch (punishType) {
            case "ban":
            case "mute":
            case "softban":
            case "kick":
            case "warn":
                // Cases for ban, mute, softban, kick, warn remain the same as in the previous version
                // ... (cases for ban, mute, softban, kick, warn are identical to the previous version)
                if (punishType.equals("ban")) {
                    commandToExecute = plugin.getConfigManager().getBanCommand()
                            .replace("{target}", target.getName())
                            .replace("{time}", time)
                            .replace("{reason}", reason);
                    punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                    if (time.equalsIgnoreCase("permanent")) {
                        punishmentEndTime = Long.MAX_VALUE;
                        durationForLog = "permanent";
                    }
                } else if (punishType.equals("mute")) {
                    commandToExecute = plugin.getConfigManager().getMuteCommand()
                            .replace("{target}", target.getName())
                            .replace("{time}", time)
                            .replace("{reason}", reason);
                    punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                    if (time.equalsIgnoreCase("permanent")) {
                        punishmentEndTime = Long.MAX_VALUE;
                        durationForLog = "permanent";
                    }
                } else if (punishType.equals("softban")) {
                    punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
                    // [BUGFIX] Add 1 second to softban duration to correct the 1-second-short issue
                    if (!time.equalsIgnoreCase("permanent")) {
                        punishmentEndTime += 1000L; // Add 1 second in milliseconds
                    }
                    if (time.equalsIgnoreCase("permanent")) {
                        punishmentEndTime = Long.MAX_VALUE;
                        durationForLog = "permanent";
                    }
                    plugin.getSoftBanDatabaseManager().softBanPlayer(target.getUniqueId(), punishmentEndTime, reason, sender.getName()); // Passing punisher name
                    sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                            "{target}", target.getName(),
                            "{time}", time,
                            "{reason}", reason,
                            "{punishment_type}", punishType);
                    return; // Important: Return after handling softban
                } else if (punishType.equals("kick")) {
                    commandToExecute = plugin.getConfigManager().getKickCommand()
                            .replace("{target}", target.getName())
                            .replace("{reason}", reason);
                    durationForLog = "permanent"; // Kick is permanent in duration log
                } else if (punishType.equals("warn")) {
                    commandToExecute = plugin.getConfigManager().getWarnCommand()
                            .replace("{target}", target.getName())
                            .replace("{reason}", reason);
                    durationForLog = "permanent"; // Warn is permanent in duration log
                }
                break;
            case "freeze": // Freeze punishment - NEW
                plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true); // Mark player as frozen
                sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                        "{target}", target.getName(),
                        "{time}", "permanent", // Freeze is permanent
                        "{reason}", reason,
                        "{punishment_type}", punishType);
                plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), Long.MAX_VALUE, "permanent"); // Log freeze as permanent
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    sendConfigMessage(onlineTarget, "messages.you_are_frozen"); // Inform the frozen player
                }
                return; // Important: Return after handling freeze
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", PUNISHMENT_TYPES));
                return;
        }

        final String finalCommandToExecute = commandToExecute; // Create final copy for lambda
        final long finalPunishmentEndTime = punishmentEndTime; // Create final copy for lambda
        final String finalDurationForLog = durationForLog; // Create final copy for lambda
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
        sendConfigMessage(sender, "messages.direct_punishment_confirmed",
                "{target}", target.getName(),
                "{time}", time,
                "{reason}", reason,
                "{punishment_type}", punishType);
        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishType, reason, sender.getName(), finalPunishmentEndTime, finalDurationForLog); // Log punishment with endTime and duration string
    }

    /**
     * Confirms and executes a direct unpunish command (unban, unmute, unsoftban, unwarn, unfreeze). - MODIFIED: Added unfreeze
     *
     * @param sender Command sender.
     * @param target Target player.
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
            case "softban": // Softban unpunish handled internally
                plugin.getSoftBanDatabaseManager().unSoftBanPlayer(target.getUniqueId(), sender.getName());
                sendConfigMessage(sender, "messages.direct_unsoftban_confirmed", "{target}", target.getName());
                return;
            case "warn": // Handle warn unpunish - execute unwarn command if configured
                String unwarnCommand = plugin.getConfigManager().getUnwarnCommand();
                if (unwarnCommand != null && !unwarnCommand.isEmpty()) {
                    commandToExecute = unwarnCommand.replace("{target}", target.getName());
                } else {
                    sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType); // Send message if unwarn command is not configured
                    return; // Exit if no unwarn command configured
                }
                break;
            case "freeze": // Unfreeze punishment - NEW
                plugin.getPluginFrozenPlayers().remove(target.getUniqueId()); // Remove player from frozen list
                sendConfigMessage(sender, "messages.direct_unfreeze_confirmed", "{target}", target.getName()); // Send confirmation message
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    sendConfigMessage(onlineTarget, "messages.you_are_unfrozen"); // Inform the player they are unfrozen
                }
                plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "un" + punishType, "Unfrozen", sender.getName(), 0L, "permanent"); // Log unfreeze action
                return; // Important: Return after handling unfreeze
            case "kick":
                sendConfigMessage(sender, "messages.unpunish_not_supported", "{punishment_type}", punishType); // Unpunish not supported for kick and warn
                return;
            default:
                sendConfigMessage(sender, "messages.invalid_punishment_type", "{types}", String.join(", ", UNPUNISHMENT_TYPES));
                return;
        }

        final String finalCommandToExecute = commandToExecute; // Create final copy for lambda
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandToExecute));
        sendConfigMessage(sender, "messages.direct_unpunishment_confirmed",
                "{target}", target.getName(),
                "{punishment_type}", punishType);
        plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), "un" + punishType, "Unpunished", sender.getName(), 0L, "permanent"); // Log unpunishment with 0L for time and "permanent" duration
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
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_freeze_command"))); // Help for /freeze command - NEW
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
    }
}
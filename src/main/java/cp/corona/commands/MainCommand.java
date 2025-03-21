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

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Handles the main command and subcommands for the CrownPunishments plugin.
 * Implements CommandExecutor and TabCompleter for command handling and tab completion, ONLY for /crown base command and its subcommands.
 * Top-level /softban and /unpunish commands are removed.
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    private final CrownPunishments plugin;

    // Command and subcommand names constants for maintainability
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String PUNISH_SUBCOMMAND = "punish";
    private static final String UNPUNISH_SUBCOMMAND = "unpunish";
    private static final String HELP_SUBCOMMAND = "help";
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
     * Executes commands when players type them in-game, handling ONLY /crown base command and its subcommands.
     * /softban and /unpunish top-level commands are removed.
     *
     * @param sender Source of the command.
     * @param command Command which was executed.
     * @param alias Alias of the command which was used.
     * @param args Passed command arguments.
     * @return true if command was handled correctly, false otherwise.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0 && alias.equalsIgnoreCase("crown")) { // Help for /crown base command without args
            help(sender);
            return true;
        }

        if (alias.equalsIgnoreCase("crown")) {
            if (args.length > 0) {
                String subcommand = args[0].toLowerCase();
                switch (subcommand) {
                    case RELOAD_SUBCOMMAND:
                        return handleReloadCommand(sender);
                    case PUNISH_SUBCOMMAND:
                        return handlePunishCommand(sender, Arrays.copyOfRange(args, 1, args.length)); // Pass args without subcommand
                    case UNPUNISH_SUBCOMMAND:
                        return handleUnpunishCommand(sender, Arrays.copyOfRange(args, 1, args.length)); // Pass args without subcommand
                    case HELP_SUBCOMMAND:
                        help(sender);
                        return true;
                    default:
                        help(sender);
                        return true;
                }
            } else {
                help(sender);
                return true;
            }
        }
        return false; // If alias is not "crown", or no valid subcommand, it's unhandled.
    }


    /**
     * Handles the reload subcommand to reload plugin configurations.
     * @param sender Command sender.
     * @return true if handled successfully.
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
     * Handles the punish subcommand to open the punishment menu or execute direct punishment, now ONLY accessible via /crown punish ...
     *
     * @param sender Command sender.
     * @param args Command arguments.
     * @return true if handled successfully.
     */
    private boolean handlePunishCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length < 2) { // Console usage check for /crown punish <player> ...
            sendConfigMessage(sender, "messages.player_only_console_punish"); // Specific message for console
            return false;
        }

        if (args.length == 0) { // Only /crown punish -> help (though ideally, /crown alone should show help)
            help(sender); // Consider more specific help for /crown punish if needed
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendConfigMessage(sender, "messages.never_played", "{input}", targetName);
            return true;
        }

        if (args.length == 1) { // /crown punish <target> - Open main menu
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
            if (args.length == 2) { // /crown punish <target> <type> - Open details menu
                if (!(sender instanceof Player)) {
                    sendConfigMessage(sender, "messages.player_only");
                    return true;
                }
                new PunishDetailsMenu(target.getUniqueId(), plugin, punishType).open((Player) sender); // /crown punish <target> <type> - Open details menu
            } else if (args.length >= 3) {
                String time = args[2];
                if (args.length == 3 && (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban"))) { // Time is relevant for ban, mute, softban
                    if (!(sender instanceof Player)) {
                        sendConfigMessage(sender, "messages.player_only");
                        return true;
                    }
                    PunishDetailsMenu detailsMenu = new PunishDetailsMenu(target.getUniqueId(), plugin, punishType); // /crown punish <target> <type> <time> - open details menu with time set
                    detailsMenu.setBanTime(time);
                    detailsMenu.open((Player) sender);
                } else if (args.length >= 3) { // Reason is always from arg 3 onwards, time might be arg 2 for ban, mute, softban, or not present for kick, warn
                    String reason = String.join(" ", Arrays.copyOfRange(args, (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? 3 : 2, args.length)); // /crown punish <target> <type> <time> <reason...> or /crown punish <target> <type> <reason...>
                    String timeForPunishment = (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) ? time : "permanent"; // Default time to permanent for kick/warn if not specified in direct command, though time is not used for kick/warn in confirmDirectPunishment
                    confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason);
                }
            }
        }
        return true;
    }

    /**
     * Handles the unpunish subcommand to remove a punishment from a player, now ONLY accessible via /crown unpunish ...
     *
     * @param sender Command sender.
     * @param args Command arguments.
     * @return true if handled successfully.
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
     * Confirms and executes a direct punishment command (ban, mute, softban, kick, warn).
     *
     * @param sender Command sender.
     * @param target Target player.
     * @param punishType Type of punishment.
     * @param time Punishment time.
     * @param reason Punishment reason.
     */
    private void confirmDirectPunishment(final CommandSender sender, final OfflinePlayer target, final String punishType, final String time, final String reason) { // Added final to variables used in lambda
        String commandToExecute = "";
        long punishmentEndTime = 0L; // Default punishment end time for logging
        String durationForLog = time; // Store duration string for logging

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
            case "softban": // Softban handled internally, direct command now supported
                punishmentEndTime = TimeUtils.parseTime(time, plugin.getConfigManager()) * 1000L + System.currentTimeMillis();
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
            case "kick":
                commandToExecute = plugin.getConfigManager().getKickCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "permanent"; // Kick is permanent in duration log
                break;
            case "warn":
                commandToExecute = plugin.getConfigManager().getWarnCommand()
                        .replace("{target}", target.getName())
                        .replace("{reason}", reason);
                durationForLog = "permanent"; // Warn is permanent in duration log
                break;
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
     * Confirms and executes a direct unpunish command (unban, unmute, unsoftban, unwarn).
     *
     * @param sender Command sender.
     * @param target Target player.
     * @param punishType Type of punishment to remove.
     */
    private void confirmDirectUnpunish(final CommandSender sender, final OfflinePlayer target, final String punishType) { // Added final to variables used in lambda
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
                plugin.getSoftBanDatabaseManager().unSoftBanPlayer(target.getUniqueId(), sender.getName()); // Passing punisher name
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
     * Provides tab completion options for commands, handling ONLY /crown base command and its subcommands.
     * Tab completion for top-level /softban and /unpunish commands are removed.
     *
     * @param sender Command sender.
     * @param command Command being typed.
     * @param alias Command alias used.
     * @param args Current command arguments.
     * @return List of tab completion options.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (alias.equalsIgnoreCase("crown")) { // Tab completion for /crown base command ONLY
            if (args.length == 1) { // Subcommands for /crown
                StringUtil.copyPartialMatches(args[0], Arrays.asList(PUNISH_SUBCOMMAND, UNPUNISH_SUBCOMMAND, HELP_SUBCOMMAND, RELOAD_SUBCOMMAND), completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Player names for /crown punish <player>
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Punishment types for /crown punish <player>
                StringUtil.copyPartialMatches(args[2], PUNISHMENT_TYPES, completions);
            } else if (args.length == 4 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Time suggestions for /crown punish <player> <type>
                String punishType = args[2].toLowerCase();
                if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) { // Time relevant for ban, mute, softban
                    StringUtil.copyPartialMatches(args[3], Arrays.asList("1s", "1m", "1h", "1d", "1y", "permanent"), completions);
                }
            } else if (args.length >= 5 && args[0].equalsIgnoreCase(PUNISH_SUBCOMMAND)) { // Reason suggestion for /crown punish <player> <type> <time>
                completions.add("reason here...");
            } else if (args.length == 2 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) { // Player names for /crown unpunish
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase(UNPUNISH_SUBCOMMAND)) { // Punishment types for /crown unpunish <player>
                StringUtil.copyPartialMatches(args[2], UNPUNISHMENT_TYPES, completions); // Use UNPUNISHMENT_TYPES for crown unpunish
            }
        } // No more else if blocks for 'punish', 'unpunish', 'softban' aliases

        Collections.sort(completions);
        return completions;
    }

    /**
     * Sends the help message to the command sender.
     * Retrieves help messages from messages.yml for customization.
     * @param sender Command sender.
     */
    private void help(CommandSender sender) {
        // Help messages from messages.yml for better customization
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_header")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish")));
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_punish_extended"))); // New help line
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish"))); // New help line
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_unpunish_command"))); // New help line for /crown unpunish command (adjust message if needed)
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_softban_command"))); // Keep help for softban subcommand under /crown punish
        sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
    }
}
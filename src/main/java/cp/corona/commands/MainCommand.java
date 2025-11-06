// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\commands\MainCommand.java
package cp.corona.commands;

import cp.corona.config.WarnLevel;
import cp.corona.crown.Crown;
import cp.corona.database.ActiveWarningEntry;
import cp.corona.database.DatabaseManager;
import cp.corona.listeners.MenuListener;
import cp.corona.menus.HistoryMenu;
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    private static final String HELP_SUBCOMMAND = "help";
    private static final String SOFTBAN_COMMAND_ALIAS = "softban";
    private static final String FREEZE_COMMAND_ALIAS = "freeze";
    private static final String BAN_COMMAND_ALIAS = "ban";
    private static final String MUTE_COMMAND_ALIAS = "mute";
    private static final String KICK_COMMAND_ALIAS = "kick";
    private static final String WARN_COMMAND_ALIAS = "warn";

    // Added constants for unpunish aliases and check alias
    private static final String UNBAN_COMMAND_ALIAS = "unban";
    private static final String UNMUTE_COMMAND_ALIAS = "unmute";
    private static final String UNWARN_COMMAND_ALIAS = "unwarn";
    private static final String UNSOFTBAN_COMMAND_ALIAS = "unsoftban";
    private static final String UNFREEZE_COMMAND_ALIAS = "unfreeze";
    private static final String CHECK_COMMAND_ALIAS = "c"; // From plugin.yml

    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final String USE_PERMISSION = "crown.use";
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

    private static final List<String> PUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "kick", "warn", "freeze");
    private static final List<String> UNPUNISHMENT_TYPES = Arrays.asList("ban", "mute", "softban", "warn", "freeze");

    // Added constants for tab completion
    private static final List<String> UNPUNISH_ALIASES = Arrays.asList(
            UNBAN_COMMAND_ALIAS, UNMUTE_COMMAND_ALIAS, UNWARN_COMMAND_ALIAS,
            UNSOFTBAN_COMMAND_ALIAS, UNFREEZE_COMMAND_ALIAS
    );
    private static final List<String> IP_FLAGS = Arrays.asList("-ip", "-i", "-local", "-l");
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList("1s", "1m", "1h", "1d", "1M", "1y", "permanent");
    private static final List<String> CHECK_ACTIONS = Arrays.asList("info", "repunish", "unpunish");
    private static final List<String> ID_SUGGESTION = Arrays.asList("<ID: XXXXXX>");
    private static final List<String> REASON_SUGGESTION = Arrays.asList("<reason>");


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
            case "c": // Handle alias from plugin.yml
                return handleCheckCommand(sender, args);
            case "history":
                return handleHistoryCommand(sender, args);
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
            case HISTORY_SUBCOMMAND:
                return handleHistoryCommand(sender, subArgs);
            case HELP_SUBCOMMAND:
            default:
                help(sender);
                return true;
        }
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
            return handleUnpunishCommand(sender, newArgs);
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

                if (isInternal) {
                    // --- INTERNAL PUNISHMENT STATUS LOGIC ---
                    if (type.equals("warn")) {
                        ActiveWarningEntry activeWarning = plugin.getSoftBanDatabaseManager().getActiveWarningByPunishmentId(punishmentId);
                        if (activeWarning != null) {
                            sendConfigMessage(sender, "messages.check_info_warn_level", "{level}", String.valueOf(activeWarning.getWarnLevel()));
                            if (activeWarning.isPaused()) {
                                status = plugin.getConfigManager().getMessage("placeholders.status_paused");
                                timeLeft = TimeUtils.formatTime((int)(activeWarning.getRemainingTimeOnPause() / 1000), plugin.getConfigManager());
                            } else {
                                status = plugin.getConfigManager().getMessage("placeholders.status_active");
                                if (activeWarning.getEndTime() != -1) {
                                    timeLeft = TimeUtils.formatTime((int)((activeWarning.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                                } else {
                                    timeLeft = "Permanent";
                                }
                            }
                        } else {
                            boolean isSystemExpired = !entry.isActive() && "System".equals(entry.getRemovedByName())
                                    && ("Expired".equalsIgnoreCase(entry.getRemovedReason()) || "Superseded by new warning.".equalsIgnoreCase(entry.getRemovedReason()));
                            status = isSystemExpired ? plugin.getConfigManager().getMessage("placeholders.status_expired")
                                    : plugin.getConfigManager().getMessage("placeholders.status_removed");
                        }
                    } else if (type.equals("kick")) {
                        status = "N/A";
                    } else {
                        boolean isPaused = !entry.isActive() && "Paused by new warning".equalsIgnoreCase(entry.getRemovedReason());
                        boolean isSystemExpired = !entry.isActive() && "System".equals(entry.getRemovedByName()) && "Expired".equalsIgnoreCase(entry.getRemovedReason());

                        if (isPaused) {
                            status = plugin.getConfigManager().getMessage("placeholders.status_paused");
                        } else if (entry.isActive()) {
                            status = plugin.getConfigManager().getMessage("placeholders.status_active");
                        } else {
                            status = isSystemExpired ? plugin.getConfigManager().getMessage("placeholders.status_expired")
                                    : plugin.getConfigManager().getMessage("placeholders.status_removed");
                        }
                        if (!type.equals("freeze") && entry.isActive() && entry.getEndTime() != Long.MAX_VALUE) {
                            timeLeft = TimeUtils.formatTime((int) ((entry.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                        }
                    }
                } else {
                    // --- EXTERNAL (NON-INTERNAL) PUNISHMENT STATUS LOGIC ---
                    if (type.equals("kick")) {
                        status = "N/A";
                        timeLeft = "N/A";
                    } else if (type.equals("warn")) {
                        status = !entry.isActive() ? plugin.getConfigManager().getMessage("placeholders.status_removed")
                                : plugin.getConfigManager().getMessage("placeholders.status_active");
                        timeLeft = "N/A";
                    } else { // For ban, mute, softban, freeze
                        if (!entry.isActive()) {
                            status = plugin.getConfigManager().getMessage("placeholders.status_removed");
                        } else if (entry.getEndTime() < System.currentTimeMillis() && entry.getEndTime() != Long.MAX_VALUE) {
                            status = plugin.getConfigManager().getMessage("placeholders.status_expired");
                        } else {
                            status = plugin.getConfigManager().getMessage("placeholders.status_active");
                            if (entry.getEndTime() != Long.MAX_VALUE) {
                                timeLeft = TimeUtils.formatTime((int) ((entry.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                            } else {
                                timeLeft = "Permanent";
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
                    TextComponent repunishButton = new TextComponent(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.check_info_repunish_button")));
                    repunishButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/check " + punishmentId + " repunish"));
                    repunishButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.check_info_repunish_hover")))));

                    TextComponent unpunishButton = new TextComponent(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.check_info_unpunish_button")));
                    unpunishButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/check " + punishmentId + " unpunish"));
                    unpunishButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.check_info_unpunish_hover")))));

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
                if (!entry.isActive()) {
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

            List<String> argsList = new ArrayList<>(Arrays.asList(args));
            argsList.remove(0); // remove target
            argsList.remove(0); // remove type

            Boolean byIpOverride = null;
            if (!argsList.isEmpty() && plugin.getConfigManager().isIpPunishmentSupported(punishType)) {
                String firstArg = argsList.get(0);
                if (firstArg.equalsIgnoreCase("-IP") || firstArg.equalsIgnoreCase("-i")) {
                    byIpOverride = true;
                    argsList.remove(0);
                } else if (firstArg.equalsIgnoreCase("-LOCAL") || firstArg.equalsIgnoreCase("-L")) {
                    byIpOverride = false;
                    argsList.remove(0);
                }
            }

            String timeForPunishment;
            String reason;

            if (punishType.equalsIgnoreCase("ban") || punishType.equalsIgnoreCase("mute") || punishType.equalsIgnoreCase("softban")) {
                if (!argsList.isEmpty() && TimeUtils.isValidTimeFormat(argsList.get(0), plugin.getConfigManager())) {
                    timeForPunishment = argsList.get(0);
                    argsList.remove(0);
                } else {
                    timeForPunishment = "permanent";
                }
            } else {
                timeForPunishment = "permanent"; // Not applicable for kick, warn, freeze
            }

            reason = !argsList.isEmpty() ? String.join(" ", argsList) : plugin.getConfigManager().getDefaultPunishmentReason(punishType);


            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[MainCommand] Direct punishment confirmed for " + target.getName() + ", type: " + punishType);
            confirmDirectPunishment(sender, target, punishType, timeForPunishment, reason, byIpOverride);
        }
        return true;
    }

    private boolean handleUnpunishCommand(CommandSender sender, String[] args) {
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

            // Note: For internal warns, we might want to remove a specific one even if not "active".
            // The logic in handleInternalUnwarn will check the active_warnings table.
            if (!entry.isActive() && !plugin.getConfigManager().isPunishmentInternal(entry.getType())) {
                sendConfigMessage(sender, "messages.punishment_not_active", "{id}", punishmentId);
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getPlayerUUID());
            String reason = (args.length > 1) ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : plugin.getConfigManager().getDefaultUnpunishmentReason(entry.getType());

            if (!checkUnpunishPermission(sender, entry.getType())) {
                sendNoPermissionUnpunishMessage(sender, entry.getType());
                return true;
            }

            if (entry.getType().equalsIgnoreCase("warn")) {
                boolean isInternal = plugin.getConfigManager().isPunishmentInternal("warn");
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

            if (!checkUnpunishPermission(sender, punishType)) {
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
                plugin.getLogger().severe("An error occurred while dispatching command: " + processedCommand);
                e.printStackTrace();
                sendConfigMessage(sender, "messages.command_dispatch_error", "{command}", processedCommand);
            }
        });
    }


    private void confirmDirectPunishment(final CommandSender sender, final OfflinePlayer target, final String punishType, final String time, final String reason, final Boolean byIpOverride) {
        if (target instanceof Player playerTarget) {
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
        boolean byIp = byIpOverride != null ? byIpOverride : plugin.getConfigManager().isPunishmentByIp(punishType);

        String ipAddress = null;
        if (byIp) {
            if (target.isOnline()) {
                InetSocketAddress address = target.getPlayer().getAddress();
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

                if (byIp && finalIpAddress != null) {
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

                switch(lowerCasePunishType) {
                    case "ban":
                    case "kick":
                        Date expiration = (endTime == Long.MAX_VALUE || lowerCasePunishType.equals("kick")) ? null : new Date(endTime);
                        List<String> screenLines = lowerCasePunishType.equals("ban") ? plugin.getConfigManager().getBanScreen() : plugin.getConfigManager().getKickScreen();
                        String kickMessage = MessageUtils.getKickMessage(screenLines, reason, durationForLog, punishmentId, expiration, plugin.getConfigManager());
                        onlinePlayer.kickPlayer(kickMessage);
                        break;

                    case "mute":
                        plugin.getMutedPlayersCache().put(onlinePlayer.getUniqueId(), endTime);
                        String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}", durationForLog, "{reason}", reason, "{punishment_id}", punishmentId);
                        onlinePlayer.sendMessage(MessageUtils.getColorMessage(muteMessage));
                        break;

                    case "softban":
                        plugin.getSoftBannedPlayersCache().put(onlinePlayer.getUniqueId(), endTime);
                        String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned", "{time}", durationForLog, "{reason}", reason, "{punishment_id}", punishmentId);
                        onlinePlayer.sendMessage(MessageUtils.getColorMessage(softbanMessage));
                        break;
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
                String targetIdentifier = isByIp ? ipAddress : target.getName();
                BanList.Type banType = isByIp ? BanList.Type.IP : BanList.Type.NAME;
                Bukkit.getBanList(banType).addBan(targetIdentifier, reason, expiration, sender.getName());

                if (target.isOnline()) {
                    String kickMessage = MessageUtils.getKickMessage(plugin.getConfigManager().getBanScreen(), reason, timeInput, punishmentId, expiration, plugin.getConfigManager());
                    target.getPlayer().kickPlayer(kickMessage);
                }
                break;
            case "mute":
                if (target.isOnline()) {
                    plugin.getMutedPlayersCache().put(target.getUniqueId(), punishmentEndTime);
                    String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted", "{time}", timeInput, "{reason}", reason, "{punishment_id}", punishmentId);
                    target.getPlayer().sendMessage(MessageUtils.getColorMessage(muteMessage));
                }
                break;
            case "softban":
                if (target.isOnline()) {
                    plugin.getSoftBannedPlayersCache().put(target.getUniqueId(), punishmentEndTime);
                    // Optionally send a message
                }
                break;
            case "kick":
                if (target.isOnline()) {
                    String kickMessage = MessageUtils.getKickMessage(plugin.getConfigManager().getKickScreen(), reason, "N/A", punishmentId, null, plugin.getConfigManager());
                    target.getPlayer().kickPlayer(kickMessage);
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
                .thenAccept(unpunishedId -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (unpunishedId == null) {
                            sendConfigMessage(sender, "messages.no_active_" + punishType, "{target}", target.getName());
                            return;
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
                    });
                });
    }

    private void handleInternalUnpunishmentPostAction(CommandSender sender, OfflinePlayer target, String punishType, String punishmentId) {
        String lowerCasePunishType = punishType.toLowerCase();

        switch(lowerCasePunishType) {
            case "ban":
                DatabaseManager.PlayerInfo playerInfo = plugin.getSoftBanDatabaseManager().getPlayerInfo(punishmentId);
                boolean wasByIp = playerInfo != null && playerInfo.getIp() != null && plugin.getSoftBanDatabaseManager().getPunishmentById(punishmentId).wasByIp();

                boolean pardoned = false;
                if (wasByIp) {
                    String ip = playerInfo.getIp();
                    if (Bukkit.getBanList(BanList.Type.IP).isBanned(ip)) {
                        Bukkit.getBanList(BanList.Type.IP).pardon(ip);
                        pardoned = true;
                    }
                }
                if (!pardoned && target.getName() != null && Bukkit.getBanList(BanList.Type.NAME).isBanned(target.getName())) {
                    Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
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
                break;
        }
    }

    private void handleInternalUnfreeze(CommandSender sender, OfflinePlayer target, String reason, String punishmentId) {
        boolean removed = plugin.getPluginFrozenPlayers().remove(target.getUniqueId()) != null;
        if (!removed) {
            sendConfigMessage(sender, "messages.no_active_freeze", "{target}", target.getName());
            return;
        }

        plugin.getSoftBanDatabaseManager().executeUnpunishmentAsync(target.getUniqueId(), "freeze", sender.getName(), reason, punishmentId)
                .thenAccept(unpunishedId -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (unpunishedId == null) return; // Should not happen if removed was true

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
                    });
                });
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

            Bukkit.getScheduler().runTask(plugin, () -> {
                sendConfigMessage(sender, "messages.direct_unpunishment_confirmed", "{target}", target.getName(), "{punishment_type}", "warn", "{punishment_id}", finalPunishmentId);
            });
        });
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

        String commandLabel = command.getName().toLowerCase();

        if (commandLabel.equals("crown")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList(PUNISH_SUBCOMMAND, UNPUNISH_SUBCOMMAND, CHECK_SUBCOMMAND, HISTORY_SUBCOMMAND, HELP_SUBCOMMAND, RELOAD_SUBCOMMAND), completions);
            } else if (args.length > 1) {
                String subcommand = args[0].toLowerCase();
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

                if (subcommand.equals(PUNISH_SUBCOMMAND)) {
                    handlePunishTab(subArgs, completions, playerNames, PUNISH_SUBCOMMAND);
                } else if (subcommand.equals(UNPUNISH_SUBCOMMAND)) {
                    handleUnpunishTab(subArgs, completions, playerNames, UNPUNISH_SUBCOMMAND);
                } else if (subcommand.equals(CHECK_SUBCOMMAND)) {
                    handleCheckTab(subArgs, completions);
                } else if (subcommand.equals(HISTORY_SUBCOMMAND)) {
                    if (subArgs.length == 1) {
                        StringUtil.copyPartialMatches(subArgs[0], playerNames, completions);
                    }
                }
            }
        }
        else if (commandLabel.equals(PUNISH_SUBCOMMAND)) {
            handlePunishTab(args, completions, playerNames, PUNISH_SUBCOMMAND);
        }
        else if (PUNISHMENT_TYPES.contains(commandLabel)) {
            handlePunishTab(args, completions, playerNames, commandLabel);
        }
        else if (commandLabel.equals(UNPUNISH_SUBCOMMAND)) {
            handleUnpunishTab(args, completions, playerNames, UNPUNISH_SUBCOMMAND);
        }
        else if (UNPUNISH_ALIASES.contains(commandLabel)) {
            handleUnpunishTab(args, completions, playerNames, commandLabel);
        }
        else if (commandLabel.equals(CHECK_SUBCOMMAND) || commandLabel.equals(CHECK_COMMAND_ALIAS)) {
            handleCheckTab(args, completions);
        }
        else if (commandLabel.equals(HISTORY_SUBCOMMAND)) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }

    private void handlePunishTab(String[] args, List<String> completions, List<String> playerNames, String commandLabel) {
        if (args.length == 0) return;

        List<String> currentArgs = new ArrayList<>(Arrays.asList(args));
        String currentArg = currentArgs.get(currentArgs.size() - 1);

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
                if (timeSupported) suggestions.addAll(TIME_SUGGESTIONS);
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
                    if (timeSupported) suggestions.addAll(TIME_SUGGESTIONS);
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
            String punishType = commandLabel;
            boolean ipSupported = plugin.getConfigManager().isIpPunishmentSupported(punishType);
            boolean timeSupported = punishType.equals("ban") || punishType.equals("mute") || punishType.equals("softban");

            if (currentArgs.size() == 1) {
                StringUtil.copyPartialMatches(currentArg, playerNames, completions);
                return;
            }

            if (currentArgs.size() == 2) {
                List<String> suggestions = new ArrayList<>();
                if (ipSupported) suggestions.addAll(IP_FLAGS);
                if (timeSupported) suggestions.addAll(TIME_SUGGESTIONS);
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
                    if (timeSupported) suggestions.addAll(TIME_SUGGESTIONS);
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
        if (sender.hasPermission(HISTORY_PERMISSION)) {
            sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_history_command")));
        }
        if (sender.hasPermission(CHECK_PERMISSION)) {
            sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_check_command")));
        }
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.help_reload")));
        }
    }
}
package cp.corona.punishments;

import cp.corona.crown.Crown;
import cp.corona.config.WarnLevel;
import cp.corona.database.ActiveWarningEntry;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import io.papermc.paper.ban.BanListType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PunishmentManager {

    private final Crown plugin;

    public PunishmentManager(Crown plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<String> issuePunishment(CommandSender sender, OfflinePlayer target, String punishType, String time, String reason, Boolean byIpOverride) {
        CompletableFuture<String> result = new CompletableFuture<>();

        boolean byIp = byIpOverride != null ? byIpOverride : plugin.getConfigManager().isPunishmentByIp(punishType);

        // Pre-emptive check for local kicks on offline players
        if (punishType.equalsIgnoreCase("kick") && !byIp && !target.isOnline()) {
            MessageUtils.sendConfigMessage(plugin, sender, "messages.player_not_online", "{input}", target.getName());
            result.complete(null);
            return result;
        }

        if (target instanceof Player playerTarget) {
            if (hasBypass(playerTarget, punishType)) {
                MessageUtils.sendConfigMessage(plugin, sender, "messages.bypass_error_" + punishType.toLowerCase(), "{target}", target.getName());
                result.complete(null);
                return result;
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
                MessageUtils.sendConfigMessage(plugin, sender, "messages.player_ip_not_found", "{target}", target.getName());
                result.complete(null);
                return result;
            }
        }
        final String finalIpAddress = ipAddress;

        if (!useInternal) {
            if (commandTemplate != null && !commandTemplate.isEmpty()) {
                String baseCommand = commandTemplate.split(" ")[0].toLowerCase();
                if (plugin.getRegisteredCommands().contains(baseCommand)) {
                    MessageUtils.sendConfigMessage(plugin, sender, "messages.command_loop_error", "{command}", baseCommand);
                    result.complete(null);
                    return result;
                }
            }
        }

        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        long punishmentEndTime;
        String durationForLog;

        switch (punishType.toLowerCase()) {
            case "ban":
            case "mute":
            case "softban":
            case "freeze": // Added freeze to have duration
                if (time == null || time.isEmpty() || time.equalsIgnoreCase("permanent") || time.equalsIgnoreCase(permanentDisplay)) {
                    punishmentEndTime = Long.MAX_VALUE;
                    durationForLog = permanentDisplay;
                } else {
                    long seconds = TimeUtils.parseTime(time, plugin.getConfigManager());
                    if (seconds <= 0) {
                        punishmentEndTime = Long.MAX_VALUE;
                        durationForLog = permanentDisplay;
                    } else {
                        punishmentEndTime = System.currentTimeMillis() + (seconds * 1000L);
                        durationForLog = time;
                    }
                }
                break;
            case "kick":
            case "warn":
                punishmentEndTime = Long.MAX_VALUE;
                durationForLog = permanentDisplay;
                break;
            default:
                result.complete(null);
                return result;
        }

        if (punishType.equalsIgnoreCase("warn") && useInternal) {
            return issueWarn(sender, target, reason, time);
        }

        final long finalPunishmentEndTime = punishmentEndTime;
        final String finalDurationForLog = durationForLog;

        plugin.getSoftBanDatabaseManager().executePunishmentAsync(target.getUniqueId(), target.getName(), punishType, reason, sender.getName(), finalPunishmentEndTime, finalDurationForLog, byIp, null)
                .thenAccept(punishmentId -> {
                    if (punishmentId == null) {
                        result.complete(null);
                        return;
                    }

                    plugin.getSoftBanDatabaseManager().logPlayerInfoAsync(punishmentId, target, finalIpAddress);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        applyPunishmentEffect(sender, target, punishType, reason, finalIpAddress, time, punishmentId, finalPunishmentEndTime, useInternal, commandTemplate);

                        if (byIp) {
                            applyIpPunishmentToOnlinePlayers(punishType, finalIpAddress, finalPunishmentEndTime, reason, finalDurationForLog, punishmentId, target.getUniqueId());
                        }

                        if (sender instanceof Player player) {
                            plugin.playSound(player, "sounds.punish_confirm");
                        }

                        String messageKey = byIp ? "messages.direct_punishment_confirmed_ip" : "messages.direct_punishment_confirmed";
                        MessageUtils.sendConfigMessage(plugin, sender, messageKey, "{target}", target.getName(), "{time}", finalDurationForLog, "{reason}", reason, "{punishment_type}", punishType, "{punishment_id}", punishmentId);

                        if (plugin.getMenuListener() != null) {
                            plugin.getMenuListener().executeHookActions(sender, target, punishType, finalDurationForLog, reason, false, Collections.emptyList());
                        }
                        result.complete(punishmentId);
                    });
                });

        return result;
    }

    public CompletableFuture<String> issueWarn(CommandSender sender, OfflinePlayer target, String reason, String timeOverride) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager dbManager = plugin.getSoftBanDatabaseManager();
            ActiveWarningEntry latestWarning = dbManager.getLatestActiveWarning(target.getUniqueId());
            int nextWarnLevel = (latestWarning != null) ? latestWarning.getWarnLevel() + 1 : 1;

            WarnLevel levelConfig = plugin.getConfigManager().getWarnLevel(nextWarnLevel);

            if (levelConfig == null) {
                Bukkit.getScheduler().runTask(plugin, () -> MessageUtils.sendConfigMessage(plugin, sender, "messages.no_warn_level_configured", "{level}", String.valueOf(nextWarnLevel)));
                result.complete(null);
                return;
            }

            long endTime;
            String durationForLog;

            if (timeOverride != null && !timeOverride.isEmpty() && !timeOverride.equalsIgnoreCase("default")) {
                long seconds = TimeUtils.parseTime(timeOverride, plugin.getConfigManager());
                if (seconds == -1) {
                    endTime = -1;
                    durationForLog = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
                } else {
                    endTime = System.currentTimeMillis() + (seconds * 1000L);
                    durationForLog = timeOverride;
                }
            } else {
                int durationSeconds = TimeUtils.parseTime(levelConfig.getExpiration(), plugin.getConfigManager());
                endTime = (durationSeconds == -1) ? -1 : System.currentTimeMillis() + (durationSeconds * 1000L);
                durationForLog = (endTime == -1) ? "Permanent" : TimeUtils.formatTime(durationSeconds, plugin.getConfigManager());
            }

            String punishmentId = dbManager.logPunishment(target.getUniqueId(), target.getName(), "warn", reason, sender.getName(), endTime, durationForLog, false, nextWarnLevel);

            if (punishmentId != null) {
                dbManager.logPlayerInfoAsync(punishmentId, target, null);
                if (sender instanceof Player player) {
                    plugin.playSound(player, "sounds.punish_confirm");
                }
            }

            dbManager.addActiveWarning(target.getUniqueId(), punishmentId, nextWarnLevel, endTime).thenRun(() -> {
                ActiveWarningEntry newWarning = dbManager.getActiveWarningByPunishmentId(punishmentId);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.getMenuListener() != null && newWarning != null) {
                        plugin.getMenuListener().executeHookActions(sender, target, "warn", durationForLog, reason, false, levelConfig.getOnWarnActions(), newWarning);
                    }
                    
                    String messageKey = "messages.direct_punishment_confirmed";
                    MessageUtils.sendConfigMessage(plugin, sender, messageKey, "{target}", target.getName(), "{time}", durationForLog, "{reason}", reason, "{punishment_type}", "warn", "{punishment_id}", punishmentId);
                    
                    result.complete(punishmentId);
                });
            });
        });
        return result;
    }

    private boolean hasBypass(Player player, String type) {
        return player.hasPermission("crown.bypass." + type.toLowerCase());
    }

    private void applyPunishmentEffect(CommandSender sender, OfflinePlayer target, String punishType, String reason, String ipAddress, String timeInput, String punishmentId, long punishmentEndTime, boolean useInternal, String commandTemplate) {
        if (!useInternal) {
            executePunishmentCommand(sender, commandTemplate, target, timeInput, reason);
            return;
        }

        switch (punishType.toLowerCase()) {
            case "ban":
                Date expiration = (punishmentEndTime == Long.MAX_VALUE) ? null : new Date(punishmentEndTime);
                if (ipAddress != null) {
                    try {
                        InetAddress addr = InetAddress.getByName(ipAddress);
                        Bukkit.getBanList(BanListType.IP).addBan(addr, reason, expiration, sender.getName());
                    } catch (UnknownHostException e) {
                        plugin.getLogger().warning("Invalid IP address for ban: " + ipAddress);
                    }
                } else {
                    Bukkit.getBanList(BanListType.PROFILE).addBan(target.getPlayerProfile(), reason, expiration, sender.getName());
                }

                Player playerTargetBan = target.getPlayer();
                if (playerTargetBan != null) {
                    String kickMessage = MessageUtils.getKickMessage(plugin.getConfigManager().getBanScreen(), reason, timeInput, punishmentId, expiration, plugin.getConfigManager());
                    playerTargetBan.kick(MessageUtils.getColorComponent(kickMessage));
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
                Player playerTargetSoft = target.getPlayer();
                if (playerTargetSoft != null) {
                    String softbanMessage = plugin.getConfigManager().getMessage("messages.you_are_softbanned", "{time}", timeInput, "{reason}", reason, "{punishment_id}", punishmentId);
                    playerTargetSoft.sendMessage(MessageUtils.getColorMessage(softbanMessage));
                }
                break;
            case "kick":
                Player playerTargetKick = target.getPlayer();
                if (playerTargetKick != null) {
                    String kickMsg = MessageUtils.getKickMessage(plugin.getConfigManager().getKickScreen(), reason, "N/A", punishmentId, null, plugin.getConfigManager());
                    playerTargetKick.kick(MessageUtils.getColorComponent(kickMsg));
                }
                break;
            case "freeze":
                plugin.getPluginFrozenPlayers().put(target.getUniqueId(), true);
                Player playerTargetFreeze = target.getPlayer();
                if (playerTargetFreeze != null) {
                    plugin.getFreezeListener().startFreezeActionsTask(playerTargetFreeze);
                    plugin.getFreezeListener().startFreezeChatSession(sender, playerTargetFreeze, punishmentId);
                    playerTargetFreeze.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_frozen")));
                }
                break;
        }
    }

    private void executePunishmentCommand(CommandSender sender, String template, OfflinePlayer target, String time, String reason) {
        if (template == null || template.isEmpty()) return;

        String processedCommand = template
                .replace("{player}", target.getName() != null ? target.getName() : target.getUniqueId().toString())
                .replace("{reason}", reason)
                .replace("{time}", time)
                .replace("{sender}", sender.getName());

        if (processedCommand.startsWith("/")) {
            processedCommand = processedCommand.substring(1);
        }

        String finalProcessedCommand = processedCommand;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalProcessedCommand)) {
                    MessageUtils.sendConfigMessage(plugin, sender, "messages.command_not_found", "{command}", finalProcessedCommand);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "An error occurred while dispatching command: " + finalProcessedCommand, e);
            }
        });
    }

    private void applyIpPunishmentToOnlinePlayers(String punishmentType, String ipAddress, long endTime, String reason, String durationForLog, String punishmentId, UUID originalTargetUUID) {
        String lowerCasePunishType = punishmentType.toLowerCase();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(originalTargetUUID)) continue;

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
}

// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\listeners\PunishmentListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.database.ActiveWarningEntry;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PunishmentListener implements Listener {

    private final Crown plugin;
    private final Set<UUID> chatFrozenPlayers = new HashSet<>();

    public PunishmentListener(Crown plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        // This event is already async, so DB calls here are safe.
        final BanList nameBanList = Bukkit.getBanList(BanList.Type.NAME);
        final BanList ipBanList = Bukkit.getBanList(BanList.Type.IP);

        final String playerName = event.getName();
        final String playerIP = event.getAddress().getHostAddress();

        BanEntry banEntry = nameBanList.isBanned(playerName) ? nameBanList.getBanEntry(playerName) :
                ipBanList.isBanned(playerIP) ? ipBanList.getBanEntry(playerIP) : null;

        if (banEntry != null) {
            String reason = banEntry.getReason() != null ? banEntry.getReason() : plugin.getConfigManager().getDefaultPunishmentReason("ban");
            Date expiration = banEntry.getExpiration();
            String timeLeft = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

            if (expiration != null) {
                long remainingMillis = expiration.getTime() - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    timeLeft = TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());
                } else {
                    // Pardon expired bans
                    if (nameBanList.isBanned(playerName)) nameBanList.pardon(playerName);
                    if (ipBanList.isBanned(playerIP)) ipBanList.pardon(playerIP);
                    return;
                }
            }

            DatabaseManager.PunishmentEntry punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishment(event.getUniqueId(), "ban");
            if (punishmentEntry == null) {
                punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(playerIP, "ban");
            }
            String punishmentId = (punishmentEntry != null) ? punishmentEntry.getPunishmentId() : "N/A";

            String kickMessage = getKickMessage(plugin.getConfigManager().getBanScreen(), reason, timeLeft, punishmentId, expiration);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();
        final String playerIP = player.getAddress().getAddress().getHostAddress();

        // Asynchronously load punishment data and populate caches
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager dbManager = plugin.getSoftBanDatabaseManager();
            List<DatabaseManager.PunishmentEntry> allActivePunishments = dbManager.getAllActivePunishments(playerUUID, playerIP);

            boolean hasMute = false;
            boolean hasSoftban = false;

            for (DatabaseManager.PunishmentEntry punishment : allActivePunishments) {
                if (punishment.getType().equalsIgnoreCase("mute")) {
                    plugin.getMutedPlayersCache().put(playerUUID, punishment.getEndTime());
                    hasMute = true;
                } else if (punishment.getType().equalsIgnoreCase("softban")) {
                    plugin.getSoftBannedPlayersCache().put(playerUUID, punishment.getEndTime());
                    hasSoftban = true;
                }
            }
            // If no active punishments of these types were found, ensure they are not in the cache
            if(!hasMute) plugin.getMutedPlayersCache().remove(playerUUID);
            if(!hasSoftban) plugin.getSoftBannedPlayersCache().remove(playerUUID);


            // The rest of the logic is for the join alert
            if (!plugin.getConfigManager().isJoinAlertEnabled() || allActivePunishments.isEmpty()) {
                return;
            }

            chatFrozenPlayers.add(playerUUID);

            List<DatabaseManager.PunishmentEntry> standardPunishments = allActivePunishments.stream()
                    .filter(p -> !p.getType().equalsIgnoreCase("freeze") && !p.getType().equalsIgnoreCase("warn"))
                    .collect(Collectors.toList());
            List<ActiveWarningEntry> activeWarnings = dbManager.getAllActiveAndPausedWarnings(playerUUID);
            Map<String, DatabaseManager.PunishmentEntry> punishmentDetailsMap = allActivePunishments.stream()
                    .filter(p -> p.getType().equalsIgnoreCase("warn"))
                    .collect(Collectors.toMap(DatabaseManager.PunishmentEntry::getPunishmentId, entry -> entry));

            final List<TextComponent> messagesToSend = buildJoinAlertMessages(standardPunishments, activeWarnings, punishmentDetailsMap);

            // Schedule final actions back on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = Bukkit.getPlayer(playerUUID);
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    chatFrozenPlayers.remove(playerUUID);
                    return;
                }

                messagesToSend.forEach(msg -> onlinePlayer.spigot().sendMessage(msg));

                String soundName = plugin.getConfigManager().getJoinAlertSound();
                if (soundName != null && !soundName.isEmpty()) {
                    try {
                        Sound sound = Sound.valueOf(soundName.toUpperCase());
                        onlinePlayer.playSound(onlinePlayer.getLocation(), sound, 1.0f, 1.0f);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound name configured for on-join-alert: " + soundName);
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> chatFrozenPlayers.remove(playerUUID), plugin.getConfigManager().getJoinAlertDuration() * 20L);
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        // Clean up caches on player quit to prevent memory leaks
        plugin.getMutedPlayersCache().remove(playerUUID);
        plugin.getSoftBannedPlayersCache().remove(playerUUID);
        plugin.getPluginFrozenPlayers().remove(playerUUID);
        chatFrozenPlayers.remove(playerUUID);
    }

    private List<TextComponent> buildJoinAlertMessages(List<DatabaseManager.PunishmentEntry> standardPunishments, List<ActiveWarningEntry> activeWarnings, Map<String, DatabaseManager.PunishmentEntry> detailsMap) {
        final List<TextComponent> messages = new ArrayList<>();
        messages.add(new TextComponent(TextComponent.fromLegacyText(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.active_punishments_header")))));

        for (DatabaseManager.PunishmentEntry punishment : standardPunishments) {
            String timeLeft = (punishment.getEndTime() == Long.MAX_VALUE) ? "Permanent" : TimeUtils.formatTime((int) ((punishment.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
            String visibleText = plugin.getConfigManager().getMessage("messages.active_punishment_entry", "{id}", punishment.getPunishmentId(), "{type}", punishment.getType(), "{time_left}", timeLeft);
            TextComponent messageComponent = new TextComponent(TextComponent.fromLegacyText(MessageUtils.getColorMessage(visibleText)));
            messageComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(buildHoverText(punishment)).create()));
            messages.add(messageComponent);
        }

        for (ActiveWarningEntry warning : activeWarnings) {
            DatabaseManager.PunishmentEntry details = detailsMap.get(warning.getPunishmentId());
            if (details == null) continue;
            String timeLeft;
            String statusSuffix = warning.isPaused() ? " &6(Paused)" : "";
            if (warning.isPaused()) {
                timeLeft = TimeUtils.formatTime((int) (warning.getRemainingTimeOnPause() / 1000), plugin.getConfigManager());
            } else if (warning.getEndTime() == -1) {
                timeLeft = "Permanent";
            } else {
                timeLeft = TimeUtils.formatTime((int) ((warning.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
            }
            String visibleText = plugin.getConfigManager().getMessage("messages.active_warning_entry", "{id}", warning.getPunishmentId(), "{type}", "warn", "{level}", String.valueOf(warning.getWarnLevel()), "{time_left}", timeLeft + statusSuffix);
            TextComponent messageComponent = new TextComponent(TextComponent.fromLegacyText(MessageUtils.getColorMessage(visibleText)));
            messageComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(buildHoverText(details)).create()));
            messages.add(messageComponent);
        }

        messages.add(new TextComponent(TextComponent.fromLegacyText(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.active_punishments_footer")))));
        TextComponent supportMessage = new TextComponent(TextComponent.fromLegacyText(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.support_link_message", "{support_link}", plugin.getConfigManager().getSupportLink()))));
        supportMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://" + plugin.getConfigManager().getSupportLink()));
        supportMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.support_link_hover"))).create()));
        messages.add(supportMessage);
        return messages;
    }


    private String buildHoverText(DatabaseManager.PunishmentEntry entry) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String hover = String.format("&eReason: &f%s\n&eDate: &f%s\n&eMethod: &f%s",
                entry.getReason(),
                dateFormat.format(entry.getTimestamp()),
                entry.wasByIp() ? plugin.getConfigManager().getMessage("placeholders.by_ip") : plugin.getConfigManager().getMessage("placeholders.by_local")
        );
        return MessageUtils.getColorMessage(hover);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (chatFrozenPlayers.contains(event.getPlayer().getUniqueId()) && !event.getMessage().startsWith("/")) {
            event.setCancelled(true);
        }
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
}
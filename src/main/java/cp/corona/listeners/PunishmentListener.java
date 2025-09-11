// src/main/java/cp/corona/listeners/PunishmentListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class PunishmentListener implements Listener {

    private final Crown plugin;

    public PunishmentListener(Crown plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerIP = event.getAddress().getHostAddress();
        BanList ipBanList = Bukkit.getBanList(BanList.Type.IP);

        if (ipBanList.isBanned(playerIP)) {
            BanEntry banEntry = ipBanList.getBanEntry(playerIP);
            if (banEntry != null) {
                String reason = banEntry.getReason() != null ? banEntry.getReason() : plugin.getConfigManager().getDefaultPunishmentReason("ban");
                Date expiration = banEntry.getExpiration();
                String timeLeft = "Permanent";

                if (expiration != null) {
                    long remainingMillis = expiration.getTime() - System.currentTimeMillis();
                    if (remainingMillis > 0) {
                        timeLeft = TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());
                    } else {
                        ipBanList.pardon(playerIP);
                        return;
                    }
                }

                DatabaseManager.PunishmentEntry punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(playerIP, "ban");
                String punishmentId = (punishmentEntry != null) ? punishmentEntry.getPunishmentId() : "N/A";

                List<String> banScreenLines = plugin.getConfigManager().getBanScreen();
                String kickMessage = getKickMessage(banScreenLines, reason, timeLeft, punishmentId, expiration);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerIP = event.getAddress().getHostAddress();

        // Check for IP-based mute
        if (plugin.getConfigManager().isPunishmentByIp("mute")) {
            DatabaseManager.PunishmentEntry muteEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(playerIP, "mute");
            if (muteEntry != null) {
                String timeLeft = TimeUtils.formatTime((int) ((muteEntry.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_muted",
                        "{time}", timeLeft,
                        "{reason}", muteEntry.getReason(),
                        "{punishment_id}", muteEntry.getPunishmentId())));
            }
        }

        // Check for IP-based softban
        if (plugin.getConfigManager().isPunishmentByIp("softban")) {
            DatabaseManager.PunishmentEntry softbanEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(playerIP, "softban");
            if (softbanEntry != null) {
                String timeLeft = TimeUtils.formatTime((int) ((softbanEntry.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_softbanned",
                        "{time}", timeLeft,
                        "{reason}", softbanEntry.getReason(),
                        "{punishment_id}", softbanEntry.getPunishmentId())));
            }
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
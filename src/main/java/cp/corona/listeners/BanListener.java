// BanListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BanListener implements Listener {

    private final Crown plugin;

    public BanListener(Crown plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();
        UUID playerUUID = event.getUniqueId();
        String playerIP = event.getAddress().getHostAddress();

        BanList nameBanList = Bukkit.getBanList(BanList.Type.NAME);
        BanList ipBanList = Bukkit.getBanList(BanList.Type.IP);

        BanEntry banEntry = null;
        boolean isIpBan = false;
        if (ipBanList.isBanned(playerIP)) {
            banEntry = ipBanList.getBanEntry(playerIP);
            isIpBan = true;
        } else if (nameBanList.isBanned(playerName)) {
            banEntry = nameBanList.getBanEntry(playerName);
        }

        if (banEntry != null) {
            String reason = banEntry.getReason() != null ? banEntry.getReason() : plugin.getConfigManager().getDefaultPunishmentReason("ban");
            Date expiration = banEntry.getExpiration();
            String timeLeft = "Permanent";
            if (expiration != null) {
                long remainingMillis = expiration.getTime() - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    timeLeft = TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());
                } else {
                    // Pardon both just in case
                    nameBanList.pardon(playerName);
                    ipBanList.pardon(playerIP);
                    return;
                }
            }

            DatabaseManager.PunishmentEntry punishmentEntry;
            if (isIpBan) {
                punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(playerIP, "ban");
            } else {
                punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishment(playerUUID, "ban");
            }
            String punishmentId = (punishmentEntry != null) ? punishmentEntry.getPunishmentId() : "N/A";

            List<String> banScreenLines = plugin.getConfigManager().getBanScreen();
            String kickMessage = getKickMessage(banScreenLines, reason, timeLeft, punishmentId, expiration);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
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
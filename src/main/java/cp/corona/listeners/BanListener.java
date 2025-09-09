// BanListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

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

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId());
        if (player.isBanned()) {
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            BanEntry banEntry = banList.getBanEntry(player.getName());

            if (banEntry != null) {
                String reason = banEntry.getReason() != null ? banEntry.getReason() : plugin.getConfigManager().getDefaultPunishmentReason("ban");
                Date expiration = banEntry.getExpiration();
                String timeLeft = "Permanent";
                if (expiration != null) {
                    long remainingMillis = expiration.getTime() - System.currentTimeMillis();
                    if (remainingMillis > 0) {
                        timeLeft = TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());
                    } else {
                        banList.pardon(player.getName());
                        return;
                    }
                }

                DatabaseManager.PunishmentEntry punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishment(player.getUniqueId(), "ban");
                String punishmentId = (punishmentEntry != null) ? punishmentEntry.getPunishmentId() : "N/A";

                List<String> banScreenLines = plugin.getConfigManager().getBanScreen();
                String kickMessage = getKickMessage(banScreenLines, reason, timeLeft, punishmentId, expiration);
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
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
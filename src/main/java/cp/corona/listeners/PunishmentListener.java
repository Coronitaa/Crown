// coronitaa/crown/Crown-0a35d634fd87d2a5ccf97c7763b7f53746dff78b/src/main/java/cp/corona/listeners/PunishmentListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
        final BanList nameBanList = Bukkit.getBanList(BanList.Type.NAME);
        final BanList ipBanList = Bukkit.getBanList(BanList.Type.IP);

        final String playerName = event.getName();
        final String playerIP = event.getAddress().getHostAddress();

        BanEntry banEntry = null;
        if (nameBanList.isBanned(playerName)) {
            banEntry = nameBanList.getBanEntry(playerName);
        } else if (ipBanList.isBanned(playerIP)) {
            banEntry = ipBanList.getBanEntry(playerIP);
        }

        if (banEntry != null) {
            String reason = banEntry.getReason() != null ? banEntry.getReason() : plugin.getConfigManager().getDefaultPunishmentReason("ban");
            Date expiration = banEntry.getExpiration();
            String timeLeft = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

            if (expiration != null) {
                long remainingMillis = expiration.getTime() - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    timeLeft = TimeUtils.formatTime((int) (remainingMillis / 1000), plugin.getConfigManager());
                } else {
                    if (nameBanList.isBanned(playerName)) {
                        nameBanList.pardon(playerName);
                    }
                    if (ipBanList.isBanned(playerIP)) {
                        ipBanList.pardon(playerIP);
                    }
                    return;
                }
            }

            DatabaseManager.PunishmentEntry punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishment(event.getUniqueId(), "ban");
            if (punishmentEntry == null) {
                punishmentEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(playerIP, "ban");
            }
            String punishmentId = (punishmentEntry != null) ? punishmentEntry.getPunishmentId() : "N/A";

            List<String> banScreenLines = plugin.getConfigManager().getBanScreen();
            String kickMessage = getKickMessage(banScreenLines, reason, timeLeft, punishmentId, expiration);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfigManager().isJoinAlertEnabled()) {
            List<DatabaseManager.PunishmentEntry> activePunishments = plugin.getSoftBanDatabaseManager()
                    .getAllActivePunishments(player.getUniqueId(), player.getAddress().getAddress().getHostAddress())
                    .stream()
                    .filter(p -> !p.getType().equalsIgnoreCase("freeze"))
                    .collect(Collectors.toList());

            if (!activePunishments.isEmpty()) {
                chatFrozenPlayers.add(player.getUniqueId());

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.active_punishments_header")));
                    for (DatabaseManager.PunishmentEntry punishment : activePunishments) {
                        String timeLeft = (punishment.getEndTime() == Long.MAX_VALUE) ? "Permanent" : TimeUtils.formatTime((int) ((punishment.getEndTime() - System.currentTimeMillis()) / 1000), plugin.getConfigManager());
                        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.active_punishment_entry",
                                "{id}", punishment.getPunishmentId(),
                                "{type}", punishment.getType(),
                                "{time_left}", timeLeft)));
                    }
                    player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.active_punishments_footer")));

                    TextComponent supportMessage = new TextComponent(TextComponent.fromLegacyText(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.support_link_message", "{support_link}", plugin.getConfigManager().getSupportLink()))));
                    supportMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://" + plugin.getConfigManager().getSupportLink()));
                    supportMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to open the support link").create()));
                    player.spigot().sendMessage(supportMessage);


                    String soundName = plugin.getConfigManager().getJoinAlertSound();
                    if (soundName != null && !soundName.isEmpty()) {
                        try {
                            Sound sound = Sound.valueOf(soundName.toUpperCase());
                            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid sound name configured for on-join-alert: " + soundName);
                        }
                    }

                }, 20L);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    chatFrozenPlayers.remove(player.getUniqueId());
                }, plugin.getConfigManager().getJoinAlertDuration() * 20L);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (chatFrozenPlayers.contains(event.getPlayer().getUniqueId())) {
            if (!event.getMessage().startsWith("/")) {
                event.setCancelled(true);
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
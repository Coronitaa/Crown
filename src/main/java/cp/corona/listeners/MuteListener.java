// MuteListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.logging.Level;

public class MuteListener implements Listener {

    private final Crown plugin;
    private final List<String> blockedCommands;

    public MuteListener(Crown plugin) {
        this.plugin = plugin;
        this.blockedCommands = plugin.getConfigManager().getBlockedMuteCommands();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSoftBanDatabaseManager().isMuted(player.getUniqueId()) ||
                (plugin.getConfigManager().isPunishmentByIp("mute") &&
                        plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(player.getAddress().getAddress().getHostAddress(), "mute") != null)) {
            event.setCancelled(true);
            String mutedMessage = plugin.getConfigManager().getMessage("messages.chat_while_muted");
            player.sendMessage(MessageUtils.getColorMessage(mutedMessage));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSoftBanDatabaseManager().isMuted(player.getUniqueId()) ||
                (plugin.getConfigManager().isPunishmentByIp("mute") &&
                        plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(player.getAddress().getAddress().getHostAddress(), "mute") != null)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
            if (blockedCommands.contains(command)) {
                event.setCancelled(true);
                String mutedMessage = plugin.getConfigManager().getMessage("messages.chat_while_muted");
                player.sendMessage(MessageUtils.getColorMessage(mutedMessage));
            }
        }
    }
}
// CommandBlockerListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.logging.Level;

public class CommandBlockerListener implements Listener {

    private final Crown plugin;
    private final List<String> blockedCommands;

    public CommandBlockerListener(Crown plugin) {
        this.plugin = plugin;
        this.blockedCommands = plugin.getConfigManager().getBlockedCommands();
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("crown.softban.bypass")) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "[CommandBlockerListener] Player " + player.getName() + " has 'crown.softban.bypass', bypassing command block.");
            }
            return;
        }

        DatabaseManager.PunishmentEntry softbanEntry = null;
        if (plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId())) {
            softbanEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishment(player.getUniqueId(), "softban");
        } else if (plugin.getConfigManager().isPunishmentByIp("softban")) {
            softbanEntry = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(player.getAddress().getAddress().getHostAddress(), "softban");
        }

        if (softbanEntry != null && (softbanEntry.getEndTime() > System.currentTimeMillis() || softbanEntry.getEndTime() == Long.MAX_VALUE)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "Player " + player.getName() + " is softbanned, attempting command: " + command);
            }

            for (String blockedCmd : blockedCommands) {
                if (command.equalsIgnoreCase(blockedCmd.toLowerCase())) {
                    event.setCancelled(true);
                    player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.softban_command_blocked")));
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().log(Level.INFO, "Command " + command + " BLOCKED for softbanned player " + player.getName());
                    }
                    return;
                }
            }
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "Command " + command + " NOT blocked for softbanned player " + player.getName() + ", command not in blocked list.");
            }

        } else {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "Player " + player.getName() + " is NOT softbanned.");
            }
        }
    }
}
// CommandBlockerListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.logging.Level;

/**
 * Listener to block commands for softbanned players.
 * Prevents softbanned players from executing configured blocked commands.
 */
public class CommandBlockerListener implements Listener {

    private final Crown plugin;
    private final List<String> blockedCommands;

    /**
     * Constructor for CommandBlockerListener.
     *
     * @param plugin Instance of the main plugin class.
     */
    public CommandBlockerListener(Crown plugin) {
        this.plugin = plugin;
        this.blockedCommands = plugin.getConfigManager().getBlockedCommands(); // Load blocked commands from config
    }

    /**
     * Handles the PlayerCommandPreprocessEvent to block commands for softbanned players.
     *
     * @param event The PlayerCommandPreprocessEvent.
     */
    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // Allow command execution if player has bypass permission
        if (player.hasPermission("crown.softban.bypass")) {
            // [FIX] Added debug log to confirm bypass permission is working for softban
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "[CommandBlockerListener] Player " + player.getName() + " has 'crown.softban.bypass', bypassing command block.");
            }
            return;
        }

        // Check if player is softbanned
        if (plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId()) ||
                (plugin.getConfigManager().isPunishmentByIp("softban") &&
                        plugin.getSoftBanDatabaseManager().getLatestActivePunishmentByIp(player.getAddress().getAddress().getHostAddress(), "softban") != null)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase(); // Extract command from message

            // Debug logging to show attempted command by softbanned player
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "Player " + player.getName() + " is softbanned, attempting command: " + command);
            }

            // Check if the command is in the blocked commands list
            for (String blockedCmd : blockedCommands) {
                if (command.equalsIgnoreCase(blockedCmd.toLowerCase())) { // Case-insensitive comparison
                    event.setCancelled(true); // Cancel the command execution
                    // Send message to player indicating command is blocked due to softban
                    player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.softban_command_blocked")));
                    // Send softban received message with reason
                    //player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.softban_received", "{reason}", plugin.getSoftBanDatabaseManager().getSoftBanReason(player.getUniqueId()))));
                    // Debug logging for blocked command
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().log(Level.INFO, "Command " + command + " BLOCKED for softbanned player " + player.getName());
                    }
                    return; // Exit to prevent further processing
                }
            }
            // Debug logging if command is not blocked (not in blocked list)
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "Command " + command + " NOT blocked for softbanned player " + player.getName() + ", command not in blocked list.");
            }

        } else {
            // Debug logging if player is not softbanned
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.INFO, "Player " + player.getName() + " is NOT softbanned.");
            }
        }
    }
}
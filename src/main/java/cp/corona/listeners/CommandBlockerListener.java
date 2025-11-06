// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\listeners\CommandBlockerListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class CommandBlockerListener implements Listener {

    private final Crown plugin;
    private final List<String> globalBlockedCommands;

    public CommandBlockerListener(Crown plugin) {
        this.plugin = plugin;
        this.globalBlockedCommands = plugin.getConfigManager().getBlockedCommands();
    }

    private boolean isPlayerSoftBanned(Player player) {
        Map<UUID, Long> softbanCache = plugin.getSoftBannedPlayersCache();
        Long endTime = softbanCache.get(player.getUniqueId());

        if (endTime != null) {
            if (endTime > System.currentTimeMillis()) {
                return true;
            } else {
                softbanCache.remove(player.getUniqueId());
                return false;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("crown.softban.bypass")) {
            return;
        }

        if (isPlayerSoftBanned(player)) {
            event.setCancelled(true); // Cancel immediately on the main thread

            // Asynchronously determine which command list to use and send the message
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<String> customBlockedCommands = plugin.getSoftBanDatabaseManager().getActiveSoftbanCustomCommands(player.getUniqueId());

                final List<String> commandsToBlock;
                final boolean usingCustomList;

                if (customBlockedCommands != null && !customBlockedCommands.isEmpty()) {
                    commandsToBlock = customBlockedCommands;
                    usingCustomList = true;
                } else {
                    commandsToBlock = this.globalBlockedCommands;
                    usingCustomList = false;
                }

                String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
                boolean isBlocked = commandsToBlock.stream().anyMatch(blockedCmd -> blockedCmd.equalsIgnoreCase(command));

                // Switch back to the main thread to interact with the player/event
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isBlocked) {
                        if (plugin.getConfigManager().isDebugEnabled()) {
                            plugin.getLogger().log(Level.INFO, "[CommandBlockerListener] Player " + player.getName() + " is softbanned. Using " + (usingCustomList ? "custom" : "global") + " command list. Command '" + command + "' BLOCKED.");
                        }
                        player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.softban_command_blocked")));
                    } else {
                        // If the command is not in the block list, we need to un-cancel the event.
                        // Note: This relies on the event being cancellable and not processed by other high-priority listeners yet.
                        event.setCancelled(false);
                        if (plugin.getConfigManager().isDebugEnabled()) {
                            plugin.getLogger().log(Level.INFO, "[CommandBlockerListener] Command '" + command + "' NOT blocked for softbanned player " + player.getName() + ", command not in the active blocked list.");
                        }
                    }
                });
            });
        }
    }
}
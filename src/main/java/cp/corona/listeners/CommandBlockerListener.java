package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class CommandBlockerListener implements Listener {

    private final Crown plugin;

    public CommandBlockerListener(Crown plugin) {
        this.plugin = plugin;
    }

    private boolean isPlayerSoftBanned(UUID playerUUID) {
        Map<UUID, Long> softbanCache = plugin.getSoftBannedPlayersCache();
        Long endTime = softbanCache.get(playerUUID);
        return endTime != null && endTime > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (player.hasPermission("crown.softban.bypass")) {
            return;
        }

        if (isPlayerSoftBanned(playerUUID)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
            List<String> blockedCommands = plugin.getSoftbannedCommandsCache().get(playerUUID);

            if (blockedCommands != null) {
                boolean isBlocked = blockedCommands.stream()
                        .anyMatch(blockedCmd -> blockedCmd.equalsIgnoreCase(command));

                if (isBlocked) {
                    event.setCancelled(true);
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().log(Level.INFO, "[CommandBlockerListener] Player " + player.getName() + " is softbanned. Command '" + command + "' BLOCKED.");
                    }
                    player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.softban_command_blocked")));
                    plugin.playSound(player, "softban_command_blocked");
                }
            }
        }
    }
}
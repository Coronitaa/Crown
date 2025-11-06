// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\listeners\MuteListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MuteListener implements Listener {

    private final Crown plugin;
    private final List<String> blockedCommands;

    public MuteListener(Crown plugin) {
        this.plugin = plugin;
        this.blockedCommands = plugin.getConfigManager().getBlockedMuteCommands();
    }

    private boolean isPlayerMuted(Player player) {
        Map<UUID, Long> muteCache = plugin.getMutedPlayersCache();
        Long endTime = muteCache.get(player.getUniqueId());

        if (endTime != null) {
            if (endTime > System.currentTimeMillis()) {
                return true; // Player is in cache and mute has not expired.
            } else {
                // Mute has expired, remove them from the cache for cleanup.
                muteCache.remove(player.getUniqueId());
                return false;
            }
        }
        // Player not in cache, so not muted.
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isPlayerMuted(player)) {
            event.setCancelled(true);
            String mutedMessage = plugin.getConfigManager().getMessage("messages.chat_while_muted");
            player.sendMessage(MessageUtils.getColorMessage(mutedMessage));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isPlayerMuted(player)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
            // Using a simple contains check is faster for Lists if they are small.
            // For very large lists, a Set would be more performant.
            if (blockedCommands.stream().anyMatch(blockedCmd -> blockedCmd.equalsIgnoreCase(command))) {
                event.setCancelled(true);
                String mutedMessage = plugin.getConfigManager().getMessage("messages.chat_while_muted");
                player.sendMessage(MessageUtils.getColorMessage(mutedMessage));
            }
        }
    }
}
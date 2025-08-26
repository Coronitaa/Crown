// src/main/java/cp/corona/listeners/PlayerChatListener.java
package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class PlayerChatListener implements Listener {

    private final Crown plugin;

    public PlayerChatListener(Crown plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSoftBanDatabaseManager().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            // Use the configurable message from messages.yml
            String mutedMessage = plugin.getConfigManager().getMessage("messages.chat_while_muted");
            player.sendMessage(MessageUtils.getColorMessage(mutedMessage));
        }
    }
}
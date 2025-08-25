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
            // Cancel the event to prevent the message from being sent
            event.setCancelled(true);
            // Optionally, send a message to the player informing them they are muted.
            // You can configure this message in your messages.yml
            player.sendMessage(MessageUtils.getColorMessage("&cYou are currently muted."));
        }
    }
}
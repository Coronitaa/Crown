package cp.corona.listeners;

import cp.corona.crown.Crown;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerChatListener implements Listener {

    private final Crown plugin;

    public PlayerChatListener(Crown plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String message = event.getMessage();

        String sql = "INSERT INTO player_chat_history (player_uuid, message) VALUES (?, ?)";

        try (Connection connection = plugin.getSoftBanDatabaseManager().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, playerUUID.toString());
            ps.setString(2, message);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not log chat message for player " + player.getName(), e);
        }
    }
}
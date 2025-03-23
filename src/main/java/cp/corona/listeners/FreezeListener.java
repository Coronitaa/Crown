package cp.corona.listeners;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener to handle the "freeze" punishment.
 * Prevents frozen players from moving, using commands, and handles actions on disconnect and chat.
 */
public class FreezeListener implements Listener {

    private final CrownPunishments plugin;

    /**
     * Constructor for FreezeListener.
     *
     * @param plugin Instance of the main plugin class.
     */
    public FreezeListener(CrownPunishments plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevents frozen players from moving.
     *
     * @param event The PlayerMoveEvent.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles actions when a frozen player disconnects, executing a permanent ban.
     *
     * @param event The PlayerQuitEvent.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            // Execute permanent ban command when frozen player disconnects
            new BukkitRunnable() {
                @Override
                public void run() {
                    String banCommand = plugin.getConfigManager().getBanCommand()
                            .replace("{target}", player.getName())
                            .replace("{time}", plugin.getConfigManager().getPluginConfig().getConfig().getString("freeze.disconnect_ban_time", "permanent")) // Corrected line 1 - Accessing config through CustomConfig and getter
                            .replace("{reason}", plugin.getConfigManager().getPluginConfig().getConfig().getString("freeze.disconnect_ban_reason", "Disconnected while frozen")); // Corrected line 2 - Accessing config through CustomConfig and getter
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), banCommand);
                }
            }.runTask(plugin);
            plugin.getLogger().warning("Frozen player " + player.getName() + " disconnected. Executing permanent ban.");
            plugin.getPluginFrozenPlayers().remove(player.getUniqueId()); // Remove player from frozen list on disconnect
        }
    }


    /**
     * Handles actions when a frozen player joins back, re-applying freeze if necessary.
     *
     * @param event The PlayerJoinEvent.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            // Re-apply freeze effects if needed on join (e.g., potion effects, messages)
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_frozen"))); // Removed prefix here
            // Optionally, re-apply immobility if PlayerMoveEvent is not sufficient across re-logins
        }
    }

    /**
     * Prevents frozen players from using commands.
     *
     * @param event The PlayerCommandPreprocessEvent.
     */
    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.freeze_command_blocked"))); // Removed prefix here
        }
    }

    /**
     * Handles player chat messages for frozen players, making chat admin-only and showing messages to frozen player.
     *
     * @param event The AsyncPlayerChatEvent.
     */
    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean isFrozen = plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId()); // Cache freeze status for efficiency

        if (isFrozen) {
            if (!player.hasPermission("crown.admin")) {
                event.setCancelled(true); // Cancel player message for non-admins

                String message = event.getMessage();
                String formattedAdminMessage = MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.frozen_player_chat_admin_only",
                        "{player}", player.getName(),
                        "{message}", message)); // Admin-only format

                // Send message only to admins
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("crown.admin"))
                        .forEach(admin -> admin.sendMessage(formattedAdminMessage));

                // Re-send the message to the frozen player so they can see their own message - NEW - CORRECTED: Send formattedAdminMessage instead of self-format
                player.sendMessage(formattedAdminMessage); // Re-send admin formatted message to frozen player - CORRECTED

            } else {
                // Admins when frozen - send messages only to other admins AND themselves
                String message = event.getMessage();
                String formattedAdminMessage = MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.frozen_player_chat_admin",
                        "{player}", player.getName(),
                        "{message}", message)); // Admin-all format

                // Send message to admins AND the admin who is frozen so they see their own admin chat too - MODIFIED
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("crown.admin"))
                        .forEach(admin -> admin.sendMessage(formattedAdminMessage));
                player.sendMessage(formattedAdminMessage); // Send to the admin player as well - NEW
            }
        } else {
            // Handling incoming chat for frozen players - Block non-admin chat to frozen players completely - No changes here
            if (plugin.getPluginFrozenPlayers().values().stream().anyMatch(Boolean::valueOf)) { // Check if any player is frozen

                Player recipient = event.getRecipients().stream().filter(plugin.getPluginFrozenPlayers()::containsKey).findFirst().orElse(null); // Find if any recipient is frozen
                if (recipient != null) {
                    if (!player.hasPermission("crown.admin")) {
                        event.setCancelled(true); // Cancel event for non-admins sending to frozen players - Complete block
                    }
                }
            }
        }
    }
}
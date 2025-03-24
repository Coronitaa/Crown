// listeners/FreezeListener.java
package cp.corona.listeners;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Listener to handle the "freeze" punishment.
 * Prevents frozen players from moving, using commands, and handles actions on disconnect and chat.
 *
 * **NEW:** Implemented repeated freeze actions using configuration.
 */
public class FreezeListener implements Listener {

    private final CrownPunishments plugin;
    private final HashMap<UUID, BukkitTask> freezeActionTasks = new HashMap<>(); // NEW: Track freeze action tasks per player

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
     * Stops freeze action task and removes player from frozen list.
     *
     * @param event The PlayerQuitEvent.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (plugin.getPluginFrozenPlayers().containsKey(playerId)) {
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

            stopFreezeActionsTask(playerId); // Stop freeze actions task when player quits - NEW
            plugin.getPluginFrozenPlayers().remove(playerId); // Remove player from frozen list on disconnect
        }
    }


    /**
     * Handles actions when a frozen player joins back, re-applying freeze if necessary.
     * Restarts freeze actions task if player is still frozen.
     *
     * @param event The PlayerJoinEvent.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (plugin.getPluginFrozenPlayers().containsKey(playerId)) {
            // Re-apply freeze effects if needed on join (e.g., potion effects, messages)
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_frozen"))); // Removed prefix here
            // Optionally, re-apply immobility if PlayerMoveEvent is not sufficient across re-logins

            startFreezeActionsTask(player); // Restart freeze actions task on join - NEW
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

    /**
     * Starts the repeating actions task for a frozen player. - NEW
     * Executes actions from config at configured interval.
     *
     * @param player The player to start freeze actions for.
     */
    public void startFreezeActionsTask(Player player) {
        UUID playerId = player.getUniqueId();
        if (freezeActionTasks.containsKey(playerId)) {
            plugin.getLogger().info("[DEBUG] Freeze actions task already running for player: " + player.getName()); // Debug: Check if task is already running
            return; // Task already running, avoid duplicates
        }

        int intervalTicks = plugin.getConfigManager().getFreezeActionsInterval(); // Get interval from config - NEW
        List<MenuItem.ClickActionData> actions = plugin.getConfigManager().loadFreezeActions(); // Load actions from config - NEW
        MenuListener menuListener = plugin.getMenuListener(); // Get MenuListener instance to execute actions - NEW

        if (actions.isEmpty()) {
            plugin.getLogger().warning("[WARNING] No freeze actions configured in config.yml. Task will not start."); // Warning if no actions are configured
            return;
        }

        plugin.getLogger().info("[DEBUG] Starting freeze actions task for player: " + player.getName() + ", interval: " + intervalTicks + " ticks, actions: " + actions.size()); // Debug log - task starting

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getPluginFrozenPlayers().containsKey(playerId)) {
                    plugin.getLogger().info("[DEBUG] Player is no longer frozen, stopping freeze actions task: " + player.getName()); // Debug log - player no longer frozen
                    stopFreezeActionsTask(playerId); // Stop task if player is no longer frozen - NEW
                    return;
                }
                plugin.getLogger().info("[DEBUG] Executing freeze actions for player: " + player.getName()); // Debug log - executing actions
                for (MenuItem.ClickActionData actionData : actions) {
                    plugin.getLogger().info("[DEBUG] Executing action: " + actionData.getAction() + ", data: " + Arrays.toString(actionData.getActionData())); // Debug log - individual action
                    menuListener.executeMenuItemAction(player, actionData.getAction(), actionData.getActionData()); // Execute each action for the player - NEW
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks); // Run task repeatedly - NEW

        freezeActionTasks.put(playerId, task); // Store task for player - NEW
        plugin.getLogger().info("[DEBUG] Freeze actions task started and stored for player: " + player.getName()); // Debug log - task started and stored
    }

    /**
     * Stops the repeating actions task for a player. - NEW
     * Cancels the BukkitTask if it is running and removes it from tracking.
     *
     * @param playerId The UUID of the player to stop freeze actions for.
     */
    public void stopFreezeActionsTask(UUID playerId) {
        if (freezeActionTasks.containsKey(playerId)) {
            BukkitTask task = freezeActionTasks.remove(playerId);
            if (task != null) {
                task.cancel(); // Cancel the task - NEW
            }
        }
    }
}
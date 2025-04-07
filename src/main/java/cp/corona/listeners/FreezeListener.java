// FreezeListener.java
package cp.corona.listeners;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent; // Import EntityDamageEvent - NEW
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
 * **NEW:**
 * - Implemented repeated freeze actions using configuration.
 * - Added invulnerability for frozen players.
 * - Applies freezing visual effect using Entity#setFreezeTicks. - MODIFIED: Using Freeze Ticks instead of PotionEffect
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
     * Makes frozen players invulnerable to damage. - NEW
     * Cancels damage events if the entity is a frozen player.
     *
     * @param event The EntityDamageEvent.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) { // Check if the entity is a player - Corrected instance check
            Player player = (Player) event.getEntity();
            if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
                event.setCancelled(true); // Cancel damage if player is frozen - NEW
                // Optional: Send a message to indicate they are frozen and cannot be damaged
                // player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.frozen_no_damage")));
            }
        }
    }

    /**
     * Handles actions when a frozen player disconnects, executing a permanent ban.
     * Stops freeze action task and removes player from frozen list.
     * Removes freezing visual effect upon disconnect. - MODIFIED: Removed potion effect, now removes freeze ticks
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
            removeFreezingEffect(player); // Remove freezing effect - MODIFIED: Call removeFreezingEffect (now freeze ticks)
        }
    }


    /**
     * Handles actions when a frozen player joins back, re-applying freeze if necessary.
     * Restarts freeze actions task if player is still frozen.
     * Applies freezing visual effect on join. - MODIFIED: Applied freeze ticks
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
            applyFreezingEffect(player); // Apply freezing effect - MODIFIED: Call applyFreezingEffect (now freeze ticks)
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
     * Applies freezing visual effect when starting the task. - MODIFIED: Apply effect here
     *
     * @param player The player to start freeze actions for.
     */
    public void startFreezeActionsTask(Player player) {
        UUID playerId = player.getUniqueId();
        if (freezeActionTasks.containsKey(playerId)) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Freeze actions task already running for player: " + player.getName()); // Debug: Check if task is already running
            return; // Task already running, avoid duplicates
        }

        int intervalTicks = plugin.getConfigManager().getFreezeActionsInterval(); // Get interval from config - NEW
        List<MenuItem.ClickActionData> actions = plugin.getConfigManager().loadFreezeActions(); // Load actions from config - NEW
        MenuListener menuListener = plugin.getMenuListener(); // Get MenuListener instance to execute actions - NEW

        if (actions.isEmpty()) {
            plugin.getLogger().warning("[WARNING] No freeze actions configured in config.yml. Task will not start."); // Warning if no actions are configured
            return;
        }

        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Starting freeze actions task for player: " + player.getName() + ", interval: " + intervalTicks + " ticks, actions: " + actions.size()); // Debug log - task starting

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getPluginFrozenPlayers().containsKey(playerId)) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Player is no longer frozen, stopping freeze actions task: " + player.getName()); // Debug log - player no longer frozen
                    stopFreezeActionsTask(playerId); // Stop task if player is no longer frozen - NEW
                    return;
                }
                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing freeze actions for player: " + player.getName()); // Debug log - executing actions
                for (MenuItem.ClickActionData actionData : actions) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing action: " + actionData.getAction() + ", data: " + Arrays.toString(actionData.getActionData())); // Debug log - individual action
                    menuListener.executeMenuItemAction(player, actionData.getAction(), actionData.getActionData()); // Execute each action for the player - NEW
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks); // Run task repeatedly - NEW

        freezeActionTasks.put(playerId, task); // Store task for player - NEW
        applyFreezingEffect(player); // Apply freezing effect when task starts - NEW - MODIFIED: Call applyFreezingEffect here
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Freeze actions task started and stored for player: " + player.getName()); // Debug log - task started and stored
    }
    /**
     * Stops the repeating actions task for a player. - NEW
     * Cancels the BukkitTask if it is running and removes it from tracking.
     * Removes freezing visual effect when stopping the task. - MODIFIED: Remove effect here
     *
     * @param playerId The UUID of the player to stop freeze actions for.
     */
    public void stopFreezeActionsTask(UUID playerId) {
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] stopFreezeActionsTask CALLED for playerId: " + playerId); // Debug log - ENTRY - NEW
        if (freezeActionTasks.containsKey(playerId)) {
            Player player = Bukkit.getPlayer(playerId); // Get player instance - NEW: Get player instance here
            if (player != null) { // Check if player is online - NEW: Check if player is online before removing effect
                removeFreezingEffect(player); // Remove freezing effect when task stops - NEW - MODIFIED: Call removeFreezingEffect here before task cancel
            } else {
                plugin.getLogger().warning("[WARNING] Player is offline, cannot remove freezing effect: " + playerId); // Warning log - player offline
            }
            BukkitTask task = freezeActionTasks.remove(playerId);
            if (task != null) {
                task.cancel(); // Cancel the task - NEW
                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Freeze actions task cancelled for playerId: " + playerId); // Debug log - task cancelled
            } else {
                plugin.getLogger().warning("[WARNING] Freeze actions task was null for playerId: " + playerId + ", cannot cancel."); // Warning log - task null
            }
        } else {
            plugin.getLogger().warning("[WARNING] No freeze actions task found to stop for playerId: " + playerId); // Warning log - task not found
        }
    }

    /**
     * Applies the freezing visual effect to a player using freeze ticks. - NEW - MODIFIED: Using freeze ticks
     *
     * @param player The player to apply the freezing effect to.
     */
    public void applyFreezingEffect(Player player) {
        player.setFreezeTicks(Integer.MAX_VALUE); // Fully freeze player visually - NEW - MODIFIED: Using setFreezeTicks
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Applied freezing effect to player: " + player.getName() + ", freeze ticks: " + player.getMaxFreezeTicks()); // Debug log - effect applied
    }

    /**
     * Removes the freezing visual effect from a player by resetting freeze ticks. - NEW - MODIFIED: Using freeze ticks
     *
     * @param player The player to remove the freezing effect from.
     */
    public void removeFreezingEffect(Player player) {
        player.setFreezeTicks(0); // Reset freeze ticks to remove effect - NEW - MODIFIED: Using setFreezeTicks
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Removed freezing effect from player: " + player.getName()); // Debug log - effect removed
    }
}
// FreezeListener.java
package cp.corona.listeners;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.ColorUtils;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
// import org.bukkit.GameMode; // No longer needed for gamemode changes
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // Import EventPriority
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent; // Import BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent; // Import EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent; // Import EntityPickupItemEvent
import org.bukkit.event.player.*; // Import all player events for simplicity here
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener to handle the "freeze" punishment.
 * Prevents frozen players from moving, using commands, chatting (non-mods),
 * interacting with the world (breaking, placing, using items, etc.), dealing damage,
 * dropping/picking up items, and handles actions on disconnect.
 *
 * **MODIFIED:**
 * - No longer changes GameMode. Instead, cancels specific interaction events.
 * - Added handlers for BlockBreakEvent, PlayerInteractEvent, EntityDamageByEntityEvent,
 *   PlayerDropItemEvent, EntityPickupItemEvent, PlayerItemConsumeEvent.
 */
public class FreezeListener implements Listener {

    private final CrownPunishments plugin;
    private final HashMap<UUID, BukkitTask> freezeActionTasks = new HashMap<>();

    /**
     * Constructor for FreezeListener.
     *
     * @param plugin Instance of the main plugin class.
     */
    public FreezeListener(CrownPunishments plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles actions when a frozen player disconnects.
     * Pre-processes and then executes a configured list of console commands after a 1-tick delay.
     * Stops freeze action task and removes player from frozen list.
     * Removes freezing visual effect upon disconnect.
     *
     * @param event The PlayerQuitEvent.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (plugin.getPluginFrozenPlayers().containsKey(playerId)) {

            final List<String> disconnectCommands = plugin.getConfigManager().getFreezeDisconnectCommands();
            final OfflinePlayer disconnectedPlayer = Bukkit.getOfflinePlayer(playerId);

            if (!disconnectCommands.isEmpty()) {
                final List<String> finalCommandsToExecute = new ArrayList<>();
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().log(Level.INFO, "[FreezeListener] Pre-processing " + disconnectCommands.size() + " disconnect commands for " + disconnectedPlayer.getName());
                }
                for (String commandTemplate : disconnectCommands) {
                    String processedCommand = plugin.getConfigManager().processPlaceholders(commandTemplate, disconnectedPlayer);
                    processedCommand = ColorUtils.translateRGBColors(processedCommand);
                    finalCommandsToExecute.add(processedCommand);
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().log(Level.INFO, "[FreezeListener]   -> Processed: \"" + processedCommand + "\"");
                    }
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (plugin.getConfigManager().isDebugEnabled()) {
                            plugin.getLogger().log(Level.INFO, "[FreezeListener] Executing " + finalCommandsToExecute.size() + " pre-processed disconnect commands for player " + disconnectedPlayer.getName() + " (after 1 tick delay)...");
                        }
                        for (String commandToExecute : finalCommandsToExecute) {
                            if (plugin.getConfigManager().isDebugEnabled()) {
                                plugin.getLogger().log(Level.INFO, "[FreezeListener] -> Dispatching: \"" + commandToExecute + "\"");
                            }
                            try {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.SEVERE, "[FreezeListener] Error dispatching command: \"" + commandToExecute + "\"", e);
                            }
                        }
                    }
                }.runTaskLater(plugin, 1L);

            } else {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().log(Level.INFO, "[FreezeListener] No disconnect commands configured in config.yml for frozen player quit.");
                }
            }

            player.setInvulnerable(false); // Still useful to remove invulnerability
            stopFreezeActionsTask(playerId);
            plugin.getPluginFrozenPlayers().remove(playerId);
            // Attempt to remove visual effects, may not apply if player is fully gone
            removeFreezingEffect(player);

            plugin.getLogger().info("Frozen player " + player.getName() + " disconnected. Disconnect actions scheduled (if configured).");
        }
    }


    /**
     * Handles actions when a frozen player joins back, re-applying freeze if necessary.
     * Restarts freeze actions task if player is still frozen.
     * Applies freezing visual effect on join.
     *
     * @param event The PlayerJoinEvent.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (plugin.getPluginFrozenPlayers().containsKey(playerId)) {
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.you_are_frozen")));
            startFreezeActionsTask(player); // Restarts task, applies visual effect + invulnerability
        }
    }

    /**
     * Prevents frozen players from using commands.
     *
     * @param event The PlayerCommandPreprocessEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST) // High priority to override others
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            // Allow essential commands if needed (e.g., /helpop, /report - check permissions)
            // String command = event.getMessage().split(" ")[0].toLowerCase();
            // if (command.equals("/helpop") || command.equals("/report")) { return; }

            event.setCancelled(true);
            player.sendMessage(MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.freeze_command_blocked")));
        }
    }

    /**
     * Prevents frozen players from moving.
     *
     * @param event The PlayerMoveEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Check if the movement is more than just looking around
        if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ())
        {
            Player player = event.getPlayer();
            if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
                // Teleport back to the 'from' location to prevent movement
                event.setTo(event.getFrom());
                // Optionally cancel, though teleporting back is often more effective
                // event.setCancelled(true);
            }
        }
    }

    /**
     * Handles player chat messages for frozen players, making chat admin-only and showing messages to frozen player.
     *
     * @param event The AsyncPlayerChatEvent.
     */
    @EventHandler(priority = EventPriority.HIGH) // High priority to catch before other chat plugins potentially
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean isFrozen = plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId());

        if (isFrozen) {
            if (!player.hasPermission("crown.mod")) {
                event.setCancelled(true);

                String message = event.getMessage();
                String formattedAdminMessage = MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.frozen_player_chat_admin_only",
                        "{player}", player.getName(),
                        "{message}", message));

                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("crown.mod"))
                        .forEach(admin -> admin.sendMessage(formattedAdminMessage));

                player.sendMessage(formattedAdminMessage); // Let the frozen player see what they typed (in admin format)

            } else { // Frozen player is an admin
                event.setCancelled(true); // Still cancel normal chat flow
                String message = event.getMessage();
                String formattedAdminMessage = MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.frozen_player_chat_admin",
                        "{player}", player.getName(),
                        "{message}", message));

                // Send only to other admins and the frozen admin themselves
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("crown.mod"))
                        .forEach(admin -> admin.sendMessage(formattedAdminMessage));
                // player.sendMessage(formattedAdminMessage); // They already see it via the stream targetting admins
            }
        } else {
            // Block non-admin chat *to* frozen players
            if (plugin.getPluginFrozenPlayers().values().stream().anyMatch(Boolean::valueOf)) {
                List<Player> recipients = new ArrayList<>(event.getRecipients());
                boolean removedAny = recipients.removeIf(recipient ->
                        plugin.getPluginFrozenPlayers().containsKey(recipient.getUniqueId()) &&
                                !player.hasPermission("crown.mod")
                );
                // If any frozen players were removed from recipients, update the event
                if(removedAny) {
                    event.getRecipients().clear();
                    event.getRecipients().addAll(recipients);
                    // Optionally inform sender: player.sendMessage(MessageUtils.getColorMessage("&cYou cannot send messages to frozen players."));
                }
            }
        }
    }

    /**
     * Prevents frozen players from breaking blocks.
     * @param event The BlockBreakEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            // Optional feedback message (can be spammy)
            // player.sendMessage(MessageUtils.getColorMessage("&cYou cannot break blocks while frozen."));
        }
    }

    /**
     * Prevents frozen players from dealing damage.
     * @param event The EntityDamageByEntityEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager) {
            if (plugin.getPluginFrozenPlayers().containsKey(damager.getUniqueId())) {
                event.setCancelled(true);
                // Optional feedback message
                // damager.sendMessage(MessageUtils.getColorMessage("&cYou cannot attack while frozen."));
            }
        }
    }

    /**
     * Prevents frozen players from interacting with blocks or using items.
     * @param event The PlayerInteractEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            // Optional feedback message
            // player.sendMessage(MessageUtils.getColorMessage("&cYou cannot interact while frozen."));
        }
    }

    /**
     * Prevents frozen players from consuming items (eating, drinking potions).
     * @param event The PlayerItemConsumeEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            // Optional feedback message
            // player.sendMessage(MessageUtils.getColorMessage("&cYou cannot consume items while frozen."));
        }
    }

    /**
     * Prevents frozen players from dropping items.
     * @param event The PlayerDropItemEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            // Optional feedback message
            // player.sendMessage(MessageUtils.getColorMessage("&cYou cannot drop items while frozen."));
        }
    }

    /**
     * Prevents frozen players from picking up items.
     * @param event The EntityPickupItemEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }


    /**
     * Starts the repeating actions task for a frozen player.
     * Executes actions from config at configured interval.
     * Applies freezing visual effect and invulnerability when starting the task.
     *
     * @param player The player to start freeze actions for.
     */
    public void startFreezeActionsTask(Player player) {
        UUID playerId = player.getUniqueId();
        if (freezeActionTasks.containsKey(playerId)) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Freeze actions task already running for player: " + player.getName());
            return;
        }

        int intervalTicks = plugin.getConfigManager().getFreezeActionsInterval();
        List<MenuItem.ClickActionData> actions = plugin.getConfigManager().loadFreezeActions();
        MenuListener menuListener = plugin.getMenuListener();
        player.setInvulnerable(true); // Make player invulnerable while frozen
        // player.setGameMode(GameMode.ADVENTURE); // REMOVED

        if (actions.isEmpty()) {
            plugin.getLogger().warning("[WARNING] No freeze actions configured in config.yml. Task will not start.");
            return; // Don't start task if no actions defined
        }

        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Starting freeze actions task for player: " + player.getName() + ", interval: " + intervalTicks + " ticks, actions: " + actions.size());

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getPluginFrozenPlayers().containsKey(playerId)) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Player is no longer frozen, stopping freeze actions task: " + player.getName());
                    this.cancel(); // Cancel this task
                    stopFreezeActionsTask(playerId); // Ensure full cleanup
                    return;
                }
                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Frozen player " + playerId + " is offline, skipping freeze actions execution.");
                    return; // Skip execution if player is offline
                }

                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing freeze actions for player: " + onlinePlayer.getName());
                for (MenuItem.ClickActionData actionData : actions) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing action: " + actionData.getAction() + ", data: " + Arrays.toString(actionData.getActionData()));
                    // Ensure the menuListener is not null before executing
                    if (menuListener != null) {
                        menuListener.executeMenuItemAction(onlinePlayer, actionData.getAction(), actionData.getActionData());
                    } else {
                        plugin.getLogger().warning("[FreezeListener] MenuListener is null, cannot execute freeze action: " + actionData.getAction());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);

        freezeActionTasks.put(playerId, task);
        applyFreezingEffect(player); // Apply visual effect
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Freeze actions task started and stored for player: " + player.getName());
    }

    /**
     * Stops the repeating actions task for a player.
     * Cancels the BukkitTask if it is running and removes it from tracking.
     * Removes freezing visual effect and invulnerability when stopping the task.
     *
     * @param playerId The UUID of the player to stop freeze actions for.
     */
    public void stopFreezeActionsTask(UUID playerId) {
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] stopFreezeActionsTask CALLED for playerId: " + playerId);

        BukkitTask task = freezeActionTasks.remove(playerId); // Remove first to prevent concurrent modification issues
        if (task != null && !task.isCancelled()) {
            task.cancel();
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Freeze actions task cancelled for playerId: " + playerId);
        } else {
            if (task == null && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("[WARNING] No freeze actions task found to stop for playerId: " + playerId);
            } // else task was already cancelled, which is fine
        }

        // Reset player state if they are online
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            removeFreezingEffect(player);
            player.setInvulnerable(false);
            // player.setGameMode(GameMode.SURVIVAL); // REMOVED
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().warning("[WARNING] Player is offline during stopFreezeActionsTask, cannot fully reset state: " + playerId);
        }
    }

    /**
     * Applies the freezing visual effect and invulnerability to a player.
     *
     * @param player The player to apply the freezing effect to.
     */
    public void applyFreezingEffect(Player player) {
        player.setFreezeTicks(Integer.MAX_VALUE); // Visual freeze
        player.setGlowing(true); // Make them glow for visibility
        player.setInvulnerable(true); // Prevent damage while frozen
        // player.setGameMode(GameMode.ADVENTURE); // REMOVED
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Applied freezing effect to player: " + player.getName());
    }

    /**
     * Removes the freezing visual effect and invulnerability from a player.
     *
     * @param player The player to remove the freezing effect from.
     */
    public void removeFreezingEffect(Player player) {
        if (player != null && player.isOnline()) { // Check if player is valid
            player.setFreezeTicks(0);
            player.setGlowing(false);
            player.setInvulnerable(false); // Ensure invulnerability is removed
            // player.setGameMode(GameMode.SURVIVAL); // REMOVED
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Removed freezing effect from player: " + player.getName());
        }
    }

    /**
     * Makes a player invulnerable if they are frozen (redundant if setInvulnerable(true) is used).
     * Kept for clarity or if specific damage types need different handling later.
     *
     * @param event The EntityDamageEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
                event.setCancelled(true); // Cancel all damage if player is frozen
            }
        }
    }
}
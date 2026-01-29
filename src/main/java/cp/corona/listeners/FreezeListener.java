package cp.corona.listeners;

import cp.corona.crown.Crown;
import cp.corona.menus.items.MenuItem;
import cp.corona.utils.ColorUtils;
import cp.corona.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Listener to handle the "freeze" punishment.
 * Prevents frozen players from moving, using commands, chatting (non-mods),
 * interacting with the world (breaking, placing, using items, etc.), dealing damage,
 * dropping/picking up items, and handles actions on disconnect.
 */
public class FreezeListener implements Listener {

    private final Crown plugin;
    private final HashMap<UUID, BukkitTask> freezeActionTasks = new HashMap<>();
    private final Map<UUID, FreezeChatSession> freezeChatSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> freezeChatIds = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> moderatorChatTargets = new ConcurrentHashMap<>();
    private static final String FREEZE_CHAT_PERMISSION = "crown.mod";

    private static class FreezeChatSession {
        private final UUID frozenId;
        private final String frozenName;
        private final String punishmentId;
        private final String punisherName;
        private final Set<UUID> participants = ConcurrentHashMap.newKeySet();

        private FreezeChatSession(UUID frozenId, String frozenName, String punishmentId, String punisherName) {
            this.frozenId = frozenId;
            this.frozenName = frozenName;
            this.punishmentId = punishmentId;
            this.punisherName = punisherName;
        }
    }

    public enum FreezeChatToggleResult {
        JOINED,
        LEFT,
        NOT_FOUND,
        NOT_ACTIVE
    }

    /**
     * Constructor for FreezeListener.
     *
     * @param plugin Instance of the main plugin class.
     */
    public FreezeListener(Crown plugin) {
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

            player.setInvulnerable(false);
            stopFreezeActionsTask(playerId);
            plugin.getPluginFrozenPlayers().remove(playerId);
            removeFreezingEffect(player);

            plugin.getLogger().info("Frozen player " + player.getName() + " disconnected. Disconnect actions scheduled (if configured).");
        }

        removeModeratorFromSessions(playerId);
        endFreezeChatSession(playerId);
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
            startFreezeActionsTask(player);
        }
    }

    /**
     * Prevents frozen players from using commands, except allowed ones.
     *
     * @param event The PlayerCommandPreprocessEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
            String message = event.getMessage();
            String command = message.split(" ")[0].substring(1);

            List<String> allowedCommands = plugin.getConfigManager().getFreezeAllowedCommands();
            for (String allowed : allowedCommands) {
                if (allowed.equalsIgnoreCase(command)) {
                    return;
                }
            }

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
        if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ())
        {
            Player player = event.getPlayer();
            if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
                event.setTo(event.getFrom());
            }
        }
    }

    /**
     * Handles player chat messages for frozen players, making chat admin-only and showing messages to frozen player.
     *
     * @param event The AsyncPlayerChatEvent.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean isFrozen = plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId());

        if (isFrozen) {
            event.setCancelled(true);
            FreezeChatSession session = freezeChatSessions.get(player.getUniqueId());
            String punishmentId = session != null && session.punishmentId != null ? session.punishmentId : "N/A";
            String message = event.getMessage();
            String formattedAdminMessage = MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.freeze_chat_frozen_format",
                    "{player}", player.getName(),
                    "{punishment_id}", punishmentId,
                    "{message}", message));
            Component chatComponent = buildFreezeChatMessage(session, formattedAdminMessage);
            sendFreezeChatToModerators(session, chatComponent);
            player.sendMessage(chatComponent);
        } else {
            if (player.hasPermission(FREEZE_CHAT_PERMISSION)) {
                UUID targetId = moderatorChatTargets.get(player.getUniqueId());
                if (targetId != null) {
                    FreezeChatSession session = freezeChatSessions.get(targetId);
                    if (session == null || !plugin.getPluginFrozenPlayers().containsKey(targetId)) {
                        moderatorChatTargets.remove(player.getUniqueId());
                        return;
                    }

                    event.setCancelled(true);
                    String message = event.getMessage();
                    String formattedAdminMessage = MessageUtils.getColorMessage(plugin.getConfigManager().getMessage("messages.freeze_chat_mod_format",
                            "{moderator}", player.getName(),
                            "{target}", session.frozenName,
                            "{message}", message));
                    Component chatComponent = buildFreezeChatMessage(session, formattedAdminMessage);
                    sendFreezeChatToModerators(session, chatComponent);

                    Player frozenPlayer = Bukkit.getPlayer(targetId);
                    if (frozenPlayer != null && frozenPlayer.isOnline()) {
                        frozenPlayer.sendMessage(chatComponent);
                    }
                    return;
                }
            }

            if (plugin.getPluginFrozenPlayers().values().stream().anyMatch(Boolean::valueOf)) {
                List<Player> recipients = new ArrayList<>(event.getRecipients());
                boolean removedAny = recipients.removeIf(recipient ->
                        plugin.getPluginFrozenPlayers().containsKey(recipient.getUniqueId())
                );
                if(removedAny) {
                    event.getRecipients().clear();
                    event.getRecipients().addAll(recipients);
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
        player.setInvulnerable(true);

        if (actions.isEmpty()) {
            plugin.getLogger().warning("[WARNING] No freeze actions configured in config.yml. Task will not start.");
            return;
        }

        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Starting freeze actions task for player: " + player.getName() + ", interval: " + intervalTicks + " ticks, actions: " + actions.size());

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getPluginFrozenPlayers().containsKey(playerId)) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Player is no longer frozen, stopping freeze actions task: " + player.getName());
                    this.cancel();
                    stopFreezeActionsTask(playerId);
                    return;
                }
                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Frozen player " + playerId + " is offline, skipping freeze actions execution.");
                    return;
                }

                if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing freeze actions for player: " + onlinePlayer.getName());
                for (MenuItem.ClickActionData actionData : actions) {
                    if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing action: " + actionData.getAction() + ", data: " + Arrays.toString(actionData.getActionData()));
                    if (menuListener != null) {
                        menuListener.executeMenuItemAction(onlinePlayer, actionData.getAction(), actionData.getActionData(), null);
                    } else {
                        plugin.getLogger().warning("[FreezeListener] MenuListener is null, cannot execute freeze action: " + actionData.getAction());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);

        freezeActionTasks.put(playerId, task);
        applyFreezingEffect(player);
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

        BukkitTask task = freezeActionTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Freeze actions task cancelled for playerId: " + playerId);
        } else {
            if (task == null && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("[WARNING] No freeze actions task found to stop for playerId: " + playerId);
            }
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            removeFreezingEffect(player);
            player.setInvulnerable(false);
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
        player.setFreezeTicks(Integer.MAX_VALUE);
        player.setGlowing(true);
        player.setInvulnerable(true);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Applied freezing effect to player: " + player.getName());
    }

    /**
     * Removes the freezing visual effect and invulnerability from a player.
     *
     * @param player The player to remove the freezing effect from.
     */
    public void removeFreezingEffect(Player player) {
        if (player != null && player.isOnline()) {
            player.setFreezeTicks(0);
            player.setGlowing(false);
            player.setInvulnerable(false);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Removed freezing effect from player: " + player.getName());
        }
    }

    public void startFreezeChatSession(CommandSender sender, Player frozenPlayer, String punishmentId) {
        if (frozenPlayer == null || !frozenPlayer.isOnline()) return;

        UUID frozenId = frozenPlayer.getUniqueId();
        endFreezeChatSession(frozenId);

        String punisherName = sender != null ? sender.getName() : "Unknown";
        FreezeChatSession session = new FreezeChatSession(frozenId, frozenPlayer.getName(), punishmentId, punisherName);

        if (sender instanceof Player moderator && moderator.hasPermission(FREEZE_CHAT_PERMISSION)) {
            removeModeratorFromSessions(moderator.getUniqueId());
            session.participants.add(moderator.getUniqueId());
            moderatorChatTargets.put(moderator.getUniqueId(), frozenId);
        }

        freezeChatSessions.put(frozenId, session);
        if (punishmentId != null && !punishmentId.isEmpty()) {
            freezeChatIds.computeIfAbsent(punishmentId, id -> ConcurrentHashMap.newKeySet()).add(frozenId);
        }
    }

    public void endFreezeChatSession(UUID frozenId) {
        FreezeChatSession session = freezeChatSessions.remove(frozenId);
        if (session == null) return;

        if (session.punishmentId != null && freezeChatIds.containsKey(session.punishmentId)) {
            Set<UUID> ids = freezeChatIds.get(session.punishmentId);
            ids.remove(frozenId);
            if (ids.isEmpty()) {
                freezeChatIds.remove(session.punishmentId);
            }
        }

        for (UUID participant : session.participants) {
            moderatorChatTargets.remove(participant, frozenId);
        }
        session.participants.clear();
    }

    private FreezeChatSession getFreezeChatSessionByInput(String input) {
        if (input == null || input.isEmpty()) return null;

        if (input.startsWith("#")) {
            String id = input.substring(1);
            Set<UUID> ids = freezeChatIds.getOrDefault(id, Collections.emptySet());
            for (UUID frozenId : ids) {
                FreezeChatSession session = freezeChatSessions.get(frozenId);
                if (session != null) return session;
            }
            return null;
        }

        for (FreezeChatSession session : freezeChatSessions.values()) {
            if (session.frozenName.equalsIgnoreCase(input)) {
                return session;
            }
        }
        return null;
    }

    private boolean toggleModeratorChat(Player moderator, FreezeChatSession session) {
        if (moderator == null || session == null) return false;

        UUID moderatorId = moderator.getUniqueId();
        UUID currentTarget = moderatorChatTargets.get(moderatorId);
        if (currentTarget != null && currentTarget.equals(session.frozenId)) {
            moderatorChatTargets.remove(moderatorId);
            session.participants.remove(moderatorId);
            return false;
        }

        removeModeratorFromSessions(moderatorId);
        session.participants.add(moderatorId);
        moderatorChatTargets.put(moderatorId, session.frozenId);
        return true;
    }

    public FreezeChatToggleResult toggleModeratorChat(Player moderator, String input) {
        FreezeChatSession session = getFreezeChatSessionByInput(input);
        if (session == null) return FreezeChatToggleResult.NOT_FOUND;
        if (!plugin.getPluginFrozenPlayers().containsKey(session.frozenId)) return FreezeChatToggleResult.NOT_ACTIVE;
        return toggleModeratorChat(moderator, session) ? FreezeChatToggleResult.JOINED : FreezeChatToggleResult.LEFT;
    }

    public List<String> getFreezeChatSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (FreezeChatSession session : freezeChatSessions.values()) {
            suggestions.add(session.frozenName);
            if (session.punishmentId != null && !session.punishmentId.isEmpty()) {
                suggestions.add("#" + session.punishmentId);
            }
        }
        return suggestions;
    }

    public String leaveModeratorChat(Player moderator) {
        if (moderator == null) return null;
        UUID currentTarget = moderatorChatTargets.get(moderator.getUniqueId());
        if (currentTarget == null) return null;
        FreezeChatSession session = freezeChatSessions.get(currentTarget);
        removeModeratorFromSessions(moderator.getUniqueId());
        return session != null ? session.frozenName : null;
    }

    private void removeModeratorFromSessions(UUID moderatorId) {
        UUID currentTarget = moderatorChatTargets.remove(moderatorId);
        if (currentTarget == null) return;

        FreezeChatSession session = freezeChatSessions.get(currentTarget);
        if (session != null) {
            session.participants.remove(moderatorId);
        }
    }

    private void sendFreezeChatToModerators(FreezeChatSession session, Component formattedMessage) {
        boolean privateChat = plugin.getConfigManager().isFreezeChatPrivate();
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!admin.hasPermission(FREEZE_CHAT_PERMISSION)) continue;
            if (privateChat && session != null && !session.participants.contains(admin.getUniqueId())) continue;
            admin.sendMessage(formattedMessage);
        }
    }

    private Component buildFreezeChatMessage(FreezeChatSession session, String formattedMessage) {
        Component messageComponent = MessageUtils.getColorComponent(formattedMessage);
        if (session == null) {
            return messageComponent;
        }

        String id = session.punishmentId != null ? session.punishmentId : "N/A";
        String hoverText = "<color:#b0b0b0>ID: <color:#ffea00>" + id + "</color>\n" +
                "Applied by: <color:#ffea00>" + session.punisherName + "</color>\n" +
                "Target: <color:#00c6ff>" + session.frozenName + "</color></color>";

        String joinCommand = "/fchat #" + id;
        return messageComponent
                .hoverEvent(HoverEvent.showText(MessageUtils.getColorComponent(hoverText)))
                .clickEvent(ClickEvent.suggestCommand(joinCommand));
    }

    /**
     * Makes a player invulnerable if they are frozen (redundant if setInvulnerable(true) is used).
     *
     * @param event The EntityDamageEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}

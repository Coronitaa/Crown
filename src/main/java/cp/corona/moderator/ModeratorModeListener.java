package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.menus.ProfileMenu;
import cp.corona.menus.ReportsMenu;
import cp.corona.menus.ToolSelectorMenu;
import cp.corona.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ModeratorModeListener implements Listener {

    private final Crown plugin;
    private final NamespacedKey toolIdKey;

    public ModeratorModeListener(Crown plugin) {
        this.plugin = plugin;
        this.toolIdKey = new NamespacedKey(plugin, "tool-id");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getModeratorModeManager().handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getModeratorModeManager().updateVanishedPlayerVisibility(event.getPlayer());
    }

    // --- INTERACTION BLOCKING ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onModInteract(PlayerInteractEvent event) {
        if (!plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) return;

        // Prevent clicking during transition
        if (plugin.getModeratorModeManager().isTransitioning(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Global interaction blocking logic (Physical world interactions)
        if (!plugin.getModeratorModeManager().isInteractionsAllowed(event.getPlayer().getUniqueId())) {
            // Cancel generic interactions, but allow tool logic (handled in HIGH priority)
            // Note: We cancel immediately, but tool logic manually checks clicked blocks/air
            if (event.getAction() == Action.PHYSICAL) {
                event.setCancelled(true);
                return;
            }
            // For click actions, we will cancel in the handler if it's not a tool use
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            if (!plugin.getModeratorModeManager().isInteractionsAllowed(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                if (!plugin.getModeratorModeManager().isInteractionsAllowed(player.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // --- TOOL LOGIC ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;
        if (!(event.getRightClicked() instanceof Player)) return;

        // Debounce check
        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) return;

        event.setCancelled(true); // Always cancel vanilla interaction
        Player target = (Player) event.getRightClicked();
        if (player.getUniqueId().equals(target.getUniqueId())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = getToolId(item);

        if (toolId == null && item.getType() == Material.AIR) {
            // Empty hand behavior
            if (player.isSneaking()) {
                plugin.getModeratorModeManager().setSelectedPlayer(player.getUniqueId(), target.getUniqueId());
                MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_player_selected", "{target}", target.getName());
            } else {
                new ProfileMenu(target.getUniqueId(), plugin).open(player);
            }
            return;
        }

        if (toolId != null) {
            handleEntityToolAction(player, target, toolId, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageTool(EntityDamageByEntityEvent event) {
        // Handle Left-Click on entity with tool
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;

        // Debounce
        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) return;

        Player target = (Player) event.getEntity();
        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = getToolId(item);

        if (toolId != null) {
            handleEntityToolAction(player, target, toolId, false); // False = Left Click
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractTool(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;

        // If transitioning, block everything
        if (plugin.getModeratorModeManager().isTransitioning(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Debounce check for air/block clicks
        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) {
            // If interaction blocked globally, ensure event is cancelled even if debounce skipped logic
            if(!plugin.getModeratorModeManager().isInteractionsAllowed(player.getUniqueId())) event.setCancelled(true);
            return;
        }

        // Exit Spectator handling
        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || (event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                exitSpectatorMode(player);
            }
            event.setCancelled(true);
            return;
        }

        // Handle inventory clicks in world (e.g. chest) if allowed
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getState() instanceof InventoryHolder) {
                if (plugin.getModeratorModeManager().isInteractionsAllowed(player.getUniqueId())) {
                    // Let vanilla handle it or open manually? Let vanilla handle if allowed.
                    // But if we have a tool, we might want tool logic instead.
                    // Priority: Tool > Block Interaction
                } else {
                    // Blocked interaction, but maybe we want to silent-open?
                    // Requirement: "anula las interacciones... lo mismo con bloques"
                    event.setUseInteractedBlock(Event.Result.DENY);
                    event.setCancelled(true);
                }
            }
        }

        ItemStack item = event.getItem();
        String toolId = getToolId(item);
        if (toolId == null) return;

        event.setCancelled(true); // Tools don't do vanilla actions
        handleToolAction(player, toolId, event.getAction());
    }

    private String getToolId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
    }

    private void handleToolAction(Player player, String toolId, Action action) {
        boolean isRight = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean isLeft = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

        switch (toolId) {
            case "vanish_spectator_tool":
                if (isRight) {
                    if (player.isSneaking()) toggleVanish(player); else enterSpectatorMode(player);
                }
                break;
            case "player_selector_tool":
                if (isRight) {
                    if (player.isSneaking()) clearSelection(player); else requestPlayerSelection(player);
                } else if (isLeft) {
                    openSelectedPlayerProfile(player);
                }
                break;
            case "tool_selector":
                if (isRight) {
                    new ToolSelectorMenu(plugin, player).open();
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
                }
                break;
            case "interaction_tool":
                if (isRight) plugin.getModeratorModeManager().toggleInteractions(player);
                break;
            case "reports_viewer":
                if (isRight) new ReportsMenu(plugin, player).open(player);
                break;
            case "random_tp_tool":
                if (isRight) {
                    if (player.isSneaking()) {
                        handleRandomTeleport(player);
                    } else {
                        Player selected = plugin.getModeratorModeManager().getSelectedPlayer(player.getUniqueId());
                        if (selected != null) teleportTo(player, selected);
                        else handleRandomTeleport(player);
                    }
                }
                break;
            case "teleport_tool":
                if (isRight) handleAscendTeleport(player);
                else if (isLeft) handlePhaseTeleport(player);
                break;
            case "freeze_player_tool":
                // Logic handled in specific entity interact event or here for RayTrace/Selected
                handleFreezeToolLogic(player, isRight, isLeft);
                break;
            case "invsee_tool":
                if (isRight) openSelectedPlayerProfile(player);
                break;
        }
    }

    private void handleEntityToolAction(Player player, Player target, String toolId, boolean isRightClick) {
        // Direct click on entity overrides selection/raytrace
        switch (toolId) {
            case "freeze_player_tool":
                boolean shift = player.isSneaking();
                if (shift && isRightClick) executeFreeze(player, target); // Shift+R: Freeze target
                else if (isRightClick) executeFreeze(player, target); // R-Click: Freeze target
                else if (shift && !isRightClick) executeUnfreeze(player, target); // Shift+L: Unfreeze target
                else executeUnfreeze(player, target); // L-Click: Unfreeze target
                break;
            case "invsee_tool":
                new ProfileMenu(target.getUniqueId(), plugin).open(player);
                MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_invsee_success", "{target}", target.getName());
                break;
        }
    }

    private void handleFreezeToolLogic(Player player, boolean isRight, boolean isLeft) {
        boolean shift = player.isSneaking();
        Player target = null;

        // Priority 1: Shift + Click (RayTrace/Target) - Ignores Selection
        if (shift) {
            target = rayTracePlayer(player);
            if (target == null) {
                // If no target in sight, check selection as fallback? Request says "ignore selection" for shift
                // But if purely air, do nothing or show error?
                // Request: "shift+click derecho a pesar de tener a un jugador seleccionado, permitiendo así congelar a alguien más"
                // Implies: Shift uses Cursor Target.
                if (target == null) {
                    sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_no_target"));
                    return;
                }
            }
        }
        // Priority 2: Standard Click - Use Selection, Fallback to Cursor
        else {
            target = plugin.getModeratorModeManager().getSelectedPlayer(player.getUniqueId());
            if (target == null) {
                target = rayTracePlayer(player);
            }
        }

        if (target == null) {
            // "si no hay ningún jugador seleccionado debe poder funcionar sólo si está apuntando aun jugador"
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_no_target"));
            return;
        }

        if (isRight) executeFreeze(player, target);
        else if (isLeft) executeUnfreeze(player, target);
    }

    private Player rayTracePlayer(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 10, e -> e instanceof Player && !e.getUniqueId().equals(player.getUniqueId()));
        if (result != null && result.getHitEntity() instanceof Player) {
            return (Player) result.getHitEntity();
        }
        return null;
    }

    private void executeFreeze(Player moderator, Player target) {
        if (plugin.getPluginFrozenPlayers().containsKey(target.getUniqueId())) {
            MessageUtils.sendConfigMessage(plugin, moderator, "messages.already_frozen", "{target}", target.getName());
            return;
        }
        // Use console dispatcher to ensure DB consistency and use configured reasons
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crown punish " + target.getName() + " freeze Staff Tool");
        MessageUtils.sendConfigMessage(plugin, moderator, "messages.mod_mode_freeze_success", "{target}", target.getName());
    }

    private void executeUnfreeze(Player moderator, Player target) {
        if (!plugin.getPluginFrozenPlayers().containsKey(target.getUniqueId())) {
            MessageUtils.sendConfigMessage(plugin, moderator, "messages.no_active_freeze", "{target}", target.getName());
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crown unpunish " + target.getName() + " freeze Staff Tool");
        MessageUtils.sendConfigMessage(plugin, moderator, "messages.unfreeze_success", "{target}", target.getName());
    }

    private void enterSpectatorMode(Player player) {
        plugin.getModeratorModeManager().setTransitioning(player.getUniqueId(), true);
        boolean wasVanished = plugin.getModeratorModeManager().isVanished(player.getUniqueId());
        plugin.getModeratorModeManager().savePreSpectatorVanishState(player.getUniqueId(), wasVanished);

        if(wasVanished) plugin.getModeratorModeManager().unvanishPlayer(player); // Unvanish to ensure clean spectator state if needed by plugins, or just logic consistency

        player.setGameMode(GameMode.SPECTATOR);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_spectator_on");

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getModeratorModeManager().setTransitioning(player.getUniqueId(), false);
                // Auto-exit handled? Request says: "durante esos 3 segundos debe bloquearse... luego debe volver al estado anterior"
                // This implies temporary spectator? OR just blocking interaction for 3s?
                // "si se cambia al modo espectador, durante esos 3 segundos debe bloquearse... además, luego debe volver al estado anterior"
                // Assuming it means: "Transition animation/lock for 3s, then STAY in spectator until exit, OR it's a 3s peek?"
                // Given standard mod tools, usually it's a toggle. But the wording "luego debe volver al estado anterior" suggests a temporary peek or just restoring vanish state ON EXIT.
                // Let's assume it means "After the transition/blocking period ends (or when switching BACK), restore state".
                // Re-reading: "durante esos 3 segundos debe bloquearse... además, luego debe volver al estado anterior (visible o invisible)"
                // This phrasing usually implies the ACTION of switching involves a 3s block.
                // I will implement: 3s block. User manually exits. On exit, restore state.
            }
        }.runTaskLater(plugin, 60L); // 3 seconds lock
        plugin.getModeratorModeManager().addSpectatorTask(player.getUniqueId(), task);
    }

    private void exitSpectatorMode(Player player) {
        plugin.getModeratorModeManager().cancelAndRemoveSpectatorTask(player.getUniqueId());
        plugin.getModeratorModeManager().setTransitioning(player.getUniqueId(), false); // Ensure lock removed
        if (player.getGameMode() != GameMode.SPECTATOR) return;

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);

        Boolean wasVanished = plugin.getModeratorModeManager().getPreSpectatorVanishState(player.getUniqueId());
        if (wasVanished != null && wasVanished) {
            plugin.getModeratorModeManager().vanishPlayer(player);
        } else {
            plugin.getModeratorModeManager().unvanishPlayer(player);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
    }

    private void toggleVanish(Player player) {
        if (plugin.getModeratorModeManager().isVanished(player.getUniqueId())) {
            plugin.getModeratorModeManager().unvanishPlayer(player);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_vanish_off");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.01);
        } else {
            plugin.getModeratorModeManager().vanishPlayer(player);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_vanish_on");
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 0.5f);
            player.getWorld().spawnParticle(Particle.ELDER_GUARDIAN, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.01);
        }
    }

    private void requestPlayerSelection(Player player) {
        plugin.getModeratorModeManager().setAwaitingInput(player, "select_player");
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_prompt_player_name");
    }

    private void openSelectedPlayerProfile(Player player) {
        Player selected = plugin.getModeratorModeManager().getSelectedPlayer(player.getUniqueId());
        if (selected == null) {
            // Attempt RayTrace if no selection
            selected = rayTracePlayer(player);
        }

        if (selected != null) {
            new ProfileMenu(selected.getUniqueId(), plugin).open(player);
        } else {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_no_player_selected");
        }
    }

    private void clearSelection(Player player) {
        plugin.getModeratorModeManager().clearSelectedPlayer(player.getUniqueId());
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_selection_cleared");
    }

    private void handleAscendTeleport(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 100);
        if (result != null && result.getHitBlock() != null) {
            Location tpLoc = result.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
            tpLoc.setYaw(player.getLocation().getYaw());
            tpLoc.setPitch(player.getLocation().getPitch());
            player.teleport(tpLoc);
            player.playSound(tpLoc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 0.8f, 1.2f);
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_surface_tp"));
        } else {
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_no_target"));
        }
    }

    private void handlePhaseTeleport(Player player) {
        RayTraceResult result = player.rayTraceBlocks(100);
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) {
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_no_target"));
            return;
        }

        Block targetBlock = result.getHitBlock();
        Block destinationBlock = targetBlock.getRelative(result.getHitBlockFace());
        Location tpLoc = destinationBlock.getLocation().add(0.5, 0, 0.5);

        if (isSafeLocation(tpLoc)) {
            tpLoc.setYaw(player.getLocation().getYaw());
            tpLoc.setPitch(player.getLocation().getPitch());
            player.teleport(tpLoc);
            player.playSound(tpLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.5f);
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_phase_tp"));
        } else {
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_no_target"));
        }
    }

    private boolean isSafeLocation(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid();
    }

    private void handleRandomTeleport(Player player) {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("crown.mod.use") && !p.getUniqueId().equals(player.getUniqueId())) {
                candidates.add(p);
            }
        }

        if (candidates.isEmpty()) {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_no_players_tp");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        teleportTo(player, target);
    }

    private void teleportTo(Player player, Player target) {
        player.teleport(target.getLocation());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_random_tp", "{target}", target.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getModeratorModeManager().getInputType(player) != null) {
            event.setCancelled(true);
            String input = event.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> handleChatInput(player, input));
        }
    }

    private void handleChatInput(Player player, String input) {
        String inputType = plugin.getModeratorModeManager().getInputType(player);
        if (inputType == null) return;

        plugin.getModeratorModeManager().clearAwaitingInput(player);

        if (input.equalsIgnoreCase("cancel")) {
            MessageUtils.sendConfigMessage(plugin, player, "messages.input_cancelled");
            return;
        }

        if ("select_player".equals(inputType)) {
            Player target = Bukkit.getPlayer(input);
            if (target == null) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_player_not_found", "{target}", input);
                return;
            }
            if (player.getUniqueId().equals(target.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            plugin.getModeratorModeManager().setSelectedPlayer(player.getUniqueId(), target.getUniqueId());
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_player_selected", "{target}", target.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof ToolSelectorMenu) {
            handleToolSelectorClick(event, player);
            return;
        }

        if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                event.setCancelled(true);
            }
        }
    }

    private void handleToolSelectorClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String toolId = meta.getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
        if (toolId == null) return;

        if (toolId.equals("player_selector_tool")) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ItemStack tool = plugin.getModeratorModeManager().getModeratorTools().get(toolId);
        if (tool == null) return;

        // Logic to swap/set item in hotbar
        for (int i = 0; i <= 8; i++) {
            ItemStack hotbarItem = player.getInventory().getItem(i);
            if (hotbarItem != null && hotbarItem.hasItemMeta()) {
                String hotbarToolId = hotbarItem.getItemMeta().getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
                if (toolId.equals(hotbarToolId)) {
                    player.getInventory().setItem(i, null);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
                    new ToolSelectorMenu(plugin, player).open();
                    return;
                }
            }
        }

        for (int i = 1; i <= 8; i++) { // Skip slot 0 (Selector)
            if (player.getInventory().getItem(i) == null) {
                player.getInventory().setItem(i, tool);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                new ToolSelectorMenu(plugin, player).open();
                return;
            }
        }

        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_hotbar_full");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    // --- GENERIC EVENT BLOCKERS ---

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                event.setCancelled(true);
                player.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player)) return;
        Player target = (Player) event.getTarget();
        if (plugin.getModeratorModeManager().isVanished(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(MessageUtils.getColorMessage(message)));
    }
}
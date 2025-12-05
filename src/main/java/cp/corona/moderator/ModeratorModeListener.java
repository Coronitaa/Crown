package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.menus.*;
import cp.corona.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ModeratorModeListener implements Listener {

    private final Crown plugin;
    private final NamespacedKey toolIdKey;
    private final Set<Material> inspectableContainers;

    public ModeratorModeListener(Crown plugin) {
        this.plugin = plugin;
        this.toolIdKey = new NamespacedKey(plugin, "tool-id");
        this.inspectableContainers = Set.of(
                Material.CHEST,
                Material.TRAPPED_CHEST,
                Material.BARREL,
                Material.SHULKER_BOX,
                Material.WHITE_SHULKER_BOX,
                Material.ORANGE_SHULKER_BOX,
                Material.MAGENTA_SHULKER_BOX,
                Material.LIGHT_BLUE_SHULKER_BOX,
                Material.YELLOW_SHULKER_BOX,
                Material.LIME_SHULKER_BOX,
                Material.PINK_SHULKER_BOX,
                Material.GRAY_SHULKER_BOX,
                Material.LIGHT_GRAY_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX,
                Material.PURPLE_SHULKER_BOX,
                Material.BLUE_SHULKER_BOX,
                Material.BROWN_SHULKER_BOX,
                Material.GREEN_SHULKER_BOX,
                Material.RED_SHULKER_BOX,
                Material.BLACK_SHULKER_BOX
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getModeratorModeManager().handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getModeratorModeManager().updateVanishedPlayerVisibility(event.getPlayer());
    }

    // --- INTERACTION BLOCKING & CONTAINER INSPECTION ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onModInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;

        if (plugin.getModeratorModeManager().isTemporarySpectator(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Silent Container Inspection Logic
        // Checks preference now instead of always forcing
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && inspectableContainers.contains(clickedBlock.getType())) {

                // Only hijack if Container Spy is enabled
                if (plugin.getModeratorModeManager().isContainerSpyEnabled(player.getUniqueId())) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    // Don't interfere if they are using a specific tool (handled later)
                    if (item.getType() == Material.AIR || !item.getType().isBlock()) {
                        event.setCancelled(true); // Stop physical open
                        handleContainerInspection(player, clickedBlock);
                        return;
                    }
                }
            }
        }

        // Standard Interaction Blocking based on Persistence Preference
        if (!plugin.getModeratorModeManager().isInteractionsAllowed(player.getUniqueId())) {
            if (event.getAction() == Action.PHYSICAL) {
                event.setCancelled(true);
                return;
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.ALLOW);
        }
    }

    private void handleContainerInspection(Player player, Block block) {
        BlockState state = block.getState();
        if (state instanceof Container container) {
            Inventory realInventory = container.getInventory();
            int size = realInventory.getSize();
            String title = MessageUtils.getColorMessage("&8Inspect: " + block.getType().name());
            Inventory inspectionInv = Bukkit.createInventory(new InspectionHolder(), size, title);

            ItemStack[] contents = realInventory.getContents();
            ItemStack[] safeContents = new ItemStack[contents.length];
            for(int i=0; i<contents.length; i++) {
                if(contents[i] != null) safeContents[i] = contents[i].clone();
            }
            inspectionInv.setContents(safeContents);

            player.openInventory(inspectionInv);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_inspecting_container", "{container}", block.getType().name().replace("_", " "));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
            if (plugin.getModeratorModeManager().isTemporarySpectator(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            if (!plugin.getModeratorModeManager().isInteractionsAllowed(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                if (plugin.getModeratorModeManager().isTemporarySpectator(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
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

        if (plugin.getModeratorModeManager().isTemporarySpectator(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getRightClicked() instanceof Player)) return;

        // Force check permissions only for tools, interactions logic is separate
        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) return;

        event.setCancelled(true);
        Player target = (Player) event.getRightClicked();
        if (player.getUniqueId().equals(target.getUniqueId())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = getToolId(item);

        if (toolId == null && item.getType() == Material.AIR) {
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
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;

        if (plugin.getModeratorModeManager().isTemporarySpectator(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) return;

        Player target = (Player) event.getEntity();
        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = getToolId(item);

        if (toolId != null) {
            handleEntityToolAction(player, target, toolId, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractTool(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;

        if (plugin.getModeratorModeManager().isTemporarySpectator(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = event.getItem();
        String toolId = getToolId(item);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getState() instanceof InventoryHolder) {
                if (!inspectableContainers.contains(clickedBlock.getType())) {
                    if (!plugin.getModeratorModeManager().isInteractionsAllowed(player.getUniqueId())) {
                        event.setUseInteractedBlock(Event.Result.DENY);
                        event.setCancelled(true);
                    }
                }
            }
        }

        if (toolId == null) return;

        event.setCancelled(true);
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
                    if (player.isSneaking()) {
                        toggleVanish(player);
                    } else {
                        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_spectator_on");
                        plugin.getModeratorModeManager().enterSpectatorMode(player);
                    }
                }
                break;
            case "player_selector_tool":
                if (isRight) {
                    if (player.isSneaking()) clearSelection(player); else requestPlayerSelection(player);
                } else if (isLeft) {
                    if (player.isSneaking()) {
                        openSelectedPlayerPunishMenu(player);
                    } else {
                        openSelectedPlayerProfile(player);
                    }
                }
                break;
            case "tool_selector":
                if (isRight) {
                    new ToolSelectorMenu(plugin, player).open();
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
                }
                break;
            // NEW: Settings Tool Logic
            case "settings_tool":
                if (isRight) {
                    new ModSettingsMenu(plugin, player).open();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
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
                if (isRight) handleBoostTeleport(player);
                else if (isLeft) handleSurfaceTeleport(player);
                break;
            case "freeze_player_tool":
                handleFreezeToolLogic(player, isRight, isLeft);
                break;
            case "invsee_tool":
                if (isRight) openSelectedPlayerProfile(player);
                break;
        }
    }

    private void handleEntityToolAction(Player player, Player target, String toolId, boolean isRightClick) {
        switch (toolId) {
            case "freeze_player_tool":
                boolean shift = player.isSneaking();
                if (isRightClick) {
                    if (shift) executeFreeze(player, target);
                    else executeFreeze(player, target);
                } else {
                    if (shift) executeUnfreeze(player, target);
                    else executeUnfreeze(player, target);
                }
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

        if (shift) {
            target = rayTracePlayer(player);
            if (target == null) {
                sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_no_target"));
                return;
            }
        } else {
            target = plugin.getModeratorModeManager().getSelectedPlayer(player.getUniqueId());
            if (target == null) {
                target = rayTracePlayer(player);
            }
        }

        if (target == null) {
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
        if (selected == null) selected = rayTracePlayer(player);

        if (selected != null) {
            new ProfileMenu(selected.getUniqueId(), plugin).open(player);
        } else {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_no_player_selected");
        }
    }

    private void openSelectedPlayerPunishMenu(Player player) {
        Player selected = plugin.getModeratorModeManager().getSelectedPlayer(player.getUniqueId());
        if (selected == null) selected = rayTracePlayer(player);

        if (selected != null) {
            new PunishMenu(selected.getUniqueId(), plugin).open(player);
        } else {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_no_player_selected");
        }
    }

    private void clearSelection(Player player) {
        plugin.getModeratorModeManager().clearSelectedPlayer(player.getUniqueId());
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_selection_cleared");
    }

    private void handleBoostTeleport(Player player) {
        Vector direction = player.getLocation().getDirection();
        player.setVelocity(direction.multiply(3.0));
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
    }

    private void handleSurfaceTeleport(Player player) {
        RayTraceResult result = player.rayTraceBlocks(100);
        if (result != null && result.getHitBlock() != null) {
            Block block = result.getHitBlock();
            Location tpLoc = block.getLocation().add(0.5, 1.0, 0.5);
            tpLoc.setYaw(player.getLocation().getYaw());
            tpLoc.setPitch(player.getLocation().getPitch());
            player.teleport(tpLoc);
            player.playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_surface_tp"));
        } else {
            sendActionBar(player, plugin.getConfigManager().getMessage("messages.mod_mode_feedback_no_target"));
        }
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

        // NEW: Handle Settings Menu clicks
        if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof ModSettingsMenu) {
            handleSettingsMenuClick(event, player);
            return;
        }

        // Prevent modification of the virtual inspection inventory
        if (event.getInventory().getHolder() instanceof InspectionHolder) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                event.setCancelled(true);
            }
        }
    }

    private void handleSettingsMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        // Map slot indices to actions based on default config structure or check names/meta
        int slot = event.getSlot();
        int interactionsSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.interactions.slot");
        int containerSpySlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.container-spy.slot");

        if (slot == interactionsSlot) {
            plugin.getModeratorModeManager().toggleInteractions(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open(); // Refresh menu
        } else if (slot == containerSpySlot) {
            plugin.getModeratorModeManager().toggleContainerSpy(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open(); // Refresh menu
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Prevent modification via drag in the virtual inspection inventory
        if (event.getInventory().getHolder() instanceof InspectionHolder) {
            event.setCancelled(true);
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

        for (int i = 1; i <= 8; i++) {
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

    private static class InspectionHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }
}
package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.menus.mod.ModSettingsMenu;
import cp.corona.menus.mod.ToolSelectorMenu;
import cp.corona.menus.profile.AuditLogBook;
import cp.corona.menus.profile.ProfileMenu;
import cp.corona.menus.punish.PunishMenu;
import cp.corona.menus.report.ReportsMenu;
import cp.corona.utils.MessageUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.jetbrains.annotations.NotNull;

public class ModeratorModeListener implements Listener {

    private final Crown plugin;
    private final NamespacedKey toolIdKey;

    private final Set<Material> silentInspectableContainers;
    private final Set<Material> blockedGuiBlocks;

    private final Map<UUID, ClickData> inspectionDoubleClicks = new HashMap<>();
    private final Map<UUID, BukkitTask> inspectionTasks = new HashMap<>();
    private record ClickData(int slot, long timestamp) {}

    public ModeratorModeListener(Crown plugin) {
        this.plugin = plugin;
        this.toolIdKey = new NamespacedKey(plugin, "tool-id");

        this.silentInspectableContainers = Set.of(
                Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
                Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
                Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
                Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
                Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
                Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
                Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
                Material.DISPENSER, Material.DROPPER, Material.HOPPER,
                Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
                Material.BREWING_STAND
        );

        this.blockedGuiBlocks = Set.of(
                Material.ENDER_CHEST,
                Material.CRAFTING_TABLE,
                Material.ENCHANTING_TABLE,
                Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
                Material.BEACON,
                Material.LOOM,
                Material.CARTOGRAPHY_TABLE,
                Material.GRINDSTONE,
                Material.STONECUTTER,
                Material.SMITHING_TABLE
        );
    }

    // --- JOIN / QUIT / PING LOGIC ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getModeratorModeManager().isSilent(player.getUniqueId())) {
            event.quitMessage(null);
        }
        plugin.getModeratorModeManager().handleDisconnect(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check for crash recovery first
        plugin.getModeratorModeManager().checkAndRestoreSession(player);
        
        plugin.getModeratorModeManager().updateVanishedPlayerVisibility(player);

        Component originalJoinMessage = event.joinMessage();
        event.joinMessage(null);

        if (player.hasPermission("crown.mod.use")) {
            plugin.getSoftBanDatabaseManager().getModPreferences(player.getUniqueId()).thenAccept(prefs -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                boolean silent = prefs.isSilent();
                boolean modOnJoin = prefs.isModOnJoin();

                if (modOnJoin) {
                    plugin.getModeratorModeManager().enableModeratorMode(player, silent);
                    if (!silent && originalJoinMessage != null) {
                        Bukkit.broadcast(originalJoinMessage);
                    }
                } else {
                    if (originalJoinMessage != null) {
                        Bukkit.broadcast(originalJoinMessage);
                    }
                }
            }));
        } else {
            if (originalJoinMessage != null) {
                event.joinMessage(originalJoinMessage);
            }
        }
    }

    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        event.getListedPlayers().removeIf(p -> plugin.getModeratorModeManager().isSilent(p.id()));
        
        // Adjust online player count for silent players
        int silentCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getModeratorModeManager().isSilent(p.getUniqueId())) {
                silentCount++;
            }
        }
        event.setNumPlayers(event.getNumPlayers() - silentCount);
    }

    // --- INTERACTION BLOCKING & CONTAINER INSPECTION ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onModInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getModeratorModeManager().isInModeratorMode(uuid)) return;

        if (plugin.getModeratorModeManager().isTemporarySpectator(uuid)) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null) {
                Material type = clickedBlock.getType();

                // 1. Silent Inspection for Supported Containers
                if (silentInspectableContainers.contains(type)) {
                    boolean spyEnabled = plugin.getModeratorModeManager().isContainerSpyEnabled(uuid);
                    if (spyEnabled) {
                        ItemStack item = player.getInventory().getItemInMainHand();
                        if (item.getType() == Material.AIR || !item.getType().isBlock()) {
                            event.setCancelled(true); // Prevent physical open
                            if (clickedBlock.getState() instanceof InventoryHolder holder) {
                                handleInventoryInspection(player, holder.getInventory(), type.name());
                            }
                            return;
                        }
                    } else {
                        // Spy Disabled: Block access entirely to prevent noise
                        event.setCancelled(true);
                        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_container_spy_disabled");
                        return;
                    }
                }

                // 2. Strict Block for non-inspectable GUIs (EnderChest, CraftingTable, etc.)
                if (blockedGuiBlocks.contains(type)) {
                    event.setCancelled(true);
                    return; // Simply block, no message needed or optional "Cannot use in mod mode"
                }
            }
        }

        // 3. Standard Interaction Blocking (Physical touches, non-GUI blocks)
        if (!plugin.getModeratorModeManager().isInteractionsAllowed(uuid)) {
            if (event.getAction() == Action.PHYSICAL) {
                event.setCancelled(true);
                return;
            }
            // Deny block interaction but allow item use (e.g. tools)
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.ALLOW);
        }
    }

    private void handleInventoryInspection(Player player, Inventory realInventory, String typeName) {
        int size = realInventory.getSize();
        Location loc = null;
        if (realInventory.getHolder() instanceof BlockState bs) loc = bs.getLocation();
        else if (realInventory.getHolder() instanceof Entity e) loc = e.getLocation();

        Inventory inspectionInv;
        Component title = LegacyComponentSerializer.legacySection().deserialize(MessageUtils.getColorMessage("&8Inspect: " + typeName.replace("_", " ")));
        
        InspectionHolder holder = new InspectionHolder(realInventory, loc);
        if (realInventory.getType() == InventoryType.CHEST) {
            inspectionInv = Bukkit.createInventory(holder, size, title);
        } else {
            try {
                inspectionInv = Bukkit.createInventory(holder, realInventory.getType(), title);
            } catch (IllegalArgumentException e) {
                int safeSize = (int) (Math.ceil(size / 9.0) * 9);
                inspectionInv = Bukkit.createInventory(holder, safeSize, title);
            }
        }
        
        holder.setInspectionInventory(inspectionInv);

        player.openInventory(inspectionInv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_inspecting_container", "{container}", typeName.replace("_", " "));

        // Start real-time update task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, holder::update, 1L, 1L);
        inspectionTasks.put(player.getUniqueId(), task);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InspectionHolder) {
            BukkitTask task = inspectionTasks.remove(event.getPlayer().getUniqueId());
            if (task != null) {
                task.cancel();
            }
            inspectionDoubleClicks.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getModeratorModeManager().isInModeratorMode(uuid)) {
            if (plugin.getModeratorModeManager().isTemporarySpectator(uuid)) {
                event.setCancelled(true);
                return;
            }

            Entity entity = event.getRightClicked();

            // 4. Universal Entity Inventory Inspection (Minecarts w/ Chest, Mules, etc.)
            // EXCLUDING PLAYERS (handled by profile tool)
            if (entity instanceof InventoryHolder holder && !(entity instanceof Player)) {
                boolean spyEnabled = plugin.getModeratorModeManager().isContainerSpyEnabled(uuid);

                event.setCancelled(true);
                if (spyEnabled) {
                    handleInventoryInspection(player, holder.getInventory(), entity.getType().name());
                } else {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_container_spy_disabled");
                }
                return;
            }

            if (!plugin.getModeratorModeManager().isInteractionsAllowed(uuid)) {
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

                // Block breaking storage entities if interactions are disabled OR spy is disabled
                if (event.getEntity() instanceof InventoryHolder && !(event.getEntity() instanceof Player)) {
                    if (!plugin.getModeratorModeManager().isInteractionsAllowed(player.getUniqueId())
                            || !plugin.getModeratorModeManager().isContainerSpyEnabled(player.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
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

        if (!(event.getRightClicked() instanceof Player target)) return;

        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) return;

        event.setCancelled(true);
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
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof Player target)) return;
        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;

        if (plugin.getModeratorModeManager().isTemporarySpectator(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getModeratorModeManager().canInteract(player.getUniqueId())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = getToolId(item);

        if (toolId != null) {
            handleEntityToolAction(player, target, toolId, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractTool(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.getModeratorModeManager().isInModeratorMode(uuid)) return;

        if (plugin.getModeratorModeManager().isTemporarySpectator(uuid)) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getModeratorModeManager().canInteract(uuid)) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = event.getItem();
        String toolId = getToolId(item);

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
                if (isRightClick) {
                    executeFreeze(player, target);
                } else {
                    executeUnfreeze(player, target);
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
        Player target;

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
    public void onAsyncPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getModeratorModeManager().getInputType(player) != null) {
            event.setCancelled(true);
            String input = LegacyComponentSerializer.legacySection().serialize(event.message());
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
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof ToolSelectorMenu) {
            handleToolSelectorClick(event, player);
            return;
        }

        // Handle Settings Menu clicks
        if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof ModSettingsMenu) {
            handleSettingsMenuClick(event, player);
            return;
        }

        // Prevent modification of the virtual inspection inventory
        if (event.getInventory().getHolder() instanceof InspectionHolder) {
            onInspectionClick(event);
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
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        // Find which item was clicked by iterating through config keys
        // This is less efficient than slot mapping but more flexible with the new config structure
        // Alternatively, we could store the key in the item's persistent data container
        
        // For now, let's try to identify by slot since we have access to the config
        String clickedKey = null;
        for (String key : plugin.getConfigManager().getSettingsMenuConfig().getConfig().getConfigurationSection("menu.items").getKeys(false)) {
            List<Integer> slots = plugin.getConfigManager().getSettingsMenuItemConfig(key).getSlots();
            if (slots.contains(event.getSlot())) {
                clickedKey = key;
                break;
            }
        }
        
        if (clickedKey == null) return;

        switch (clickedKey) {
            case "interactions":
                plugin.getModeratorModeManager().toggleInteractions(player);
                break;
            case "container-spy":
                plugin.getModeratorModeManager().toggleContainerSpy(player);
                break;
            case "fly":
                plugin.getModeratorModeManager().toggleFly(player);
                break;
            case "mod-on-join":
                plugin.getModeratorModeManager().toggleModOnJoin(player);
                break;
            case "silent":
                plugin.getModeratorModeManager().toggleSilent(player);
                break;
            case "glowing":
                plugin.getModeratorModeManager().toggleGlowing(player);
                break;
            case "night-vision":
                plugin.getModeratorModeManager().toggleNightVision(player);
                break;
            case "walk-speed":
                if (event.getClick() == ClickType.LEFT) {
                    plugin.getModeratorModeManager().modifyWalkSpeed(player, 0.25f);
                } else if (event.getClick() == ClickType.RIGHT) {
                    plugin.getModeratorModeManager().modifyWalkSpeed(player, -0.25f);
                } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                    plugin.getModeratorModeManager().resetWalkSpeed(player);
                }
                break;
            case "fly-speed":
                if (event.getClick() == ClickType.LEFT) {
                    plugin.getModeratorModeManager().modifyFlySpeed(player, 0.25f);
                } else if (event.getClick() == ClickType.RIGHT) {
                    plugin.getModeratorModeManager().modifyFlySpeed(player, -0.25f);
                } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                    plugin.getModeratorModeManager().resetFlySpeed(player);
                }
                break;
            case "jump-boost":
                if (event.getClick() == ClickType.LEFT) {
                    plugin.getModeratorModeManager().modifyJumpMultiplier(player, 0.5f);
                } else if (event.getClick() == ClickType.RIGHT) {
                    plugin.getModeratorModeManager().modifyJumpMultiplier(player, -0.5f);
                } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                    plugin.getModeratorModeManager().resetJumpMultiplier(player);
                }
                break;
        }
        
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        new ModSettingsMenu(plugin, player).open();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
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

        // Right-click to add/remove from favorites
        if (event.getClick() == ClickType.RIGHT) {
            plugin.getModeratorModeManager().toggleFavoriteTool(player, toolId);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            new ToolSelectorMenu(plugin, player).open(); // Re-open to show changes
            return;
        }

        // Left-click to add/remove from hotbar for the current session
        if (event.getClick() == ClickType.LEFT) {
            ItemStack tool = plugin.getModeratorModeManager().getModeratorTools().get(toolId);
            if (tool == null) return;

            // Check if tool is already in hotbar (slots 1-7) and remove it
            for (int i = 1; i <= 7; i++) {
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

            // Add tool to the first available slot (1-7)
            for (int i = 1; i <= 7; i++) {
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
        if (event.getEntity() instanceof Player player) {
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
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
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player target)) return;
        
        if (plugin.getModeratorModeManager().isInModeratorMode(target.getUniqueId())) {
             if (event.getEntity() instanceof ExperienceOrb) {
                 event.setCancelled(true);
                 event.setTarget(null);
             }
        }

        if (plugin.getModeratorModeManager().isVanished(target.getUniqueId())) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setAmount(0);
        }
    }

    @EventHandler
    public void onPlayerPickupExperience(PlayerPickupExperienceEvent event) {
        if (plugin.getModeratorModeManager().isInModeratorMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void sendActionBar(Player player, String message) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(MessageUtils.getColorMessage(message)));
    }

    public static class InspectionHolder implements InventoryHolder {
        private final Inventory original;
        private final Location location;
        private Inventory inspectionInventory;

        public InspectionHolder(Inventory original, Location location) {
            this.original = original;
            this.location = location;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inspectionInventory;
        }

        public Inventory getOriginalInventory() {
            return original;
        }

        public Location getLocation() {
            return location;
        }

        public void setInspectionInventory(Inventory inspectionInventory) {
            this.inspectionInventory = inspectionInventory;
        }

        public void update() {
            if (inspectionInventory != null) {
                inspectionInventory.setContents(original.getContents());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInspectionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof InspectionHolder inspectionHolder) {
            // Prevent any modification to the inspection inventory
            event.setCancelled(true);

            // Allow confiscation logic only for clicks inside the top inventory
            if (Objects.equals(event.getClickedInventory(), topInventory)) {
                handleConfiscateLogic(event, player, inspectionHolder);
            }
        }
    }

    private void handleConfiscateLogic(InventoryClickEvent event, Player player, InspectionHolder inspectionHolder) {
        if (event.getClick() != ClickType.LEFT) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID playerUUID = player.getUniqueId();
        ClickData lastClick = inspectionDoubleClicks.get(playerUUID);
        long now = System.currentTimeMillis();
        int currentSlot = event.getSlot();

        // If clicking a different slot, reset the confirmation for the new slot
        if (lastClick == null || lastClick.slot() != currentSlot) {
            inspectionDoubleClicks.put(playerUUID, new ClickData(currentSlot, now));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
            MessageUtils.sendConfigMessage(plugin, player, "messages.confiscate_confirm");
            return;
        }

        // If clicking the same slot, check the time
        long diff = now - lastClick.timestamp();
        if (diff < 200) return; // Debounce

        if (diff <= 2000) { // Confirm within 2 seconds
            // Update timestamp to prevent double-firing events from triggering "new click" logic
            // and to debounce subsequent clicks while item is being removed.
            inspectionDoubleClicks.put(playerUUID, new ClickData(currentSlot, now));

            Inventory original = inspectionHolder.getOriginalInventory();
            ItemStack realItem = original.getItem(currentSlot);

            if (realItem == null || realItem.getType() == Material.AIR) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.confiscate_fail_gone");
                return;
            }

            String serialized = AuditLogBook.serialize(realItem);
            String containerType = LegacyComponentSerializer.legacySection().serialize(event.getView().title()).replace("Inspect: ", "");
            Location loc = inspectionHolder.getLocation();
            if (loc != null) {
                containerType += " (" + loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
            }

            plugin.getSoftBanDatabaseManager().addConfiscatedItem(serialized, playerUUID, containerType)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        original.setItem(currentSlot, null);
                        // The inventory will update automatically via the BukkitTask
                        MessageUtils.sendConfigMessage(plugin, player, "messages.item_confiscated");
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
                    }));
        } else {
            // If time is over 2 seconds, treat it as a new first click
            inspectionDoubleClicks.put(playerUUID, new ClickData(currentSlot, now));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
            MessageUtils.sendConfigMessage(plugin, player, "messages.confiscate_confirm");
        }
    }
}
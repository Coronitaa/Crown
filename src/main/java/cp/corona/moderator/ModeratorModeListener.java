package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.menus.*;
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
import org.bukkit.event.inventory.ClickType;
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
        
        // Pass location to constructor
        if (realInventory.getType() == InventoryType.CHEST) {
            inspectionInv = Bukkit.createInventory(new InspectionHolder(realInventory, loc), size, title);
        } else {
            try {
                inspectionInv = Bukkit.createInventory(new InspectionHolder(realInventory, loc), realInventory.getType(), title);
            } catch (IllegalArgumentException e) {
                int safeSize = (int) (Math.ceil(size / 9.0) * 9);
                inspectionInv = Bukkit.createInventory(new InspectionHolder(realInventory, loc), safeSize, title);
            }
        }

        ItemStack[] contents = realInventory.getContents();
        for(int i=0; i<contents.length; i++) {
            if(contents[i] != null) inspectionInv.setItem(i, contents[i].clone());
        }

        player.openInventory(inspectionInv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_inspecting_container", "{container}", typeName.replace("_", " "));
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
                    return;
                } else {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_container_spy_disabled");
                    return;
                }
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
        int slot = event.getSlot();

        int interactionsSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.interactions.slot");
        int containerSpySlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.container-spy.slot");
        int flySlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.fly.slot");
        int modOnJoinSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.mod-on-join.slot");
        int silentSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.silent.slot");
        int walkSpeedSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.walk-speed.slot");
        int flySpeedSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.fly-speed.slot");
        int jumpBoostSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.jump-boost.slot");
        int nightVisionSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.night-vision.slot");
        int glowingSlot = plugin.getConfigManager().getModModeConfig().getConfig().getInt("mod-settings-menu.items.glowing.slot");

        if (slot == interactionsSlot) {
            plugin.getModeratorModeManager().toggleInteractions(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == containerSpySlot) {
            plugin.getModeratorModeManager().toggleContainerSpy(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == flySlot) {
            plugin.getModeratorModeManager().toggleFly(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == modOnJoinSlot) {
            plugin.getModeratorModeManager().toggleModOnJoin(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == silentSlot) {
            plugin.getModeratorModeManager().toggleSilent(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == glowingSlot) {
            plugin.getModeratorModeManager().toggleGlowing(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == walkSpeedSlot) {
            if (event.getClick() == ClickType.LEFT) {
                plugin.getModeratorModeManager().modifyWalkSpeed(player, 0.25f);
            } else if (event.getClick() == ClickType.RIGHT) {
                plugin.getModeratorModeManager().modifyWalkSpeed(player, -0.25f);
            } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                plugin.getModeratorModeManager().resetWalkSpeed(player);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == flySpeedSlot) {
            if (event.getClick() == ClickType.LEFT) {
                plugin.getModeratorModeManager().modifyFlySpeed(player, 0.25f);
            } else if (event.getClick() == ClickType.RIGHT) {
                plugin.getModeratorModeManager().modifyFlySpeed(player, -0.25f);
            } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                plugin.getModeratorModeManager().resetFlySpeed(player);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == jumpBoostSlot) {
            if (event.getClick() == ClickType.LEFT) {
                plugin.getModeratorModeManager().modifyJumpMultiplier(player, 0.5f);
            } else if (event.getClick() == ClickType.RIGHT) {
                plugin.getModeratorModeManager().modifyJumpMultiplier(player, -0.5f);
            } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                plugin.getModeratorModeManager().resetJumpMultiplier(player);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        } else if (slot == nightVisionSlot) {
            plugin.getModeratorModeManager().toggleNightVision(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ModSettingsMenu(plugin, player).open();
        }
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

    public record InspectionHolder(Inventory original, Location location) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return original; }
        public Location getLocation() { return location; }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInspectionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof InspectionHolder inspectionHolder)) return;

        event.setCancelled(true); // Ensure Read-Only

        if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) return;

        if (event.getClick() == ClickType.LEFT) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            ClickData lastClick = inspectionDoubleClicks.get(player.getUniqueId());
            long now = System.currentTimeMillis();

            if (lastClick != null && lastClick.slot() == event.getSlot()) {
                long diff = now - lastClick.timestamp();

                // DEBOUNCE: Ignorar si el segundo click es en menos de 500ms
                if (diff < 500) {
                    return; 
                }

                if (diff < 3000) {
                    inspectionDoubleClicks.remove(player.getUniqueId());
                    
                    Inventory original = inspectionHolder.getInventory();
                    ItemStack realItem = original.getItem(event.getSlot());
                    
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
                    
                    plugin.getSoftBanDatabaseManager().addConfiscatedItem(serialized, player.getUniqueId(), containerType)
                            .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                                original.setItem(event.getSlot(), null);
                                event.getInventory().setItem(event.getSlot(), null); // Borrar de la vista
                                MessageUtils.sendConfigMessage(plugin, player, "messages.item_confiscated");
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
                            }));
                    return;
                }
            }


            inspectionDoubleClicks.put(player.getUniqueId(), new ClickData(event.getSlot(), now));
            
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
            MessageUtils.sendConfigMessage(plugin, player, "messages.confiscate_confirm");
        }
    }
}

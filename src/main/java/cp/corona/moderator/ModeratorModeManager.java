package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Sound;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ModeratorModeManager {

    private final Crown plugin;
    private final Map<UUID, PlayerState> savedStates = new ConcurrentHashMap<>();
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Map<String, ItemStack> moderatorTools = new HashMap<>();
    private final NamespacedKey toolIdKey;

    private final Map<UUID, UUID> selectedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> spectatorTasks = new ConcurrentHashMap<>();
    private final Map<UUID, String> awaitingInput = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastInteraction = new ConcurrentHashMap<>();

    // Stores active preferences for online moderators
    private final Map<UUID, ModPreferenceData> activePreferences = new ConcurrentHashMap<>();

    private final Map<UUID, Long> spectatorExpirations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> preSpectatorVanishState = new ConcurrentHashMap<>();

    public ModeratorModeManager(Crown plugin) {
        this.plugin = plugin;
        this.toolIdKey = new NamespacedKey(plugin, "tool-id");
        loadModeratorItems();
    }

    private void loadModeratorItems() {
        moderatorTools.clear();
        ConfigurationSection inventorySection = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("moderator-inventory");
        if (inventorySection != null) loadItemsFromSection(inventorySection);
        ConfigurationSection selectorSection = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("tool-selector-menu.items");
        if (selectorSection != null) loadItemsFromSection(selectorSection);
    }

    private void loadItemsFromSection(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            String toolId = section.getString(key + ".tool-id");
            if (toolId == null || moderatorTools.containsKey(toolId)) continue;

            Material material = Material.matchMaterial(section.getString(key + ".material", "STONE"));
            ItemStack item = new ItemStack(material != null ? material : Material.STONE);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(MessageUtils.getColorMessage(section.getString(key + ".name")));
                meta.setLore(section.getStringList(key + ".lore").stream().map(MessageUtils::getColorMessage).collect(Collectors.toList()));
                meta.getPersistentDataContainer().set(toolIdKey, PersistentDataType.STRING, toolId);
                item.setItemMeta(meta);
                moderatorTools.put(toolId, item);
            }
        }
    }

    public void toggleModeratorMode(Player player) {
        if (savedStates.containsKey(player.getUniqueId())) disableModeratorMode(player, false);
        else enableModeratorMode(player, false, false);
    }

    // Overload for automatic enabling (Listener)
    public void enableModeratorMode(Player player, boolean isSilentPref) {
        enableModeratorMode(player, isSilentPref, true);
    }

    // Main enable method
    public void enableModeratorMode(Player player, boolean knownSilentState, boolean isAutoJoin) {
        if (savedStates.containsKey(player.getUniqueId())) return; // Already enabled

        // Save current state (Inventory, GameMode, Location, etc.)
        savedStates.put(player.getUniqueId(), new PlayerState(player));

        // Load preferences from DB (or use defaults if new)
        plugin.getSoftBanDatabaseManager().getModPreferences(player.getUniqueId()).thenAccept(dbPrefs -> {
            ModPreferenceData prefs = new ModPreferenceData(
                    dbPrefs.isInteractions(),
                    dbPrefs.isContainerSpy(),
                    dbPrefs.isFlyEnabled(),
                    dbPrefs.isModOnJoin(),
                    dbPrefs.isSilent()
            );
            activePreferences.put(player.getUniqueId(), prefs);

            // Apply preferences on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && isInModeratorMode(player.getUniqueId())) {
                    // Apply GameMode based on Interactions
                    player.setGameMode(prefs.isInteractions() ? GameMode.SURVIVAL : GameMode.ADVENTURE);

                    // Apply Flight based on Fly Enabled
                    player.setAllowFlight(prefs.isFlyEnabled());
                    player.setFlying(prefs.isFlyEnabled());

                    // Handle Silent Mode Messages
                    if (prefs.isSilent()) {
                        // If enabled via command (manual toggle), simulate leave.
                        // If enabled via AutoJoin, the listener already suppressed the real join, so we do nothing here.
                        if (!isAutoJoin) {
                            broadcastFakeQuit(player);
                        }
                    }
                }
            });
        });

        // Setup Moderator Inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Default state before prefs load (safety defaults)
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setSilent(true);

        ConfigurationSection inventorySection = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("moderator-inventory");
        if (inventorySection != null) {
            inventorySection.getKeys(false).forEach(key -> {
                String toolId = inventorySection.getString(key + ".tool-id");
                int slot = inventorySection.getInt(key + ".slot");
                if (moderatorTools.containsKey(toolId)) {
                    player.getInventory().setItem(slot, moderatorTools.get(toolId));
                }
            });
        }

        vanishPlayer(player);

        // Notify player
        String msgKey = (isAutoJoin && knownSilentState) ? "messages.mod_mode_enabled_silent" : "messages.mod_mode_enabled";
        MessageUtils.sendConfigMessage(plugin, player, msgKey);
    }

    private void disableModeratorMode(Player player, boolean isDisconnecting) {
        boolean wasSilent = isSilent(player.getUniqueId());

        PlayerState state = savedStates.remove(player.getUniqueId());
        if (state != null) {
            unvanishPlayer(player);
            // CRITICAL FIX: Always restore state on the player object.
            // If disconnecting, this puts the items back in the player's inventory
            // so Bukkit saves them to disk.
            state.restore(player);
        }

        clearSelectedPlayer(player.getUniqueId());
        cancelAndRemoveSpectatorTask(player.getUniqueId());
        awaitingInput.remove(player.getUniqueId());
        lastInteraction.remove(player.getUniqueId());
        activePreferences.remove(player.getUniqueId());
        spectatorExpirations.remove(player.getUniqueId());
        preSpectatorVanishState.remove(player.getUniqueId());

        // Only send chat feedback if player is still online (not disconnecting)
        if (!isDisconnecting && player.isOnline()) {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_disabled");

            // If player was silent and is manually disabling mod mode, simulate a join
            if (wasSilent) {
                broadcastFakeJoin(player);
            }
        }
    }

    public void handleDisconnect(Player player) {
        if (isInModeratorMode(player.getUniqueId())) {
            disableModeratorMode(player, true); // True = Disconnecting
        }
    }

    // --- Fake Messages ---
    private void broadcastFakeJoin(Player player) {
        String msg = plugin.getConfigManager().getMessage("messages.mod_mode_fake_join");
        if (plugin.isPlaceholderAPIEnabled()) {
            msg = PlaceholderAPI.setPlaceholders(player, msg);
        }
        msg = msg.replace("{player}", player.getName());
        Bukkit.broadcastMessage(MessageUtils.getColorMessage(msg));
    }

    private void broadcastFakeQuit(Player player) {
        String msg = plugin.getConfigManager().getMessage("messages.mod_mode_fake_quit");
        if (plugin.isPlaceholderAPIEnabled()) {
            msg = PlaceholderAPI.setPlaceholders(player, msg);
        }
        msg = msg.replace("{player}", player.getName());
        Bukkit.broadcastMessage(MessageUtils.getColorMessage(msg));
    }

    // --- Preference Toggles & Logic ---

    private void updateAndSavePreferences(UUID uuid, ModPreferenceData prefs) {
        activePreferences.put(uuid, prefs);
        plugin.getSoftBanDatabaseManager().saveModPreferences(
                uuid,
                prefs.isInteractions(),
                prefs.isContainerSpy(),
                prefs.isFlyEnabled(),
                prefs.isModOnJoin(),
                prefs.isSilent()
        );
    }

    public void toggleInteractions(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, new ModPreferenceData(false, true, true, false, false));
        boolean newState = !prefs.isInteractions();
        prefs.setInteractions(newState);

        updateAndSavePreferences(uuid, prefs);

        if (newState) {
            player.setGameMode(GameMode.SURVIVAL);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_interactions_enabled");
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_interactions_disabled");
        }

        player.setAllowFlight(prefs.isFlyEnabled());
        player.setFlying(prefs.isFlyEnabled());
    }

    public void toggleContainerSpy(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, new ModPreferenceData(false, true, true, false, false));
        prefs.setContainerSpy(!prefs.isContainerSpy());
        updateAndSavePreferences(uuid, prefs);
    }

    public void toggleFly(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, new ModPreferenceData(false, true, true, false, false));
        boolean newState = !prefs.isFlyEnabled();
        prefs.setFlyEnabled(newState);

        updateAndSavePreferences(uuid, prefs);

        player.setAllowFlight(newState);
        player.setFlying(newState);

        if(newState) MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_fly_enabled");
        else MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_fly_disabled");
    }

    public void toggleModOnJoin(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, new ModPreferenceData(false, true, true, false, false));
        boolean newState = !prefs.isModOnJoin();
        prefs.setModOnJoin(newState);

        updateAndSavePreferences(uuid, prefs);

        if(newState) MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_join_enabled");
        else MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_join_disabled");
    }

    public void toggleSilent(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, new ModPreferenceData(false, true, true, false, false));
        boolean newState = !prefs.isSilent();
        prefs.setSilent(newState);

        updateAndSavePreferences(uuid, prefs);

        if (newState) {
            broadcastFakeQuit(player);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_silent_enabled");
        } else {
            broadcastFakeJoin(player);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_silent_disabled");
        }
    }

    // --- State Getters ---

    public boolean isInteractionsAllowed(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isInteractions();
    }

    public boolean isContainerSpyEnabled(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isContainerSpy();
    }

    public boolean isFlyEnabled(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isFlyEnabled();
    }

    public boolean isModOnJoinEnabled(UUID uuid) {
        if (activePreferences.containsKey(uuid)) {
            return activePreferences.get(uuid).isModOnJoin();
        }
        return false;
    }

    public boolean isSilent(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isSilent();
    }

    // --- General Mod Mode Logic ---

    public boolean isInModeratorMode(UUID uuid) { return savedStates.containsKey(uuid); }
    public boolean isVanished(UUID uuid) { return vanishedPlayers.contains(uuid); }

    public Player getSelectedPlayer(UUID moderator) {
        UUID targetId = selectedPlayers.get(moderator);
        return targetId != null ? Bukkit.getPlayer(targetId) : null;
    }

    public void setSelectedPlayer(UUID moderatorUUID, UUID targetUUID) {
        selectedPlayers.put(moderatorUUID, targetUUID);
        Player moderator = Bukkit.getPlayer(moderatorUUID);
        if (moderator != null) updateSelectorItem(moderator, targetUUID);
    }

    public void clearSelectedPlayer(UUID moderatorUUID) {
        selectedPlayers.remove(moderatorUUID);
        Player moderator = Bukkit.getPlayer(moderatorUUID);
        if (moderator != null) updateSelectorItem(moderator, null);
    }

    private void updateSelectorItem(Player moderator, UUID targetUUID) {
        for (int i = 0; i < moderator.getInventory().getSize(); i++) {
            ItemStack item = moderator.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) continue;

            String toolId = item.getItemMeta().getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
            if ("player_selector_tool".equals(toolId)) {
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();

                if (targetUUID != null) {
                    Player target = Bukkit.getPlayer(targetUUID);
                    String targetName = target != null ? target.getName() : "Unknown";

                    meta.setDisplayName(MessageUtils.getColorMessage("&eSelector: &f" + targetName));
                    if (!lore.isEmpty()) lore.set(0, MessageUtils.getColorMessage("&7Target: &a" + targetName));

                    if (meta instanceof SkullMeta && target != null) {
                        ((SkullMeta) meta).setOwningPlayer(target);
                    }
                } else {
                    ConfigurationSection original = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("moderator-inventory.player-selector");
                    if (original != null) {
                        meta.setDisplayName(MessageUtils.getColorMessage(original.getString("name")));
                        lore = original.getStringList("lore").stream().map(MessageUtils::getColorMessage).collect(Collectors.toList());
                    }
                    if (meta instanceof SkullMeta) {
                        ((SkullMeta) meta).setOwningPlayer(null);
                    }
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
                return;
            }
        }
    }

    public boolean isSpectatorTaskActive(UUID uuid) { return spectatorTasks.containsKey(uuid); }
    public void addSpectatorTask(UUID uuid, BukkitTask task) { spectatorTasks.put(uuid, task); }
    public void cancelAndRemoveSpectatorTask(UUID uuid) {
        if (spectatorTasks.containsKey(uuid)) {
            spectatorTasks.get(uuid).cancel();
            spectatorTasks.remove(uuid);
        }
    }

    public void setAwaitingInput(Player player, String type) { awaitingInput.put(player.getUniqueId(), type); }
    public String getInputType(Player player) { return awaitingInput.get(player.getUniqueId()); }
    public void clearAwaitingInput(Player player) { awaitingInput.remove(player.getUniqueId()); }

    public Map<String, ItemStack> getModeratorTools() { return Collections.unmodifiableMap(moderatorTools); }

    public boolean canInteract(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = lastInteraction.getOrDefault(uuid, 0L);
        if (now - last < 250) return false;
        lastInteraction.put(uuid, now);
        return true;
    }

    public void vanishPlayer(Player player) {
        vanishedPlayers.add(player.getUniqueId());
        player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.setCollidable(false);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission("crown.mod.seevanish")) onlinePlayer.hidePlayer(plugin, player);
        }
    }

    public void unvanishPlayer(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        player.removeMetadata("vanished", plugin);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setCollidable(true);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(plugin, player);
        }
    }

    public void updateVanishedPlayerVisibility(Player observer) {
        for (UUID vanishedUUID : vanishedPlayers) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedUUID);
            if (vanishedPlayer != null && !observer.getUniqueId().equals(vanishedUUID)) {
                if (!observer.hasPermission("crown.mod.seevanish")) observer.hidePlayer(plugin, vanishedPlayer);
                else observer.showPlayer(plugin, vanishedPlayer);
            }
        }
    }

    // --- Spectator Logic ---

    public void savePreSpectatorVanishState(UUID uuid, boolean vanished) { preSpectatorVanishState.put(uuid, vanished); }
    public Boolean getPreSpectatorVanishState(UUID uuid) { return preSpectatorVanishState.get(uuid); }

    public boolean isTemporarySpectator(UUID uuid) {
        return spectatorExpirations.containsKey(uuid);
    }

    public long getRemainingSpectatorTime(UUID uuid) {
        if (!spectatorExpirations.containsKey(uuid)) return 0;
        return (spectatorExpirations.get(uuid) - System.currentTimeMillis()) / 1000;
    }

    public void enterSpectatorMode(Player player) {
        if (isTemporarySpectator(player.getUniqueId())) return;

        boolean wasVanished = isVanished(player.getUniqueId());
        savePreSpectatorVanishState(player.getUniqueId(), wasVanished);

        if(wasVanished) unvanishPlayer(player);

        player.setGameMode(GameMode.SPECTATOR);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);

        int durationSeconds = plugin.getConfigManager().getPluginConfig().getConfig().getInt("mod-mode.spectator-duration", 3);
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        spectatorExpirations.put(player.getUniqueId(), endTime);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                exitSpectatorMode(player);
            }
        }.runTaskLater(plugin, durationSeconds * 20L);

        addSpectatorTask(player.getUniqueId(), task);
    }

    public void exitSpectatorMode(Player player) {
        cancelAndRemoveSpectatorTask(player.getUniqueId());
        spectatorExpirations.remove(player.getUniqueId());

        if (player.getGameMode() != GameMode.SPECTATOR) return;

        // Restore GameMode based on Preferences
        if (isInteractionsAllowed(player.getUniqueId())) {
            player.setGameMode(GameMode.SURVIVAL);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
        }

        // Restore Fly based on Preferences
        boolean fly = isFlyEnabled(player.getUniqueId());
        player.setAllowFlight(fly);
        player.setFlying(fly);

        Boolean wasVanished = getPreSpectatorVanishState(player.getUniqueId());
        if (wasVanished == null || wasVanished) {
            vanishPlayer(player);
        } else {
            unvanishPlayer(player);
        }

        // Task 2: Sound after spectator
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }

    // Helper class for caching preferences
    private static class ModPreferenceData {
        private boolean interactions;
        private boolean containerSpy;
        private boolean flyEnabled;
        private boolean modOnJoin;
        private boolean silent;

        public ModPreferenceData(boolean interactions, boolean containerSpy, boolean flyEnabled, boolean modOnJoin, boolean silent) {
            this.interactions = interactions;
            this.containerSpy = containerSpy;
            this.flyEnabled = flyEnabled;
            this.modOnJoin = modOnJoin;
            this.silent = silent;
        }

        public boolean isInteractions() { return interactions; }
        public void setInteractions(boolean interactions) { this.interactions = interactions; }
        public boolean isContainerSpy() { return containerSpy; }
        public void setContainerSpy(boolean containerSpy) { this.containerSpy = containerSpy; }
        public boolean isFlyEnabled() { return flyEnabled; }
        public void setFlyEnabled(boolean flyEnabled) { this.flyEnabled = flyEnabled; }
        public boolean isModOnJoin() { return modOnJoin; }
        public void setModOnJoin(boolean modOnJoin) { this.modOnJoin = modOnJoin; }
        public boolean isSilent() { return silent; }
        public void setSilent(boolean silent) { this.silent = silent; }
    }

    private static class PlayerState {
        private final ItemStack[] inventoryContents;
        private final ItemStack[] armorContents;
        private final GameMode gameMode;
        private final boolean wasFlying;
        private final boolean allowFlight;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final float experience;
        private final int level;
        private final boolean wasSilent;
        private final boolean wasCollidable;

        PlayerState(Player player) {
            this.inventoryContents = player.getInventory().getContents();
            this.armorContents = player.getInventory().getArmorContents();
            this.gameMode = player.getGameMode();
            this.wasFlying = player.isFlying();
            this.allowFlight = player.getAllowFlight();
            this.health = player.getHealth();
            this.foodLevel = player.getFoodLevel();
            this.saturation = player.getSaturation();
            this.experience = player.getExp();
            this.level = player.getLevel();
            this.wasSilent = player.isSilent();
            this.wasCollidable = player.isCollidable();
        }

        void restore(Player player) {
            player.getInventory().setContents(inventoryContents);
            player.getInventory().setArmorContents(armorContents);
            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            player.setFlying(wasFlying);
            player.setHealth(health);
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExp(experience);
            player.setLevel(level);
            player.setSilent(wasSilent);
            player.setCollidable(wasCollidable);
            player.updateInventory();
        }
    }
}
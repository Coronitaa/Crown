package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.io.IOException;
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

    private final File sessionsFile;
    private final YamlConfiguration sessionsConfig;

    public ModeratorModeManager(Crown plugin) {
        this.plugin = plugin;
        this.toolIdKey = new NamespacedKey(plugin, "tool-id");
        this.sessionsFile = new File(plugin.getDataFolder(), "mod_sessions.yml");
        this.sessionsConfig = YamlConfiguration.loadConfiguration(sessionsFile);
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
                List<String> lore = section.getStringList(key + ".lore").stream()
                        .map(MessageUtils::getColorMessage)
                        .collect(Collectors.toList());

                // Add usage instructions to the lore
                List<String> usage = section.getStringList(key + ".usage");
                if (!usage.isEmpty()) {
                    if (!lore.isEmpty()) lore.add(""); // Add a spacer
                    lore.addAll(usage.stream().map(MessageUtils::getColorMessage).toList());
                }

                meta.setLore(lore);
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

        PlayerState state = new PlayerState(player);
        savedStates.put(player.getUniqueId(), state);
        saveSessionToDisk(player.getUniqueId(), state);

        // Load preferences from DB (or use defaults if new)
        plugin.getSoftBanDatabaseManager().getModPreferences(player.getUniqueId()).thenAccept(dbPrefs -> {
            List<String> favoriteTools = dbPrefs.getFavoriteTools();
            // If the player has never set favorites (list is null), load the default ones.
            // If they have set them, even to be empty, respect that.
            if (favoriteTools == null) {
                favoriteTools = new ArrayList<>(plugin.getConfigManager().getModModeConfig().getConfig().getStringList("default-favorites"));
            }

            ModPreferenceData prefs = new ModPreferenceData(
                    dbPrefs.isInteractions(),
                    dbPrefs.isContainerSpy(),
                    dbPrefs.isFlyEnabled(),
                    dbPrefs.isModOnJoin(),
                    dbPrefs.isSilent(),
                    new ArrayList<>(favoriteTools), // Make a mutable copy
                    dbPrefs.getWalkSpeed(),
                    dbPrefs.getFlySpeed(),
                    dbPrefs.getJumpMultiplier(),
                    dbPrefs.isNightVision(),
                    dbPrefs.isGlowingEnabled()
            );
            activePreferences.put(player.getUniqueId(), prefs);

            // Apply preferences on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && isInModeratorMode(player.getUniqueId())) {
                    player.getInventory().clear(); // Clear inventory before applying items

                    // Apply fixed items first
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

                    // Load favorite tools into hotbar
                    loadFavoriteTools(player);

                    // Apply GameMode based on Interactions
                    player.setGameMode(prefs.isInteractions() ? GameMode.SURVIVAL : GameMode.ADVENTURE);

                    // Apply Flight based on Fly Enabled
                    player.setAllowFlight(prefs.isFlyEnabled());
                    player.setFlying(prefs.isFlyEnabled());

                    // Apply Speeds
                    player.setWalkSpeed(prefs.getWalkSpeed() * 0.2f); // Default is 0.2
                    player.setFlySpeed(prefs.getFlySpeed() * 0.1f); // Default is 0.1

                    // Apply Jump Boost
                    applyJumpBoost(player, prefs.getJumpMultiplier());

                    // Apply Night Vision
                    if (prefs.isNightVision()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                    } else {
                        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    }

                    // Handle Silent Mode Messages
                    if (prefs.isSilent()) {
                        if (!isAutoJoin) {
                            broadcastFakeQuit(player);
                        }
                    }
                }
            });
        });

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Default state before prefs load
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setSilent(true);
        player.setExp(0f);
        player.setLevel(0);

        vanishPlayer(player);

        // Notify player
        String msgKey = (isAutoJoin && knownSilentState) ? "messages.mod_mode_enabled_silent" : "messages.mod_mode_enabled";
        MessageUtils.sendConfigMessage(plugin, player, msgKey);
    }

    private void disableModeratorMode(Player player, boolean isDisconnecting) {
        boolean wasSilent = isSilent(player.getUniqueId());

        // Save preferences before disabling
        if (activePreferences.containsKey(player.getUniqueId())) {
            updateAndSavePreferences(player.getUniqueId(), activePreferences.get(player.getUniqueId()));
        }

        PlayerState state = savedStates.remove(player.getUniqueId());
        if (state != null) {
            unvanishPlayer(player);
            state.restore(player);
            removeSessionFromDisk(player.getUniqueId());
        }

        // Reset scoreboard to main to clear any custom teams
        if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        clearSelectedPlayer(player.getUniqueId());
        cancelAndRemoveSpectatorTask(player.getUniqueId());
        awaitingInput.remove(player.getUniqueId());
        lastInteraction.remove(player.getUniqueId());
        activePreferences.remove(player.getUniqueId());
        spectatorExpirations.remove(player.getUniqueId());
        preSpectatorVanishState.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        if (!isDisconnecting && player.isOnline()) {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_disabled");

            if (wasSilent) {
                broadcastFakeJoin(player);
            }
        }
    }

    public void disableAllModerators() {
        for (UUID uuid : savedStates.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                disableModeratorMode(player, true);
            }
        }
    }

    public void handleDisconnect(Player player) {
        if (isInModeratorMode(player.getUniqueId())) {
            disableModeratorMode(player, true); // True = Disconnecting
        }
    }

    public void checkAndRestoreSession(Player player) {
        if (sessionsConfig.contains(player.getUniqueId().toString())) {
            plugin.getLogger().warning("Detected crash session for " + player.getName() + ". Restoring inventory...");
            PlayerState state = PlayerState.deserialize(sessionsConfig.getConfigurationSection(player.getUniqueId().toString()));
            if (state != null) {
                state.restore(player);
                removeSessionFromDisk(player.getUniqueId());
                MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_crash_restore");
            }
        }
    }

    private void saveSessionToDisk(UUID uuid, PlayerState state) {
        String path = uuid.toString();
        state.serialize(sessionsConfig.createSection(path));
        try {
            sessionsConfig.save(sessionsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save mod session for " + uuid);
            e.printStackTrace();
        }
    }

    private void removeSessionFromDisk(UUID uuid) {
        sessionsConfig.set(uuid.toString(), null);
        try {
            sessionsConfig.save(sessionsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to remove mod session for " + uuid);
            e.printStackTrace();
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
                prefs.isSilent(),
                prefs.getFavoriteTools(),
                prefs.getWalkSpeed(),
                prefs.getFlySpeed(),
                prefs.getJumpMultiplier(),
                prefs.isNightVision(),
                prefs.isGlowingEnabled()
        );
    }

    public void toggleInteractions(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
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
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        prefs.setContainerSpy(!prefs.isContainerSpy());
        updateAndSavePreferences(uuid, prefs);
    }

    public void toggleFly(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
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
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        boolean newState = !prefs.isModOnJoin();
        prefs.setModOnJoin(newState);

        updateAndSavePreferences(uuid, prefs);

        if(newState) MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_join_enabled");
        else MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_join_disabled");
    }

    public void toggleSilent(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
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

    public void toggleGlowing(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        boolean newState = !prefs.isGlowingEnabled();
        prefs.setGlowingEnabled(newState);

        updateAndSavePreferences(uuid, prefs);

        if (newState) {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_glowing_enabled");
        } else {
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_glowing_disabled");
        }
    }

    public void modifyWalkSpeed(Player player, float amount) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        float newSpeed = Math.max(0.25f, Math.min(5.0f, prefs.getWalkSpeed() + amount));
        
        // Round to nearest 0.25
        newSpeed = Math.round(newSpeed * 4) / 4.0f;
        
        prefs.setWalkSpeed(newSpeed);
        updateAndSavePreferences(uuid, prefs);
        
        player.setWalkSpeed(newSpeed * 0.2f); // Default is 0.2
    }

    public void resetWalkSpeed(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        prefs.setWalkSpeed(1.0f);
        updateAndSavePreferences(uuid, prefs);
        player.setWalkSpeed(0.2f);
    }

    public void modifyFlySpeed(Player player, float amount) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        float newSpeed = Math.max(0.25f, Math.min(5.0f, prefs.getFlySpeed() + amount));
        
        // Round to nearest 0.25
        newSpeed = Math.round(newSpeed * 4) / 4.0f;
        
        prefs.setFlySpeed(newSpeed);
        updateAndSavePreferences(uuid, prefs);
        
        player.setFlySpeed(newSpeed * 0.1f); // Default is 0.1
    }

    public void resetFlySpeed(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        prefs.setFlySpeed(1.0f);
        updateAndSavePreferences(uuid, prefs);
        player.setFlySpeed(0.1f);
    }

    public void modifyJumpMultiplier(Player player, float amount) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        float newMultiplier = Math.max(0.5f, Math.min(10.0f, prefs.getJumpMultiplier() + amount));
        newMultiplier = Math.round(newMultiplier * 2) / 2.0f; // Round to nearest 0.5
        prefs.setJumpMultiplier(newMultiplier);
        updateAndSavePreferences(uuid, prefs);
        applyJumpBoost(player, newMultiplier);
    }

    public void resetJumpMultiplier(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        prefs.setJumpMultiplier(1.0f);
        updateAndSavePreferences(uuid, prefs);
        applyJumpBoost(player, 1.0f);
    }

    private void applyJumpBoost(Player player, float multiplier) {
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        if (multiplier > 1.0f) {
            // This is an approximation. The jump boost effect is not linear.
            // Level 1 = +50% height, Level 2 = +100%
            // We'll map our multiplier to the amplifier.
            int amplifier = (int) Math.round(multiplier) - 1;
            if (amplifier >= 0) { // Amplifier can be 0 for small boosts
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, amplifier, false, false));
            }
        }
    }
    
    public void toggleNightVision(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, createDefaultPrefs());
        boolean newState = !prefs.isNightVision();
        prefs.setNightVision(newState);
        updateAndSavePreferences(uuid, prefs);

        if (newState) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    public float getWalkSpeed(UUID uuid) {
        return activePreferences.containsKey(uuid) ? activePreferences.get(uuid).getWalkSpeed() : 1.0f;
    }

    public float getFlySpeed(UUID uuid) {
        return activePreferences.containsKey(uuid) ? activePreferences.get(uuid).getFlySpeed() : 1.0f;
    }
    
    public float getJumpMultiplier(UUID uuid) {
        return activePreferences.containsKey(uuid) ? activePreferences.get(uuid).getJumpMultiplier() : 1.0f;
    }

    public boolean isNightVisionEnabled(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isNightVision();
    }

    public boolean isGlowingEnabled(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isGlowingEnabled();
    }

    public void toggleFavoriteTool(Player player, String toolId) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.get(uuid);
        if (prefs == null) return;

        List<String> favorites = prefs.getFavoriteTools();
        if (favorites.contains(toolId)) {
            favorites.remove(toolId);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_favorite_removed");
        } else {
            // Max 7 favorites (hotbar slots 1-7, as 0 and 8 are fixed)
            if (favorites.size() >= 7) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_favorites_full");
                return;
            }
            favorites.add(toolId);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_favorite_added");
        }

        updateAndSavePreferences(uuid, prefs);
        loadFavoriteTools(player); // Refresh hotbar
    }

    private void loadFavoriteTools(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.get(uuid);
        if (prefs == null) return;

        // Create a set of tool IDs currently in fixed slots to avoid duplication
        Set<String> fixedToolIds = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String toolId = meta.getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
                    if (toolId != null && isFixedTool(toolId)) {
                        fixedToolIds.add(toolId);
                    }
                }
            }
        }

        // Clear existing non-fixed tools from slots 1-7
        for (int i = 1; i <= 7; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String toolId = meta.getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
                    if (toolId != null && !isFixedTool(toolId)) {
                        player.getInventory().setItem(i, null);
                    }
                }
            }
        }

        // Add favorite tools, avoiding duplicates of fixed tools
        int currentSlot = 1;
        for (String toolId : prefs.getFavoriteTools()) {
            if (fixedToolIds.contains(toolId)) continue; // Skip if it's already in a fixed slot

            if (currentSlot > 7) break;
            ItemStack tool = moderatorTools.get(toolId);
            if (tool != null) {
                // Find next available slot from 1-7
                while(currentSlot <= 7 && player.getInventory().getItem(currentSlot) != null) {
                    currentSlot++;
                }
                if(currentSlot <= 7) {
                    player.getInventory().setItem(currentSlot, tool.clone());
                    currentSlot++;
                }
            }
        }
    }

    private boolean isFixedTool(String toolId) {
        ConfigurationSection inventorySection = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("moderator-inventory");
        if (inventorySection == null) return false;

        for (String key : inventorySection.getKeys(false)) {
            if (toolId.equals(inventorySection.getString(key + ".tool-id"))) {
                return true;
            }
        }
        return false;
    }

    private ModPreferenceData createDefaultPrefs() {
        List<String> defaultFavorites = plugin.getConfigManager().getModModeConfig().getConfig().getStringList("default-favorites");
        return new ModPreferenceData(false, true, true, false, false, new ArrayList<>(defaultFavorites), 1.0f, 1.0f, 1.0f, false, false);
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

    public List<String> getFavoriteTools(UUID uuid) {
        if (activePreferences.containsKey(uuid)) {
            return Collections.unmodifiableList(activePreferences.get(uuid).getFavoriteTools());
        }
        return Collections.emptyList();
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

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String toolId = meta.getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
            if ("player_selector_tool".equals(toolId)) {
                List<String> lore;

                if (targetUUID != null) {
                    Player target = Bukkit.getPlayer(targetUUID);
                    String targetName = target != null ? target.getName() : "Unknown";

                    meta.setDisplayName(MessageUtils.getColorMessage("&eSelector: &f" + targetName));
                    // Rebuild lore to ensure correctness
                    lore = new ArrayList<>();
                    lore.add(MessageUtils.getColorMessage("&7Target: &a" + targetName));
                    // Add usage from original tool
                    ItemStack originalTool = moderatorTools.get("player_selector_tool");
                    if (originalTool != null && originalTool.hasItemMeta()) {
                        ItemMeta originalMeta = originalTool.getItemMeta();
                        if (originalMeta != null) {
                            List<String> usageLore = originalMeta.getLore();
                            if (usageLore != null && usageLore.size() > 1) {
                               lore.addAll(usageLore.subList(1, usageLore.size()));
                            }
                        }
                    }

                    if (meta instanceof SkullMeta && target != null) {
                        ((SkullMeta) meta).setOwningPlayer(target);
                    }
                } else {
                    // Restore from fresh tool
                    ItemStack originalTool = moderatorTools.get("player_selector_tool");
                    if (originalTool != null) {
                        ItemMeta originalMeta = originalTool.getItemMeta();
                        if (originalMeta != null) {
                            meta.setDisplayName(originalMeta.getDisplayName());
                            lore = originalMeta.getLore();
                        } else {
                            lore = new ArrayList<>();
                        }
                    } else {
                        lore = new ArrayList<>();
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
        player.setGlowing(true); // NEW: Enable glowing for visibility to other staff

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // Logic: Hide from everyone who doesn't have permission.
            // Those with permission will see the player (showPlayer) and see the glow.
            if (!onlinePlayer.hasPermission("crown.mod.seevanish")) onlinePlayer.hidePlayer(plugin, player);
        }
    }

    public void unvanishPlayer(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        player.removeMetadata("vanished", plugin);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setCollidable(true);
        player.setGlowing(false); // NEW: Disable glowing

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
    }

    // Helper class for caching preferences
    private static class ModPreferenceData {
        private boolean interactions;
        private boolean containerSpy;
        private boolean flyEnabled;
        private boolean modOnJoin;
        private boolean silent;
        private final List<String> favoriteTools;
        private float walkSpeed;
        private float flySpeed;
        private float jumpMultiplier;
        private boolean nightVision;
        private boolean glowingEnabled;

        public ModPreferenceData(boolean interactions, boolean containerSpy, boolean flyEnabled, boolean modOnJoin, boolean silent, List<String> favoriteTools, float walkSpeed, float flySpeed, float jumpMultiplier, boolean nightVision, boolean glowingEnabled) {
            this.interactions = interactions;
            this.containerSpy = containerSpy;
            this.flyEnabled = flyEnabled;
            this.modOnJoin = modOnJoin;
            this.silent = silent;
            this.favoriteTools = favoriteTools;
            this.walkSpeed = walkSpeed;
            this.flySpeed = flySpeed;
            this.jumpMultiplier = jumpMultiplier;
            this.nightVision = nightVision;
            this.glowingEnabled = glowingEnabled;
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
        public List<String> getFavoriteTools() { return favoriteTools; }
        public float getWalkSpeed() { return walkSpeed; }
        public void setWalkSpeed(float walkSpeed) { this.walkSpeed = walkSpeed; }
        public float getFlySpeed() { return flySpeed; }
        public void setFlySpeed(float flySpeed) { this.flySpeed = flySpeed; }
        public float getJumpMultiplier() { return jumpMultiplier; }
        public void setJumpMultiplier(float jumpMultiplier) { this.jumpMultiplier = jumpMultiplier; }
        public boolean isNightVision() { return nightVision; }
        public void setNightVision(boolean nightVision) { this.nightVision = nightVision; }
        public boolean isGlowingEnabled() { return glowingEnabled; }
        public void setGlowingEnabled(boolean glowingEnabled) { this.glowingEnabled = glowingEnabled; }
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
        private final float walkSpeed;
        private final float flySpeed;
        private final Collection<PotionEffect> potionEffects;

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
            this.walkSpeed = player.getWalkSpeed();
            this.flySpeed = player.getFlySpeed();
            this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
        }

        // Constructor for deserialization
        private PlayerState(ItemStack[] inventoryContents, ItemStack[] armorContents, GameMode gameMode, boolean wasFlying, boolean allowFlight, double health, int foodLevel, float saturation, float experience, int level, boolean wasSilent, boolean wasCollidable, float walkSpeed, float flySpeed, Collection<PotionEffect> potionEffects) {
            this.inventoryContents = inventoryContents;
            this.armorContents = armorContents;
            this.gameMode = gameMode;
            this.wasFlying = wasFlying;
            this.allowFlight = allowFlight;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.experience = experience;
            this.level = level;
            this.wasSilent = wasSilent;
            this.wasCollidable = wasCollidable;
            this.walkSpeed = walkSpeed;
            this.flySpeed = flySpeed;
            this.potionEffects = potionEffects;
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
            player.setWalkSpeed(walkSpeed);
            player.setFlySpeed(flySpeed);
            
            // Clear mod-mode effects before restoring
            for (PotionEffectType type : List.of(PotionEffectType.JUMP_BOOST, PotionEffectType.NIGHT_VISION, PotionEffectType.INVISIBILITY)) {
                player.removePotionEffect(type);
            }
            
            for (PotionEffect effect : potionEffects) {
                player.addPotionEffect(effect);
            }
            
            player.updateInventory();
        }

        void serialize(ConfigurationSection section) {
            section.set("inventory", inventoryContents);
            section.set("armor", armorContents);
            section.set("gamemode", gameMode.name());
            section.set("wasFlying", wasFlying);
            section.set("allowFlight", allowFlight);
            section.set("health", health);
            section.set("foodLevel", foodLevel);
            section.set("saturation", saturation);
            section.set("experience", experience);
            section.set("level", level);
            section.set("wasSilent", wasSilent);
            section.set("wasCollidable", wasCollidable);
            section.set("walkSpeed", walkSpeed);
            section.set("flySpeed", flySpeed);
            section.set("potionEffects", new ArrayList<>(potionEffects));
        }

        static PlayerState deserialize(ConfigurationSection section) {
            if (section == null) return null;
            try {
                List<ItemStack> invList = (List<ItemStack>) section.getList("inventory");
                ItemStack[] inventory = invList != null ? invList.toArray(new ItemStack[0]) : new ItemStack[0];

                List<ItemStack> armorList = (List<ItemStack>) section.getList("armor");
                ItemStack[] armor = armorList != null ? armorList.toArray(new ItemStack[0]) : new ItemStack[0];

                GameMode gm = GameMode.valueOf(section.getString("gamemode", "SURVIVAL"));
                boolean flying = section.getBoolean("wasFlying");
                boolean allowFlight = section.getBoolean("allowFlight");
                double health = section.getDouble("health");
                int food = section.getInt("foodLevel");
                float saturation = (float) section.getDouble("saturation");
                float exp = (float) section.getDouble("experience");
                int level = section.getInt("level");
                boolean silent = section.getBoolean("wasSilent");
                boolean collidable = section.getBoolean("wasCollidable");
                float walkSpeed = (float) section.getDouble("walkSpeed");
                float flySpeed = (float) section.getDouble("flySpeed");
                List<PotionEffect> effects = (List<PotionEffect>) section.getList("potionEffects", new ArrayList<>());

                return new PlayerState(inventory, armor, gm, flying, allowFlight, health, food, saturation, exp, level, silent, collidable, walkSpeed, flySpeed, effects);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}

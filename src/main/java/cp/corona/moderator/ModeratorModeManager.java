package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
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

    // MODIFIED: Replaced simple Set with Map storing full preferences
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
        if (savedStates.containsKey(player.getUniqueId())) disableModeratorMode(player);
        else enableModeratorMode(player);
    }

    private void enableModeratorMode(Player player) {
        savedStates.put(player.getUniqueId(), new PlayerState(player));

        // Load preferences from DB
        plugin.getSoftBanDatabaseManager().getModPreferences(player.getUniqueId()).thenAccept(dbPrefs -> {
            activePreferences.put(player.getUniqueId(), new ModPreferenceData(dbPrefs.isInteractions(), dbPrefs.isContainerSpy()));

            // Sync GameMode based on loaded preference
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && isInModeratorMode(player.getUniqueId())) {
                    player.setGameMode(dbPrefs.isInteractions() ? GameMode.SURVIVAL : GameMode.ADVENTURE);
                }
            });
        });

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Default to ADVENTURE until prefs load
        player.setGameMode(GameMode.ADVENTURE);

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setAllowFlight(true);
        player.setFlying(true);
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
        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_enabled");
    }

    private void disableModeratorMode(Player player) {
        PlayerState state = savedStates.remove(player.getUniqueId());
        if (state != null) {
            unvanishPlayer(player);
            state.restore(player);
        }
        clearSelectedPlayer(player.getUniqueId());
        cancelAndRemoveSpectatorTask(player.getUniqueId());
        awaitingInput.remove(player.getUniqueId());
        lastInteraction.remove(player.getUniqueId());
        activePreferences.remove(player.getUniqueId()); // Clear cache
        spectatorExpirations.remove(player.getUniqueId());
        preSpectatorVanishState.remove(player.getUniqueId());

        MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_disabled");
    }

    public void handleDisconnect(Player player) {
        if (isInModeratorMode(player.getUniqueId())) disableModeratorMode(player);
    }

    // ... (Vanish and Selection logic remains same)

    public void toggleInteractions(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, new ModPreferenceData(false, true));
        boolean newState = !prefs.isInteractions();

        prefs.setInteractions(newState);
        activePreferences.put(uuid, prefs);

        // Save to DB
        plugin.getSoftBanDatabaseManager().saveModPreferences(uuid, newState, prefs.isContainerSpy());

        if (newState) {
            player.setGameMode(GameMode.SURVIVAL);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_interactions_enabled");
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_interactions_disabled");
        }
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    public void toggleContainerSpy(Player player) {
        UUID uuid = player.getUniqueId();
        ModPreferenceData prefs = activePreferences.getOrDefault(uuid, new ModPreferenceData(false, true));
        boolean newState = !prefs.isContainerSpy();

        prefs.setContainerSpy(newState);
        activePreferences.put(uuid, prefs);

        // Save to DB
        plugin.getSoftBanDatabaseManager().saveModPreferences(uuid, prefs.isInteractions(), newState);

        // No message needed here usually as the GUI updates, but can add feedback if desired
    }

    // ... (canInteract and getters)

    public boolean isInteractionsAllowed(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isInteractions();
    }

    public boolean isContainerSpyEnabled(UUID uuid) {
        return activePreferences.containsKey(uuid) && activePreferences.get(uuid).isContainerSpy();
    }

    // ... (Spectator logic)

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

        player.setAllowFlight(true);
        player.setFlying(true);

        Boolean wasVanished = getPreSpectatorVanishState(player.getUniqueId());
        if (wasVanished == null || wasVanished) {
            vanishPlayer(player);
        } else {
            unvanishPlayer(player);
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
    }

    // Helper class for caching prefs
    private static class ModPreferenceData {
        private boolean interactions;
        private boolean containerSpy;

        public ModPreferenceData(boolean interactions, boolean containerSpy) {
            this.interactions = interactions;
            this.containerSpy = containerSpy;
        }

        public boolean isInteractions() { return interactions; }
        public void setInteractions(boolean interactions) { this.interactions = interactions; }
        public boolean isContainerSpy() { return containerSpy; }
        public void setContainerSpy(boolean containerSpy) { this.containerSpy = containerSpy; }
    }

    // ... (PlayerState inner class and methods to get/set tools remain the same)
    // Make sure updateSelectorItem etc. are present from previous version

    // Re-adding omitted methods for completeness context
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
                    if (meta instanceof SkullMeta && target != null) ((SkullMeta) meta).setOwningPlayer(target);
                } else {
                    ConfigurationSection original = plugin.getConfigManager().getModModeConfig().getConfig().getConfigurationSection("moderator-inventory.player-selector");
                    if (original != null) {
                        meta.setDisplayName(MessageUtils.getColorMessage(original.getString("name")));
                        lore = original.getStringList("lore").stream().map(MessageUtils::getColorMessage).collect(Collectors.toList());
                    }
                    if (meta instanceof SkullMeta) ((SkullMeta) meta).setOwningPlayer(null);
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
    public void savePreSpectatorVanishState(UUID uuid, boolean vanished) { preSpectatorVanishState.put(uuid, vanished); }
    public Boolean getPreSpectatorVanishState(UUID uuid) { return preSpectatorVanishState.get(uuid); }
    public boolean isTemporarySpectator(UUID uuid) { return spectatorExpirations.containsKey(uuid); }
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
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
        int durationSeconds = plugin.getConfigManager().getPluginConfig().getConfig().getInt("mod-mode.spectator-duration", 3);
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        spectatorExpirations.put(player.getUniqueId(), endTime);
        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() { exitSpectatorMode(player); }
        }.runTaskLater(plugin, durationSeconds * 20L);
        addSpectatorTask(player.getUniqueId(), task);
    }
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

    private static class PlayerState {
        // ... (PlayerState fields and constructor match previous version)
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
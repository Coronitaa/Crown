// src/main/java/cp/corona/listeners/MenuListener.java
package cp.corona.listeners;

import cp.corona.config.MainConfigManager;
import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.menus.HistoryMenu;
import cp.corona.menus.PunishDetailsMenu;
import cp.corona.menus.PunishMenu;
import cp.corona.menus.TimeSelectorMenu;
import cp.corona.menus.actions.ClickAction;
import cp.corona.menus.items.MenuItem;
import cp.corona.menus.items.MenuItem.ClickActionData;
import cp.corona.utils.ColorUtils;
import cp.corona.utils.MessageUtils;
import cp.corona.utils.TimeUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;


public class MenuListener implements Listener {
    private final Crown plugin;
    // Input tracking maps
    private final HashMap<UUID, BukkitTask> inputTimeouts = new HashMap<>();
    private final HashMap<UUID, PunishDetailsMenu> pendingDetailsMenus = new HashMap<>();
    private final HashMap<UUID, String> inputTypes = new HashMap<>();

    // Constants for punishment types (consistency)
    private static final String BAN_PUNISHMENT_TYPE = "ban";
    private static final String MUTE_PUNISHMENT_TYPE = "mute";
    private static final String SOFTBAN_PUNISHMENT_TYPE = "softban";
    private static final String KICK_PUNISHMENT_TYPE = "kick";
    private static final String WARN_PUNISHMENT_TYPE = "warn";
    private static final String FREEZE_PUNISHMENT_TYPE = "freeze";

    // Permission constants
    private static final String MOD_PERMISSION = "crown.mod";
    private static final String ADMIN_PERMISSION = "crown.admin";
    private static final String USE_PERMISSION = "crown.use";
    private static final String PUNISH_BAN_PERMISSION = "crown.punish.ban";
    private static final String UNPUNISH_BAN_PERMISSION = "crown.unpunish.ban";
    private static final String PUNISH_MUTE_PERMISSION = "crown.punish.mute";
    private static final String UNPUNISH_MUTE_PERMISSION = "crown.unpunish.mute";
    private static final String PUNISH_SOFTBAN_PERMISSION = "crown.punish.softban";
    private static final String UNPUNISH_SOFTBAN_PERMISSION = "crown.unpunish.softban";
    private static final String UNPUNISH_WARN_PERMISSION = "crown.unpunish.warn";
    private static final String PUNISH_KICK_PERMISSION = "crown.punish.kick";
    private static final String PUNISH_WARN_PERMISSION = "crown.punish.warn";
    private static final String PUNISH_FREEZE_PERMISSION = "crown.punish.freeze";
    private static final String UNPUNISH_FREEZE_PERMISSION = "crown.unpunish.freeze";

    public MenuListener(Crown plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof PunishMenu) && !(holder instanceof PunishDetailsMenu) &&
                !(holder instanceof TimeSelectorMenu) && !(holder instanceof HistoryMenu)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (event.getClickedInventory() == null || clickedItem == null || clickedItem.getType() == Material.AIR) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        MenuItem clickedMenuItem = getMenuItemClicked(event.getRawSlot(), holder);
        if (clickedMenuItem != null) {
            clickedMenuItem.playClickSound(player);

            List<MenuItem.ClickActionData> actionsToExecute = Collections.emptyList();
            if (event.isLeftClick()) {
                actionsToExecute = clickedMenuItem.getLeftClickActions();
            } else if (event.isRightClick()) {
                actionsToExecute = clickedMenuItem.getRightClickActions();
            }

            if (!actionsToExecute.isEmpty()) {
                for (MenuItem.ClickActionData actionData : actionsToExecute) {
                    handleMenuItemClick(player, holder, actionData.getAction(), actionData.getActionData(), event, clickedMenuItem);
                }
            }
        } else {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Clicked on slot " + event.getRawSlot() + " in " + holder.getClass().getSimpleName() + " with no associated MenuItem found.");
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (event.getPlayer() instanceof Player player) {
            if (holder instanceof PunishMenu || holder instanceof PunishDetailsMenu || holder instanceof TimeSelectorMenu || holder instanceof HistoryMenu) {
                executeMenuOpenActions(player, holder);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (event.getPlayer() instanceof Player player) {
            // No action needed here for now
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!inputTimeouts.containsKey(player.getUniqueId())) {
            return;
        }

        if (plugin.getPluginFrozenPlayers().containsKey(player.getUniqueId()) && !player.hasPermission(MOD_PERMISSION)) {
            event.setCancelled(true);
            sendConfigMessage(player, "messages.freeze_command_blocked");
            return;
        }

        event.setCancelled(true);

        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handlePlayerInput(player, message));
    }

    private void handleMenuItemClick(Player player, InventoryHolder holder, ClickAction action, String[] actionData, InventoryClickEvent event, MenuItem clickedMenuItem) {
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - START - Action: " + action + ", ActionData: " + Arrays.toString(actionData) + ", Item: " + (clickedMenuItem != null ? clickedMenuItem.getName() : "null") + ", Holder Type: " + holder.getClass().getSimpleName());

        String[] processedActionData = actionData;
        if (actionData != null && actionData.length > 0) {
            processedActionData = Arrays.stream(actionData)
                    .filter(Objects::nonNull)
                    .map(data -> replacePlaceholders(player, data, holder))
                    .toArray(String[]::new);
        }

        boolean handledByMenuSpecific = false;
        if (holder instanceof PunishMenu punishMenu) {
            handledByMenuSpecific = handlePunishMenuActions(player, punishMenu, action, processedActionData, clickedMenuItem);
        } else if (holder instanceof PunishDetailsMenu punishDetailsMenu) {
            handledByMenuSpecific = handlePunishDetailsMenuActions(player, punishDetailsMenu, action, processedActionData, clickedMenuItem);
        } else if (holder instanceof TimeSelectorMenu timeSelectorMenu) {
            handledByMenuSpecific = handleTimeSelectorMenuActions(player, timeSelectorMenu, action, processedActionData, clickedMenuItem);
        } else if (holder instanceof HistoryMenu historyMenu) {
            handledByMenuSpecific = handleHistoryMenuActions(player, historyMenu, action, processedActionData, clickedMenuItem);
        }

        if (!handledByMenuSpecific) {
            switch (action) {
                case CONSOLE_COMMAND: executeConsoleCommand(player, processedActionData, holder); break;
                case PLAYER_COMMAND:
                case PLAYER_COMMAND_OP: executeCommandAction(player, action, processedActionData, holder); break;
                case CLOSE_MENU: player.closeInventory(); break;
                case PLAY_SOUND: executePlaySoundAction(player, processedActionData); break;
                case TITLE: executeTitleAction(player, processedActionData); break;
                case MESSAGE: executeMessageAction(player, processedActionData, holder); break;
                case ACTIONBAR: executeActionbarAction(player, processedActionData); break;
                case PLAY_SOUND_TARGET: executePlaySoundTargetAction(player, holder, processedActionData); break;
                case TITLE_TARGET: executeTitleTargetAction(player, holder, processedActionData); break;
                case MESSAGE_TARGET: executeMessageTargetAction(player, holder, processedActionData); break;
                case ACTIONBAR_TARGET: executeActionbarTargetAction(player, holder, processedActionData); break;
                case GIVE_EFFECT_TARGET: executeGiveEffectTargetAction(player, holder, actionData); break;
                case PLAY_SOUND_MODS: executePlaySoundModsAction(player, holder, processedActionData); break;
                case TITLE_MODS: executeTitleModsAction(player, holder, processedActionData); break;
                case MESSAGE_MODS: executeMessageModsAction(player, holder, processedActionData); break;
                case ACTIONBAR_MODS: executeActionbarModsAction(player, holder, processedActionData); break;
                default:
                    if (plugin.getConfigManager().isDebugEnabled() && action != ClickAction.NO_ACTION) {
                        plugin.getLogger().info("[DEBUG] Action " + action + " was not handled by common handlers (expected if menu-specific).");
                    }
                    break;
            }
        }

        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] handleMenuItemClick - END - Action: " + action + ", ActionData: " + Arrays.toString(actionData));
    }

    public void executeMenuItemAction(Player player, ClickAction action, String[] actionData) {
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] executeMenuItemAction - START - Player: " + player.getName() + ", Action: " + action + ", ActionData: " + Arrays.toString(actionData));

        String[] processedActionData = actionData;
        if (actionData != null && actionData.length > 0) {
            processedActionData = Arrays.stream(actionData)
                    .filter(Objects::nonNull)
                    .map(data -> replacePlaceholders(player, data, null))
                    .toArray(String[]::new);
        }

        switch (action) {
            case CONSOLE_COMMAND: executeConsoleCommand(player, processedActionData, null); break;
            case PLAYER_COMMAND:
            case PLAYER_COMMAND_OP: executeCommandAction(player, action, processedActionData, null); break;
            case PLAY_SOUND: executePlaySoundAction(player, processedActionData); break;
            case TITLE: executeTitleAction(player, processedActionData); break;
            case MESSAGE: executeMessageAction(player, processedActionData, null); break;
            case ACTIONBAR: executeActionbarAction(player, processedActionData); break;
            default:
                plugin.getLogger().warning("[WARNING] executeMenuItemAction called with context-dependent action ("+action+") without inventory context. Action skipped for player " + player.getName() + ".");
                break;
        }

        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] executeMenuItemAction - END - Player: " + player.getName() + ", Action: " + action);
    }

    public void executeMenuOpenActions(Player player, InventoryHolder holder) {
        List<MenuItem.ClickActionData> openActions;
        FileConfiguration config = null;
        String path = null;

        if (holder instanceof PunishMenu) {
            config = plugin.getConfigManager().getPunishMenuConfig().getConfig(); path = "menu";
        } else if (holder instanceof PunishDetailsMenu detailsMenu) {
            config = plugin.getConfigManager().getPunishDetailsMenuConfig().getConfig(); path = "menu.punish_details." + detailsMenu.getPunishmentType();
        } else if (holder instanceof TimeSelectorMenu) {
            config = plugin.getConfigManager().getTimeSelectorMenuConfig().getConfig(); path = "menu";
        } else if (holder instanceof HistoryMenu) {
            config = plugin.getConfigManager().getHistoryMenuConfig().getConfig(); path = "menu";
        }

        if (config != null && path != null) {
            openActions = plugin.getConfigManager().loadMenuOpenActions(config, path);
            if (!openActions.isEmpty() && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Executing " + openActions.size() + " open actions for " + holder.getClass().getSimpleName());
            }
            for (MenuItem.ClickActionData actionData : openActions) {
                executeMenuItemAction(player, actionData.getAction(), actionData.getActionData());
            }
        } else if (plugin.getConfigManager().isDebugEnabled()){
            plugin.getLogger().info("[DEBUG] No valid config/path found for open actions for " + holder.getClass().getSimpleName());
        }
    }
    private void executePlaySoundAction(Player player, String[] soundArgs) {
        if (soundArgs == null || soundArgs.length < 1 || soundArgs[0] == null || soundArgs[0].isEmpty()) {
            plugin.getLogger().warning("PLAY_SOUND action requires at least a non-empty sound name."); return;
        }
        try {
            Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
            float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f;
            float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;
            player.playSound(player.getLocation(), sound, volume, pitch);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND played '" + sound.name() + "' for " + player.getName());
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND: " + Arrays.toString(soundArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND: " + soundArgs[0]); }
    }

    private void executeTitleAction(Player player, String[] titleArgs) {
        if (titleArgs == null || titleArgs.length < 3 || titleArgs[0] == null || titleArgs[1] == null || titleArgs[2] == null) {
            plugin.getLogger().warning("TITLE action requires at least non-null title, subtitle, and time_seconds arguments."); return;
        }
        String titleText = MessageUtils.getColorMessage(titleArgs[0]);
        String subtitleText = MessageUtils.getColorMessage(titleArgs[1]);
        try {
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            int fadeInTicks = titleArgs.length > 3 && titleArgs[3] != null ? Integer.parseInt(titleArgs[3]) : 10;
            int fadeOutTicks = titleArgs.length > 4 && titleArgs[4] != null ? Integer.parseInt(titleArgs[4]) : 20;
            player.sendTitle(titleText, subtitleText, fadeInTicks, timeSeconds * 20, fadeOutTicks);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE sent to " + player.getName() + ": " + titleText);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid number format for time/fade values in TITLE action: " + Arrays.toString(titleArgs)); }
    }

    private void executeMessageAction(Player player, String[] messageArgs, InventoryHolder holder) {
        String messageText = (messageArgs != null && messageArgs.length > 0)
                ? String.join(":", messageArgs)
                : null;

        if (messageText == null) {
            plugin.getLogger().warning("MESSAGE action requires a non-null message text argument."); return;
        }

        OfflinePlayer target = getTargetForAction(holder);
        if (target != null) {
            messageText = plugin.getConfigManager().processPlaceholders(messageText, target);
        }
        messageText = MessageUtils.getColorMessage(messageText);

        player.sendMessage(messageText);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE sent to " + player.getName() + ": " + messageText);
    }

    private void executeActionbarAction(Player player, String[] messageArgs) {
        String messageText = (messageArgs != null && messageArgs.length > 0)
                ? String.join(":", messageArgs)
                : null;

        if (messageText == null) {
            plugin.getLogger().warning("ACTIONBAR action requires a non-null message text argument."); return;
        }
        messageText = MessageUtils.getColorMessage(messageText);

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(messageText));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR sent to " + player.getName() + ": " + messageText);
    }

    private void executePlaySoundTargetAction(Player player, InventoryHolder holder, String[] soundArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (soundArgs == null || soundArgs.length < 1 || soundArgs[0] == null || soundArgs[0].isEmpty()) { plugin.getLogger().warning("PLAY_SOUND_TARGET action requires at least a non-empty sound name."); return; }
        try {
            Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
            float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f;
            float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;
            targetPlayer.playSound(targetPlayer.getLocation(), sound, volume, pitch);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_TARGET played '" + sound.name() + "' for " + targetPlayer.getName());
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND_TARGET: " + Arrays.toString(soundArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND_TARGET: " + soundArgs[0]); }
    }

    private void executeTitleTargetAction(Player player, InventoryHolder holder, String[] titleArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (titleArgs == null || titleArgs.length < 3 || titleArgs[0] == null || titleArgs[1] == null || titleArgs[2] == null) { plugin.getLogger().warning("TITLE_TARGET action requires at least non-null title, subtitle, and time_seconds arguments."); return; }

        String titleText = plugin.getConfigManager().processPlaceholders(titleArgs[0], target);
        titleText = MessageUtils.getColorMessage(titleText);
        String subtitleText = plugin.getConfigManager().processPlaceholders(titleArgs[1], target);
        subtitleText = MessageUtils.getColorMessage(subtitleText);
        try {
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            int fadeInTicks = titleArgs.length > 3 && titleArgs[3] != null ? Integer.parseInt(titleArgs[3]) : 10;
            int fadeOutTicks = titleArgs.length > 4 && titleArgs[4] != null ? Integer.parseInt(titleArgs[4]) : 20;
            targetPlayer.sendTitle(titleText, subtitleText, fadeInTicks, timeSeconds * 20, fadeOutTicks);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_TARGET sent to " + targetPlayer.getName() + ": " + titleText);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid number format for time/fade values in TITLE_TARGET action: " + Arrays.toString(titleArgs)); }
    }

    private void executeMessageTargetAction(Player player, InventoryHolder holder, String[] messageArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();

        String messageText = (messageArgs != null && messageArgs.length > 0)
                ? String.join(":", messageArgs)
                : null;

        if (messageText == null) { plugin.getLogger().warning("MESSAGE_TARGET action requires a non-null message text argument."); return; }

        messageText = plugin.getConfigManager().processPlaceholders(messageText, target);
        messageText = MessageUtils.getColorMessage(messageText);

        targetPlayer.sendMessage(messageText);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_TARGET sent to " + targetPlayer.getName() + ": " + messageText);
    }

    private void executeActionbarTargetAction(Player player, InventoryHolder holder, String[] messageArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();

        String messageText = (messageArgs != null && messageArgs.length > 0)
                ? String.join(":", messageArgs)
                : null;

        if (messageText == null) { plugin.getLogger().warning("ACTIONBAR_TARGET action requires a non-null message text argument."); return; }

        messageText = plugin.getConfigManager().processPlaceholders(messageText, target);
        messageText = MessageUtils.getColorMessage(messageText);

        targetPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(messageText));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_TARGET sent to " + targetPlayer.getName() + ": " + messageText);
    }

    private void executeGiveEffectTargetAction(Player player, InventoryHolder holder, String[] effectArgs) {
        OfflinePlayer target = getTargetForAction(holder);
        if (target == null || !target.isOnline()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] GIVE_EFFECT_TARGET skipped: Target offline or null."); return; }
        Player targetPlayer = target.getPlayer();
        if (effectArgs == null || effectArgs.length < 3 || effectArgs[0] == null || effectArgs[1] == null || effectArgs[2] == null) { plugin.getLogger().warning("GIVE_EFFECT_TARGET action requires at least effect_type, duration_seconds, and amplifier arguments."); return; }
        try {
            NamespacedKey effectKey = NamespacedKey.minecraft(effectArgs[0].toLowerCase()); PotionEffectType effectType = PotionEffectType.getByKey(effectKey);
            if (effectType == null) effectType = PotionEffectType.getByName(effectArgs[0].toUpperCase());
            if (effectType == null) { plugin.getLogger().warning("Invalid PotionEffectType configured: " + effectArgs[0] + " for GIVE_EFFECT_TARGET action."); return; }
            int durationSeconds = Integer.parseInt(effectArgs[1]); int amplifier = Integer.parseInt(effectArgs[2]);
            boolean particles = effectArgs.length <= 3 || effectArgs[3] == null || Boolean.parseBoolean(effectArgs[3]);
            boolean icon = particles; boolean ambient = false;
            PotionEffect effect = new PotionEffect(effectType, durationSeconds * 20, amplifier, ambient, particles, icon);
            targetPlayer.addPotionEffect(effect);
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] GIVE_EFFECT_TARGET action executed for player: " + targetPlayer.getName() + ", effect: " + effectType.getKey() + ", duration: " + durationSeconds + "s, amplifier: " + amplifier);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid duration or amplifier format for GIVE_EFFECT_TARGET action: " + Arrays.toString(effectArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("IllegalArgumentException in GIVE_EFFECT_TARGET action: " + e.getMessage() + ", Args: " + Arrays.toString(effectArgs)); }
    }

    private void executePlaySoundModsAction(Player player, InventoryHolder holder, String[] soundArgs) {
        if (soundArgs == null || soundArgs.length < 1 || soundArgs[0] == null || soundArgs[0].isEmpty()) { plugin.getLogger().warning("PLAY_SOUND_MODS action requires at least a non-empty sound name."); return; }
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_MODS skipped: No online mods."); return; }
        try {
            Sound sound = Sound.valueOf(soundArgs[0].toUpperCase());
            float volume = soundArgs.length > 1 ? Float.parseFloat(soundArgs[1]) : 1.0f;
            float pitch = soundArgs.length > 2 ? Float.parseFloat(soundArgs[2]) : 1.0f;
            mods.forEach(mod -> mod.playSound(mod.getLocation(), sound, volume, pitch));
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] PLAY_SOUND_MODS played sound '" + sound.name() + "' for " + mods.size() + " mods.");
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid volume or pitch format for PLAY_SOUND_MODS: " + Arrays.toString(soundArgs));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound name configured for PLAY_SOUND_MODS: " + soundArgs[0]); }
    }

    private void executeTitleModsAction(Player player, InventoryHolder holder, String[] titleArgs) {
        if (titleArgs == null || titleArgs.length < 3 || titleArgs[0] == null || titleArgs[1] == null || titleArgs[2] == null) { plugin.getLogger().warning("TITLE_MODS action requires at least non-null title, subtitle, and time_seconds arguments."); return; }
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_MODS skipped: No online mods."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String titleText = plugin.getConfigManager().processPlaceholders(titleArgs[0], target); titleText = replacePlaceholders(player, titleText, holder); titleText = MessageUtils.getColorMessage(titleText);
        String subtitleText = plugin.getConfigManager().processPlaceholders(titleArgs[1], target); subtitleText = replacePlaceholders(player, subtitleText, holder); subtitleText = MessageUtils.getColorMessage(subtitleText);
        try {
            int timeSeconds = Integer.parseInt(titleArgs[2]);
            int fadeInTicks = titleArgs.length > 3 && titleArgs[3] != null ? Integer.parseInt(titleArgs[3]) : 10;
            int fadeOutTicks = titleArgs.length > 4 && titleArgs[4] != null ? Integer.parseInt(titleArgs[4]) : 20;
            final String finalTitle = titleText; final String finalSubtitle = subtitleText;
            mods.forEach(mod -> mod.sendTitle(finalTitle, finalSubtitle, fadeInTicks, timeSeconds * 20, fadeOutTicks));
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] TITLE_MODS sent to " + mods.size() + " mods. Title: " + finalTitle);
        } catch (NumberFormatException e) { plugin.getLogger().warning("Invalid number format for time/fade values in TITLE_MODS action: " + Arrays.toString(titleArgs)); }
    }

    private void executeMessageModsAction(Player player, InventoryHolder holder, String[] messageArgs) {
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_MODS skipped: No online mods."); return; }
        OfflinePlayer target = getTargetForAction(holder);

        String baseMessage = (messageArgs != null && messageArgs.length > 0)
                ? String.join(":", messageArgs)
                : null;

        if (baseMessage == null) { plugin.getLogger().warning("MESSAGE_MODS action requires a non-null message text argument."); return; }

        String initiatorName = (player != null) ? player.getName() : "System";
        baseMessage = plugin.getConfigManager().processPlaceholders(baseMessage, target);
        baseMessage = baseMessage.replace("{player}", initiatorName);
        baseMessage = MessageUtils.getColorMessage(baseMessage);

        final String finalMessage = baseMessage;
        mods.forEach(mod -> mod.sendMessage(finalMessage));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] MESSAGE_MODS sent to " + mods.size() + " mods: " + finalMessage);
    }

    private void executeActionbarModsAction(Player player, InventoryHolder holder, String[] messageArgs) {
        List<Player> mods = getMods(); if (mods.isEmpty()) { if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_MODS skipped: No online mods."); return; }
        OfflinePlayer target = getTargetForAction(holder);

        String baseMessage = (messageArgs != null && messageArgs.length > 0)
                ? String.join(":", messageArgs)
                : null;

        if (baseMessage == null) { plugin.getLogger().warning("ACTIONBAR_MODS action requires a non-null message text argument."); return; }

        String initiatorName = (player != null) ? player.getName() : "System";
        baseMessage = plugin.getConfigManager().processPlaceholders(baseMessage, target);
        baseMessage = baseMessage.replace("{player}", initiatorName);
        baseMessage = MessageUtils.getColorMessage(baseMessage);

        final String finalMessage = baseMessage;
        mods.forEach(mod -> mod.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(finalMessage)));
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] ACTIONBAR_MODS sent to " + mods.size() + " mods: " + finalMessage);
    }

    private void executeCommandAction(Player player, ClickAction action, String[] commandData, InventoryHolder holder) {
        if (commandData == null || commandData.length < 1 || commandData[0] == null || commandData[0].isEmpty()) { plugin.getLogger().warning("Invalid COMMAND action data: Command string is missing or empty."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String commandToExecute = plugin.getConfigManager().processPlaceholders(commandData[0], target);
        commandToExecute = ColorUtils.translateRGBColors(commandToExecute);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing COMMAND: " + action + " Command: " + commandToExecute);
        final String finalCommand = commandToExecute;
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (action) {
                case CONSOLE_COMMAND: Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand); break;
                case PLAYER_COMMAND: player.performCommand(finalCommand); break;
                case PLAYER_COMMAND_OP:
                    boolean wasOp = player.isOp();
                    try { player.setOp(true); player.performCommand(finalCommand); }
                    catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Error executing OP command '" + finalCommand + "' for player " + player.getName(), e); }
                    finally { if (!wasOp) player.setOp(false); }
                    break;
                default: plugin.getLogger().warning("executeCommandAction called with non-command action: " + action); break;
            }
        });
    }

    private void executeConsoleCommand(Player player, String[] commandData, InventoryHolder holder) {
        if (commandData == null || commandData.length < 1 || commandData[0] == null || commandData[0].isEmpty()) { plugin.getLogger().warning("Invalid CONSOLE_COMMAND action data: Command string is missing or empty."); return; }
        OfflinePlayer target = getTargetForAction(holder);
        String commandToExecute = plugin.getConfigManager().processPlaceholders(commandData[0], target);
        commandToExecute = ColorUtils.translateRGBColors(commandToExecute);
        if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().info("[DEBUG] Executing CONSOLE_COMMAND: " + commandToExecute);
        final String finalCommand = commandToExecute;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }

    private boolean handlePunishMenuActions(Player player, PunishMenu punishMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        UUID targetUUID = punishMenu.getTargetUUID();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;

        switch (action) {
            case OPEN_MENU:
                if (firstArg != null) {
                    switch (firstArg.toLowerCase()) {
                        case "ban_details": if (!player.hasPermission(PUNISH_BAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "ban details"); return true; } new PunishDetailsMenu(targetUUID, plugin, BAN_PUNISHMENT_TYPE).open(player); return true;
                        case "mute_details": if (!player.hasPermission(PUNISH_MUTE_PERMISSION)) { sendNoPermissionMenuMessage(player, "mute details"); return true; } new PunishDetailsMenu(targetUUID, plugin, MUTE_PUNISHMENT_TYPE).open(player); return true;
                        case "softban_details": if (!player.hasPermission(PUNISH_SOFTBAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "softban details"); return true; } new PunishDetailsMenu(targetUUID, plugin, SOFTBAN_PUNISHMENT_TYPE).open(player); return true;
                        case "kick_details": if (!player.hasPermission(PUNISH_KICK_PERMISSION)) { sendNoPermissionMenuMessage(player, "kick details"); return true; } new PunishDetailsMenu(targetUUID, plugin, KICK_PUNISHMENT_TYPE).open(player); return true;
                        case "warn_details": if (!player.hasPermission(PUNISH_WARN_PERMISSION)) { sendNoPermissionMenuMessage(player, "warn details"); return true; } new PunishDetailsMenu(targetUUID, plugin, WARN_PUNISHMENT_TYPE).open(player); return true;
                        case "freeze_details": if (!player.hasPermission(PUNISH_FREEZE_PERMISSION)) { sendNoPermissionMenuMessage(player, "freeze details"); return true; } new PunishDetailsMenu(targetUUID, plugin, FREEZE_PUNISHMENT_TYPE).open(player); return true;
                        case "history_menu": if (!player.hasPermission(USE_PERMISSION)) { sendNoPermissionMenuMessage(player, "history menu"); return true; } new HistoryMenu(targetUUID, plugin).open(player); return true;
                        default: return false;
                    }
                }
                return false;

            case REQUEST_INPUT:
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("change_target")) {
                        if (!player.hasPermission(USE_PERMISSION)) { sendNoPermissionMenuMessage(player, "change target action"); return true; }
                        player.closeInventory();
                        requestNewTargetName(player);
                        return true;
                    }
                }
                return false;

            default:
                return false;
        }
    }

    private boolean handlePunishDetailsMenuActions(Player player, PunishDetailsMenu punishDetailsMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case OPEN_MENU:
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("time_selector")) { if (punishDetailsMenu.isTimeRequired()) { new TimeSelectorMenu(punishDetailsMenu, plugin).open(player); } else { sendConfigMessage(player, "messages.time_not_applicable"); } return true; }
                    else if (firstArg.equalsIgnoreCase("punish_menu")) { new PunishMenu(punishDetailsMenu.getTargetUUID(), plugin).open(player); return true; }
                } return false;
            case REQUEST_INPUT:
                if (firstArg != null && firstArg.equalsIgnoreCase("reason_input")) { requestReasonInput(player, punishDetailsMenu); return true; } return false;
            case CONFIRM_PUNISHMENT: handleConfirmButtonClick(player, punishDetailsMenu); return true;
            case UN_SOFTBAN: if (!player.hasPermission(UNPUNISH_SOFTBAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "unsoftban"); return true; } handleUnsoftbanButtonClick(player, punishDetailsMenu); return true;
            case UN_FREEZE: if (!player.hasPermission(UNPUNISH_FREEZE_PERMISSION)) { sendNoPermissionMenuMessage(player, "unfreeze"); return true; } handleUnfreezeButtonClick(player, punishDetailsMenu); return true;
            case UN_BAN: if (!player.hasPermission(UNPUNISH_BAN_PERMISSION)) { sendNoPermissionMenuMessage(player, "unban"); return true; } executeUnbanAction(player, punishDetailsMenu, plugin.getConfigManager().getDefaultUnpunishmentReason(BAN_PUNISHMENT_TYPE)); return true;
            case UN_MUTE: if (!player.hasPermission(UNPUNISH_MUTE_PERMISSION)) { sendNoPermissionMenuMessage(player, "unmute"); return true; } executeUnmuteAction(player, punishDetailsMenu, plugin.getConfigManager().getDefaultUnpunishmentReason(MUTE_PUNISHMENT_TYPE)); return true;
            case UN_WARN: if (!player.hasPermission(UNPUNISH_WARN_PERMISSION)) { sendNoPermissionMenuMessage(player, "unwarn"); return true; } executeUnwarnAction(player, punishDetailsMenu, plugin.getConfigManager().getDefaultUnpunishmentReason(WARN_PUNISHMENT_TYPE)); return true;
            default: return false;
        }
    }


    private boolean handleTimeSelectorMenuActions(Player player, TimeSelectorMenu timeSelectorMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        PunishDetailsMenu detailsMenu = timeSelectorMenu.getPunishDetailsMenu();
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case ADJUST_TIME:
                if (firstArg != null) {
                    int secondsToAdd = switch (firstArg.toLowerCase()) { case "minus_5_min" -> -300; case "minus_2_hour" -> -7200; case "minus_1_day" -> -86400; case "minus_5_day" -> -432000; case "plus_15_min" -> 900; case "plus_6_hour" -> 21600; case "plus_1_day" -> 86400; case "plus_7_day" -> 604800; default -> 0; };
                    if (secondsToAdd != 0) { timeSelectorMenu.adjustTime(secondsToAdd); timeSelectorMenu.updateTimeDisplayItem(player); } return true;
                } return false;
            case REQUEST_INPUT:
                if (firstArg != null && firstArg.equalsIgnoreCase("custom_time_input")) { requestCustomTimeInput(player, detailsMenu); return true; } return false;
            case SET_PUNISHMENT_TYPE:
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("permanent_time")) { setPermanentTime(detailsMenu, player); return true; }
                    else if (firstArg.equalsIgnoreCase("confirm_time")) { handleTimeDisplayClick(timeSelectorMenu, detailsMenu, player); return true; }
                } return false;
            case OPEN_MENU:
                if (firstArg != null && firstArg.equalsIgnoreCase("punish_details")) { detailsMenu.open(player); return true; } return false;
            default: return false;
        }
    }

    private boolean handleHistoryMenuActions(Player player, HistoryMenu historyMenu, ClickAction action, String[] actionData, MenuItem clickedMenuItem) {
        String firstArg = (actionData != null && actionData.length > 0) ? actionData[0] : null;
        switch (action) {
            case OPEN_MENU:
                if (firstArg != null && firstArg.equalsIgnoreCase("punish_menu")) { new PunishMenu(historyMenu.getTargetUUID(), plugin).open(player); return true; } return false;
            case ADJUST_PAGE:
                if (firstArg != null) {
                    if (firstArg.equalsIgnoreCase("next_page")) historyMenu.nextPage(player);
                    else if (firstArg.equalsIgnoreCase("previous_page")) historyMenu.previousPage(player);
                    return true;
                } return false;
            default: return false;
        }
    }

    private void requestNewTargetName(Player player) {
        sendConfigMessage(player, "messages.prompt_new_target");
        storeInputData(player, setupChatInputTimeout(player, null, "change_target"), null, "change_target");
    }

    private void requestReasonInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        String promptPath = "messages.prompt_" + punishDetailsMenu.getPunishmentType().toLowerCase() + "_reason";
        sendConfigMessage(player, promptPath);
        storeInputData(player, setupChatInputTimeout(player, punishDetailsMenu, "reason_input"), punishDetailsMenu, "reason_input");
    }

    private void requestCustomTimeInput(Player player, PunishDetailsMenu punishDetailsMenu) {
        player.closeInventory();
        sendConfigMessage(player, "messages.prompt_custom_time");
        storeInputData(player, setupChatInputTimeout(player, punishDetailsMenu, "custom_time"), punishDetailsMenu, "custom_time");
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Requested custom_time input from " + player.getName() + ". Timeout task stored.");
        }
    }

    private BukkitTask setupChatInputTimeout(Player player, PunishDetailsMenu menu, String inputType) {
        cancelExistingTimeout(player);

        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (inputTypes.getOrDefault(player.getUniqueId(), "").equals(inputType)) {
                    handleInputTimeout(player);
                }
            }
        }.runTaskLater(plugin, 400L);

        return timeoutTask;
    }

    private void storeInputData(Player player, BukkitTask task, PunishDetailsMenu menu, String inputType) {
        if (task != null) {
            inputTimeouts.put(player.getUniqueId(), task);
        } else {
            plugin.getLogger().warning("Attempted to store a null timeout task for " + player.getName() + ", inputType: " + inputType);
        }

        if (menu != null) {
            pendingDetailsMenus.put(player.getUniqueId(), menu);
        } else {
            pendingDetailsMenus.remove(player.getUniqueId());
        }
        inputTypes.put(player.getUniqueId(), inputType);

        if (plugin.getConfigManager().isDebugEnabled() && task != null) {
            plugin.getLogger().info("[DEBUG] Stored input data for " + player.getName() + ": inputType=" + inputType + ", menuContext=" + (menu != null) + ", taskID=" + task.getTaskId());
        }
    }

    private void cancelExistingTimeout(Player player) {
        BukkitTask existingTask = inputTimeouts.remove(player.getUniqueId());
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }
    }

    private void handleInputTimeout(Player player) {
        if (player != null && player.isOnline()) {
            if (inputTypes.containsKey(player.getUniqueId())) {
                sendConfigMessage(player, "messages.input_timeout");
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[DEBUG] Input timed out for " + player.getName() + ". Type was: " + inputTypes.get(player.getUniqueId()));
                }
            }
        }
        clearPlayerInputData(player);
    }

    private void handlePlayerInput(Player player, String input) {
        PunishDetailsMenu detailsMenu = pendingDetailsMenus.get(player.getUniqueId());
        String inputType = inputTypes.get(player.getUniqueId());

        if (inputType == null) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Received chat input from " + player.getName() + " but no input type was stored (likely timed out or manually closed menu just before input). Input: " + input);
            }
            return;
        }

        cancelExistingTimeout(player);

        if (input.equalsIgnoreCase("cancel")) {
            handleCancelInput(player, detailsMenu);
            return;
        }

        processValidInput(player, input, detailsMenu, inputType);
    }

    private void handleCancelInput(Player player, PunishDetailsMenu detailsMenu) {
        sendConfigMessage(player, "messages.input_cancelled");
        if (detailsMenu != null) {
            reopenDetailsMenu(player, detailsMenu);
        }
        clearPlayerInputData(player);
    }

    private void processValidInput(Player player, String input, PunishDetailsMenu detailsMenu, String inputType) {
        switch (inputType.toLowerCase()) {
            case "change_target":
                handleNewTargetInput(player, input);
                break;
            case "reason_input":
                if (detailsMenu != null) {
                    handleReasonInput(player, input, detailsMenu);
                } else {
                    plugin.getLogger().warning("Reason input received but no details menu context for " + player.getName());
                }
                break;
            case "custom_time":
                if (detailsMenu != null) {
                    handleCustomTimeInput(player, input, detailsMenu);
                } else {
                    plugin.getLogger().warning("Custom time input received but no details menu context for " + player.getName());
                }
                break;
            default:
                plugin.getLogger().warning("Unknown input type processed: " + inputType + " for player " + player.getName());
                break;
        }
        clearPlayerInputData(player);
    }

    private void handleNewTargetInput(Player player, String input) {
        OfflinePlayer newTarget = Bukkit.getOfflinePlayer(input);
        if (!newTarget.hasPlayedBefore() && !newTarget.isOnline()) {
            sendConfigMessage(player, "messages.never_played", "{input}", input);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> new PunishMenu(newTarget.getUniqueId(), plugin).open(player));
        }
    }

    private void handleReasonInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        detailsMenu.setBanReason(input);
        reopenDetailsMenu(player, detailsMenu);
    }

    private void handleCustomTimeInput(Player player, String input, PunishDetailsMenu detailsMenu) {
        int seconds = TimeUtils.parseTime(input, plugin.getConfigManager());
        String permanentKeyword = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

        if (seconds > 0 || input.equalsIgnoreCase(permanentKeyword)) {
            String timeToSet = input.equalsIgnoreCase(permanentKeyword) ? permanentKeyword : TimeUtils.formatTime(seconds, plugin.getConfigManager());
            detailsMenu.setBanTime(timeToSet);
            reopenDetailsMenu(player, detailsMenu);
        } else {
            sendConfigMessage(player, "messages.invalid_time_format_command", "{input}", input);
            reopenDetailsMenu(player, detailsMenu);
        }
    }

    private void reopenDetailsMenu(Player player, PunishDetailsMenu detailsMenu) {
        Bukkit.getScheduler().runTask(plugin, () -> detailsMenu.open(player));
    }

    private void clearPlayerInputData(Player player) {
        cancelExistingTimeout(player);
        pendingDetailsMenus.remove(player.getUniqueId());
        inputTypes.remove(player.getUniqueId());
    }

    private void handleConfirmButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        boolean timeMissing = punishDetailsMenu.isTimeRequired() && !punishDetailsMenu.isTimeSet();
        boolean reasonMissing = punishDetailsMenu.isReasonRequiredForConfirmation() && !punishDetailsMenu.isReasonSet();

        if (timeMissing || reasonMissing) {
            sendValidationMessages(player, timeMissing, reasonMissing);
        } else {
            confirmDynamicPunishment(player, punishDetailsMenu);
        }
    }

    private void handleUnsoftbanButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getSoftBanDatabaseManager().isSoftBanned(targetUUID)) {
            confirmUnsoftban(player, punishDetailsMenu, plugin.getConfigManager().getDefaultUnpunishmentReason(SOFTBAN_PUNISHMENT_TYPE));
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            sendConfigMessage(player, "messages.no_active_softban", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }

    private void handleUnfreezeButtonClick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        if (plugin.getPluginFrozenPlayers().containsKey(targetUUID)) {
            confirmUnfreeze(player, punishDetailsMenu, plugin.getConfigManager().getDefaultUnpunishmentReason(FREEZE_PUNISHMENT_TYPE));
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            sendConfigMessage(player, "messages.no_active_freeze", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }


    private void confirmDynamicPunishment(Player player, PunishDetailsMenu punishDetailsMenu) {
        String type = punishDetailsMenu.getPunishmentType().toLowerCase();
        if (!checkPunishmentPermission(player, type)) {
            sendNoPermissionMenuMessage(player, type + " punishment");
            playSound(player, "punish_error");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(punishDetailsMenu.getTargetUUID());
        if (hasBypassPermission(target, type)) {
            sendBypassError(player, target, type);
            playSound(player, "punish_error");
            player.closeInventory();
            return;
        }

        switch (type) {
            case BAN_PUNISHMENT_TYPE:
            case MUTE_PUNISHMENT_TYPE:
                confirmStandardPunishment(player, punishDetailsMenu);
                break;
            case SOFTBAN_PUNISHMENT_TYPE:
                confirmSoftBan(player, punishDetailsMenu);
                break;
            case KICK_PUNISHMENT_TYPE:
                confirmKick(player, punishDetailsMenu);
                break;
            case WARN_PUNISHMENT_TYPE:
                confirmWarn(player, punishDetailsMenu);
                break;
            case FREEZE_PUNISHMENT_TYPE:
                confirmFreeze(player, punishDetailsMenu);
                break;
            default:
                plugin.getLogger().warning("Attempted to confirm unknown punishment type: " + type);
                sendConfigMessage(player, "messages.invalid_punishment_type", "{types}", "Known Types");
                playSound(player, "punish_error");
                break;
        }
    }

    private void confirmStandardPunishment(Player player, PunishDetailsMenu detailsMenu) {
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String punishmentType = detailsMenu.getPunishmentType();
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal(punishmentType);
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand(punishmentType);
        String timeInput = detailsMenu.getBanTime() != null ? detailsMenu.getBanTime() : "permanent";
        String reason = detailsMenu.getBanReason() != null ? detailsMenu.getBanReason() : "No reason specified";

        if (useInternal) {
            if (punishmentType.equalsIgnoreCase(BAN_PUNISHMENT_TYPE)) {
                long banDuration = TimeUtils.parseTime(timeInput, plugin.getConfigManager());
                Date expiration = (banDuration > 0) ? new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(banDuration)) : null;
                String punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, punishmentType, reason, player.getName(), expiration != null ? expiration.getTime() : Long.MAX_VALUE, timeInput);

                boolean banByIp = plugin.getConfigManager().isPunishmentByIp("ban");
                String targetIdentifier = target.getName();
                Player onlineTarget = target.getPlayer();

                if (banByIp) {
                    if (onlineTarget != null && onlineTarget.getAddress() != null) {
                        targetIdentifier = onlineTarget.getAddress().getAddress().getHostAddress();
                        Bukkit.getBanList(BanList.Type.IP).addBan(targetIdentifier, reason, expiration, player.getName());
                    } else {
                        sendConfigMessage(player, "messages.player_not_online_for_ipban", "{target}", target.getName());
                        return;
                    }
                } else {
                    Bukkit.getBanList(BanList.Type.NAME).addBan(targetIdentifier, reason, expiration, player.getName());
                }

                if (target.isOnline()) {
                    String kickMessage = getKickMessage(plugin.getConfigManager().getBanScreen(), reason, timeInput, punishmentId, expiration);
                    target.getPlayer().kickPlayer(kickMessage);
                }

                executePunishmentCommandAndLog(player, "", target, detailsMenu, punishmentType, timeInput, reason, punishmentId);
            } else if (punishmentType.equalsIgnoreCase(MUTE_PUNISHMENT_TYPE)) {
                long muteDuration = TimeUtils.parseTime(timeInput, plugin.getConfigManager());
                long endTime = (muteDuration > 0) ? System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(muteDuration) : Long.MAX_VALUE;
                String punishmentId = plugin.getSoftBanDatabaseManager().mutePlayer(target.getUniqueId(), endTime, reason, player.getName());

                if (target.isOnline()) {
                    String muteMessage = plugin.getConfigManager().getMessage("messages.you_are_muted",
                            "{time}", timeInput,
                            "{reason}", reason,
                            "{punishment_id}", punishmentId);
                    target.getPlayer().sendMessage(MessageUtils.getColorMessage(muteMessage));
                }
                playSound(player, "punish_confirm");
                sendPunishmentConfirmation(player, target, timeInput, reason, punishmentType, punishmentId);
                executeHookActions(player, target, punishmentType, timeInput, reason, false);
                player.closeInventory();
            }
        } else {
            String processedCommand = commandTemplate
                    .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                    .replace("{time}", timeInput)
                    .replace("{reason}", reason);
            executePunishmentCommandAndLog(player, processedCommand, target, detailsMenu, punishmentType, timeInput, reason, null);
        }
    }

    private void confirmSoftBan(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Softbanned by moderator";
        String timeInput = punishDetailsMenu.getBanTime() != null ? punishDetailsMenu.getBanTime() : "permanent";
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal("softban");
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand("softban");

        long endTime = calculateEndTime(timeInput);
        String durationString = timeInput;

        if (endTime != Long.MAX_VALUE) {
            endTime += 1000L;
        } else {
            durationString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        }

        if(useInternal) {
            String punishmentId = plugin.getSoftBanDatabaseManager().softBanPlayer(targetUUID, endTime, reason, player.getName());
            playSound(player, "punish_confirm");
            sendPunishmentConfirmation(player, target, timeInput, reason, SOFTBAN_PUNISHMENT_TYPE, punishmentId);
            executeHookActions(player, target, SOFTBAN_PUNISHMENT_TYPE, timeInput, reason, false);
        } else {
            String processedCommand = commandTemplate
                    .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                    .replace("{time}", timeInput)
                    .replace("{reason}", reason);
            executePunishmentCommandAndLog(player, processedCommand, target, punishDetailsMenu, SOFTBAN_PUNISHMENT_TYPE, timeInput, reason, null);
        }
    }

    private void confirmFreeze(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Frozen by moderator";
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal("freeze");
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand("freeze");
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");

        if (plugin.getPluginFrozenPlayers().containsKey(targetUUID)) {
            sendConfigMessage(player, "messages.already_frozen", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
            return;
        }

        if(useInternal) {
            plugin.getPluginFrozenPlayers().put(targetUUID, true);
            playSound(player, "punish_confirm");
            String punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, FREEZE_PUNISHMENT_TYPE, reason, player.getName(), Long.MAX_VALUE, permanentDisplay);
            sendPunishmentConfirmation(player, target, permanentDisplay, reason, FREEZE_PUNISHMENT_TYPE, punishmentId);

            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                sendFreezeReceivedMessage(onlineTarget);
                plugin.getFreezeListener().startFreezeActionsTask(onlineTarget);
            }
            executeHookActions(player, target, FREEZE_PUNISHMENT_TYPE, permanentDisplay, reason, false);
        } else {
            String processedCommand = commandTemplate
                    .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                    .replace("{reason}", reason);
            executePunishmentCommandAndLog(player, processedCommand, target, punishDetailsMenu, FREEZE_PUNISHMENT_TYPE, "permanent", reason, null);
        }
    }

    private void confirmKick(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Kicked by moderator";
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal("kick");
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand("kick");
        String punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), KICK_PUNISHMENT_TYPE, reason, player.getName(), 0L, "N/A");

        if (useInternal) {
            if (target.isOnline()) {
                String kickMessage = getKickMessage(plugin.getConfigManager().getKickScreen(), reason, "N/A", punishmentId, null);
                target.getPlayer().kickPlayer(kickMessage);
            }
            executePunishmentCommandAndLog(player, "", target, punishDetailsMenu, KICK_PUNISHMENT_TYPE, "N/A", reason, punishmentId);
        } else {
            String processedCommand = commandTemplate
                    .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                    .replace("{reason}", reason);
            executePunishmentCommandAndLog(player, processedCommand, target, punishDetailsMenu, KICK_PUNISHMENT_TYPE, "N/A", reason, punishmentId);
        }
    }

    private void confirmWarn(Player player, PunishDetailsMenu punishDetailsMenu) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String reason = punishDetailsMenu.getBanReason() != null ? punishDetailsMenu.getBanReason() : "Warned by moderator";
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal("warn");
        String commandTemplate = plugin.getConfigManager().getPunishmentCommand("warn");
        String punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), WARN_PUNISHMENT_TYPE, reason, player.getName(), 0L, "N/A");

        if (useInternal) {
            if (target.isOnline()) {
                target.getPlayer().sendMessage(MessageUtils.getColorMessage(reason));
            }
            executePunishmentCommandAndLog(player, "", target, punishDetailsMenu, WARN_PUNISHMENT_TYPE, "N/A", reason, punishmentId);
        } else {
            String processedCommand = commandTemplate
                    .replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString())
                    .replace("{reason}", reason);
            executePunishmentCommandAndLog(player, processedCommand, target, punishDetailsMenu, WARN_PUNISHMENT_TYPE, "N/A", reason, punishmentId);
        }
    }



    private void confirmUnsoftban(Player player, PunishDetailsMenu punishDetailsMenu, String reason) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String punishmentId = plugin.getSoftBanDatabaseManager().unSoftBanPlayer(targetUUID, player.getName(), reason);
        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, SOFTBAN_PUNISHMENT_TYPE, punishmentId);
        executeHookActions(player, target, SOFTBAN_PUNISHMENT_TYPE, "N/A", reason, true);
    }


    private void confirmUnfreeze(Player player, PunishDetailsMenu punishDetailsMenu, String reason) {
        UUID targetUUID = punishDetailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean removed = plugin.getPluginFrozenPlayers().remove(targetUUID) != null;

        if (removed) {
            String originalPunishmentId = plugin.getSoftBanDatabaseManager().getLatestPunishmentId(targetUUID, FREEZE_PUNISHMENT_TYPE);
            String finalReason = reason;
            if (reason.equals(plugin.getConfigManager().getDefaultUnpunishmentReason(FREEZE_PUNISHMENT_TYPE))) {
                finalReason = reason.replace("{player}", player.getName()) +
                        (originalPunishmentId != null ? " (ID: " + originalPunishmentId + ")" : "");
            }

            String punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unfreeze", finalReason, player.getName(), 0L, "N/A");
            playSound(player, "punish_confirm");
            sendUnpunishConfirmation(player, target, FREEZE_PUNISHMENT_TYPE, punishmentId);
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                plugin.getFreezeListener().stopFreezeActionsTask(targetUUID);
                sendUnfreezeMessage(onlineTarget);
            }
            executeHookActions(player, target, FREEZE_PUNISHMENT_TYPE, "N/A", finalReason, true);
        } else {
            sendConfigMessage(player, "messages.no_active_freeze", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            playSound(player, "punish_error");
        }
    }



    private void executeUnbanAction(Player player, InventoryHolder holder, String reason) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal("ban");
        String commandTemplate = plugin.getConfigManager().getUnpunishCommand("ban");
        String originalPunishmentId = plugin.getSoftBanDatabaseManager().getLatestPunishmentId(targetUUID, "ban");

        String finalReason = reason;
        if (reason.equals(plugin.getConfigManager().getDefaultUnpunishmentReason(BAN_PUNISHMENT_TYPE))) {
            finalReason = reason.replace("{player}", player.getName()) +
                    (originalPunishmentId != null ? " (ID: " + originalPunishmentId + ")" : "");
        }


        if (useInternal) {
            String latestBanId = plugin.getSoftBanDatabaseManager().getLatestActivePunishmentId(targetUUID, "ban");
            DatabaseManager.PlayerInfo playerInfo = null;
            if (latestBanId != null) {
                playerInfo = plugin.getSoftBanDatabaseManager().getPlayerInfo(latestBanId);
            }

            boolean banByIp = plugin.getConfigManager().isPunishmentByIp("ban");
            boolean pardoned = false;

            if (banByIp && playerInfo != null && playerInfo.getIp() != null) {
                String ip = playerInfo.getIp();
                if (Bukkit.getBanList(BanList.Type.IP).isBanned(ip)) {
                    Bukkit.getBanList(BanList.Type.IP).pardon(ip);
                    pardoned = true;
                }
            }

            if (!pardoned) {
                if (target.isBanned()) {
                    Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
                    pardoned = true;
                }
            }

            if (!pardoned) {
                sendConfigMessage(player, "messages.not_banned");
                return;
            }
        } else {
            String processedCommand = commandTemplate.replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand));
        }
        String punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unban", finalReason, player.getName(), 0L, "N/A");

        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, BAN_PUNISHMENT_TYPE, punishmentId);

        executeHookActions(player, target, BAN_PUNISHMENT_TYPE, "N/A", finalReason, true);
    }


    private void executeUnmuteAction(Player player, InventoryHolder holder, String reason) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal("mute");
        String commandTemplate = plugin.getConfigManager().getUnpunishCommand("mute");
        String originalPunishmentId = plugin.getSoftBanDatabaseManager().getLatestPunishmentId(targetUUID, "mute");

        String finalReason = reason;
        if (reason.equals(plugin.getConfigManager().getDefaultUnpunishmentReason(MUTE_PUNISHMENT_TYPE))) {
            finalReason = reason.replace("{player}", player.getName()) +
                    (originalPunishmentId != null ? " (ID: " + originalPunishmentId + ")" : "");
        }
        String punishmentId = null;

        if (useInternal) {
            if (!plugin.getSoftBanDatabaseManager().isMuted(target.getUniqueId())) {
                sendConfigMessage(player, "messages.not_muted");
                return;
            }
            punishmentId = plugin.getSoftBanDatabaseManager().unmutePlayer(target.getUniqueId(), player.getName(), finalReason);
        } else {
            String processedCommand = commandTemplate.replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand));
            punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unmute", finalReason, player.getName(), 0L, "N/A");
        }

        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, MUTE_PUNISHMENT_TYPE, punishmentId);

        executeHookActions(player, target, MUTE_PUNISHMENT_TYPE, "N/A", finalReason, true);
    }


    private void executeUnwarnAction(Player player, InventoryHolder holder, String reason) {
        PunishDetailsMenu detailsMenu = (PunishDetailsMenu) holder;
        UUID targetUUID = detailsMenu.getTargetUUID();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        boolean useInternal = plugin.getConfigManager().isPunishmentInternal("warn");
        String commandTemplate = plugin.getConfigManager().getUnpunishCommand("warn");
        String originalPunishmentId = plugin.getSoftBanDatabaseManager().getLatestPunishmentId(targetUUID, "warn");

        String finalReason = reason;
        if (reason.equals(plugin.getConfigManager().getDefaultUnpunishmentReason(WARN_PUNISHMENT_TYPE))) {
            finalReason = reason.replace("{player}", player.getName()) +
                    (originalPunishmentId != null ? " (ID: " + originalPunishmentId + ")" : "");
        }
        String punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(targetUUID, "unwarn", finalReason, player.getName(), 0L, "N/A");

        if (!useInternal) {
            String processedCommand = commandTemplate.replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString());
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand));
        } else {
            plugin.getLogger().warning("Unwarn command is empty, internal unwarn is not supported.");
        }

        playSound(player, "punish_confirm");
        sendUnpunishConfirmation(player, target, WARN_PUNISHMENT_TYPE, punishmentId);
        executeHookActions(player, target, WARN_PUNISHMENT_TYPE, "N/A", finalReason, true);
    }

    private void setPermanentTime(PunishDetailsMenu detailsMenu, Player player) {
        String permanentDisplayString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        detailsMenu.setBanTime(permanentDisplayString);
        reopenDetailsMenu(player, detailsMenu);
    }

    private void handleTimeDisplayClick(TimeSelectorMenu timeSelectorMenu, PunishDetailsMenu detailsMenu, Player player) {
        if (timeSelectorMenu.getCurrentTimeSeconds() > 0) {
            String formattedTime = timeSelectorMenu.getFormattedTime();
            detailsMenu.setBanTime(formattedTime);
            reopenDetailsMenu(player, detailsMenu);
        } else {
            sendConfigMessage(player, "messages.set_valid_time_confirm");
            playSound(player, "punish_error");
        }
    }

    private MenuItem getMenuItemClicked(int slot, InventoryHolder holder) {
        if (holder instanceof PunishMenu menu) return getPunishMenuItem(slot, menu);
        if (holder instanceof PunishDetailsMenu menu) return getPunishDetailsMenuItem(slot, menu);
        if (holder instanceof TimeSelectorMenu menu) return getTimeSelectorMenuItem(slot, menu);
        if (holder instanceof HistoryMenu menu) return getHistoryMenuItem(slot, menu);
        return null;
    }

    private MenuItem getPunishMenuItem(int slot, PunishMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getPunishMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        } return null;
    }

    private MenuItem getPunishDetailsMenuItem(int slot, PunishDetailsMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager(); String punishmentType = menu.getPunishmentType();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getDetailsMenuItemConfig(punishmentType, itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        } return null;
    }

    private MenuItem getTimeSelectorMenuItem(int slot, TimeSelectorMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getTimeSelectorItemKeys()) {
            MenuItem menuItem = configManager.getTimeSelectorMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        } return null;
    }

    private MenuItem getHistoryMenuItem(int slot, HistoryMenu menu) {
        MainConfigManager configManager = plugin.getConfigManager();
        for (String itemKey : menu.getMenuItemKeys()) {
            MenuItem menuItem = configManager.getHistoryMenuItemConfig(itemKey);
            if (menuItem != null && menuItem.getSlots() != null && menuItem.getSlots().contains(slot)) return menuItem;
        }
        for (MenuItem historyEntryItem : menu.getHistoryEntryItems()) {
            if (historyEntryItem != null && historyEntryItem.getSlots() != null && historyEntryItem.getSlots().contains(slot)) return historyEntryItem;
        }
        return null;
    }

    private OfflinePlayer getTargetForAction(InventoryHolder holder) {
        if (holder instanceof PunishMenu menu) return Bukkit.getOfflinePlayer(menu.getTargetUUID());
        if (holder instanceof PunishDetailsMenu menu) return Bukkit.getOfflinePlayer(menu.getTargetUUID());
        if (holder instanceof TimeSelectorMenu menu) return Bukkit.getOfflinePlayer(menu.getPunishDetailsMenu().getTargetUUID());
        if (holder instanceof HistoryMenu menu) return Bukkit.getOfflinePlayer(menu.getTargetUUID());
        if (holder instanceof TempHolder temp) return Bukkit.getOfflinePlayer(temp.getTargetUUID());
        return null;
    }

    private String replacePlaceholders(Player player, String text, InventoryHolder holder) {
        if (text == null) return null;
        String playerName = (player != null) ? player.getName() : "Unknown";
        return text.replace("{player}", playerName);
    }

    private List<Player> getMods() {
        return Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(MOD_PERMISSION)).collect(Collectors.toList());
    }

    private void playSound(Player player, String soundKey) {
        String soundName = plugin.getConfigManager().getSoundName(soundKey);
        if (soundName != null && !soundName.isEmpty()) {
            try { Sound sound = Sound.valueOf(soundName.toUpperCase()); player.playSound(player.getLocation(), sound, 1.0f, 1.0f); }
            catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid sound configured for key '" + soundKey + "': " + soundName); }
        }
    }

    private void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        MessageUtils.sendConfigMessage(plugin, sender, path, replacements);
    }

    private void sendNoPermissionMenuMessage(Player player, String actionName) {
        sendConfigMessage(player, "messages.no_permission_menu_action", "{action}", actionName);
    }

    private void sendValidationMessages(Player player, boolean timeMissing, boolean reasonMissing) {
        if (timeMissing && reasonMissing) sendConfigMessage(player, "messages.set_time_reason_before_confirm");
        else if (timeMissing) sendConfigMessage(player, "messages.set_time_before_confirm");
        else if (reasonMissing) sendConfigMessage(player, "messages.set_reason_before_confirm");
        playSound(player, "punish_error");
    }

    private void sendPunishmentConfirmation(Player player, OfflinePlayer target, String timeValue, String reason, String punishmentType, String punishmentId) {
        player.closeInventory();
        String punishmentActionVerb = plugin.getConfigManager().getPunishmentDisplayForm(punishmentType, true);
        sendConfigMessage(player, "messages.punishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString(),
                "{time}", timeValue,
                "{reason}", reason,
                "{punishment_action_verb}", punishmentActionVerb,
                "{punishment_id}", punishmentId);
    }

    private void sendUnpunishConfirmation(Player player, OfflinePlayer target, String punishType, String punishmentId) {
        player.closeInventory();
        sendConfigMessage(player, "messages.direct_unpunishment_confirmed",
                "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString(),
                "{punishment_type}", punishType,
                "{punishment_id}", punishmentId);
    }

    private void sendFreezeReceivedMessage(Player player) {
        sendConfigMessage(player, "messages.you_are_frozen");
    }

    private void sendUnfreezeMessage(Player player) {
        sendConfigMessage(player, "messages.you_are_unfrozen");
    }

    private long calculateEndTime(String timeInput) {
        if (timeInput == null) return 0L;
        String permanentDisplay = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
        if (timeInput.equalsIgnoreCase(permanentDisplay)) return Long.MAX_VALUE;
        int seconds = TimeUtils.parseTime(timeInput, plugin.getConfigManager());
        if (seconds <= 0) return 0L;
        return System.currentTimeMillis() + (seconds * 1000L);
    }

    private boolean hasBypassPermission(OfflinePlayer target, String punishmentType) {
        if (!(target instanceof Player onlineTarget)) return false;
        String bypassPerm = "crown.bypass." + punishmentType.toLowerCase();
        return onlineTarget.hasPermission(bypassPerm);
    }

    private void sendBypassError(Player punisher, OfflinePlayer target, String punishmentType) {
        String messageKey = "messages.bypass_error_" + punishmentType.toLowerCase();
        sendConfigMessage(punisher, messageKey, "{target}", target.getName() != null ? target.getName() : target.getUniqueId().toString());
    }

    private boolean checkPunishmentPermission(Player executor, String punishmentType) {
        String perm = "crown.punish." + punishmentType.toLowerCase();
        return executor.hasPermission(perm);
    }

    private void executePunishmentCommandAndLog(Player player, String command, OfflinePlayer target, PunishDetailsMenu detailsMenu, String punishmentType, String timeValue, String reason, String punishmentId) {
        if (command != null && !command.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }

        playSound(player, "punish_confirm");

        if (punishmentId == null) {
            long punishmentEndTime = 0L;
            String durationString = "permanent";

            if (punishmentType.equalsIgnoreCase(BAN_PUNISHMENT_TYPE) || punishmentType.equalsIgnoreCase(MUTE_PUNISHMENT_TYPE)) {
                punishmentEndTime = calculateEndTime(timeValue);
                durationString = timeValue;
                if (punishmentEndTime == Long.MAX_VALUE) {
                    durationString = plugin.getConfigManager().getMessage("placeholders.permanent_time_display");
                }
            }
            punishmentId = plugin.getSoftBanDatabaseManager().logPunishment(target.getUniqueId(), punishmentType, reason, player.getName(), punishmentEndTime, durationString);
        }
        sendPunishmentConfirmation(player, target, timeValue, reason, punishmentType, punishmentId);
        executeHookActions(player, target, punishmentType, timeValue, reason, false);
    }

    public void executeHookActions(CommandSender executor, OfflinePlayer target, String punishmentType, String time, String reason, boolean isUnpunish) {
        List<ClickActionData> actions;
        String hookType = isUnpunish ? "on_unpunish" : "on_punish";

        if (isUnpunish) {
            actions = plugin.getConfigManager().getOnUnpunishActions(punishmentType);
        } else {
            actions = plugin.getConfigManager().getOnPunishActions(punishmentType);
        }

        if (actions.isEmpty()) {
            return;
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Preparing to execute " + actions.size() + " hook actions for " + hookType + "." + punishmentType);
        }

        final String executorName = (executor instanceof Player) ? executor.getName() : "Console";
        final String targetName = (target != null && target.getName() != null) ? target.getName() : (target != null ? target.getUniqueId().toString() : "Unknown");
        final String finalTime = (time != null) ? time : "N/A";
        final String finalReason = (reason != null) ? reason : "N/A";
        final String finalPunishmentTypePlaceholder = punishmentType;

        for (ClickActionData actionData : actions) {
            String[] originalArgs = actionData.getActionData();
            String[] processedHookArgs = new String[originalArgs.length];

            for (int i = 0; i < originalArgs.length; i++) {
                if (originalArgs[i] != null) {
                    String currentArg = originalArgs[i];
                    currentArg = currentArg.replace("{player}", executorName);
                    currentArg = currentArg.replace("{target}", targetName);
                    currentArg = currentArg.replace("{reason}", finalReason);
                    currentArg = currentArg.replace("{time}", finalTime);
                    currentArg = currentArg.replace("{punishment_type}", finalPunishmentTypePlaceholder);
                    currentArg = plugin.getConfigManager().processPlaceholders(currentArg, null);
                    processedHookArgs[i] = MessageUtils.getColorMessage(currentArg);
                } else {
                    processedHookArgs[i] = null;
                }
            }

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] -> Executing Hook Action: " + actionData.getAction() + " with Processed Args: " + Arrays.toString(processedHookArgs));
            }

            executeSpecificHookAction(executor, target, actionData.getAction(), processedHookArgs, plugin);
        }
    }
    private String getKickMessage(List<String> lines, String reason, String timeLeft, String punishmentId, Date expiration) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = dateFormat.format(new Date());
        String dateUntil = expiration != null ? dateFormat.format(expiration) : "Never";

        return lines.stream()
                .map(MessageUtils::getColorMessage)
                .map(line -> line.replace("{reason}", reason))
                .map(line -> line.replace("{time_left}", timeLeft))
                .map(line -> line.replace("{punishment_id}", punishmentId))
                .map(line -> line.replace("{date}", date))
                .map(line -> line.replace("{date_until}", dateUntil))
                .map(line -> line.replace("{support_link}", plugin.getConfigManager().getSupportLink()))
                .collect(Collectors.joining("\n"));
    }
    private void executeSpecificHookAction(CommandSender executor, OfflinePlayer target, ClickAction action, String[] actionArgs, Crown plugin) {
        if (action == ClickAction.NO_ACTION) return;

        if (plugin.getConfigManager().isDebugEnabled()) {
            String executorName = (executor instanceof Player) ? executor.getName() : "Console";
            String targetName = (target != null && target.getName() != null) ? target.getName() : "Unknown";
            plugin.getLogger().info("[DEBUG] executeSpecificHookAction - Action: " + action + " | Executor: " + executorName + " | Target: " + targetName + " | Using Args: " + Arrays.toString(actionArgs));
        }

        switch (action) {
            case CONSOLE_COMMAND:
                if (actionArgs.length >= 1 && actionArgs[0] != null) {
                    final String commandToRun = actionArgs[0];
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun));
                } else { logInvalidArgs(action, actionArgs, plugin); }
                break;

            case PLAYER_COMMAND:
            case PLAYER_COMMAND_OP:
                if (!(executor instanceof Player playerExecutor)) {
                    plugin.getLogger().warning("Skipping PLAYER_COMMAND/PLAYER_COMMAND_OP hook action: Executor is not a player."); return;
                }
                if (actionArgs.length >= 1 && actionArgs[0] != null) {
                    final String commandToRun = actionArgs[0];
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (action == ClickAction.PLAYER_COMMAND_OP) {
                            boolean wasOp = playerExecutor.isOp();
                            try {
                                playerExecutor.setOp(true);
                                playerExecutor.performCommand(commandToRun);
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.SEVERE, "Error executing OP command '" + commandToRun + "' for player " + playerExecutor.getName(), e);
                            } finally {
                                if (!wasOp) {
                                    playerExecutor.setOp(false);
                                }
                            }
                        } else {
                            playerExecutor.performCommand(commandToRun);
                        }
                    });
                } else { logInvalidArgs(action, actionArgs, plugin); }
                break;

            case PLAY_SOUND:
                if (executor instanceof Player playerExecutor) { executePlaySoundAction(playerExecutor, actionArgs); }
                break;
            case PLAY_SOUND_TARGET:
                if (target != null) { executePlaySoundTargetAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;
            case PLAY_SOUND_MODS:
                if (target != null) { executePlaySoundModsAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;

            case TITLE:
                if (executor instanceof Player playerExecutor) { executeTitleAction(playerExecutor, actionArgs); }
                break;
            case TITLE_TARGET:
                if (target != null) { executeTitleTargetAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;
            case TITLE_MODS:
                if (target != null) { executeTitleModsAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;

            case MESSAGE:
                if (executor instanceof Player playerExecutor) { executeMessageAction(playerExecutor, actionArgs, null); }
                else { executor.sendMessage(actionArgs.length > 0 ? actionArgs[0] : ""); }
                break;
            case MESSAGE_TARGET:
                if (target != null) { executeMessageTargetAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;
            case MESSAGE_MODS:
                if (target != null) { executeMessageModsAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;

            case ACTIONBAR:
                if (executor instanceof Player playerExecutor) { executeActionbarAction(playerExecutor, actionArgs); }
                break;
            case ACTIONBAR_TARGET:
                if (target != null) { executeActionbarTargetAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;
            case ACTIONBAR_MODS:
                if (target != null) { executeActionbarModsAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;

            case GIVE_EFFECT_TARGET:
                if (target != null) { executeGiveEffectTargetAction(null, new TempHolder(target.getUniqueId()), actionArgs); }
                else { logTargetMissing(action, plugin); } break;

            default:
                if (plugin.getConfigManager().isDebugEnabled()) { plugin.getLogger().info("[DEBUG] Skipping unsupported action type for hook execution: " + action); }
                break;
        }
    }

    private void logTargetMissing(ClickAction action, Crown plugin) {
        plugin.getLogger().warning("Cannot execute hook action " + action + ": Target player context is missing.");
    }

    private void logInvalidArgs(ClickAction action, String[] args, Crown plugin) {
        plugin.getLogger().warning("Invalid arguments for hook action " + action + ": " + Arrays.toString(args));
    }

    private static class TempHolder implements InventoryHolder {
        private final UUID targetUUID;
        TempHolder(UUID targetUUID) { this.targetUUID = targetUUID; }
        @Override public @NotNull Inventory getInventory() { return null; }
        public UUID getTargetUUID() { return targetUUID; }
    }
}
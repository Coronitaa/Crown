package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ModeratorStateUpdateTask extends BukkitRunnable {

    private final Crown plugin;

    public ModeratorStateUpdateTask(Crown plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                String baseMessage;
                String extraInfo = "";

                // Spectator Mode Handling
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    // Check the state BEFORE entering spectator to show correct status
                    Boolean wasVanished = plugin.getModeratorModeManager().getPreSpectatorVanishState(player.getUniqueId());
                    // Fallback to current state if pre-state is null
                    boolean isVanished = (wasVanished != null) ? wasVanished : plugin.getModeratorModeManager().isVanished(player.getUniqueId());

                    String statusPart = isVanished ?
                            plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_vanished").split("\\|")[0] : // Get just the "VANISHED" part
                            plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_visible").split("\\|")[0];

                    String hintPart = plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_spectator");

                    // Combine: "VANISHED | Right-click to exit"
                    baseMessage = statusPart + " &8| " + hintPart.replaceAll(".*\\|\\s*", "");
                }
                // Normal Mod Mode
                else {
                    if (plugin.getModeratorModeManager().isVanished(player.getUniqueId())) {
                        baseMessage = plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_vanished");
                    } else {
                        baseMessage = plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_visible");
                    }
                }

                // Append Selected Player info if applicable
                Player selected = plugin.getModeratorModeManager().getSelectedPlayer(player.getUniqueId());
                if (selected != null) {
                    extraInfo = plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_selected", "{target}", selected.getName());
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(MessageUtils.getColorMessage(baseMessage + extraInfo)));
            }
        }
    }
}
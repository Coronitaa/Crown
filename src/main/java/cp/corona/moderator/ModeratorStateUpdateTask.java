package cp.corona.moderator;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ModeratorStateUpdateTask extends BukkitRunnable {

    private final Crown plugin;
    private final Set<UUID> playersToGlow = new HashSet<>();

    public ModeratorStateUpdateTask(Crown plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        playersToGlow.clear();
        Set<Player> moderators = new HashSet<>();

        // First, handle action bars and determine which players should glow globally
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                moderators.add(player);
                handleActionBar(player);
                updatePlayersToGlowSet(player);
            }
        }

        // Now, update the glowing state for all players based on the collected set
        updateGlobalGlowingState();

        // Finally, update the team colors for each moderator individually
        for (Player moderator : moderators) {
            updateModeratorGlowTeams(moderator);
        }
    }

    private void handleActionBar(Player player) {
        UUID uuid = player.getUniqueId();
        String baseMessage;
        String extraInfo = "";

        if (plugin.getModeratorModeManager().isTemporarySpectator(uuid)) {
            long remainingTime = plugin.getModeratorModeManager().getRemainingSpectatorTime(uuid);
            baseMessage = plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_spectator_timer")
                    .replace("{time}", String.valueOf(remainingTime + 1));
        } else {
            baseMessage = plugin.getModeratorModeManager().isVanished(uuid)
                    ? plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_vanished")
                    : plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_visible");
        }

        Player selected = plugin.getModeratorModeManager().getSelectedPlayer(uuid);
        if (selected != null) {
            extraInfo = plugin.getConfigManager().getMessage("messages.mod_mode_actionbar_selected", "{target}", selected.getName());
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(MessageUtils.getColorMessage(baseMessage + extraInfo)));
    }

    private void updatePlayersToGlowSet(Player moderator) {
        Player selected = plugin.getModeratorModeManager().getSelectedPlayer(moderator.getUniqueId());
        if (selected != null) {
            playersToGlow.add(selected.getUniqueId());
        }

        if (plugin.getModeratorModeManager().isGlowingEnabled(moderator.getUniqueId())) {
            double range = plugin.getConfigManager().getModModeConfig().getConfig().getDouble("glowing-settings.range", 50.0);
            double rangeSq = range * range; // Pre-calcular cuadrado

            // Optimización: Usar getNearbyEntities si el rango es limitado, en lugar de iterar a todos
            if (range > 0 && range < 100) { // Si el rango es razonable para búsqueda espacial
                for (Entity entity : moderator.getNearbyEntities(range, range, range)) {
                    if (entity instanceof Player target) {
                        if (target.equals(moderator) || plugin.getModeratorModeManager().isInModeratorMode(target.getUniqueId())) continue;
                        playersToGlow.add(target.getUniqueId());
                    }
                }
            } else {
                // Fallback a iteración global para rango infinito o muy grande (filtrando por mundo primero)
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (target.getWorld() != moderator.getWorld()) continue; // Chequeo rápido de mundo
                    if (target.equals(moderator) || plugin.getModeratorModeManager().isInModeratorMode(target.getUniqueId())) continue;
                    
                    if (range == -1 || moderator.getLocation().distanceSquared(target.getLocation()) <= rangeSq) {
                        playersToGlow.add(target.getUniqueId());
                    }
                }
            }
        }
    }

    private void updateGlobalGlowingState() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) continue;

            boolean shouldGlow = playersToGlow.contains(player.getUniqueId());
            if (player.isGlowing() != shouldGlow) {
                player.setGlowing(shouldGlow);
            }
        }
    }

    private void updateModeratorGlowTeams(Player moderator) {
        if (moderator.getScoreboard() == Bukkit.getScoreboardManager().getMainScoreboard()) {
            moderator.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        Scoreboard board = moderator.getScoreboard();

        Team glowTeam = getOrCreateTeam(board, "crown_glow", "glowing-settings.color", ChatColor.AQUA);
        Team selectedTeam = getOrCreateTeam(board, "crown_selected", "glowing-settings.selected-color", ChatColor.YELLOW);

        Player selectedPlayer = plugin.getModeratorModeManager().getSelectedPlayer(moderator.getUniqueId());

        for (UUID glowingUuid : playersToGlow) {
            Player target = Bukkit.getPlayer(glowingUuid);
            if (target == null) continue;

            if (target.equals(selectedPlayer)) {
                if (!selectedTeam.hasEntry(target.getName())) {
                    selectedTeam.addEntry(target.getName());
                }
            } else {
                if (!glowTeam.hasEntry(target.getName())) {
                    glowTeam.addEntry(target.getName());
                }
            }
        }

        // Clean up teams from players who are no longer glowing for this moderator
        cleanupTeam(glowTeam, playersToGlow);
        cleanupTeam(selectedTeam, playersToGlow);
    }

    private Team getOrCreateTeam(Scoreboard board, String name, String colorPath, ChatColor defaultColor) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        String colorName = plugin.getConfigManager().getModModeConfig().getConfig().getString(colorPath, defaultColor.name());
        try {
            team.setColor(ChatColor.valueOf(colorName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            team.setColor(defaultColor);
        }
        return team;
    }

    private void cleanupTeam(Team team, Set<UUID> currentlyGlowing) {
        Set<String> entriesToRemove = new HashSet<>();
        for (String entry : team.getEntries()) {
            Player p = Bukkit.getPlayer(entry);
            if (p == null || !currentlyGlowing.contains(p.getUniqueId())) {
                entriesToRemove.add(entry);
            }
        }
        entriesToRemove.forEach(team::removeEntry);
    }
}

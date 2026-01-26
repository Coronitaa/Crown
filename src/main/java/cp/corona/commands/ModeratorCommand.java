package cp.corona.commands;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ModeratorCommand implements CommandExecutor, TabCompleter {

    private final Crown plugin;

    public ModeratorCommand(Crown plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.sendConfigMessage(plugin, sender, "messages.player_only");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("crown.mod.use")) {
            MessageUtils.sendConfigMessage(plugin, player, "messages.no_permission");
            return true;
        }

        // /mod target <player>
        if (args.length > 0 && args[0].equalsIgnoreCase("target")) {
            if (args.length < 2) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.invalid_player_name");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.player_not_online", "{input}", args[1]);
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_target_self_error");
                return true;
            }

            if (!plugin.getModeratorModeManager().isInModeratorMode(player.getUniqueId())) {
                plugin.getModeratorModeManager().enableModeratorMode(player, false, false);
            }
            
            plugin.getModeratorModeManager().setSelectedPlayer(player.getUniqueId(), target.getUniqueId());
            MessageUtils.sendConfigMessage(plugin, player, "messages.mod_mode_target_set", "{target}", target.getName());
            return true;
        }

        plugin.getModeratorModeManager().toggleModeratorMode(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("crown.mod.use")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], List.of("target"), completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("target")) {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            }
        }
        return completions;
    }
}

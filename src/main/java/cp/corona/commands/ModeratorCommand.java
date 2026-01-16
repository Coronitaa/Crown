package cp.corona.commands;

import cp.corona.crown.Crown;
import cp.corona.menus.mod.LockerMenu;
import cp.corona.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

        // /mod locker [player|all]
        if (args.length > 0 && args[0].equalsIgnoreCase("locker")) {
            if (args.length > 1) {
                if (!player.hasPermission("crown.mod.locker.admin")) {
                    MessageUtils.sendConfigMessage(plugin, player, "messages.no_permission");
                    return true;
                }

                if (args[1].equalsIgnoreCase("all")) {
                    // Global Locker
                    new LockerMenu(plugin, player, null, 1).open();
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                     MessageUtils.sendConfigMessage(plugin, player, "messages.never_played", "{input}", args[1]);
                     return true;
                }
                MessageUtils.sendConfigMessage(plugin, player, "messages.locker_opened_other", "{target}", target.getName());
                new LockerMenu(plugin, player, target.getUniqueId(), 1).open();
            } else {
                // Self Locker
                new LockerMenu(plugin, player, player.getUniqueId(), 1).open();
            }
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
            StringUtil.copyPartialMatches(args[0], Collections.singletonList("locker"), completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("locker")) {
            if (sender.hasPermission("crown.mod.locker.admin")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    suggestions.add(p.getName());
                }
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
            } else {
                // For non-admins, only suggest online players
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                                                 .map(Player::getName)
                                                 .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            }
        }
        return completions;
    }
}

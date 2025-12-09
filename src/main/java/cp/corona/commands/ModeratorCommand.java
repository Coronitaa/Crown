package cp.corona.commands;

import cp.corona.crown.Crown;
import cp.corona.menus.LockerMenu;
import cp.corona.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        if (args.length > 0 && args[0].equalsIgnoreCase("locker")) {
            new LockerMenu(plugin, player, 1).open();
            return true;
        }

        plugin.getModeratorModeManager().toggleModeratorMode(player);
        return true;
    }

    // ADDED: Tab Completion
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("crown.mod.use")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Collections.singletonList("locker"), completions);
        }
        return completions;
    }
}

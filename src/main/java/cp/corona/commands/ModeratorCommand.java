package cp.corona.commands;

import cp.corona.crown.Crown;
import cp.corona.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ModeratorCommand implements CommandExecutor {

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

        plugin.getModeratorModeManager().toggleModeratorMode(player);
        return true;
    }
}
package cp.corona.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface SubCommand {
    String getName();
    String getPermission();
    void execute(CommandSender sender, String[] args);
    List<String> onTabComplete(CommandSender sender, String[] args);
}

package cp.corona.commands;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class SimpleSubCommand implements SubCommand {
    private final String name;
    private final String permission;
    private final BiConsumer<CommandSender, String[]> executor;
    private final BiFunction<CommandSender, String[], List<String>> tabCompleter;

    public SimpleSubCommand(String name, String permission, BiConsumer<CommandSender, String[]> executor,
                            BiFunction<CommandSender, String[], List<String>> tabCompleter) {
        this.name = name;
        this.permission = permission;
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        executor.accept(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (tabCompleter == null) {
            return Collections.emptyList();
        }
        return tabCompleter.apply(sender, args);
    }
}

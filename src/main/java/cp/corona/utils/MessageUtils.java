package cp.corona.utils;

import cp.corona.config.MainConfigManager;
import cp.corona.crown.Crown;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for handling messages and color formatting.
 */
public class MessageUtils {

    /**
     * Gets a color formatted message, supporting both legacy and RGB color codes.
     * Uses {@link ColorUtils#translateRGBColors(String)} to translate color codes.
     *
     * @param message The message to format with colors.
     * @return The color formatted message.
     */
    public static String getColorMessage(String message){
        if (message == null || message.isEmpty()) {
            return message; // Return original message if null or empty
        }
        return ColorUtils.translateRGBColors(message); // Use ColorUtils to translate colors
    }

    /**
     * Sends a message to a player, after formatting it with color codes.
     *
     * @param player  The player to send the message to.
     * @param message The message to send.
     */
    public static void sendMessage(Player player, String message) {
        if (player != null && message != null && !message.isEmpty()) {
            player.sendMessage(getColorMessage(message)); // Format message and send to player
        }
    }

    /**
     * Sends a message from the configuration to the command sender, with optional replacements.
     * @param plugin Instance of the main plugin class to access config manager.
     * @param sender Command sender.
     * @param path Path to the message in messages.yml.
     * @param replacements Placeholders to replace in the message.
     */
    public static void sendConfigMessage(Crown plugin, CommandSender sender, String path, String... replacements) {
        String message = plugin.getConfigManager().getMessage(path, replacements);
        sender.sendMessage(MessageUtils.getColorMessage(message));
    }

    /**
     * Generates a formatted kick/ban message from a list of strings.
     * @param lines The list of lines from the configuration.
     * @param reason The reason for the punishment.
     * @param timeLeft The formatted time remaining.
     * @param punishmentId The ID of the punishment.
     * @param expiration The expiration date, or null if permanent.
     * @param configManager The configuration manager to get the support link.
     * @return A single formatted string ready to be used as a kick message.
     */
    public static String getKickMessage(List<String> lines, String reason, String timeLeft, String punishmentId, Date expiration, MainConfigManager configManager) {
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
                .map(line -> line.replace("{support_link}", configManager.getSupportLink()))
                .collect(Collectors.joining("\n"));
    }
}
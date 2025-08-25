// utils/MessageUtils.java
package cp.corona.utils;

import cp.corona.crown.Crown;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
}
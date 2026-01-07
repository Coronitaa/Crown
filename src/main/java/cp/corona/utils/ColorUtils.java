// utils/ColorUtils.java
package cp.corona.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling color codes, including RGB hex codes.
 * Provides methods to translate color codes in messages.
 * Supports legacy color codes ('&') and RGB hex codes ("#RRGGBB").
 * Now also supports MiniMessage format for advanced gradients and styling.
 */
public final class ColorUtils {

    // Regex to match RGB hex codes (#RRGGBB)
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    
    // MiniMessage instance
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Private constructor to prevent instantiation of this utility class.
     * Utility classes should not be instantiated as they are meant to provide static utility methods.
     */
    private ColorUtils() {}

    /**
     * Translates a string with color codes (including RGB codes) into a formatted string with ChatColor.
     * Supports legacy color codes ('&') and RGB hex color codes (e.g., #FF0000 for red).
     * RGB hex codes are parsed and converted to net.md_5.bungee.api.ChatColor.of(hexCode).
     * Legacy color codes are handled by ChatColor.translateAlternateColorCodes('&', ...).
     *
     * @param message The message to be translated.
     * @return The translated message with color codes applied.
     */
    public static String translateRGBColors(String message) {
        if (message == null || message.isEmpty()) {
            return message; // Return original message if null or empty
        }
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        // Find all hex color codes and replace them with actual ChatColors
        while (matcher.find()) {
            String hexColorCode = matcher.group();
            // Replace hex color code with Bungee's ChatColor RGB format
            matcher.appendReplacement(buffer, ChatColor.of(hexColorCode).toString());
        }
        matcher.appendTail(buffer); // Append the rest of the message

        // Translate legacy color codes and return the final colored string
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Parses a string with color codes (including RGB codes) into an Adventure Component.
     * Tries to parse as MiniMessage first if it contains tags like <gradient> or <rainbow>.
     * Otherwise falls back to legacy/hex parsing.
     *
     * @param message The message to be parsed.
     * @return The parsed Component.
     */
    public static Component parseComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        // Check if the message contains MiniMessage tags for gradients or rainbows
        // This is a simple heuristic; you might want to make it more robust or just default to MiniMessage
        if (message.contains("<gradient") || message.contains("<rainbow") || message.contains("<color:")) {
             // Convert legacy ampersand codes to MiniMessage format if mixed usage is expected,
             // but ideally, users should stick to one format.
             // For now, we assume if they use tags, they want MiniMessage parsing.
             return MINI_MESSAGE.deserialize(message);
        }

        // Reuse the logic to get a legacy string with colors, then deserialize it to a Component
        String legacyText = translateRGBColors(message);
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }
}
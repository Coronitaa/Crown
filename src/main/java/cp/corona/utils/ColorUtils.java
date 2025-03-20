// ColorUtils.java
package cp.corona.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling color codes, including RGB hex codes.
 * Provides methods to translate color codes in messages.
 * Supports legacy color codes ('&') and RGB hex codes ("#RRGGBB").
 */
public final class ColorUtils {

    // Regex to match RGB hex codes (#RRGGBB)
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    /**
     * Private constructor to prevent instantiation of this utility class.
     * Utility classes should not be instantiated.
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
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group();
            String replacement = ChatColor.of(hexCode).toString();
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
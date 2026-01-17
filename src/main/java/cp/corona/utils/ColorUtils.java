// utils/ColorUtils.java
package cp.corona.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling color codes, including RGB hex codes.
 * Provides methods to translate color codes in messages.
 * Supports legacy color codes ('&'), RGB hex codes ("#RRGGBB" or "&#RRGGBB"),
 * and MiniMessage format (<color:white>, <gradient:...>).
 */
public final class ColorUtils {

    // Regex to match RGB hex codes (#RRGGBB or &#RRGGBB) for final cleanup if needed
    private static final Pattern HEX_PATTERN = Pattern.compile("(&?#)([a-fA-F0-9]{6})");
    
    // Patterns for Legacy to MiniMessage conversion
    private static final Pattern SPIGOT_HEX_PATTERN = Pattern.compile("&x&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])");
    private static final Pattern STANDARD_HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("&([0-9a-fk-or])");

    private static final Map<String, String> LEGACY_MAP = new HashMap<>();
    static {
        LEGACY_MAP.put("0", "<black>");
        LEGACY_MAP.put("1", "<dark_blue>");
        LEGACY_MAP.put("2", "<dark_green>");
        LEGACY_MAP.put("3", "<dark_aqua>");
        LEGACY_MAP.put("4", "<dark_red>");
        LEGACY_MAP.put("5", "<dark_purple>");
        LEGACY_MAP.put("6", "<gold>");
        LEGACY_MAP.put("7", "<gray>");
        LEGACY_MAP.put("8", "<dark_gray>");
        LEGACY_MAP.put("9", "<blue>");
        LEGACY_MAP.put("a", "<green>");
        LEGACY_MAP.put("b", "<aqua>");
        LEGACY_MAP.put("c", "<red>");
        LEGACY_MAP.put("d", "<light_purple>");
        LEGACY_MAP.put("e", "<yellow>");
        LEGACY_MAP.put("f", "<white>");
        LEGACY_MAP.put("k", "<obfuscated>");
        LEGACY_MAP.put("l", "<bold>");
        LEGACY_MAP.put("m", "<strikethrough>");
        LEGACY_MAP.put("n", "<underlined>");
        LEGACY_MAP.put("o", "<italic>");
        LEGACY_MAP.put("r", "<reset>");
    }

    // MiniMessage instance - Lenient configuration to avoid crashes on invalid tags
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .strict(false)
            .build();
    
    // Legacy serializer that supports hex colors in the format Spigot expects
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ColorUtils() {}

    /**
     * Translates a string with mixed color codes (MiniMessage, Legacy, Hex) into a formatted legacy string.
     * 
     * Process:
     * 1. Parses MiniMessage tags.
     * 2. Serializes to Legacy string (preserving hex colors).
     * 3. Translates remaining legacy '&' codes and custom hex formats.
     *
     * @param message The message to be translated.
     * @return The translated message with legacy color codes applied (ready for Spigot/Bukkit).
     */
    public static String translateRGBColors(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // 1. Sanitize input: Replace § with & to prevent MiniMessage errors
        // and allow legacy codes to pass through as literal text.
        String safeMessage = message.replace('§', '&');
        
        // 2. Convert Legacy codes to MiniMessage tags to ensure they play nice with gradients/etc.
        safeMessage = convertLegacyToMiniMessage(safeMessage);

        // 3. Parse MiniMessage tags -> Component
        Component component;
        try {
            component = MINI_MESSAGE.deserialize(safeMessage);
        } catch (Exception e) {
            // Fallback if MiniMessage fails completely (e.g. unclosed tags)
            // We return the legacy translation so at least & codes work
            return ChatColor.translateAlternateColorCodes('&', safeMessage);
        }

        // 4. Serialize Component -> Legacy String
        // This converts the component back to a string using § codes, including §x hex format.
        String legacy = LEGACY_SERIALIZER.serialize(component);

        // 5. Handle legacy & codes and custom hex formats that might have been treated as text
        // (This catches anything that wasn't converted or was added later)
        Matcher matcher = HEX_PATTERN.matcher(legacy);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(2); // Get the hex part
            matcher.appendReplacement(buffer, ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Converts legacy ampersand codes and hex codes to MiniMessage tags.
     * This allows legacy codes to be used inside MiniMessage structures like gradients.
     */
    private static String convertLegacyToMiniMessage(String text) {
        // Handle Spigot Hex: &x&r&r&g&g&b&b -> <#rrggbb>
        text = SPIGOT_HEX_PATTERN.matcher(text).replaceAll("<#$1$2$3$4$5$6>");
        
        // Handle Standard Hex: &#rrggbb -> <#rrggbb>
        text = STANDARD_HEX_PATTERN.matcher(text).replaceAll("<#$1>");
        
        // Handle Standard Codes: &c -> <red>, etc.
        Matcher matcher = LEGACY_CODE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase();
            String replacement = LEGACY_MAP.getOrDefault(code, "&" + code);
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }

    /**
     * Parses a string with mixed color codes into an Adventure Component.
     * It ensures that legacy codes and hex codes are also parsed correctly.
     *
     * @param message The message to be parsed.
     * @return The parsed Component.
     */
    public static Component parseComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        
        // To ensure consistency, we first convert everything to a fully colored legacy string,
        // then deserialize it back to a Component.
        String legacyText = translateRGBColors(message);
        return LEGACY_SERIALIZER.deserialize(legacyText);
    }
}
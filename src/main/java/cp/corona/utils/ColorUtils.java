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
    private static final Pattern CENTER_TAG_PATTERN = Pattern.compile("(?i)^\\s*(<center>|\\[center\\])\\s*");
    private static final int CENTER_PX = 154;

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

        // 1. Apply centering tags before any color parsing
        String centeredMessage = applyCentering(message);

        // 2. Sanitize input: Replace § with & to prevent MiniMessage errors
        // and allow legacy codes to pass through as literal text.
        String safeMessage = centeredMessage.replace('§', '&');
        
        // 3. Convert Legacy codes to MiniMessage tags to ensure they play nice with gradients/etc.
        safeMessage = convertLegacyToMiniMessage(safeMessage);

        // 4. Parse MiniMessage tags -> Component
        Component component;
        try {
            component = MINI_MESSAGE.deserialize(safeMessage);
        } catch (Exception e) {
            // Fallback if MiniMessage fails completely (e.g. unclosed tags)
            // We return the legacy translation so at least & codes work
            return ChatColor.translateAlternateColorCodes('&', safeMessage);
        }

        // 5. Serialize Component -> Legacy String
        // This converts the component back to a string using § codes, including §x hex format.
        String legacy = LEGACY_SERIALIZER.serialize(component);

        // 6. Handle legacy & codes and custom hex formats that might have been treated as text
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

    private static String applyCentering(String message) {
        String[] lines = message.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = centerLine(lines[i]);
        }
        return String.join("\n", lines);
    }

    private static String centerLine(String line) {
        Matcher matcher = CENTER_TAG_PATTERN.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        String lineContent = line.substring(matcher.end());
        int messagePxSize = getMessagePixelWidth(lineContent);
        int toCompensate = CENTER_PX - (messagePxSize / 2);
        if (toCompensate <= 0) {
            return lineContent;
        }

        int spaceLength = DefaultFontInfo.SPACE.getLength() + 1;
        int compensated = 0;
        StringBuilder padding = new StringBuilder();
        while (compensated < toCompensate) {
            padding.append(' ');
            compensated += spaceLength;
        }
        return padding + lineContent;
    }

    private static int getMessagePixelWidth(String message) {
        int messagePxSize = 0;
        boolean isBold = false;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            if (c == '<') {
                int tagEnd = message.indexOf('>', i);
                if (tagEnd != -1) {
                    String tagContent = message.substring(i + 1, tagEnd).trim().toLowerCase();
                    boolean isClosing = tagContent.startsWith("/");
                    if (isClosing) {
                        tagContent = tagContent.substring(1);
                    }
                    int tagDivider = tagContent.indexOf(':');
                    if (tagDivider != -1) {
                        tagContent = tagContent.substring(0, tagDivider);
                    }
                    if (tagContent.equals("bold") || tagContent.equals("b")) {
                        isBold = !isClosing;
                    } else if (tagContent.equals("reset")) {
                        isBold = false;
                    }
                    i = tagEnd;
                    continue;
                }
            }

            if (c == '§' || c == '&') {
                if (i + 1 < message.length()) {
                    char code = message.charAt(i + 1);
                    if (code == 'x' || code == 'X') {
                        int skipTo = i + 13;
                        if (skipTo < message.length()) {
                            i = skipTo;
                        } else {
                            break;
                        }
                        continue;
                    }
                    if (code == 'l' || code == 'L') {
                        isBold = true;
                    } else if (code == 'r' || code == 'R') {
                        isBold = false;
                    }
                    i++;
                    continue;
                }
            }

            DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
            messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
            messagePxSize++;
        }

        if (messagePxSize > 0) {
            messagePxSize--;
        }
        return messagePxSize;
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

    private enum DefaultFontInfo {
        A('A', 5),
        a('a', 5),
        B('B', 5),
        b('b', 5),
        C('C', 5),
        c('c', 5),
        D('D', 5),
        d('d', 5),
        E('E', 5),
        e('e', 5),
        F('F', 5),
        f('f', 4),
        G('G', 5),
        g('g', 5),
        H('H', 5),
        h('h', 5),
        I('I', 3),
        i('i', 1),
        J('J', 5),
        j('j', 5),
        K('K', 5),
        k('k', 4),
        L('L', 5),
        l('l', 1),
        M('M', 5),
        m('m', 5),
        N('N', 5),
        n('n', 5),
        O('O', 5),
        o('o', 5),
        P('P', 5),
        p('p', 5),
        Q('Q', 5),
        q('q', 5),
        R('R', 5),
        r('r', 5),
        S('S', 5),
        s('s', 5),
        T('T', 5),
        t('t', 4),
        U('U', 5),
        u('u', 5),
        V('V', 5),
        v('v', 5),
        W('W', 5),
        w('w', 5),
        X('X', 5),
        x('x', 5),
        Y('Y', 5),
        y('y', 5),
        Z('Z', 5),
        z('z', 5),
        NUM_1('1', 5),
        NUM_2('2', 5),
        NUM_3('3', 5),
        NUM_4('4', 5),
        NUM_5('5', 5),
        NUM_6('6', 5),
        NUM_7('7', 5),
        NUM_8('8', 5),
        NUM_9('9', 5),
        NUM_0('0', 5),
        EXCLAMATION_POINT('!', 1),
        AT_SYMBOL('@', 6),
        NUM_SIGN('#', 5),
        DOLLAR_SIGN('$', 5),
        PERCENT('%', 5),
        UP_ARROW('^', 5),
        AMPERSAND('&', 5),
        ASTERISK('*', 5),
        LEFT_PARENTHESIS('(', 4),
        RIGHT_PARENTHESIS(')', 4),
        MINUS('-', 5),
        UNDERSCORE('_', 5),
        PLUS_SIGN('+', 5),
        EQUALS_SIGN('=', 5),
        LEFT_CURL_BRACE('{', 4),
        RIGHT_CURL_BRACE('}', 4),
        LEFT_BRACKET('[', 3),
        RIGHT_BRACKET(']', 3),
        COLON(':', 1),
        SEMI_COLON(';', 1),
        DOUBLE_QUOTE('"', 3),
        SINGLE_QUOTE('\'', 1),
        LEFT_ARROW('<', 4),
        RIGHT_ARROW('>', 4),
        QUESTION_MARK('?', 5),
        SLASH('/', 5),
        BACK_SLASH('\\', 5),
        LINE('|', 1),
        TILDE('~', 5),
        TICK('`', 2),
        PERIOD('.', 1),
        COMMA(',', 1),
        SPACE(' ', 3),
        DEFAULT('a', 4);

        private final char character;
        private final int length;

        DefaultFontInfo(char character, int length) {
            this.character = character;
            this.length = length;
        }

        public char getCharacter() {
            return this.character;
        }

        public int getLength() {
            return this.length;
        }

        public int getBoldLength() {
            if (this == SPACE) {
                return this.length;
            }
            return this.length + 1;
        }

        public static DefaultFontInfo getDefaultFontInfo(char character) {
            for (DefaultFontInfo dFI : DefaultFontInfo.values()) {
                if (dFI.getCharacter() == character) {
                    return dFI;
                }
            }
            return DefaultFontInfo.DEFAULT;
        }
    }
}

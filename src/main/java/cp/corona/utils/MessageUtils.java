// MessageUtils.java
package cp.corona.utils;

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
        return ColorUtils.translateRGBColors(message);
    }
}
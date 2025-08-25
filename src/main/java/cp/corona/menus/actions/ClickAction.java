// ClickAction.java
package cp.corona.menus.actions;

/**
 * ////////////////////////////////////////////////
 * //             Crown             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Enumeration of possible actions that can be triggered when clicking a MenuItem or opening a menu.
 * Defines actions like opening menus, setting punishment types, requesting input, executing commands,
 * playing sounds, showing titles, sending messages and more.
 */
public enum ClickAction {
    OPEN_MENU,
    SET_PUNISHMENT_TYPE,
    REQUEST_INPUT,
    ADJUST_TIME,
    CONFIRM_PUNISHMENT,
    UN_SOFTBAN,
    UN_FREEZE,
    UN_BAN,
    UN_MUTE,
    UN_WARN,
    CLOSE_MENU,
    ADJUST_PAGE,
    CONSOLE_COMMAND,
    PLAYER_COMMAND,
    PLAYER_COMMAND_OP,
    PLAY_SOUND,
    TITLE,
    MESSAGE,
    PLAY_SOUND_TARGET,
    GIVE_EFFECT_TARGET,
    TITLE_TARGET,
    MESSAGE_TARGET,
    ACTIONBAR,            // Show action bar to the player clicking. Args: [message]
    ACTIONBAR_TARGET,     // Show action bar to the target player. Args: [message]
    ACTIONBAR_MODS,       // Show action bar to all online players with 'crown.mod'. Args: [message]
    MESSAGE_MODS,         // Send chat message to all online players with 'crown.mod'. Args: [message]
    TITLE_MODS,           // Show title to all online players with 'crown.mod'. Args: [title, subtitle, time_seconds, fade_in_ticks, fade_out_ticks]
    PLAY_SOUND_MODS,      // Play sound to all online players with 'crown.mod'. Args: [sound_name, volume, pitch]
    NO_ACTION;

    /**
     * Safely gets a ClickAction enum value from a string.
     * Returns NO_ACTION if the string does not match any enum value.
     *
     * @param name The string representation of the ClickAction. Case-insensitive.
     * @return The ClickAction enum value, or NO_ACTION if invalid.
     */
    public static ClickAction safeValueOf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return NO_ACTION;
        }
        try {
            return valueOf(name.trim().toUpperCase()); // Use trim() and toUpperCase() for robustness
        } catch (IllegalArgumentException e) {
            // Log the invalid action name if debugging is enabled or needed
            // Bukkit.getLogger().warning("[ClickAction] Invalid ClickAction name provided: " + name);
            return NO_ACTION; // Return NO_ACTION for invalid names
        }
    }
}
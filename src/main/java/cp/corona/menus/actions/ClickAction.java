// ClickAction.java
package cp.corona.menus.actions;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
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
    UN_WARN, // NEW: Added UN_WARN action type
    CLOSE_MENU,
    ADJUST_PAGE,
    CONSOLE_COMMAND,
    PLAYER_COMMAND,
    PLAYER_COMMAND_OP,
    PLAY_SOUND,
    TITLE,
    MESSAGE,
    PLAY_SOUND_TARGET, // NEW: Plays sound to the target player
    GIVE_EFFECT_TARGET, // NEW: Gives effect to the target player
    TITLE_TARGET, // NEW: Shows title to the target player
    MESSAGE_TARGET, // NEW: Sends message to the target player
    NO_ACTION;

    /**
     * Safely gets a ClickAction enum value from a string.
     * Returns NO_ACTION if the string does not match any enum value.
     *
     * @param name The string representation of the ClickAction.
     * @return The ClickAction enum value, or NO_ACTION if invalid.
     */
    public static ClickAction safeValueOf(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NO_ACTION;
        }
    }
}
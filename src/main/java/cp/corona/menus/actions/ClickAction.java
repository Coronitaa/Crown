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
    CLOSE_MENU,
    ADJUST_PAGE,
    CONSOLE_COMMAND,
    PLAYER_COMMAND, // NEW: Executes a command as the player
    PLAYER_COMMAND_OP, // NEW: Executes a command as the player with OP permissions
    PLAY_SOUND,     // Plays a sound - NEW
    TITLE,          // Shows a title - NEW
    MESSAGE,        // Sends a message - NEW
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
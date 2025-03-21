// ClickAction.java
package cp.corona.menus.actions;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Enumeration of possible actions that can be triggered when clicking a MenuItem in a menu.
 * Defines actions like opening menus, setting punishment types, requesting input, executing commands and more.
 *
 * Updated to include CONSOLE_COMMAND and CLOSE_MENU actions and reflect the refactored action system.
 */
public enum ClickAction {
    OPEN_MENU,          // Opens another menu
    SET_PUNISHMENT_TYPE, // Sets the type of punishment
    REQUEST_INPUT,      // Requests text input from the player via chat
    ADJUST_TIME,        // Adjusts the punishment time in the TimeSelectorMenu
    CONFIRM_PUNISHMENT, // Confirms and executes the punishment
    UN_SOFTBAN,         // Executes the un-softban action
    CLOSE_MENU,         // Closes the current menu - NEW ACTION TYPE
    ADJUST_PAGE,        // Changes page in a paginated menu (e.g., HistoryMenu)
    CONSOLE_COMMAND,    // Executes a predefined console command
    NO_ACTION;          // No action is performed - default action

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
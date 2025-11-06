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
    TOGGLE_PUNISH_METHOD,
    APPLY_SOFTBAN,
    APPLY_MUTE,
    APPLY_BAN,
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
    ACTIONBAR,
    ACTIONBAR_TARGET,
    ACTIONBAR_MODS,
    MESSAGE_MODS,
    TITLE_MODS,
    PLAY_SOUND_MODS,
    NO_ACTION;

    public static ClickAction safeValueOf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return NO_ACTION;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NO_ACTION;
        }
    }
}
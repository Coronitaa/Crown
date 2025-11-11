// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\menus\actions\ClickAction.java
package cp.corona.menus.actions;

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
    REQUEST_CLEAR_FULL_INVENTORY,
    REQUEST_CLEAR_ENDER_CHEST,
    OPEN_AUDIT_LOG,
    OPEN_PROFILE_TARGET,
    OPEN_PROFILE_REQUESTER,
    OPEN_PROFILE_MODERATOR,
    // ADDED START
    OPEN_REPORTS_MENU,
    OPEN_REPORT_DETAILS,
    FILTER_REPORTS_STATUS,
    FILTER_REPORTS_NAME,
    CHANGE_REPORT_STATUS,
    ASSIGN_MODERATOR,
    REPORTS_FILTER_TARGET,
    REPORTS_FILTER_REQUESTER,
    HISTORY_TARGET,
    HISTORY_REQUESTER,
    FILTER_MY_REPORTS,

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
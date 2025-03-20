// ClickAction.java
package cp.corona.menus.actions;

public enum ClickAction {
    OPEN_MENU,
    SET_PUNISHMENT_TYPE,
    REQUEST_INPUT,
    ADJUST_TIME,
    CONFIRM_PUNISHMENT,
    UN_SOFTBAN,
    CLOSE_MENU,
    ADJUST_PAGE,
    NO_ACTION; // Default action if none specified

    public static ClickAction safeValueOf(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NO_ACTION;
        }
    }
}
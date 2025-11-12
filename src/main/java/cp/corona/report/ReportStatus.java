package cp.corona.report;

public enum ReportStatus {
    PENDING("Pending", "&e"),
    TAKEN("Taken", "&6"),
    ASSIGNED("Assigned", "&d"),
    RESOLVED("Resolved", "&a"),
    REJECTED("Rejected", "&c");

    private final String displayName;
    private final String color;

    ReportStatus(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public ReportStatus next() {
        // The cycle for the status button. Assignment is a separate action.
        switch (this) {
            case PENDING: return TAKEN;
            case TAKEN: return RESOLVED;
            case ASSIGNED: return TAKEN; // If an assigned mod takes it
            case RESOLVED: return REJECTED;
            case REJECTED:
            default:
                return PENDING;
        }
    }
}
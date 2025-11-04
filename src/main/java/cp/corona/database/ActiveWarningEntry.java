// src/main/java/cp/corona/database/ActiveWarningEntry.java
package cp.corona.database;

import java.util.UUID;

public class ActiveWarningEntry {
    private final int id;
    private final UUID playerUUID;
    private final String punishmentId;
    private final int warnLevel;
    private final long endTime;
    private final boolean isPaused;
    private final long remainingTimeOnPause;
    private final String associatedPunishmentIds;


    public ActiveWarningEntry(int id, UUID playerUUID, String punishmentId, int warnLevel, long endTime, boolean isPaused, long remainingTimeOnPause, String associatedPunishmentIds) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.punishmentId = punishmentId;
        this.warnLevel = warnLevel;
        this.endTime = endTime;
        this.isPaused = isPaused;
        this.remainingTimeOnPause = remainingTimeOnPause;
        this.associatedPunishmentIds = associatedPunishmentIds;
    }

    public int getId() { return id; }
    public UUID getPlayerUUID() { return playerUUID; }
    public String getPunishmentId() { return punishmentId; }
    public int getWarnLevel() { return warnLevel; }
    public long getEndTime() { return endTime; }
    public boolean isPaused() { return isPaused; }
    public long getRemainingTimeOnPause() { return remainingTimeOnPause; }
    public String getAssociatedPunishmentIds() { return associatedPunishmentIds; }

}
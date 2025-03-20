// TimeUtils.java
package cp.corona.utils;

import cp.corona.config.MainConfigManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling time formatting and parsing.
 */
public class TimeUtils {

    /**
     * Formats time in seconds into a human-readable string.
     *
     * @param totalSeconds  Total seconds to format.
     * @param configManager MainConfigManager instance to get time units from config.
     * @return Formatted time string. Returns "Permanent" if totalSeconds is 0 or less.
     */
    public static String formatTime(int totalSeconds, MainConfigManager configManager) {
        if (totalSeconds <= 0) {
            return configManager.getMessage("messages.permanent_time_display"); // Get "Permanent" text from messages.yml
        }

        int years = totalSeconds / (60 * 60 * 24 * 365);
        int days = (totalSeconds % (60 * 60 * 24 * 365)) / (60 * 60 * 24);
        int hours = (totalSeconds % (60 * 60 * 24)) / (60 * 60);
        int minutes = (totalSeconds % (60 * 60)) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder formattedTime = new StringBuilder();

        if (years > 0) formattedTime.append(years).append(configManager.getYearsTimeUnit()).append(" ");
        if (days > 0) formattedTime.append(days).append(configManager.getDayTimeUnit()).append(" ");
        if (hours > 0) formattedTime.append(hours).append(configManager.getHoursTimeUnit()).append(" ");
        if (minutes > 0) formattedTime.append(minutes).append(configManager.getMinutesTimeUnit()).append(" ");
        if (seconds > 0 || formattedTime.length() == 0)
            formattedTime.append(seconds).append(configManager.getSecondsTimeUnit()); // Always show seconds if no other unit is shown or seconds > 0


        return formattedTime.toString().trim();
    }

    /**
     * Parses a time string (e.g., "1d", "2h30m") into seconds.
     *
     * @param timeString    The time string to parse.
     * @param configManager MainConfigManager instance to get time units from config.
     * @return Total seconds, or 0 if parsing fails.
     */
    public static int parseTime(String timeString, MainConfigManager configManager) {
        int totalSeconds = 0;
        String yearsUnit = configManager.getYearsTimeUnit();
        String dayUnit = configManager.getDayTimeUnit();
        String hoursUnit = configManager.getHoursTimeUnit();
        String minutesUnit = configManager.getMinutesTimeUnit();
        String secondsUnit = configManager.getSecondsTimeUnit();

        // Regex pattern to capture time values and units
        String pattern = "(\\d+)([y" + dayUnit + "h" + minutesUnit + secondsUnit + "]\\s*)"; // e.g., (\d+)([y|d|h|m|s]\s*)
        Pattern r = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = r.matcher(timeString);

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).trim().toLowerCase();

            if (unit.startsWith("y")) {
                totalSeconds += value * 60 * 60 * 24 * 365;
            } else if (unit.startsWith("d")) {
                totalSeconds += value * 60 * 60 * 24;
            } else if (unit.startsWith("h")) {
                totalSeconds += value * 60 * 60;
            } else if (unit.startsWith("m")) {
                totalSeconds += value * 60;
            } else if (unit.startsWith("s") || unit.isEmpty()) { // handles cases like "30" (default seconds)
                totalSeconds += value;
            }
        }
        return totalSeconds;
    }
}
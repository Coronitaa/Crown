// utils/TimeUtils.java
package cp.corona.utils;

import cp.corona.config.MainConfigManager;
import cp.corona.crown.Crown;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling time formatting and parsing.
 */
public class TimeUtils {

    /**
     * Formats time in seconds into a human-readable string using configured units.
     * Returns the configured display text for permanent duration if totalSeconds is 0 or less.
     *
     * @param totalSeconds  Total seconds to format.
     * @param configManager MainConfigManager instance to get time units and placeholder text from config.
     * @return Formatted time string (e.g., "1d 2h 30m") or the permanent display text.
     */
    public static String formatTime(int totalSeconds, MainConfigManager configManager) {
        if (totalSeconds <= 0) {
            // Use the configured display text for "Permanent" from the placeholders section
            return configManager.getMessage("placeholders.permanent_time_display"); // CORRECTED PATH
        }

        int years = totalSeconds / (60 * 60 * 24 * 365);
        int days = (totalSeconds % (60 * 60 * 24 * 365)) / (60 * 60 * 24);
        int hours = (totalSeconds % (60 * 60 * 24)) / (60 * 60);
        int minutes = (totalSeconds % (60 * 60)) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder formattedTime = new StringBuilder();
        boolean unitAdded = false; // Flag to track if any unit larger than seconds was added

        // Append years if greater than 0
        if (years > 0) {
            formattedTime.append(years).append(configManager.getYearsTimeUnit()).append(" ");
            unitAdded = true;
        }
        // Append days if greater than 0
        if (days > 0) {
            formattedTime.append(days).append(configManager.getDayTimeUnit()).append(" ");
            unitAdded = true;
        }
        // Append hours if greater than 0
        if (hours > 0) {
            formattedTime.append(hours).append(configManager.getHoursTimeUnit()).append(" ");
            unitAdded = true;
        }
        // Append minutes if greater than 0
        if (minutes > 0) {
            formattedTime.append(minutes).append(configManager.getMinutesTimeUnit()).append(" ");
            unitAdded = true;
        }
        // Append seconds if > 0 OR if no other units were added (to prevent empty string for times < 1min)
        if (seconds > 0 || !unitAdded) {
            formattedTime.append(seconds).append(configManager.getSecondsTimeUnit());
        }


        return formattedTime.toString().trim(); // Trim to remove potential trailing space
    }

    /**
     * Parses a time string (e.g., "1d", "2h30m") into total seconds using configured units.
     * Does NOT parse the "permanent" display string, only unit-based strings.
     *
     * @param timeString    The time string to parse (e.g., "1d2h").
     * @param configManager MainConfigManager instance to get time units from config.
     * @return Total seconds, or 0 if parsing fails or input is empty/invalid.
     */
    public static int parseTime(String timeString, MainConfigManager configManager) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return 0;
        }

        int totalSeconds = 0;
        // Fetch time units from config
        String yearsUnit = configManager.getYearsTimeUnit();
        String dayUnit = configManager.getDayTimeUnit();
        String hoursUnit = configManager.getHoursTimeUnit();
        String minutesUnit = configManager.getMinutesTimeUnit();
        String secondsUnit = configManager.getSecondsTimeUnit();

        // Build regex pattern dynamically based on configured units
        // Ensure units are escaped if they contain regex special characters (though unlikely for single letters)
        // Using Pattern.quote() is safer if units could be multi-character or special
        String patternString = "(\\d+)\\s*(" +
                Pattern.quote(yearsUnit) + "|" +
                Pattern.quote(dayUnit) + "|" +
                Pattern.quote(hoursUnit) + "|" +
                Pattern.quote(minutesUnit) + "|" +
                Pattern.quote(secondsUnit) + ")";

        Pattern r = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        Matcher matcher = r.matcher(timeString);

        boolean foundMatch = false;
        while (matcher.find()) {
            foundMatch = true;
            try {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).trim(); // No need for toLowerCase if using CASE_INSENSITIVE

                // Compare with configured units (case-insensitive)
                if (unit.equalsIgnoreCase(yearsUnit)) {
                    totalSeconds += value * 60 * 60 * 24 * 365;
                } else if (unit.equalsIgnoreCase(dayUnit)) {
                    totalSeconds += value * 60 * 60 * 24;
                } else if (unit.equalsIgnoreCase(hoursUnit)) {
                    totalSeconds += value * 60 * 60;
                } else if (unit.equalsIgnoreCase(minutesUnit)) {
                    totalSeconds += value * 60;
                } else if (unit.equalsIgnoreCase(secondsUnit)) {
                    totalSeconds += value;
                }
            } catch (NumberFormatException e) {
                // Ignore parts with invalid numbers
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("[TimeUtils] Invalid number format in time string part: " + matcher.group(0));
                }
            }
        }

        // Handle cases where the input is just a number (treat as default unit - seconds)
        if (!foundMatch && timeString.matches("\\d+")) {
            try {
                totalSeconds = Integer.parseInt(timeString);
                // If default unit is not seconds, adjust accordingly (example for minutes)
                // if (!configManager.getDefaultTimeUnit().equalsIgnoreCase("s")) { ... }
            } catch (NumberFormatException e) {
                totalSeconds = 0; // Should not happen due to regex check, but safe fallback
            }
        } else if (!foundMatch) {
            // Input string did not contain valid number-unit pairs and wasn't just a number
            totalSeconds = 0;
        }


        return totalSeconds;
    }

    // Static reference to the plugin instance - needed for logging in static context
    private static Crown plugin = null;

    // Method to initialize the plugin reference (call this from onEnable)
    public static void initialize(Crown instance) {
        plugin = instance;
    }
}
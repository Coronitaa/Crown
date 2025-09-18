// src/main/java/cp/corona/utils/TimeUtils.java
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
     * @return Formatted time string (e.g., "1y 2M 3d") or the permanent display text.
     */
    public static String formatTime(int totalSeconds, MainConfigManager configManager) {
        if (totalSeconds <= 0) {
            return configManager.getMessage("placeholders.permanent_time_display");
        }

        int years = totalSeconds / (60 * 60 * 24 * 365);
        totalSeconds %= (60 * 60 * 24 * 365);
        int months = totalSeconds / (60 * 60 * 24 * 30);
        totalSeconds %= (60 * 60 * 24 * 30);
        int days = totalSeconds / (60 * 60 * 24);
        totalSeconds %= (60 * 60 * 24);
        int hours = totalSeconds / (60 * 60);
        totalSeconds %= (60 * 60);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        StringBuilder formattedTime = new StringBuilder();
        boolean unitAdded = false;

        if (years > 0) {
            formattedTime.append(years).append(configManager.getYearsTimeUnit()).append(" ");
            unitAdded = true;
        }
        if (months > 0) {
            formattedTime.append(months).append(configManager.getMonthsTimeUnit()).append(" ");
            unitAdded = true;
        }
        if (days > 0) {
            formattedTime.append(days).append(configManager.getDayTimeUnit()).append(" ");
            unitAdded = true;
        }
        if (hours > 0) {
            formattedTime.append(hours).append(configManager.getHoursTimeUnit()).append(" ");
            unitAdded = true;
        }
        if (minutes > 0) {
            formattedTime.append(minutes).append(configManager.getMinutesTimeUnit()).append(" ");
            unitAdded = true;
        }
        if (seconds > 0 || !unitAdded) {
            formattedTime.append(seconds).append(configManager.getSecondsTimeUnit());
        }


        return formattedTime.toString().trim();
    }

    /**
     * Parses a time string (e.g., "1y", "2M") into total seconds using configured units.
     *
     * @param timeString    The time string to parse (e.g., "1y2M").
     * @param configManager MainConfigManager instance to get time units from config.
     * @return Total seconds, or 0 if parsing fails or input is empty/invalid.
     */
    public static int parseTime(String timeString, MainConfigManager configManager) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return 0;
        }

        int totalSeconds = 0;
        String yearsUnit = configManager.getYearsTimeUnit();
        String monthsUnit = configManager.getMonthsTimeUnit();
        String dayUnit = configManager.getDayTimeUnit();
        String hoursUnit = configManager.getHoursTimeUnit();
        String minutesUnit = configManager.getMinutesTimeUnit();
        String secondsUnit = configManager.getSecondsTimeUnit();

        String patternString = "(\\d+)\\s*(" +
                Pattern.quote(yearsUnit) + "|" +
                Pattern.quote(monthsUnit) + "|" +
                Pattern.quote(dayUnit) + "|" +
                Pattern.quote(hoursUnit) + "|" +
                Pattern.quote(minutesUnit) + "|" +
                Pattern.quote(secondsUnit) + ")";

        Pattern r = Pattern.compile(patternString);
        Matcher matcher = r.matcher(timeString);

        boolean foundMatch = false;
        while (matcher.find()) {
            foundMatch = true;
            try {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).trim();

                if (unit.equals(yearsUnit)) {
                    totalSeconds += value * 60 * 60 * 24 * 365;
                } else if (unit.equals(monthsUnit)) {
                    totalSeconds += value * 60 * 60 * 24 * 30;
                } else if (unit.equals(dayUnit)) {
                    totalSeconds += value * 60 * 60 * 24;
                } else if (unit.equals(hoursUnit)) {
                    totalSeconds += value * 60 * 60;
                } else if (unit.equals(minutesUnit)) {
                    totalSeconds += value * 60;
                } else if (unit.equals(secondsUnit)) {
                    totalSeconds += value;
                }
            } catch (NumberFormatException e) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("[TimeUtils] Invalid number format in time string part: " + matcher.group(0));
                }
            }
        }
        if (!foundMatch && timeString.matches("\\d+")) {
            try {
                totalSeconds = Integer.parseInt(timeString);
            } catch (NumberFormatException e) {
                totalSeconds = 0;
            }
        } else if (!foundMatch) {
            totalSeconds = 0;
        }


        return totalSeconds;
    }

    public static boolean isValidTimeFormat(String timeString, MainConfigManager configManager) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return false;
        }
        if (timeString.equalsIgnoreCase("permanent")) {
            return true;
        }

        String yearsUnit = configManager.getYearsTimeUnit();
        String monthsUnit = configManager.getMonthsTimeUnit();
        String dayUnit = configManager.getDayTimeUnit();
        String hoursUnit = configManager.getHoursTimeUnit();
        String minutesUnit = configManager.getMinutesTimeUnit();
        String secondsUnit = configManager.getSecondsTimeUnit();

        String patternString = "(\\d+)\\s*(" +
                Pattern.quote(yearsUnit) + "|" +
                Pattern.quote(monthsUnit) + "|" +
                Pattern.quote(dayUnit) + "|" +
                Pattern.quote(hoursUnit) + "|" +
                Pattern.quote(minutesUnit) + "|" +
                Pattern.quote(secondsUnit) + ")";

        Pattern r = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        Matcher matcher = r.matcher(timeString);

        return matcher.find();
    }


    private static Crown plugin = null;

    public static void initialize(Crown instance) {
        plugin = instance;
    }
}
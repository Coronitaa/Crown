package cp.corona.config;

import cp.corona.crownpunishments.CrownPunishments;
import cp.corona.utils.TimeUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ////////////////////////////////////////////////
 * //             CrownPunishments             //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * PlaceholderAPI Expansion class for CrownPunishments.
 * Registers custom placeholders to be used with PlaceholderAPI.
 */
public class CrownPunishmentsPlaceholders extends PlaceholderExpansion {

    private final CrownPunishments plugin;

    /**
     * Constructor for CrownPunishmentsPlaceholders.
     * @param plugin Instance of the main plugin.
     */
    public CrownPunishmentsPlaceholders(CrownPunishments plugin) {
        this.plugin = plugin;
    }

    /**
     * Persist this expansion through PlaceholderAPI reloads.
     * @return true to persist through reloads.
     */
    @Override
    public boolean persist(){
        return true;
    }


    /**
     * Gets the placeholder identifier.
     * This is what is used in placeholders like %crownpunishments_XXXX%
     * @return Placeholder identifier "crownpunishments".
     */
    @Override
    public @NotNull String getIdentifier() {
        return "crownpunishments";
    }

    /**
     * Gets the author of this expansion.
     * @return Author name.
     */
    @Override
    public @NotNull String getAuthor(){
        return "Corona";
    }

    /**
     * Gets the version of this expansion.
     * @return Plugin version.
     */
    @Override
    public @NotNull String getVersion(){
        return plugin.getDescription().getVersion();
    }

    /**
     * This is the method called when a placeholder with our identifier is found.
     * For example: %crownpunishments_placeholdername%
     *
     * @param player    Player (can be null if placeholder is used in console or non-player context).
     * @param params    The part after 'crownpunishments_' in the placeholder (e.g., 'placeholdername').
     * @return          The String value to replace the placeholder with, or null if placeholder is unknown.
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer player, String params) {

        if(player == null){
            return null; // Placeholder is used in console or without player context, return null
        }

        if(params.equalsIgnoreCase("is_softbanned")){
            return String.valueOf(plugin.getSoftBanDatabaseManager().isSoftBanned(player.getUniqueId()));
        }

        if(params.equalsIgnoreCase("softban_time_left")){
            long endTime = plugin.getSoftBanDatabaseManager().getSoftBanEndTime(player.getUniqueId());
            if (endTime == 0) {
                return "N/A"; // Or any default value if not soft-banned
            }
            if (endTime == Long.MAX_VALUE) {
                return plugin.getConfigManager().getMessage("messages.permanent_time_display");
            }
            int remainingSeconds = (int) ((endTime - System.currentTimeMillis()) / 1000);
            return TimeUtils.formatTime(remainingSeconds, plugin.getConfigManager());
        }

        // If placeholder is not recognized, return null
        return null;
    }

    // Removed @Override and super calls for unregister() and register() - CORRECTED!
    @Override
    public boolean register(){
        return super.register();
    }

}
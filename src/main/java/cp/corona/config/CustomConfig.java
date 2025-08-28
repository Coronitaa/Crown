// CustomConfig.java
package cp.corona.config;

import cp.corona.crown.Crown;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * ////////////////////////////////////////////////
 * //                   Crown                    //
 * //         Developed with passion by         //
 * //                   Corona                 //
 * ////////////////////////////////////////////////
 *
 * Manages custom configuration files for the plugin.
 * Handles file creation, loading, and reloading of YAML configurations.
 */
public class CustomConfig {
    private final Crown plugin;
    private final String fileName;
    private FileConfiguration fileConfiguration = null;
    private File file = null;
    private final String folderName;
    private final boolean newFile;

    /**
     * Constructor for CustomConfig.
     *
     * @param fileName   The name of the file.
     * @param folderName The folder name where the file is located (null if in plugin's data folder root).
     * @param plugin     Instance of the main plugin class.
     * @param newFile    Whether to create a new file if it doesn't exist.
     */
    public CustomConfig(String fileName, String folderName, Crown plugin, boolean newFile) {
        this.fileName = fileName;
        this.folderName = folderName;
        this.plugin = plugin;
        this.newFile = newFile;
    }

    /**
     * Registers the configuration file, creating it if it doesn't exist and loading it.
     * Handles creation in specified folder or plugin's data folder root.
     */
    public void registerConfig() {
        // Determine file location based on folderName
        if (folderName != null) {
            File folder = new File(plugin.getDataFolder(), folderName);
            if (!folder.exists()) {
                folder.mkdirs(); // Create folder if it doesn't exist
            }
            file = new File(folder, fileName);
        } else {
            file = new File(plugin.getDataFolder(), fileName);
        }

        // Create new file if it doesn't exist and newFile is true
        if (!file.exists()) {
            if (newFile) {
                try {
                    boolean created = file.createNewFile();
                    if (!created) {
                        plugin.getLogger().log(Level.WARNING, "Failed to create new file: " + fileName);
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error creating new file: " + fileName, e);
                }
            } else {
                // Save resource from plugin's jar if newFile is false
                if (folderName != null) {
                    plugin.saveResource(folderName + File.separator + fileName, false);
                } else {
                    plugin.saveResource(fileName, false);
                }
            }
        }

        // Load configuration from file
        fileConfiguration = new YamlConfiguration();
        try {
            fileConfiguration.load(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading config file: " + fileName, e);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Invalid configuration in file: " + fileName, e);
        }
    }

    /**
     * Reloads the configuration from file.
     *
     * @return true if reload was successful.
     */
    public boolean reloadConfig() {
        // Ensure file is not null, re-resolve if necessary
        if (fileConfiguration == null) {
            if (folderName != null) {
                File folder = new File(plugin.getDataFolder(), folderName);
                file = new File(folder, fileName);
            } else {
                file = new File(plugin.getDataFolder(), fileName);
            }
        }
        fileConfiguration = YamlConfiguration.loadConfiguration(file); // Load configuration

        // Set defaults from file, if file exists
        if (file != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(file);
            fileConfiguration.setDefaults(defConfig);
        }
        return true;
    }

    /**
     * Gets the FileConfiguration associated with this CustomConfig.
     *
     * @return The FileConfiguration.
     */
    public FileConfiguration getConfig() {
        if (fileConfiguration == null) {
            reloadConfig(); // Reload config if it's null
        }
        return fileConfiguration;
    }
}
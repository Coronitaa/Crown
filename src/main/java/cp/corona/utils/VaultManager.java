package cp.corona.utils;

import cp.corona.crown.Crown;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultManager {

    private final Crown plugin;
    private Economy economy = null;

    public VaultManager(Crown plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        economy = rsp.getProvider();
    }

    public double getBalance(Player player) {
        if (economy == null) {
            return 0.0;
        }
        return economy.getBalance(player);
    }
}
package cp.corona.menus;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

public abstract class CrownMenu implements InventoryHolder {
    public abstract void handleClick(InventoryClickEvent event);

    public void handleOpen(InventoryOpenEvent event) {}

    public void handleClose(InventoryCloseEvent event) {}
}

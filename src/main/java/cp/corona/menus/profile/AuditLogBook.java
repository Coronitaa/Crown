// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\menus\AuditLogBook.java
package cp.corona.menus.profile;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class AuditLogBook {

    private final Crown plugin;
    private final UUID targetUUID;
    private final Player viewer;

    public AuditLogBook(Crown plugin, UUID targetUUID, Player viewer) {
        this.plugin = plugin;
        this.targetUUID = targetUUID;
        this.viewer = viewer;
    }

    public void openBook() {
        // Fetch data asynchronously first
        plugin.getSoftBanDatabaseManager().getOperatorActions(targetUUID).thenAccept(logEntries -> 
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta meta = (BookMeta) book.getItemMeta();
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);

                String title = plugin.getConfigManager().getAuditLogText("book.title", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
                meta.setTitle(MessageUtils.getColorMessage(title));
                meta.setAuthor(MessageUtils.getColorMessage(plugin.getConfigManager().getAuditLogText("book.author")));

                buildAndOpen(logEntries, book, meta);
            })
        );
    }

    private void buildAndOpen(List<DatabaseManager.AuditLogEntry> logEntries, ItemStack book, BookMeta bookMeta) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        if (logEntries.isEmpty()) {
            String noLogsMessage = plugin.getConfigManager().getAuditLogText("book.no-logs-message");
            bookMeta.addPages(MessageUtils.getColorComponent(noLogsMessage));
        } else {
            List<Component> pages = buildPages(logEntries);
            for (Component page : pages) {
                bookMeta.addPages(page);
            }
        }

        book.setItemMeta(bookMeta);
        viewer.openBook(book);
    }

    private List<Component> buildPages(List<DatabaseManager.AuditLogEntry> logEntries) {
        List<Component> pages = new ArrayList<>();
        // Removed page count logic to fulfill the new requirement
        final int MAX_LINES_PER_PAGE = 7; // We can fit more lines now

        for (int i = 0; i < logEntries.size(); i += MAX_LINES_PER_PAGE) {
            Component currentPage = Component.empty();
            List<DatabaseManager.AuditLogEntry> pageEntries = logEntries.subList(i, Math.min(i + MAX_LINES_PER_PAGE, logEntries.size()));

            for(DatabaseManager.AuditLogEntry entry : pageEntries) {
                Component line = formatLogEntry(entry);
                if (line != null) {
                    currentPage = currentPage.append(line).append(Component.newline()).append(Component.newline());
                }
            }
            pages.add(currentPage);
        }

        return pages;
    }

    private Component formatLogEntry(DatabaseManager.AuditLogEntry entry) {
        String actionTypeKey = entry.getActionType().toLowerCase();
        String formatKey = "book.line-formats." + actionTypeKey.replace("_", "-");
        String format = plugin.getConfigManager().getAuditLogText(formatKey);
        if (format.isEmpty()) return null;

        OfflinePlayer executor = Bukkit.getOfflinePlayer(entry.getExecutorUUID());
        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getTargetUUID());
        HoverEvent<Component> actionHoverEvent = createActionHoverEvent(entry, executor, target);

        format = format.replace("{executor}", executor.getName() != null ? executor.getName() : "Unknown");

        Component builder = Component.empty();

        if (actionTypeKey.startsWith("item_")) {
            String[] parts = format.split("(\\{item_name})");
            // Part before item
            Component preText = MessageUtils.getColorComponent(parts[0]).hoverEvent(actionHoverEvent);
            builder = builder.append(preText);

            // Item part
            String[] details = entry.getDetails().split(":", 2);
            int amount = Integer.parseInt(details[0]);
            ItemStack item = deserialize(details[1]);
            if (item != null) {
                builder = builder.append(createHoverableItemComponent(item));
            }

            // Part after item
            if (parts.length > 1) {
                Component postText = MessageUtils.getColorComponent(parts[1].replace("{item_count}", String.valueOf(amount))).hoverEvent(actionHoverEvent);
                builder = builder.append(postText);
            }
        } else if (actionTypeKey.contains("clear")) {
            // For clear actions, the entire line is hoverable
            String lineText = format.replace("{cleared_count}", entry.getDetails());
            Component lineComponent = MessageUtils.getColorComponent(lineText).hoverEvent(actionHoverEvent);
            builder = builder.append(lineComponent);
        }

        return builder;
    }

    private HoverEvent<Component> createActionHoverEvent(DatabaseManager.AuditLogEntry entry, OfflinePlayer executor, OfflinePlayer target) {
        Timestamp ts = entry.getTimestamp();
        String date = new SimpleDateFormat("yyyy-MM-dd").format(ts);
        String time = new SimpleDateFormat("HH:mm:ss z").format(ts);
        String inventoryType = getInventoryType(entry.getActionType());

        List<String> hoverLines = plugin.getConfigManager().getAuditLogConfig().getConfig().getStringList("book.hover-format");
        Component hoverText = Component.empty();
        for (int i = 0; i < hoverLines.size(); i++) {
            String line = hoverLines.get(i);
            line = line.replace("{executor}", executor.getName() != null ? executor.getName() : "Unknown")
                    .replace("{target}", target.getName() != null ? target.getName() : "Unknown")
                    .replace("{date}", date)
                    .replace("{time}", time)
                    .replace("{inventory_type}", inventoryType);
            hoverText = hoverText.append(MessageUtils.getColorComponent(line));
            if (i < hoverLines.size() - 1) {
                hoverText = hoverText.append(Component.newline());
            }
        }

        return HoverEvent.showText(hoverText);
    }

    private String getInventoryType(String actionType) {
        actionType = actionType.toLowerCase();
        if (actionType.contains("inventory")) return "Player Inventory";
        if (actionType.contains("ender_chest") || actionType.contains("enderchest")) return "Ender Chest";
        if (actionType.contains("profile")) return "Player Equipment";
        return "Unknown";
    }

    private Component createHoverableItemComponent(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Component itemNameComp;
        if (meta != null && meta.hasDisplayName()) {
            itemNameComp = meta.displayName();
        } else {
            String name = item.getType().name().replace("_", " ").toLowerCase();
            itemNameComp = Component.text(name);
        }

        // Create the item component for the book page: " [ItemName] "
        // We make brackets Aqua. Name keeps its style or inherits Aqua.
        Component itemComponent = Component.text(" [", NamedTextColor.AQUA)
                .append(itemNameComp)
                .append(Component.text("] ", NamedTextColor.AQUA));

        // Create the hover text
        Component hoverBuilder = itemNameComp;
        // Ensure it has a color if it doesn't have one (default to Aqua to match title style if plain)
        if (hoverBuilder.color() == null) {
            hoverBuilder = hoverBuilder.color(NamedTextColor.AQUA);
        }

        if (meta != null) {
            if (meta.hasEnchants()) {
                for (Map.Entry<Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
                    hoverBuilder = hoverBuilder.append(Component.newline());
                    String enchantName = enchant.getKey().getKey().getKey().replace("_", " ");
                    enchantName = enchantName.substring(0, 1).toUpperCase() + enchantName.substring(1);
                    hoverBuilder = hoverBuilder.append(Component.text(enchantName + " " + enchant.getValue(), NamedTextColor.GRAY));
                }
            }
            if (meta.hasLore()) {
                List<Component> lore = meta.lore();
                if (lore != null) {
                    hoverBuilder = hoverBuilder.append(Component.newline());
                    for (Component loreLine : lore) {
                        hoverBuilder = hoverBuilder.append(Component.newline()).append(loreLine);
                    }
                }
            }
        }

        return itemComponent.hoverEvent(HoverEvent.showText(hoverBuilder));
    }

    @SuppressWarnings("deprecation")
    public static String serialize(ItemStack item) {
        if (item == null) return "null";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize item stack.", e);
        }
    }

    @SuppressWarnings("deprecation")
    public static ItemStack deserialize(String data) {
        if (data == null || data.equals("null")) return null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (IOException | ClassNotFoundException e) {
            Bukkit.getServer().getLogger().log(Level.SEVERE, "Unable to deserialize item stack.", e);
            return null;
        }
    }
}

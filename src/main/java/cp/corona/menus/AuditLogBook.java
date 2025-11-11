// PATH: C:\Users\Valen\Desktop\Se vienen Cositas\PluginCROWN\CROWN\src\main\java\cp\corona\menus\AuditLogBook.java
package cp.corona.menus;

import cp.corona.crown.Crown;
import cp.corona.database.DatabaseManager;
import cp.corona.utils.MessageUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
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
import java.util.stream.Collectors;

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
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta loadingMeta = (BookMeta) book.getItemMeta();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);

        String title = plugin.getConfigManager().getAuditLogText("book.title", "{target}", target.getName() != null ? target.getName() : targetUUID.toString());
        loadingMeta.setTitle(MessageUtils.getColorMessage(title));
        loadingMeta.setAuthor(MessageUtils.getColorMessage(plugin.getConfigManager().getAuditLogText("book.author")));
        loadingMeta.addPage("Loading audit log...");
        book.setItemMeta(loadingMeta);

        viewer.openBook(book);

        plugin.getSoftBanDatabaseManager().getOperatorActions(targetUUID).thenAccept(logEntries -> {
            Bukkit.getScheduler().runTask(plugin, () -> buildAndOpen(logEntries, book));
        });
    }

    private void buildAndOpen(List<DatabaseManager.AuditLogEntry> logEntries, ItemStack book) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        BookMeta bookMeta = (BookMeta) book.getItemMeta();

        if (logEntries.isEmpty()) {
            String noLogsMessage = plugin.getConfigManager().getAuditLogText("book.no-logs-message");
            bookMeta.setPages(MessageUtils.getColorMessage(noLogsMessage));
        } else {
            List<BaseComponent[]> pages = buildPages(logEntries);
            bookMeta.spigot().setPages(pages);
        }

        book.setItemMeta(bookMeta);
        viewer.openBook(book);
    }

    private List<BaseComponent[]> buildPages(List<DatabaseManager.AuditLogEntry> logEntries) {
        List<BaseComponent[]> pages = new ArrayList<>();
        // Removed page count logic to fulfill the new requirement
        final int MAX_LINES_PER_PAGE = 7; // We can fit more lines now

        for (int i = 0; i < logEntries.size(); i += MAX_LINES_PER_PAGE) {
            ComponentBuilder currentPageBuilder = new ComponentBuilder();
            List<DatabaseManager.AuditLogEntry> pageEntries = logEntries.subList(i, Math.min(i + MAX_LINES_PER_PAGE, logEntries.size()));

            for(DatabaseManager.AuditLogEntry entry : pageEntries) {
                BaseComponent[] line = formatLogEntry(entry);
                if (line != null) {
                    currentPageBuilder.append(line);
                    currentPageBuilder.append("\n\n");
                }
            }
            pages.add(currentPageBuilder.create());
        }

        return pages;
    }

    private BaseComponent[] formatLogEntry(DatabaseManager.AuditLogEntry entry) {
        String actionTypeKey = entry.getActionType().toLowerCase();
        String formatKey = "book.line-formats." + actionTypeKey.replace("_", "-");
        String format = plugin.getConfigManager().getAuditLogText(formatKey);
        if (format.isEmpty()) return null;

        OfflinePlayer executor = Bukkit.getOfflinePlayer(entry.getExecutorUUID());
        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getTargetUUID());
        HoverEvent actionHoverEvent = createActionHoverEvent(entry, executor, target);

        format = format.replace("{executor}", executor.getName() != null ? executor.getName() : "Unknown");

        ComponentBuilder builder = new ComponentBuilder();

        if (actionTypeKey.startsWith("item_")) {
            String[] parts = format.split("(\\{item_name\\})");
            // Part before item
            TextComponent preText = new TextComponent(MessageUtils.getColorMessage(parts[0]));
            preText.setHoverEvent(actionHoverEvent);
            builder.append(preText);

            // Item part
            String[] details = entry.getDetails().split(":", 2);
            int amount = Integer.parseInt(details[0]);
            ItemStack item = deserialize(details[1]);
            if (item != null) {
                builder.append(createHoverableItemComponent(item));
            }

            // Part after item
            if (parts.length > 1) {
                TextComponent postText = new TextComponent(MessageUtils.getColorMessage(parts[1].replace("{item_count}", String.valueOf(amount))));
                postText.setHoverEvent(actionHoverEvent);
                builder.append(postText);
            }
        } else if (actionTypeKey.contains("clear")) {
            // For clear actions, the entire line is hoverable
            String lineText = format.replace("{cleared_count}", entry.getDetails());
            TextComponent lineComponent = new TextComponent(MessageUtils.getColorMessage(lineText));
            lineComponent.setHoverEvent(actionHoverEvent);
            builder.append(lineComponent);
        }

        return builder.create();
    }

    private HoverEvent createActionHoverEvent(DatabaseManager.AuditLogEntry entry, OfflinePlayer executor, OfflinePlayer target) {
        Timestamp ts = entry.getTimestamp();
        String date = new SimpleDateFormat("yyyy-MM-dd").format(ts);
        String time = new SimpleDateFormat("HH:mm:ss z").format(ts);
        String inventoryType = getInventoryType(entry.getActionType());

        List<String> hoverLines = plugin.getConfigManager().getAuditLogConfig().getConfig().getStringList("book.hover-format");
        String hoverText = hoverLines.stream()
                .map(line -> line.replace("{executor}", executor.getName() != null ? executor.getName() : "Unknown"))
                .map(line -> line.replace("{target}", target.getName() != null ? target.getName() : "Unknown"))
                .map(line -> line.replace("{date}", date))
                .map(line -> line.replace("{time}", time))
                .map(line -> line.replace("{inventory_type}", inventoryType))
                .map(MessageUtils::getColorMessage)
                .collect(Collectors.joining("\n"));

        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText));
    }

    private String getInventoryType(String actionType) {
        actionType = actionType.toLowerCase();
        if (actionType.contains("inventory")) return "Player Inventory";
        if (actionType.contains("enderchest")) return "Ender Chest";
        if (actionType.contains("profile")) return "Player Equipment";
        return "Unknown";
    }

    private TextComponent createHoverableItemComponent(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String itemName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : item.getType().name().replace("_", " ").toLowerCase();

        TextComponent itemComponent = new TextComponent(MessageUtils.getColorMessage(" &b[" + itemName + "]&r "));

        ComponentBuilder hoverBuilder = new ComponentBuilder();
        hoverBuilder.append(new TextComponent(itemName)).color(net.md_5.bungee.api.ChatColor.AQUA);

        if (meta != null) {
            if (meta.hasEnchants()) {
                for (Map.Entry<Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
                    hoverBuilder.append("\n").reset();
                    String enchantName = enchant.getKey().getKey().getKey().replace("_", " ");
                    enchantName = enchantName.substring(0, 1).toUpperCase() + enchantName.substring(1);
                    hoverBuilder.append(new TextComponent(enchantName + " " + enchant.getValue())).color(net.md_5.bungee.api.ChatColor.GRAY);
                }
            }
            if (meta.hasLore()) {
                hoverBuilder.append("\n"); // Add a space before lore
                for (String loreLine : meta.getLore()) {
                    hoverBuilder.append("\n").reset().append(new TextComponent(MessageUtils.getColorMessage(loreLine)));
                }
            }
        }

        itemComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverBuilder.create())));
        return itemComponent;
    }

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

    public static ItemStack deserialize(String data) {
        if (data == null || data.equals("null")) return null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (IOException | ClassNotFoundException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Unable to deserialize item stack.", e);
            return null;
        }
    }
}
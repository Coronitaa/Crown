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

        String title = plugin.getConfigManager().getAuditLogText("book.title").replace("{target}", target.getName() != null ? target.getName() : targetUUID.toString());
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
            return; // Don't proceed if the player closed the menu or went offline
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

        // This is the crucial step: Re-open the book for the player to force a client-side update.
        viewer.openBook(book);
    }

    private List<BaseComponent[]> buildPages(List<DatabaseManager.AuditLogEntry> logEntries) {
        List<BaseComponent[]> pages = new ArrayList<>();
        ComponentBuilder currentPageBuilder = new ComponentBuilder();
        int linesOnPage = 0;
        final int MAX_LINES_PER_PAGE = 5;

        for (DatabaseManager.AuditLogEntry entry : logEntries) {
            if (linesOnPage >= MAX_LINES_PER_PAGE) {
                pages.add(currentPageBuilder.create());
                currentPageBuilder = new ComponentBuilder();
                linesOnPage = 0;
            }

            BaseComponent[] line = formatLogEntry(entry);
            if (line != null) {
                currentPageBuilder.append(line);
                currentPageBuilder.append("\n\n");
                linesOnPage++;
            }
        }

        if (linesOnPage > 0) {
            pages.add(currentPageBuilder.create());
        }

        // Add page numbers
        for (int i = 0; i < pages.size(); i++) {
            String pageHeader = plugin.getConfigManager().getAuditLogText("book.page-format")
                    .replace("{page}", String.valueOf(i + 1))
                    .replace("{totalPages}", String.valueOf(pages.size()));

            BaseComponent[] pageContent = pages.get(i);
            pages.set(i, new ComponentBuilder(MessageUtils.getColorMessage(pageHeader)).append(pageContent).create());
        }

        return pages;
    }

    private BaseComponent[] formatLogEntry(DatabaseManager.AuditLogEntry entry) {
        String formatKey = "book.line-formats." + entry.getActionType().toLowerCase().replace("_", "-");
        String format = plugin.getConfigManager().getAuditLogText(formatKey);
        if (format.isEmpty()) return null;

        String executorName = Bukkit.getOfflinePlayer(entry.getExecutorUUID()).getName();
        Timestamp ts = entry.getTimestamp();
        String date = new SimpleDateFormat("yyyy-MM-dd").format(ts);
        String time = new SimpleDateFormat("HH:mm:ss").format(ts);

        format = format.replace("{header}", plugin.getConfigManager().getAuditLogText("book.line-formats.header"));
        format = format.replace("{executor}", executorName != null ? executorName : "Unknown");
        format = format.replace("{date}", date);
        format = format.replace("{time}", time);

        ComponentBuilder builder = new ComponentBuilder();
        String[] parts = format.split("(\\{item_name\\})|(\\{cleared_count\\})");

        builder.append(MessageUtils.getColorMessage(parts[0]));

        if (entry.getActionType().startsWith("ITEM_")) {
            String[] details = entry.getDetails().split(":", 2);
            int amount = Integer.parseInt(details[0]);
            ItemStack item = deserialize(details[1]);

            if (item != null) {
                builder.append(createHoverableItemComponent(item));
            }
            if (parts.length > 1) {
                builder.append(MessageUtils.getColorMessage(parts[1].replace("{item_count}", String.valueOf(amount))));
            }
        } else if (entry.getActionType().contains("CLEAR")) {
            builder.append(MessageUtils.getColorMessage(entry.getDetails()));
            if (parts.length > 1) {
                builder.append(MessageUtils.getColorMessage(parts[1]));
            }
        }

        return builder.create();
    }

    private TextComponent createHoverableItemComponent(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String itemName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : item.getType().name().replace("_", " ").toLowerCase();

        TextComponent itemComponent = new TextComponent(MessageUtils.getColorMessage("&b[" + itemName + "]"));

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
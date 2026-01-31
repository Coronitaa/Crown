package cp.corona.utils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventorySerializer {

    public static String toBase64(Inventory inventory) {
        return itemStackArrayToBase64(inventory.getContents());
    }

    public static String itemStackArrayToBase64(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize item stacks.", e);
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        byte[] bytes = Base64.getDecoder().decode(data);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to deserialize item stacks.", e);
        }
    }

    public static String potionEffectsToBase64(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) return "";
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (PotionEffect effect : effects) {
            serialized.add(effect.serialize());
        }
        return objectToBase64(serialized);
    }

    public static Collection<PotionEffect> potionEffectsFromBase64(String data) {
        if (data == null || data.isEmpty()) return Collections.emptyList();
        Object obj = objectFromBase64(data);
        if (!(obj instanceof List<?> list)) return Collections.emptyList();
        List<PotionEffect> effects = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<?, ?> mapEntry : rawMap.entrySet()) {
                if (mapEntry.getKey() instanceof String key) {
                    map.put(key, mapEntry.getValue());
                }
            }
            try {
                effects.add(new PotionEffect(map));
            } catch (Exception ignored) {
            }
        }
        return effects;
    }

    private static String objectToBase64(Object obj) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(obj);
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize object.", e);
        }
    }

    private static Object objectFromBase64(String data) {
        byte[] bytes = Base64.getDecoder().decode(data);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to deserialize object.", e);
        }
    }
}

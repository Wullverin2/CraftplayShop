package de.craftplay.shop.core.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ItemSerializer {
    public String serialize(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(output)) {
            dataOutput.writeObject(itemStack);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            return "";
        }
    }

    public ItemStack deserialize(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(input)) {
            Object object = dataInput.readObject();
            if (object instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (IOException | ClassNotFoundException exception) {
            return null;
        }
        return null;
    }
}

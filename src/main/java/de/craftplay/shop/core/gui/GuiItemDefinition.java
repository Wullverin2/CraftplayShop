package de.craftplay.shop.core.gui;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record GuiItemDefinition(String id, int slot, ConfigurationSection section, List<String> leftClickActions,
                                List<String> rightClickActions, boolean closeInventory, String permission) {
}

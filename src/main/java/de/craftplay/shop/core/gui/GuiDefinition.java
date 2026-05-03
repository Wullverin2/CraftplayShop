package de.craftplay.shop.core.gui;

import java.util.Map;

public record GuiDefinition(String id, String language, String title, int size, Map<Integer, GuiItemDefinition> items) {
}

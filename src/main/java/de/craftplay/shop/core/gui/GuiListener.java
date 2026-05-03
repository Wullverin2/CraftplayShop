package de.craftplay.shop.core.gui;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.servershop.ServerShopCategoryHolder;
import de.craftplay.shop.servershop.ServerShopHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {
    private final CraftplayShopPlugin plugin;

    public GuiListener(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GuiHolder guiHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
                return;
            }
            GuiItemDefinition item = guiHolder.items().get(event.getRawSlot());
            if (item != null) {
                plugin.getGuiActionExecutor().execute(player, item, event.isRightClick());
            }
            return;
        }
        if (holder instanceof ServerShopHolder serverShopHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                plugin.getServerShopGui().handleClick(player, serverShopHolder, event);
            }
            return;
        }
        if (holder instanceof ServerShopCategoryHolder categoryHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                plugin.getServerShopCategoryGui().handleClick(player, categoryHolder, event);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GuiHolder || holder instanceof ServerShopHolder || holder instanceof ServerShopCategoryHolder) {
            event.setCancelled(true);
        }
    }
}

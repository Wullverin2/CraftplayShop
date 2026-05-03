package de.craftplay.shop.core.gui;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.servershop.ServerShopCategoryHolder;
import de.craftplay.shop.servershop.ServerShopHolder;
import de.craftplay.shop.servershop.admin.ServerShopAdminHolder;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
            return;
        }
        if (holder instanceof ServerShopAdminHolder adminHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                plugin.getServerShopAdminEditor().handleClick(player, adminHolder, event);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GuiHolder || holder instanceof ServerShopHolder || holder instanceof ServerShopCategoryHolder || holder instanceof ServerShopAdminHolder) {
            event.setCancelled(true);
            if (holder instanceof ServerShopAdminHolder adminHolder && event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                plugin.getServerShopAdminEditor().handleDrag(player, adminHolder, event);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getServerShopAdminEditor().hasTextInput(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getServerShopAdminEditor().handleTextInput(event.getPlayer(), event.getMessage()));
    }
}

package de.craftplay.shop.core.gui;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.servershop.ServerShopAmountHolder;
import de.craftplay.shop.servershop.ServerShopCategoryHolder;
import de.craftplay.shop.servershop.ServerShopHolder;
import de.craftplay.shop.servershop.SellGuiHolder;
import de.craftplay.shop.servershop.admin.ServerShopAdminHolder;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
        if (holder instanceof ServerShopAmountHolder amountHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                plugin.getServerShopCategoryGui().handleAmountClick(player, amountHolder, event);
            }
            return;
        }
        if (holder instanceof ServerShopAdminHolder adminHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                plugin.getServerShopAdminEditor().handleClick(player, adminHolder, event);
            }
            return;
        }
        if (holder instanceof SellGuiHolder sellGuiHolder && event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
            plugin.getSellCommandService().handleSellGuiClick(player, sellGuiHolder, event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GuiHolder || holder instanceof ServerShopHolder || holder instanceof ServerShopCategoryHolder || holder instanceof ServerShopAmountHolder || holder instanceof ServerShopAdminHolder || holder instanceof SellGuiHolder) {
            event.setCancelled(true);
            if (holder instanceof ServerShopAdminHolder adminHolder && event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                plugin.getServerShopAdminEditor().handleDrag(player, adminHolder, event);
            }
            if (holder instanceof SellGuiHolder sellGuiHolder) {
                event.setCancelled(false);
                plugin.getSellCommandService().handleSellGuiDrag(sellGuiHolder, event);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof SellGuiHolder sellGuiHolder && event.getPlayer() instanceof org.bukkit.entity.Player player) {
            plugin.getSellCommandService().handleSellGuiClose(player, sellGuiHolder);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.getServerShopCategoryGui().hasAmountInput(event.getPlayer())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getServerShopCategoryGui().handleAmountInput(event.getPlayer(), event.getMessage()));
            return;
        }
        if (!plugin.getServerShopAdminEditor().hasTextInput(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getServerShopAdminEditor().handleTextInput(event.getPlayer(), event.getMessage()));
    }
}

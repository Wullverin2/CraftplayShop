package de.craftplay.shop.permissionshop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionProductService {
    private final CraftplayShopPlugin plugin;
    private final Map<String, PermissionProduct> products = new ConcurrentHashMap<>();

    public PermissionProductService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        products.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("permissionShop.products");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection product = section.getConfigurationSection(id);
            if (product == null) {
                continue;
            }
            Material material = Material.matchMaterial(product.getString("material", "PAPER"));
            products.put(id, new PermissionProduct(
                    id,
                    product.getBoolean("enabled", true),
                    product.getString("displayName", id),
                    product.getStringList("lore"),
                    material == null ? Material.PAPER : material,
                    product.getInt("slot", -1),
                    product.getDouble("price", 0.0D),
                    product.getStringList("permissions"),
                    product.getStringList("commands"),
                    product.getStringList("playerCommands"),
                    product.getStringList("requiredPermissions"),
                    product.getStringList("ownedPermissions"),
                    product.getBoolean("oneTime", true),
                    product.getString("duration", "")
            ));
        }
    }

    public void open(Player player) {
        if (!enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return;
        }
        YamlConfiguration gui = loadGui(player);
        Map<Integer, String> productsBySlot = new LinkedHashMap<>();
        PermissionShopHolder holder = new PermissionShopHolder(productsBySlot);
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("size", 54)),
                TextUtil.color(gui.getString("title", "&8PermissionShop")));
        holder.setInventory(inventory);
        fill(inventory, gui);
        for (PermissionProduct product : products.values().stream().sorted(java.util.Comparator.comparingInt(PermissionProduct::slot)).toList()) {
            if (!product.enabled() || product.slot() < 0 || product.slot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(product.slot(), productItem(player, product));
            productsBySlot.put(product.slot(), product.id());
        }
        player.openInventory(inventory);
    }

    public void handleClick(Player player, PermissionShopHolder holder, InventoryClickEvent event) {
        String productId = holder.productIdAt(event.getRawSlot());
        if (productId == null) {
            return;
        }
        PermissionProduct product = products.get(productId);
        if (product == null) {
            plugin.getLanguageService().send(player, "permissionshop.missingProduct");
            return;
        }
        buy(player, product);
        open(player);
    }

    public boolean buy(Player player, String productId) {
        PermissionProduct product = products.get(productId);
        if (product == null) {
            plugin.getLanguageService().send(player, "permissionshop.missingProduct");
            return false;
        }
        return buy(player, product);
    }

    private boolean buy(Player player, PermissionProduct product) {
        if (!player.hasPermission(PermissionNodes.USE) || !player.hasPermission("craftplayshop.permissionshop.use")) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return false;
        }
        if (!product.enabled()) {
            plugin.getLanguageService().send(player, "permissionshop.disabled");
            return false;
        }
        if (!hasAll(player, product.requiredPermissions())) {
            plugin.getLanguageService().send(player, "permissionshop.requirementsMissing");
            return false;
        }
        if (product.oneTime() && ownsAny(player, product.ownedPermissions().isEmpty() ? product.permissions() : product.ownedPermissions())) {
            plugin.getLanguageService().send(player, "permissionshop.alreadyOwned");
            return false;
        }
        if (!plugin.getEconomyService().has(player, product.price())) {
            plugin.getLanguageService().send(player, "serverShop.notEnoughMoney");
            return false;
        }
        if (!plugin.getEconomyService().withdraw(player, product.price())) {
            plugin.getLanguageService().send(player, "serverShop.notEnoughMoney");
            return false;
        }
        if (!executeProduct(player, product)) {
            plugin.getEconomyService().deposit(player, product.price());
            plugin.getLanguageService().send(player, "permissionshop.commandFailed");
            return false;
        }
        plugin.getTransactionService().logAsync(TransactionType.PERMISSION_SHOP_BUY, player, "permissionshop:" + product.id(), product.icon(), 1, product.price(), product.price());
        plugin.getLanguageService().send(player, "permissionshop.bought", Map.of(
                "product", TextUtil.color(product.displayName()),
                "price", plugin.getEconomyService().format(product.price())
        ));
        return true;
    }

    private boolean executeProduct(Player player, PermissionProduct product) {
        for (String permission : product.permissions()) {
            String template = product.duration().isBlank()
                    ? plugin.getConfig().getString("permissionShop.execution.permissionCommandFormat", "lp user %player% permission set %permission% true")
                    : plugin.getConfig().getString("permissionShop.execution.temporaryPermissionCommandFormat", "lp user %player% permission settemp %permission% true %duration%");
            if (!dispatchConsole(player, replaceCommandPlaceholders(template, player, product.id(), permission, product.duration()))) {
                return false;
            }
        }
        for (String command : product.commands()) {
            if (!dispatchConsole(player, replaceCommandPlaceholders(command, player, product.id(), "", product.duration()))) {
                return false;
            }
        }
        for (String command : product.playerCommands()) {
            String parsed = replaceCommandPlaceholders(command, player, product.id(), "", product.duration());
            player.performCommand(parsed.startsWith("/") ? parsed.substring(1) : parsed);
        }
        return true;
    }

    private boolean dispatchConsole(Player player, String command) {
        try {
            return plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
        } catch (Exception exception) {
            plugin.getPluginLogService().error("Could not execute PermissionShop command.", exception);
            return false;
        }
    }

    private String replaceCommandPlaceholders(String value, Player player, String productId, String permission, String duration) {
        Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        placeholders.put("product_id", productId);
        placeholders.put("permission", permission);
        placeholders.put("duration", duration == null ? "" : duration);
        return plugin.getPlaceholderApiHook().apply(player, PlaceholderUtil.apply(value, placeholders));
    }

    private ItemStack productItem(Player player, PermissionProduct product) {
        ItemStack stack = product.icon();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(product.displayName()));
            Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
            placeholders.put("price", plugin.getEconomyService().format(product.price()));
            placeholders.put("product", TextUtil.color(product.displayName()));
            placeholders.put("product_id", product.id());
            placeholders.put("duration", product.duration());
            meta.setLore(product.lore().stream().map(line -> TextUtil.color(PlaceholderUtil.apply(line, placeholders))).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void fill(Inventory inventory, YamlConfiguration gui) {
        ConfigurationSection filler = gui.getConfigurationSection("filler");
        if (filler == null || !filler.getBoolean("enabled", true)) {
            return;
        }
        Material material = Material.matchMaterial(filler.getString("material", "BLACK_STAINED_GLASS_PANE"));
        ItemStack stack = new ItemStack(material == null ? Material.BLACK_STAINED_GLASS_PANE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(filler.getString("name", " ")));
            stack.setItemMeta(meta);
        }
        for (int slot : filler.getIntegerList("slots")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private YamlConfiguration loadGui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/permissionshop.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/permissionshop.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private boolean hasAll(Player player, List<String> permissions) {
        for (String permission : permissions) {
            if (!permission.isBlank() && !player.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    private boolean ownsAny(Player player, List<String> permissions) {
        for (String permission : permissions) {
            if (!permission.isBlank() && player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private int sanitizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("permissionShop.enabled", false);
    }
}

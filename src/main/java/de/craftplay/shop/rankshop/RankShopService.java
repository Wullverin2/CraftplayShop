package de.craftplay.shop.rankshop;

import de.craftplay.shop.CraftplayShopPlugin;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RankShopService {
    private final CraftplayShopPlugin plugin;
    private final Map<String, RankProduct> products = new ConcurrentHashMap<>();

    public RankShopService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        products.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("rankShop.products");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection product = section.getConfigurationSection(id);
            if (product == null) {
                continue;
            }
            Material material = Material.matchMaterial(product.getString("material", "NAME_TAG"));
            products.put(id, new RankProduct(
                    id,
                    product.getBoolean("enabled", true),
                    product.getString("displayName", id),
                    product.getStringList("lore"),
                    material == null ? Material.NAME_TAG : material,
                    product.getInt("slot", -1),
                    product.getDouble("price", 0.0D),
                    product.getString("group", ""),
                    product.getString("duration", ""),
                    product.getStringList("commands"),
                    product.getStringList("playerCommands"),
                    product.getStringList("requiredPermissions"),
                    product.getStringList("ownedPermissions"),
                    product.getStringList("removeGroups"),
                    product.getBoolean("oneTime", true)
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
        RankShopHolder holder = new RankShopHolder(productsBySlot);
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("size", 54)),
                TextUtil.color(gui.getString("title", "&8RankShop")));
        holder.setInventory(inventory);
        fill(inventory, gui);
        for (RankProduct product : products.values().stream().sorted(java.util.Comparator.comparingInt(RankProduct::slot)).toList()) {
            if (!product.enabled() || product.slot() < 0 || product.slot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(product.slot(), productItem(player, product));
            productsBySlot.put(product.slot(), product.id());
        }
        player.openInventory(inventory);
    }

    public void handleClick(Player player, RankShopHolder holder, InventoryClickEvent event) {
        String productId = holder.productIdAt(event.getRawSlot());
        if (productId == null) {
            return;
        }
        RankProduct product = products.get(productId);
        if (product == null) {
            plugin.getLanguageService().send(player, "rankshop.missingProduct");
            return;
        }
        buy(player, product);
        open(player);
    }

    public boolean buy(Player player, String productId) {
        RankProduct product = products.get(productId);
        if (product == null) {
            plugin.getLanguageService().send(player, "rankshop.missingProduct");
            return false;
        }
        return buy(player, product);
    }

    private boolean buy(Player player, RankProduct product) {
        if (!player.hasPermission("craftplayshop.rankshop.use")) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return false;
        }
        if (!product.enabled()) {
            plugin.getLanguageService().send(player, "rankshop.disabled");
            return false;
        }
        if (!hasAll(player, product.requiredPermissions())) {
            plugin.getLanguageService().send(player, "rankshop.requirementsMissing");
            return false;
        }
        if (product.oneTime() && ownsAny(player, product.ownedPermissions())) {
            plugin.getLanguageService().send(player, "rankshop.alreadyOwned");
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
            plugin.getLanguageService().send(player, "rankshop.commandFailed");
            return false;
        }
        plugin.getTransactionService().logAsync(TransactionType.RANK_SHOP_BUY, player, "rankshop:" + product.id(), product.icon(), 1, product.price(), product.price());
        plugin.getLanguageService().send(player, "rankshop.bought", Map.of(
                "product", TextUtil.color(product.displayName()),
                "price", plugin.getEconomyService().format(product.price())
        ));
        return true;
    }

    private boolean executeProduct(Player player, RankProduct product) {
        String removeFormat = plugin.getConfig().getString("rankShop.execution.removeGroupCommandFormat", "lp user %player% parent remove %group%");
        for (String group : product.removeGroups()) {
            if (!dispatchConsole(player, replaceCommandPlaceholders(removeFormat, player, product.id(), group, product.duration()))) {
                return false;
            }
        }
        if (!product.group().isBlank()) {
            String addFormat = product.duration().isBlank()
                    ? plugin.getConfig().getString("rankShop.execution.addGroupCommandFormat", "lp user %player% parent add %group%")
                    : plugin.getConfig().getString("rankShop.execution.temporaryAddGroupCommandFormat", "lp user %player% parent addtemp %group% %duration%");
            if (!dispatchConsole(player, replaceCommandPlaceholders(addFormat, player, product.id(), product.group(), product.duration()))) {
                return false;
            }
        }
        for (String command : product.commands()) {
            if (!dispatchConsole(player, replaceCommandPlaceholders(command, player, product.id(), product.group(), product.duration()))) {
                return false;
            }
        }
        for (String command : product.playerCommands()) {
            String parsed = replaceCommandPlaceholders(command, player, product.id(), product.group(), product.duration());
            player.performCommand(parsed.startsWith("/") ? parsed.substring(1) : parsed);
        }
        return true;
    }

    private boolean dispatchConsole(Player player, String command) {
        try {
            return plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
        } catch (Exception exception) {
            plugin.getPluginLogService().error("Could not execute RankShop command.", exception);
            return false;
        }
    }

    private String replaceCommandPlaceholders(String value, Player player, String productId, String group, String duration) {
        Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        placeholders.put("product_id", productId);
        placeholders.put("group", group == null ? "" : group);
        placeholders.put("duration", duration == null ? "" : duration);
        return plugin.getPlaceholderApiHook().apply(player, PlaceholderUtil.apply(value, placeholders));
    }

    private ItemStack productItem(Player player, RankProduct product) {
        ItemStack stack = product.icon();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(product.displayName()));
            Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
            placeholders.put("price", plugin.getEconomyService().format(product.price()));
            placeholders.put("product", TextUtil.color(product.displayName()));
            placeholders.put("product_id", product.id());
            placeholders.put("group", product.group());
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
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/rankshop.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/rankshop.yml");
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
        return plugin.getConfig().getBoolean("rankShop.enabled", false);
    }
}

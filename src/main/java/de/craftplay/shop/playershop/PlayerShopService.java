package de.craftplay.shop.playershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.LocationUtil;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PlayerShopService implements Listener {
    private static final String SIGN_MARKER_SHOP = "[shop]";
    private static final String SIGN_MARKER_CSHOP = "[cshop]";
    private static final String DISPLAY_TAG_PREFIX = "craftplayshop_playershop_display_";

    private final CraftplayShopPlugin plugin;
    private final Map<String, PlayerShop> byContainer = new HashMap<>();
    private final Map<String, PlayerShop> bySign = new HashMap<>();
    private final Map<Long, PlayerShop> byId = new HashMap<>();
    private final Map<String, PendingCreation> pendingCreations = new HashMap<>();
    private final Map<UUID, ChatCreation> chatCreations = new HashMap<>();
    private final Map<UUID, Boolean> searchInputs = new HashMap<>();
    private BukkitTask cleanupTask;
    private BukkitTask displayAnimationTask;

    public PlayerShopService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void load() {
        synchronized (byContainer) {
            byContainer.clear();
            bySign.clear();
            byId.clear();
            for (PlayerShop shop : loadShops()) {
                cache(shop);
                updateSign(shop);
                spawnDisplay(shop);
            }
        }
        startCleanupTask();
        startDisplayAnimationTask();
    }

    public void shutdown() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        cleanupTask = null;
        if (displayAnimationTask != null && !displayAnimationTask.isCancelled()) {
            displayAnimationTask.cancel();
        }
        displayAnimationTask = null;
    }

    public int deleteShopsInRegion(String world, int minX, int maxX, int minZ, int maxZ) {
        List<PlayerShop> removed = new ArrayList<>();
        synchronized (byContainer) {
            for (PlayerShop shop : byContainer.values()) {
                if (shop.world().equals(world)
                        && shop.containerX() >= minX
                        && shop.containerX() <= maxX
                        && shop.containerZ() >= minZ
                        && shop.containerZ() <= maxZ) {
                    removed.add(shop);
                }
            }
            for (PlayerShop shop : removed) {
                uncache(shop);
            }
        }
        if (!removed.isEmpty()) {
            plugin.getTaskService().runAsync(() -> deleteMany(removed));
        }
        return removed.size();
    }

    public void openHome(Player player) {
        YamlConfiguration gui = gui(player);
        PlayerShopMenuHolder holder = new PlayerShopMenuHolder(PlayerShopMenuView.HOME);
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "home.size", 27), title(player, gui, "home.title", "&8PlayerShop"));
        addConfiguredButton(player, gui, inventory, holder, "home.buttons.allShops", PlayerShopMenuAction.ALL_SHOPS);
        addConfiguredButton(player, gui, inventory, holder, "home.buttons.ownShops", PlayerShopMenuAction.OWN_SHOPS);
        addConfiguredButton(player, gui, inventory, holder, "home.buttons.search", PlayerShopMenuAction.SEARCH);
        addConfiguredButton(player, gui, inventory, holder, "home.buttons.close", PlayerShopMenuAction.CLOSE);
        player.openInventory(inventory);
    }

    public void openAll(Player player) {
        openList(player, PlayerShopMenuView.ALL, "", 0);
    }

    public void openOwn(Player player) {
        openList(player, PlayerShopMenuView.OWN, "", 0);
    }

    public void requestSearch(Player player) {
        synchronized (searchInputs) {
            searchInputs.put(player.getUniqueId(), true);
        }
        player.closeInventory();
        plugin.getLanguageService().send(player, "playerShop.searchPrompt");
    }

    public boolean hasSearchInput(Player player) {
        synchronized (searchInputs) {
            return searchInputs.containsKey(player.getUniqueId());
        }
    }

    public void handleSearchInput(Player player, String message) {
        synchronized (searchInputs) {
            searchInputs.remove(player.getUniqueId());
        }
        String query = message == null ? "" : message.trim();
        if (query.equalsIgnoreCase("cancel") || query.equalsIgnoreCase("abbrechen")) {
            plugin.getLanguageService().send(player, "playerShop.searchCancelled");
            return;
        }
        openList(player, PlayerShopMenuView.SEARCH, query, 0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!plugin.getConfigService().playerShopsEnabled() || !plugin.getConfig().getBoolean("playerShops.creation.signCreationEnabled", true)) {
            return;
        }
        String firstLine = normalize(event.getLine(0));
        PlayerShopType type = shopTypeFromMarker(firstLine);
        if (type == null) {
            return;
        }
        if (type != PlayerShopType.SELL && type != PlayerShopType.BUY) {
            plugin.getLanguageService().send(event.getPlayer(), "general.featureNotAvailable");
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(PermissionNodes.PLAYER_SHOP_CREATE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        Block containerBlock = findAdjacentContainer(event.getBlock());
        if (containerBlock == null) {
            plugin.getLanguageService().send(player, "playerShop.noContainer");
            return;
        }
        if (findByLocation(containerBlock.getLocation()) != null) {
            plugin.getLanguageService().send(player, "playerShop.alreadyExists");
            return;
        }
        if (!plugin.getProtectionService().canCreateShop(player, containerBlock.getLocation())) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        ItemStack handItem = player.getInventory().getItemInMainHand();
        int amount = parsePositiveInt(event.getLine(1));
        double price = parsePositiveDouble(event.getLine(2));
        if (amount <= 0) {
            plugin.getLanguageService().send(player, "playerShop.invalidAmount");
            return;
        }
        if (price <= 0.0D) {
            plugin.getLanguageService().send(player, "playerShop.invalidPrice");
            return;
        }

        if (handItem == null || handItem.getType().isAir()) {
            PendingCreation pending = new PendingCreation(player.getUniqueId(), player.getName(), type, containerBlock.getLocation(), event.getBlock().getLocation(), amount, price);
            synchronized (pendingCreations) {
                pendingCreations.put(LocationUtil.compact(event.getBlock().getLocation()), pending);
            }
            writePendingSign(event, pending);
            plugin.getLanguageService().send(player, "playerShop.hitSignWithItem");
            return;
        }
        ItemStack template = handItem.clone();
        template.setAmount(1);
        PlayerShop shop = createShop(player, type, containerBlock.getLocation(), event.getBlock().getLocation(), template, amount, price);
        if (shop == null) {
            plugin.getLanguageService().send(player, "general.databaseError");
            return;
        }
        writeSign(event, shop);
        plugin.getLanguageService().send(player, "playerShop.created", Map.of(
                "amount", Integer.toString(amount),
                "item", displayItem(template),
                "price", plugin.getEconomyService().format(price)
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerShopUseBeforeProtection(PlayerInteractEvent event) {
        if (!plugin.getConfigService().playerShopsEnabled()
                || event.getClickedBlock() == null
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        PlayerShop shop = findByLocation(event.getClickedBlock().getLocation());
        if (shop == null || !shop.active()) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        if (player.isSneaking() && canManage(player, shop)) {
            openEditGui(player, shop);
            return;
        }
        useShop(player, shop);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfigService().playerShopsEnabled()
                || event.getClickedBlock() == null
                || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK)) {
            return;
        }
        if (startChatCreation(event)) {
            return;
        }
        if (completePendingCreation(event)) {
            return;
        }
        PlayerShop shop = findByLocation(event.getClickedBlock().getLocation());
        if (shop != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().isSneaking() && canManage(event.getPlayer(), shop)) {
            event.setCancelled(true);
            openEditGui(event.getPlayer(), shop);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (shop == null || !shop.active()) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        useShop(player, shop);
    }

    private void useShop(Player player, PlayerShop shop) {
        if (shop.type() == PlayerShopType.SELL) {
            buyFromShop(player, shop);
        } else if (shop.type() == PlayerShopType.BUY) {
            sellToShop(player, shop);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        ChatCreation creation;
        synchronized (chatCreations) {
            creation = chatCreations.get(event.getPlayer().getUniqueId());
        }
        if (creation == null) {
            if (hasSearchInput(event.getPlayer())) {
                event.setCancelled(true);
                String message = event.getMessage();
                plugin.getTaskService().runSync(() -> handleSearchInput(event.getPlayer(), message));
            }
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getTaskService().runSync(() -> handleCreationChat(event.getPlayer(), message));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfigService().playerShopsEnabled()
                || !plugin.getConfig().getBoolean("playerShops.protection.blockContainerOpenByOthers", true)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        PlayerShop shop = findByInventory(event.getInventory());
        if (shop == null || canManage(player, shop)) {
            return;
        }
        event.setCancelled(true);
        plugin.getLanguageService().send(player, "playerShop.containerProtected");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerShopDeleteHolder holder
                && event.getWhoClicked() instanceof Player player) {
            event.setCancelled(true);
            handleDeleteConfirmClick(player, holder, event);
            return;
        }
        if (event.getInventory().getHolder() instanceof PlayerShopMenuHolder holder
                && event.getWhoClicked() instanceof Player player) {
            event.setCancelled(true);
            handleMenuClick(player, holder, event);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PlayerShopEditHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        PlayerShop shop = findById(holder.shopId());
        if (shop == null || !canManage(player, shop)) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) {
            return;
        }
        YamlConfiguration gui = gui(player);
        int itemSlot = slot(gui, "edit.slots.item", 11);
        int amountSlot = slot(gui, "edit.slots.amount", 13);
        int priceSlot = slot(gui, "edit.slots.price", 15);
        int displaySlot = slot(gui, "edit.slots.display", 22);
        int closeSlot = slot(gui, "edit.slots.close", 26);
        if (slot == itemSlot) {
            ItemStack cursor = event.getCursor();
            ItemStack replacement = cursor == null || cursor.getType().isAir() ? player.getInventory().getItemInMainHand() : cursor;
            if (replacement == null || replacement.getType().isAir()) {
                plugin.getLanguageService().send(player, "playerShop.noHandItem");
                return;
            }
            ItemStack template = replacement.clone();
            template.setAmount(1);
            updateShop(player, shop, template, shop.amount(), shop.price(), shop.displayType());
            return;
        }
        if (slot == amountSlot) {
            int delta = event.isShiftClick() ? 10 : 1;
            int amount = event.isRightClick() ? shop.amount() - delta : shop.amount() + delta;
            updateShop(player, shop, shop.itemStack(), Math.max(1, amount), shop.price(), shop.displayType());
            return;
        }
        if (slot == priceSlot) {
            double delta = event.isShiftClick() ? 10.0D : 1.0D;
            double price = event.isRightClick() ? shop.price() - delta : shop.price() + delta;
            updateShop(player, shop, shop.itemStack(), shop.amount(), Math.max(0.01D, price), shop.displayType());
            return;
        }
        if (slot == displaySlot) {
            updateShop(player, shop, shop.itemStack(), shop.amount(), shop.price(), nextDisplayType(shop.displayType()));
            return;
        }
        if (slot == closeSlot) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerShopEditHolder
                || event.getInventory().getHolder() instanceof PlayerShopMenuHolder
                || event.getInventory().getHolder() instanceof PlayerShopDeleteHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfigService().playerShopsEnabled()) {
            return;
        }
        PlayerShop shop = findByLocation(event.getBlock().getLocation());
        if (shop == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!canManage(player, shop)) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "playerShop.breakProtected");
            return;
        }
        if (plugin.getConfig().getBoolean("playerShops.protection.deleteShopOnOwnerBreak", true)) {
            deleteShop(shop);
            plugin.getLanguageService().send(player, "playerShop.deleted");
        }
    }

    private PlayerShop createShop(Player player, PlayerShopType type, Location container, Location sign, ItemStack itemStack, int amount, double price) {
        return createShop(player, type, container, sign, itemStack, amount, price, defaultDisplayType());
    }

    private PlayerShop createShop(Player player, PlayerShopType type, Location container, Location sign, ItemStack itemStack, int amount, double price, PlayerShopDisplayType displayType) {
        String table = plugin.getDatabaseService().table("player_shops");
        long now = System.currentTimeMillis();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (owner_uuid, owner_name, type, world, container_x, container_y, container_z, sign_x, sign_y, sign_z, item_data, material, amount, price, display_type, active, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getName());
                statement.setString(3, type.name());
                statement.setString(4, container.getWorld().getName());
                statement.setInt(5, container.getBlockX());
                statement.setInt(6, container.getBlockY());
                statement.setInt(7, container.getBlockZ());
                statement.setInt(8, sign.getBlockX());
                statement.setInt(9, sign.getBlockY());
                statement.setInt(10, sign.getBlockZ());
                statement.setString(11, plugin.getItemSerializer().serialize(itemStack));
                statement.setString(12, itemStack.getType().name());
                statement.setInt(13, amount);
                statement.setDouble(14, price);
                statement.setString(15, displayType.name());
                statement.setBoolean(16, true);
                statement.setLong(17, now);
                statement.setLong(18, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        PlayerShop shop = new PlayerShop(keys.getLong(1), player.getUniqueId(), player.getName(), type,
                                container.getWorld().getName(), container.getBlockX(), container.getBlockY(), container.getBlockZ(),
                                sign.getBlockX(), sign.getBlockY(), sign.getBlockZ(), itemStack, itemStack.getType().name(), amount, price, displayType, true, now, now);
                        synchronized (byContainer) {
                            cache(shop);
                        }
                        spawnDisplay(shop);
                        return shop;
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not create player shop.", exception);
            }
        }
        return null;
    }

    private void buyFromShop(Player buyer, PlayerShop shop) {
        if (!buyer.hasPermission(PermissionNodes.PLAYER_SHOP_BUY)) {
            plugin.getLanguageService().send(buyer, "general.noPermission");
            return;
        }
        if (!plugin.getProtectionService().canUseShop(buyer, shop.containerLocation())) {
            plugin.getLanguageService().send(buyer, "general.noPermission");
            return;
        }
        if (buyer.getUniqueId().equals(shop.ownerUuid())) {
            plugin.getLanguageService().send(buyer, "playerShop.cannotBuyOwn");
            return;
        }
        BlockState state = shop.containerLocation().getBlock().getState();
        if (!(state instanceof Container container)) {
            plugin.getLanguageService().send(buyer, "playerShop.missingContainer");
            return;
        }
        Inventory inventory = container.getInventory();
        if (countMatching(inventory, shop.itemStack()) < shop.amount()) {
            plugin.getLanguageService().send(buyer, "playerShop.outOfStock");
            return;
        }
        if (!hasSpace(buyer.getInventory(), shop.itemStack(), shop.amount())) {
            plugin.getLanguageService().send(buyer, "serverShop.inventoryFull");
            return;
        }
        if (!plugin.getEconomyService().has(buyer, shop.price())) {
            plugin.getLanguageService().send(buyer, "serverShop.notEnoughMoney");
            return;
        }
        if (!plugin.getEconomyService().withdraw(buyer, shop.price())) {
            plugin.getLanguageService().send(buyer, "serverShop.notEnoughMoney");
            return;
        }
        List<ItemStack> removed = removeMatching(inventory, shop.itemStack(), shop.amount());
        if (removedAmount(removed) != shop.amount()) {
            for (ItemStack stack : removed) {
                inventory.addItem(stack);
            }
            plugin.getEconomyService().deposit(buyer, shop.price());
            plugin.getLanguageService().send(buyer, "playerShop.outOfStock");
            return;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(shop.ownerUuid());
        if (!plugin.getEconomyService().deposit(owner, shop.price())) {
            for (ItemStack stack : removed) {
                inventory.addItem(stack);
            }
            plugin.getEconomyService().deposit(buyer, shop.price());
            plugin.getLanguageService().send(buyer, "general.databaseError");
            return;
        }
        Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(createStack(shop.itemStack(), shop.amount()));
        if (!leftovers.isEmpty()) {
            int delivered = shop.amount() - totalAmount(leftovers.values());
            if (delivered > 0) {
                List<ItemStack> takenBack = removeMatching(buyer.getInventory(), shop.itemStack(), delivered);
                for (ItemStack stack : takenBack) {
                    inventory.addItem(stack);
                }
            }
            for (ItemStack stack : leftovers.values()) {
                inventory.addItem(stack);
            }
            plugin.getEconomyService().deposit(buyer, shop.price());
            plugin.getLanguageService().send(buyer, "serverShop.inventoryFull");
            return;
        }
        plugin.getTransactionService().logAsync(TransactionType.PLAYER_SHOP_BUY, buyer, "playershop:" + shop.id(), shop.itemStack(), shop.amount(), shop.price() / shop.amount(), shop.price());
        plugin.getLanguageService().send(buyer, "playerShop.bought", Map.of(
                "amount", Integer.toString(shop.amount()),
                "item", displayItem(shop.itemStack()),
                "price", plugin.getEconomyService().format(shop.price()),
                "owner", shop.ownerName()
        ));
        Player onlineOwner = Bukkit.getPlayer(shop.ownerUuid());
        if (onlineOwner != null) {
            plugin.getLanguageService().send(onlineOwner, "playerShop.sold", Map.of(
                    "amount", Integer.toString(shop.amount()),
                    "item", displayItem(shop.itemStack()),
                    "price", plugin.getEconomyService().format(shop.price()),
                    "buyer", buyer.getName()
            ));
        }
        updateSign(shop);
    }

    private void sellToShop(Player seller, PlayerShop shop) {
        if (!seller.hasPermission(PermissionNodes.PLAYER_SHOP_SELL)) {
            plugin.getLanguageService().send(seller, "general.noPermission");
            return;
        }
        if (!plugin.getProtectionService().canUseShop(seller, shop.containerLocation())) {
            plugin.getLanguageService().send(seller, "general.noPermission");
            return;
        }
        if (seller.getUniqueId().equals(shop.ownerUuid())) {
            plugin.getLanguageService().send(seller, "playerShop.cannotUseOwn");
            return;
        }
        BlockState state = shop.containerLocation().getBlock().getState();
        if (!(state instanceof Container container)) {
            plugin.getLanguageService().send(seller, "playerShop.missingContainer");
            return;
        }
        Inventory inventory = container.getInventory();
        if (countMatching(seller.getInventory(), shop.itemStack()) < shop.amount()) {
            plugin.getLanguageService().send(seller, "playerShop.notEnoughItems");
            return;
        }
        if (!hasSpace(inventory, shop.itemStack(), shop.amount())) {
            plugin.getLanguageService().send(seller, "playerShop.shopFull");
            return;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(shop.ownerUuid());
        if (!plugin.getEconomyService().has(owner, shop.price())) {
            plugin.getLanguageService().send(seller, "playerShop.ownerNoMoney");
            return;
        }
        if (!plugin.getEconomyService().withdraw(owner, shop.price())) {
            plugin.getLanguageService().send(seller, "playerShop.ownerNoMoney");
            return;
        }
        List<ItemStack> removed = removeMatching(seller.getInventory(), shop.itemStack(), shop.amount());
        if (removedAmount(removed) != shop.amount()) {
            for (ItemStack stack : removed) {
                seller.getInventory().addItem(stack);
            }
            plugin.getEconomyService().deposit(owner, shop.price());
            plugin.getLanguageService().send(seller, "playerShop.notEnoughItems");
            return;
        }
        Map<Integer, ItemStack> leftovers = inventory.addItem(createStack(shop.itemStack(), shop.amount()));
        if (!leftovers.isEmpty()) {
            int delivered = shop.amount() - totalAmount(leftovers.values());
            if (delivered > 0) {
                List<ItemStack> takenBack = removeMatching(inventory, shop.itemStack(), delivered);
                for (ItemStack stack : takenBack) {
                    seller.getInventory().addItem(stack);
                }
            }
            for (ItemStack stack : leftovers.values()) {
                seller.getInventory().addItem(stack);
            }
            plugin.getEconomyService().deposit(owner, shop.price());
            plugin.getLanguageService().send(seller, "playerShop.shopFull");
            return;
        }
        if (!plugin.getEconomyService().deposit(seller, shop.price())) {
            removeMatching(inventory, shop.itemStack(), shop.amount());
            for (ItemStack stack : removed) {
                seller.getInventory().addItem(stack);
            }
            plugin.getEconomyService().deposit(owner, shop.price());
            plugin.getLanguageService().send(seller, "general.databaseError");
            return;
        }
        plugin.getTransactionService().logAsync(TransactionType.PLAYER_SHOP_SELL, seller, "playershop:" + shop.id(), shop.itemStack(), shop.amount(), shop.price() / shop.amount(), shop.price());
        plugin.getLanguageService().send(seller, "playerShop.soldToShop", Map.of(
                "amount", Integer.toString(shop.amount()),
                "item", displayItem(shop.itemStack()),
                "price", plugin.getEconomyService().format(shop.price()),
                "owner", shop.ownerName()
        ));
        Player onlineOwner = Bukkit.getPlayer(shop.ownerUuid());
        if (onlineOwner != null) {
            plugin.getLanguageService().send(onlineOwner, "playerShop.boughtFromPlayer", Map.of(
                    "amount", Integer.toString(shop.amount()),
                    "item", displayItem(shop.itemStack()),
                    "price", plugin.getEconomyService().format(shop.price()),
                    "seller", seller.getName()
            ));
        }
        updateSign(shop);
    }

    private List<PlayerShop> loadShops() {
        List<PlayerShop> shops = new ArrayList<>();
        String table = plugin.getDatabaseService().table("player_shops");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement("SELECT * FROM " + table);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ItemStack itemStack = plugin.getItemSerializer().deserialize(result.getString("item_data"));
                    if (itemStack == null || itemStack.getType().isAir()) {
                        continue;
                    }
                    shops.add(new PlayerShop(
                            result.getLong("id"),
                            UUID.fromString(result.getString("owner_uuid")),
                            result.getString("owner_name"),
                            PlayerShopType.valueOf(result.getString("type")),
                            result.getString("world"),
                            result.getInt("container_x"),
                            result.getInt("container_y"),
                            result.getInt("container_z"),
                            result.getInt("sign_x"),
                            result.getInt("sign_y"),
                            result.getInt("sign_z"),
                            itemStack,
                            result.getString("material"),
                            result.getInt("amount"),
                            result.getDouble("price"),
                            displayType(result.getString("display_type")),
                            result.getBoolean("active"),
                            result.getLong("created_at"),
                            result.getLong("updated_at")));
                }
            } catch (SQLException | IllegalArgumentException exception) {
                plugin.getPluginLogService().error("Could not load player shops.", exception);
            }
        }
        shops.sort(Comparator.comparingLong(PlayerShop::id));
        return shops;
    }

    private void deleteShop(PlayerShop shop) {
        synchronized (byContainer) {
            uncache(shop);
        }
        plugin.getTaskService().runAsync(() -> deleteOne(shop.id()));
    }

    private void deleteOne(long id) {
        String table = plugin.getDatabaseService().table("player_shops");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not delete player shop.", exception);
            }
        }
    }

    private void deleteMany(List<PlayerShop> shops) {
        String table = plugin.getDatabaseService().table("player_shops");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
                for (PlayerShop shop : shops) {
                    statement.setLong(1, shop.id());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not delete player shops in PlotSquared region.", exception);
            }
        }
        plugin.getPluginLogService().info("Removed " + shops.size() + " player shops after PlotSquared plot deletion.");
    }

    private void startCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        if (!plugin.getConfig().getBoolean("playerShops.cleanup.enabled", true)) {
            return;
        }
        long interval = Math.max(20L, plugin.getConfig().getLong("playerShops.cleanup.intervalSeconds", 60L) * 20L);
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupMissingPhysicalShops, interval, interval);
    }

    private void cleanupMissingPhysicalShops() {
        List<PlayerShop> invalid = snapshotShops().stream()
                .filter(shop -> !physicalShopExists(shop))
                .toList();
        if (invalid.isEmpty()) {
            return;
        }
        synchronized (byContainer) {
            for (PlayerShop shop : invalid) {
                uncache(shop);
            }
        }
        plugin.getTaskService().runAsync(() -> deleteMany(invalid));
    }

    private boolean physicalShopExists(PlayerShop shop) {
        if (Bukkit.getWorld(shop.world()) == null) {
            return false;
        }
        return shop.containerLocation().getBlock().getState() instanceof Container
                && new Location(Bukkit.getWorld(shop.world()), shop.signX(), shop.signY(), shop.signZ()).getBlock().getState() instanceof Sign;
    }

    private void openEditGui(Player player, PlayerShop shop) {
        if (!player.hasPermission(PermissionNodes.PLAYER_SHOP_EDIT) && !player.hasPermission(PermissionNodes.PLAYER_SHOP_ADMIN)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        YamlConfiguration gui = gui(player);
        Map<String, String> placeholders = shopPlaceholders(shop);
        Inventory inventory = Bukkit.createInventory(new PlayerShopEditHolder(shop.id()), size(gui, "edit.size", 27),
                title(player, gui, "edit.title", plugin.getLanguageService().get(player, "playerShop.editTitle"), placeholders));
        inventory.setItem(slot(gui, "edit.slots.item", 11), editItem(player, gui, shop, placeholders));
        inventory.setItem(slot(gui, "edit.slots.amount", 13), configuredItem(player, gui.getConfigurationSection("edit.items.amount"), placeholders));
        inventory.setItem(slot(gui, "edit.slots.price", 15), configuredItem(player, gui.getConfigurationSection("edit.items.price"), placeholders));
        inventory.setItem(slot(gui, "edit.slots.display", 22), configuredItem(player, gui.getConfigurationSection("edit.items.display"), placeholders));
        inventory.setItem(slot(gui, "edit.slots.close", 26), configuredItem(player, gui.getConfigurationSection("edit.items.close"), placeholders));
        player.openInventory(inventory);
    }

    private void openList(Player player, PlayerShopMenuView view, String query, int page) {
        YamlConfiguration gui = gui(player);
        String titlePath = switch (view) {
            case OWN -> "list.ownTitle";
            case SEARCH -> "list.searchTitle";
            default -> "list.allTitle";
        };
        List<Integer> slots = gui.getIntegerList("list.shopSlots");
        if (slots.isEmpty()) {
            slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }
        List<PlayerShop> shops = shopsForView(player, view, query).stream()
                .filter(PlayerShop::active)
                .sorted(Comparator.comparing(PlayerShop::ownerName).thenComparing(PlayerShop::material).thenComparingLong(PlayerShop::id))
                .toList();
        int perPage = Math.max(1, slots.size());
        int totalPages = Math.max(1, (int) Math.ceil(shops.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        Map<String, String> pagePlaceholders = Map.of(
                "query", query == null ? "" : query,
                "page", Integer.toString(safePage + 1),
                "pages", Integer.toString(totalPages),
                "count", Integer.toString(shops.size())
        );
        PlayerShopMenuHolder holder = new PlayerShopMenuHolder(view, query, safePage, totalPages);
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "list.size", 54), title(player, gui, titlePath, "&8PlayerShops", pagePlaceholders));
        addConfiguredButton(player, gui, inventory, holder, "list.buttons.back", PlayerShopMenuAction.BACK, pagePlaceholders);
        addConfiguredButton(player, gui, inventory, holder, "list.buttons.search", PlayerShopMenuAction.SEARCH, pagePlaceholders);
        addConfiguredButton(player, gui, inventory, holder, "list.buttons.ownShops", PlayerShopMenuAction.OWN_SHOPS, pagePlaceholders);
        if (safePage > 0) {
            addConfiguredButton(player, gui, inventory, holder, "list.buttons.previous", PlayerShopMenuAction.PREVIOUS_PAGE, pagePlaceholders);
        }
        if (safePage + 1 < totalPages) {
            addConfiguredButton(player, gui, inventory, holder, "list.buttons.next", PlayerShopMenuAction.NEXT_PAGE, pagePlaceholders);
        }
        if (shops.isEmpty()) {
            ConfigurationSection empty = gui.getConfigurationSection("list.emptyItem");
            if (empty != null) {
                inventory.setItem(empty.getInt("slot", 22), configuredItem(player, empty, pagePlaceholders));
            }
        }
        int start = safePage * perPage;
        int end = Math.min(shops.size(), start + perPage);
        for (int index = start; index < end; index++) {
            int slotIndex = index - start;
            int slot = slots.get(slotIndex);
            PlayerShop shop = shops.get(index);
            inventory.setItem(slot, shopListItem(player, gui, shop));
            holder.shops().put(slot, shop.id());
        }
        player.openInventory(inventory);
    }

    private List<PlayerShop> shopsForView(Player player, PlayerShopMenuView view, String query) {
        return switch (view) {
            case OWN -> snapshotShops().stream()
                    .filter(shop -> shop.ownerUuid().equals(player.getUniqueId()))
                    .toList();
            case SEARCH -> snapshotShops().stream()
                    .filter(shop -> matchesSearch(shop, query))
                    .toList();
            default -> snapshotShops();
        };
    }

    private void handleMenuClick(Player player, PlayerShopMenuHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) {
            return;
        }
        PlayerShopMenuAction action = holder.actions().get(slot);
        if (action != null) {
            switch (action) {
                case HOME, BACK -> openHome(player);
                case ALL_SHOPS -> openAll(player);
                case OWN_SHOPS -> openOwn(player);
                case SEARCH -> requestSearch(player);
                case NEXT_PAGE -> openList(player, holder.view(), holder.query(), holder.page() + 1);
                case PREVIOUS_PAGE -> openList(player, holder.view(), holder.query(), holder.page() - 1);
                case CLOSE -> player.closeInventory();
            }
            return;
        }
        Long shopId = holder.shops().get(slot);
        if (shopId == null) {
            return;
        }
        PlayerShop shop = findById(shopId);
        if (shop == null) {
            player.closeInventory();
            plugin.getLanguageService().send(player, "playerShop.missingContainer");
            return;
        }
        if (event.isShiftClick() && event.isLeftClick()) {
            if (!canManage(player, shop)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return;
            }
            openDeleteConfirm(player, shop, holder);
            return;
        }
        if (event.isRightClick()) {
            teleportToShop(player, shop);
            return;
        }
        if (event.isLeftClick()) {
            if (!canManage(player, shop)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return;
            }
            openEditGui(player, shop);
        }
    }

    private void openDeleteConfirm(Player player, PlayerShop shop, PlayerShopMenuHolder returnHolder) {
        YamlConfiguration gui = gui(player);
        Map<String, String> placeholders = shopPlaceholders(shop);
        placeholders = new HashMap<>(placeholders);
        placeholders.put("page", Integer.toString(returnHolder.page() + 1));
        placeholders.put("pages", Integer.toString(returnHolder.totalPages()));
        placeholders.put("query", returnHolder.query());
        PlayerShopDeleteHolder holder = new PlayerShopDeleteHolder(shop.id(), returnHolder.view(), returnHolder.query(), returnHolder.page());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "deleteConfirm.size", 27),
                title(player, gui, "deleteConfirm.title", "&8Delete PlayerShop", placeholders));
        int previewSlot = slot(gui, "deleteConfirm.slots.preview", 13);
        int confirmSlot = slot(gui, "deleteConfirm.slots.confirm", 11);
        int cancelSlot = slot(gui, "deleteConfirm.slots.cancel", 15);
        if (previewSlot >= 0 && previewSlot < inventory.getSize()) {
            inventory.setItem(previewSlot, shopListItem(player, gui, shop));
        }
        if (confirmSlot >= 0 && confirmSlot < inventory.getSize()) {
            inventory.setItem(confirmSlot, configuredItem(player, gui.getConfigurationSection("deleteConfirm.items.confirm"), placeholders));
        }
        if (cancelSlot >= 0 && cancelSlot < inventory.getSize()) {
            inventory.setItem(cancelSlot, configuredItem(player, gui.getConfigurationSection("deleteConfirm.items.cancel"), placeholders));
        }
        player.openInventory(inventory);
    }

    private void handleDeleteConfirmClick(Player player, PlayerShopDeleteHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        YamlConfiguration gui = gui(player);
        if (slot == slot(gui, "deleteConfirm.slots.cancel", 15)) {
            openList(player, holder.returnView(), holder.query(), holder.page());
            return;
        }
        if (slot != slot(gui, "deleteConfirm.slots.confirm", 11)) {
            return;
        }
        PlayerShop shop = findById(holder.shopId());
        if (shop == null) {
            plugin.getLanguageService().send(player, "playerShop.missingContainer");
            openList(player, holder.returnView(), holder.query(), holder.page());
            return;
        }
        if (!canManage(player, shop)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            openList(player, holder.returnView(), holder.query(), holder.page());
            return;
        }
        deleteShop(shop);
        plugin.getLanguageService().send(player, "playerShop.deleted");
        openList(player, holder.returnView(), holder.query(), holder.page());
    }

    private List<PlayerShop> snapshotShops() {
        synchronized (byContainer) {
            return new ArrayList<>(byId.values());
        }
    }

    private boolean matchesSearch(PlayerShop shop, String query) {
        String normalized = TextUtil.stripColor(query).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return true;
        }
        return TextUtil.stripColor(displayItem(shop.itemStack())).toLowerCase(Locale.ROOT).contains(normalized)
                || shop.material().toLowerCase(Locale.ROOT).contains(normalized)
                || shop.ownerName().toLowerCase(Locale.ROOT).contains(normalized)
                || shop.type().name().toLowerCase(Locale.ROOT).contains(normalized);
    }

    private void teleportToShop(Player player, PlayerShop shop) {
        Location signLocation = new Location(Bukkit.getWorld(shop.world()), shop.signX(), shop.signY(), shop.signZ());
        if (signLocation.getWorld() == null || !(signLocation.getBlock().getState() instanceof Sign)) {
            plugin.getLanguageService().send(player, "playerShop.missingContainer");
            return;
        }
        Location target = signLocation.clone().add(0.5D, 0.0D, 0.5D);
        BlockData data = signLocation.getBlock().getBlockData();
        if (data instanceof WallSign wallSign) {
            Block front = signLocation.getBlock().getRelative(wallSign.getFacing());
            if (front.getType().isAir()) {
                target = front.getLocation().add(0.5D, 0.0D, 0.5D);
            }
        }
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleport(target);
        plugin.getLanguageService().send(player, "playerShop.teleported");
    }

    private ItemStack shopListItem(Player player, YamlConfiguration gui, PlayerShop shop) {
        ItemStack itemStack = shop.itemStack().clone();
        itemStack.setAmount(Math.max(1, Math.min(64, shop.amount())));
        ConfigurationSection section = gui.getConfigurationSection("list.shopItem");
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && section != null) {
            Map<String, String> placeholders = shopPlaceholders(shop);
            meta.setDisplayName(TextUtil.color(applyPlaceholders(player, section.getString("name", "%item%"), placeholders)));
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(TextUtil.color(applyPlaceholders(player, line, placeholders)));
            }
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void addConfiguredButton(Player player, YamlConfiguration gui, Inventory inventory, PlayerShopMenuHolder holder, String path, PlayerShopMenuAction action) {
        addConfiguredButton(player, gui, inventory, holder, path, action, Map.of());
    }

    private void addConfiguredButton(Player player, YamlConfiguration gui, Inventory inventory, PlayerShopMenuHolder holder, String path, PlayerShopMenuAction action, Map<String, String> placeholders) {
        ConfigurationSection section = gui.getConfigurationSection(path);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, configuredItem(player, section, placeholders));
        holder.actions().put(slot, action);
    }

    private ItemStack configuredItem(Player player, ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return new ItemStack(Material.BARRIER);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack itemStack = new ItemStack(material == null ? Material.STONE : material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(applyPlaceholders(player, section.getString("name", ""), placeholders)));
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(TextUtil.color(applyPlaceholders(player, line, placeholders)));
            }
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private Map<String, String> shopPlaceholders(PlayerShop shop) {
        int stock = matchingStock(shop);
        return Map.ofEntries(
                Map.entry("id", Long.toString(shop.id())),
                Map.entry("item", displayItem(shop.itemStack())),
                Map.entry("material", shop.material()),
                Map.entry("owner", shop.ownerName()),
                Map.entry("type", shop.type() == PlayerShopType.BUY ? "Ankauf" : "Verkauf"),
                Map.entry("type_en", shop.type() == PlayerShopType.BUY ? "Buying" : "Selling"),
                Map.entry("amount", Integer.toString(shop.amount())),
                Map.entry("price", plugin.getEconomyService().format(shop.price())),
                Map.entry("stock", Integer.toString(stock)),
                Map.entry("world", shop.world()),
                Map.entry("x", Integer.toString(shop.containerX())),
                Map.entry("y", Integer.toString(shop.containerY())),
                Map.entry("z", Integer.toString(shop.containerZ())),
                Map.entry("display_type", shop.displayType().name())
        );
    }

    private int matchingStock(PlayerShop shop) {
        if (!(shop.containerLocation().getBlock().getState() instanceof Container container)) {
            return 0;
        }
        return countMatching(container.getInventory(), shop.itemStack());
    }

    private String applyPlaceholders(Player player, String value, Map<String, String> placeholders) {
        Map<String, String> merged = new HashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        String parsed = PlaceholderUtil.apply(value, merged);
        return plugin.getPlaceholderApiHook().apply(player, parsed);
    }

    private YamlConfiguration gui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/playershop.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/playershop.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private int size(YamlConfiguration gui, String path, int fallback) {
        int size = gui.getInt(path, fallback);
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }

    private int slot(YamlConfiguration gui, String path, int fallback) {
        return gui.getInt(path, fallback);
    }

    private String title(Player player, YamlConfiguration gui, String path, String fallback) {
        return title(player, gui, path, fallback, Map.of());
    }

    private String title(Player player, YamlConfiguration gui, String path, String fallback, Map<String, String> placeholders) {
        return TextUtil.color(applyPlaceholders(player, gui.getString(path, fallback), placeholders));
    }

    private ItemStack editItem(Player player, YamlConfiguration gui, PlayerShop shop, Map<String, String> placeholders) {
        ItemStack itemStack = shop.itemStack().clone();
        itemStack.setAmount(1);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            ConfigurationSection section = gui.getConfigurationSection("edit.items.item");
            if (section != null && section.contains("name")) {
                meta.setDisplayName(TextUtil.color(applyPlaceholders(player, section.getString("name", "%item%"), placeholders)));
            }
            List<String> lore = new ArrayList<>();
            if (section != null && section.contains("lore")) {
                for (String line : section.getStringList("lore")) {
                    lore.add(TextUtil.color(applyPlaceholders(player, line, placeholders)));
                }
            } else {
                if (meta.hasLore() && meta.getLore() != null) {
                    lore.addAll(meta.getLore());
                }
                lore.add("");
                lore.add(TextUtil.color("&7Klicke mit einem Item am Cursor"));
                lore.add(TextUtil.color("&7oder in der Hand, um das Shop-Item zu aendern."));
            }
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack button(Material material, String name, List<String> loreLines) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setLore(loreLines.stream().map(TextUtil::color).toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void updateShop(Player player, PlayerShop oldShop, ItemStack itemStack, int amount, double price, PlayerShopDisplayType displayType) {
        long now = System.currentTimeMillis();
        PlayerShop updated = new PlayerShop(oldShop.id(), oldShop.ownerUuid(), oldShop.ownerName(), oldShop.type(),
                oldShop.world(), oldShop.containerX(), oldShop.containerY(), oldShop.containerZ(),
                oldShop.signX(), oldShop.signY(), oldShop.signZ(), itemStack, itemStack.getType().name(), amount, price,
                displayType, oldShop.active(), oldShop.createdAt(), now);
        synchronized (byContainer) {
            uncache(oldShop);
            cache(updated);
        }
        updateSign(updated);
        spawnDisplay(updated);
        saveShopAsync(updated);
        openEditGui(player, updated);
        plugin.getLanguageService().send(player, "playerShop.updated");
    }

    private void saveShopAsync(PlayerShop shop) {
        plugin.getTaskService().runAsync(() -> {
            String table = plugin.getDatabaseService().table("player_shops");
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "UPDATE " + table + " SET item_data = ?, material = ?, amount = ?, price = ?, display_type = ?, updated_at = ? WHERE id = ?")) {
                    statement.setString(1, plugin.getItemSerializer().serialize(shop.itemStack()));
                    statement.setString(2, shop.material());
                    statement.setInt(3, shop.amount());
                    statement.setDouble(4, shop.price());
                    statement.setString(5, shop.displayType().name());
                    statement.setLong(6, shop.updatedAt());
                    statement.setLong(7, shop.id());
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not update player shop.", exception);
                }
            }
        });
    }

    private PlayerShop findByInventory(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof BlockState state)) {
            return null;
        }
        return findByLocation(state.getLocation());
    }

    private PlayerShop findByLocation(Location location) {
        String key = LocationUtil.compact(location);
        synchronized (byContainer) {
            PlayerShop shop = byContainer.get(key);
            if (shop != null) {
                return shop;
            }
            return bySign.get(key);
        }
    }

    private PlayerShop findById(long id) {
        synchronized (byContainer) {
            return byId.get(id);
        }
    }

    private void cache(PlayerShop shop) {
        byContainer.put(key(shop.world(), shop.containerX(), shop.containerY(), shop.containerZ()), shop);
        bySign.put(key(shop.world(), shop.signX(), shop.signY(), shop.signZ()), shop);
        byId.put(shop.id(), shop);
    }

    private void uncache(PlayerShop shop) {
        byContainer.remove(key(shop.world(), shop.containerX(), shop.containerY(), shop.containerZ()));
        bySign.remove(key(shop.world(), shop.signX(), shop.signY(), shop.signZ()));
        byId.remove(shop.id());
        removeDisplay(shop);
    }

    private String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private boolean canManage(Player player, PlayerShop shop) {
        return player.getUniqueId().equals(shop.ownerUuid()) || player.hasPermission(PermissionNodes.PLAYER_SHOP_ADMIN);
    }

    private Block findAdjacentContainer(Block signBlock) {
        for (org.bukkit.block.BlockFace face : List.of(
                org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP,
                org.bukkit.block.BlockFace.DOWN)) {
            Block relative = signBlock.getRelative(face);
            if (relative.getState() instanceof Container) {
                return relative;
            }
        }
        return null;
    }

    private boolean startChatCreation(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("playerShops.creation.chatCreationEnabled", true)
                || event.getAction() != Action.LEFT_CLICK_BLOCK
                || !event.getPlayer().isSneaking()
                || event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof Container)) {
            return false;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(PermissionNodes.PLAYER_SHOP_CREATE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            event.setCancelled(true);
            return true;
        }
        Block container = event.getClickedBlock();
        if (findByLocation(container.getLocation()) != null) {
            plugin.getLanguageService().send(player, "playerShop.alreadyExists");
            event.setCancelled(true);
            return true;
        }
        if (!plugin.getProtectionService().canCreateShop(player, container.getLocation())) {
            plugin.getLanguageService().send(player, "general.noPermission");
            event.setCancelled(true);
            return true;
        }
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) {
            plugin.getLanguageService().send(player, "playerShop.noHandItem");
            event.setCancelled(true);
            return true;
        }
        ItemStack template = handItem.clone();
        template.setAmount(1);
        synchronized (chatCreations) {
            chatCreations.put(player.getUniqueId(), new ChatCreation(container.getLocation(), event.getBlockFace(), template, null, 0, 0.0D, CreationStep.TYPE));
        }
        plugin.getLanguageService().send(player, "playerShop.creationTypePrompt");
        event.setCancelled(true);
        return true;
    }

    private void handleCreationChat(Player player, String message) {
        ChatCreation creation;
        synchronized (chatCreations) {
            creation = chatCreations.get(player.getUniqueId());
        }
        if (creation == null) {
            return;
        }
        String input = normalize(message);
        if ("cancel".equals(input) || "abbrechen".equals(input)) {
            synchronized (chatCreations) {
                chatCreations.remove(player.getUniqueId());
            }
            plugin.getLanguageService().send(player, "playerShop.creationCancelled");
            return;
        }
        if (creation.step() == CreationStep.TYPE) {
            PlayerShopType type = parseCreationType(input);
            if (type == null) {
                plugin.getLanguageService().send(player, "playerShop.creationTypeInvalid");
                return;
            }
            updateChatCreation(player, new ChatCreation(creation.containerLocation(), creation.clickedFace(), creation.itemStack(), type, 0, 0.0D, CreationStep.AMOUNT));
            plugin.getLanguageService().send(player, type == PlayerShopType.BUY ? "playerShop.creationBuyAmountPrompt" : "playerShop.creationSellAmountPrompt");
            return;
        }
        if (creation.step() == CreationStep.AMOUNT) {
            int amount = parsePositiveInt(message);
            if (amount <= 0) {
                plugin.getLanguageService().send(player, "playerShop.invalidAmount");
                return;
            }
            updateChatCreation(player, new ChatCreation(creation.containerLocation(), creation.clickedFace(), creation.itemStack(), creation.type(), amount, 0.0D, CreationStep.PRICE));
            plugin.getLanguageService().send(player, "playerShop.creationPricePrompt", Map.of(
                    "amount", Integer.toString(amount),
                    "item", displayItem(creation.itemStack())
            ));
            return;
        }
        double price = parsePositiveDouble(message);
        if (price <= 0.0D) {
            plugin.getLanguageService().send(player, "playerShop.invalidPrice");
            return;
        }
        Location signLocation = placeAutoSign(creation.containerLocation(), creation.clickedFace(), player);
        if (signLocation == null) {
            plugin.getLanguageService().send(player, "playerShop.noSignSpace");
            return;
        }
        PlayerShop shop = createShop(player, creation.type(), creation.containerLocation(), signLocation, creation.itemStack(), creation.amount(), price);
        if (shop == null) {
            plugin.getLanguageService().send(player, "general.databaseError");
            return;
        }
        BlockState state = signLocation.getBlock().getState();
        if (state instanceof Sign sign) {
            writeSign(sign, shop);
        }
        synchronized (chatCreations) {
            chatCreations.remove(player.getUniqueId());
        }
        plugin.getLanguageService().send(player, "playerShop.created", Map.of(
                "amount", Integer.toString(shop.amount()),
                "item", displayItem(shop.itemStack()),
                "price", plugin.getEconomyService().format(shop.price())
        ));
    }

    private void updateChatCreation(Player player, ChatCreation creation) {
        synchronized (chatCreations) {
            chatCreations.put(player.getUniqueId(), creation);
        }
    }

    private PlayerShopType parseCreationType(String input) {
        return switch (input) {
            case "verkaufen", "sell", "selling", "v" -> PlayerShopType.SELL;
            case "ankaufen", "kaufen", "buy", "buying", "a" -> PlayerShopType.BUY;
            default -> null;
        };
    }

    private Location placeAutoSign(Location containerLocation, BlockFace clickedFace, Player player) {
        Block container = containerLocation.getBlock();
        BlockFace face = clickedFace == null ? BlockFace.NORTH : clickedFace;
        if (face == BlockFace.DOWN) {
            return null;
        }
        Block signBlock = container.getRelative(face);
        if (!signBlock.getType().isAir()) {
            return null;
        }
        if (face == BlockFace.UP) {
            signBlock.setType(autoStandingSignMaterial(), false);
            BlockData data = signBlock.getBlockData();
            if (data instanceof Directional directional) {
                directional.setFacing(player.getFacing().getOppositeFace());
                signBlock.setBlockData(directional, false);
            }
            return signBlock.getLocation();
        }
        Material material = autoSignMaterial();
        signBlock.setType(material, false);
        BlockData data = signBlock.getBlockData();
        if (data instanceof WallSign wallSign) {
            wallSign.setFacing(face);
            signBlock.setBlockData(wallSign, false);
            return signBlock.getLocation();
        }
        signBlock.setType(Material.AIR, false);
        return null;
    }

    private Material autoSignMaterial() {
        String configured = plugin.getConfig().getString("playerShops.creation.autoSignMaterial", "OAK_WALL_SIGN");
        Material material = Material.matchMaterial(configured == null ? "" : configured);
        if (material == null || !material.name().endsWith("_WALL_SIGN")) {
            return Material.OAK_WALL_SIGN;
        }
        return material;
    }

    private Material autoStandingSignMaterial() {
        String configured = plugin.getConfig().getString("playerShops.creation.autoStandingSignMaterial", "OAK_SIGN");
        Material material = Material.matchMaterial(configured == null ? "" : configured);
        if (material == null || !material.name().endsWith("_SIGN") || material.name().endsWith("_WALL_SIGN")) {
            return Material.OAK_SIGN;
        }
        return material;
    }

    private boolean completePendingCreation(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Sign sign)) {
            return false;
        }
        PendingCreation pending;
        synchronized (pendingCreations) {
            pending = pendingCreations.get(LocationUtil.compact(clicked.getLocation()));
        }
        if (pending == null) {
            return false;
        }
        Player player = event.getPlayer();
        if (!pending.ownerUuid().equals(player.getUniqueId())) {
            return false;
        }
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) {
            plugin.getLanguageService().send(player, "playerShop.hitSignWithItem");
            event.setCancelled(true);
            return true;
        }
        ItemStack template = handItem.clone();
        template.setAmount(1);
        PlayerShop shop = createShop(player, pending.type(), pending.containerLocation(), pending.signLocation(), template, pending.amount(), pending.price());
        if (shop == null) {
            plugin.getLanguageService().send(player, "general.databaseError");
            event.setCancelled(true);
            return true;
        }
        synchronized (pendingCreations) {
            pendingCreations.remove(LocationUtil.compact(clicked.getLocation()));
        }
        writeSign(sign, shop);
        plugin.getLanguageService().send(player, "playerShop.created", Map.of(
                "amount", Integer.toString(shop.amount()),
                "item", displayItem(shop.itemStack()),
                "price", plugin.getEconomyService().format(shop.price())
        ));
        event.setCancelled(true);
        return true;
    }

    private PlayerShopType shopTypeFromMarker(String line) {
        List<String> configured = plugin.getConfig().getStringList("playerShops.creation.signMarkers");
        if (configured.isEmpty()) {
            configured = List.of(SIGN_MARKER_SHOP, SIGN_MARKER_CSHOP);
        }
        for (String marker : configured) {
            if (normalize(marker).equals(line)) {
                return typeFromLine(line);
            }
        }
        return typeFromLine(line);
    }

    private void writeSign(SignChangeEvent event, PlayerShop shop) {
        List<String> lines = signLines(shop);
        for (int i = 0; i < 4; i++) {
            event.setLine(i, lines.get(i));
        }
    }

    private void writeSign(Sign sign, PlayerShop shop) {
        List<String> lines = signLines(shop);
        for (int i = 0; i < 4; i++) {
            sign.setLine(i, lines.get(i));
        }
        sign.update(true);
    }

    private void writePendingSign(SignChangeEvent event, PendingCreation pending) {
        List<String> lines = plugin.getConfig().getStringList("playerShops.sign.pending.lines");
        if (lines.size() < 4) {
            lines = List.of("&6&l[shop]", "&eItem fehlt", "&7Mit Item klicken", "&8%owner%");
        }
        for (int i = 0; i < 4; i++) {
            event.setLine(i, TextUtil.color(applySignPlaceholders(lines.get(i), pending, null)));
        }
    }

    private void updateSign(PlayerShop shop) {
        if (Bukkit.getWorld(shop.world()) == null) {
            return;
        }
        BlockState state = new Location(Bukkit.getWorld(shop.world()), shop.signX(), shop.signY(), shop.signZ()).getBlock().getState();
        if (state instanceof Sign sign) {
            writeSign(sign, shop);
        }
    }

    private List<String> signLines(PlayerShop shop) {
        String path = shop.type() == PlayerShopType.BUY ? "playerShops.sign.buy.lines" : "playerShops.sign.sell.lines";
        List<String> configured = plugin.getConfig().getStringList(path);
        if (configured.size() < 4 || isLegacyDefaultSign(shop.type(), configured)) {
            configured = shop.type() == PlayerShopType.BUY
                    ? List.of("%status_color%&l%action%", "&f%amount%x %item%", "&a%price%", "&8%owner%")
                    : List.of("%status_color%&l%action%", "&f%amount%x %item%", "&a%price%", "&8%owner%");
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            lines.add(TextUtil.color(applySignPlaceholders(configured.get(i), null, shop)));
        }
        return lines;
    }

    private boolean isLegacyDefaultSign(PlayerShopType type, List<String> configured) {
        if (configured.size() < 4) {
            return false;
        }
        List<String> legacy = type == PlayerShopType.BUY
                ? List.of("%stock_color%&l[shop]", "Buying; &l%amount%", "&a%price%", "%owner%")
                : List.of("%stock_color%&l[shop]", "Selling; &l%amount%", "&a%price%", "%owner%");
        return configured.subList(0, 4).equals(legacy);
    }

    private String applySignPlaceholders(String line, PendingCreation pending, PlayerShop shop) {
        String owner = shop != null ? shop.ownerName() : pending.ownerName();
        int amount = shop != null ? shop.amount() : pending.amount();
        double price = shop != null ? shop.price() : pending.price();
        String item = shop != null ? displayItem(shop.itemStack()) : "";
        PlayerShopType type = shop != null ? shop.type() : pending.type();
        SignStatus status = signStatus(shop);
        String action = type == PlayerShopType.BUY ? "Ankaufen" : "Verkaufen";
        String actionEnglish = type == PlayerShopType.BUY ? "Buying" : "Selling";
        String formattedPrice = plugin.getEconomyService().format(price);
        return line
                .replace("%owner%", owner)
                .replace("%amount%", Integer.toString(amount))
                .replace("%item%", item)
                .replace("%price%", formattedPrice)
                .replace("%type%", type.name())
                .replace("%action%", action)
                .replace("%action_en%", actionEnglish)
                .replace("%status%", status.text())
                .replace("%available%", Boolean.toString(status.available()))
                .replace("%stock%", Integer.toString(status.stock()))
                .replace("%space%", Integer.toString(status.space()))
                .replace("%status_color%", status.color())
                .replace("%stock_color%", status.color())
                .replace("[owner]", owner)
                .replace("[amount]", Integer.toString(amount))
                .replace("[item]", item)
                .replace("[price]", formattedPrice)
                .replace("[type]", type.name())
                .replace("[action]", action)
                .replace("[status]", status.text())
                .replace("[available]", Boolean.toString(status.available()))
                .replace("[stock]", Integer.toString(status.stock()))
                .replace("[space]", Integer.toString(status.space()))
                .replace("[status color]", status.color())
                .replace("[stock color]", status.color());
    }

    private SignStatus signStatus(PlayerShop shop) {
        boolean available = false;
        int stock = 0;
        int space = 0;
        if (shop != null) {
            BlockState state = shop.containerLocation().getBlock().getState();
            if (state instanceof Container container) {
                stock = countMatching(container.getInventory(), shop.itemStack());
                space = availableSpace(container.getInventory(), shop.itemStack());
                if (shop.type() == PlayerShopType.SELL) {
                    available = stock >= shop.amount();
                } else if (shop.type() == PlayerShopType.BUY) {
                    available = space >= shop.amount()
                            && plugin.getEconomyService().has(Bukkit.getOfflinePlayer(shop.ownerUuid()), shop.price());
                }
            }
        }
        String color = plugin.getConfig().getString(available
                ? "playerShops.sign.status.availableColor"
                : "playerShops.sign.status.unavailableColor", available ? "&a" : "&c");
        String text = plugin.getConfig().getString(available
                ? "playerShops.sign.status.availableText"
                : "playerShops.sign.status.unavailableText", available ? "Verfuegbar" : "Nicht verfuegbar");
        return new SignStatus(available, color == null ? "" : color, text == null ? "" : text, stock, space);
    }

    private PlayerShopType typeFromLine(String line) {
        return switch (line) {
            case "shop", "[shop]", "sell", "[sell]", "cshop", "[cshop]" -> PlayerShopType.SELL;
            case "buy", "[buy]" -> PlayerShopType.BUY;
            case "barter", "[barter]", "trade", "[trade]" -> PlayerShopType.TRADE_ITEM;
            case "combo", "[combo]" -> PlayerShopType.BUY_SELL;
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int parsePositiveInt(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private double parsePositiveDouble(String value) {
        String cleaned = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("sell", "")
                .replace("s", "")
                .replace("price", "")
                .replace("preis", "")
                .replace(":", "")
                .replace(",", ".")
                .trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException exception) {
            return -1.0D;
        }
    }

    private int countMatching(Inventory inventory, ItemStack expected) {
        int amount = 0;
        ItemStack[] contents = inventory instanceof PlayerInventory playerInventory
                ? playerInventory.getStorageContents()
                : inventory.getContents();
        for (ItemStack content : contents) {
            if (plugin.getItemMatcher().matches(content, expected, plugin.getConfigService().playerShopItemMatchMode())) {
                amount += content.getAmount();
            }
        }
        return amount;
    }

    private List<ItemStack> removeMatching(Inventory inventory, ItemStack expected, int amount) {
        List<ItemStack> removed = new ArrayList<>();
        int remaining = amount;
        boolean playerStorage = inventory instanceof PlayerInventory;
        ItemStack[] contents = playerStorage ? ((PlayerInventory) inventory).getStorageContents() : inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack content = contents[slot];
            if (!plugin.getItemMatcher().matches(content, expected, plugin.getConfigService().playerShopItemMatchMode())) {
                continue;
            }
            int take = Math.min(content.getAmount(), remaining);
            ItemStack taken = content.clone();
            taken.setAmount(take);
            removed.add(taken);
            content.setAmount(content.getAmount() - take);
            contents[slot] = content.getAmount() <= 0 ? null : content;
            remaining -= take;
        }
        if (playerStorage) {
            ((PlayerInventory) inventory).setStorageContents(contents);
        } else {
            inventory.setContents(contents);
        }
        return removed;
    }

    private int removedAmount(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getAmount).sum();
    }

    private int totalAmount(Collection<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getAmount).sum();
    }

    private boolean hasSpace(Inventory inventory, ItemStack itemStack, int amount) {
        return availableSpace(inventory, itemStack) >= amount;
    }

    private int availableSpace(Inventory inventory, ItemStack itemStack) {
        int available = 0;
        int maxStackSize = Math.min(itemStack.getMaxStackSize(), inventory.getMaxStackSize());
        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                available += maxStackSize;
            } else if (content.isSimilar(itemStack)) {
                available += Math.max(0, maxStackSize - content.getAmount());
            }
        }
        return available;
    }

    private ItemStack createStack(ItemStack template, int amount) {
        ItemStack stack = template.clone();
        stack.setAmount(amount);
        return stack;
    }

    private void spawnDisplay(PlayerShop shop) {
        removeDisplay(shop);
        if (shop.displayType() == PlayerShopDisplayType.NONE || Bukkit.getWorld(shop.world()) == null) {
            return;
        }
        Location base = displayBaseLocation(shop);
        String tag = displayTag(shop);
        ItemStack displayItem = shop.itemStack().clone();
        displayItem.setAmount(1);
        try {
            if (shop.displayType() == PlayerShopDisplayType.GLASS_CASE) {
                Block anchor = displayAnchorBlock(shop);
                Location glassOrigin = anchor.getLocation().add(0.0D, 1.05D, 0.0D);
                Location itemCenter = glassOrigin.clone().add(0.5D, 0.5D, 0.5D);
                BlockDisplay glass = glassOrigin.getWorld().spawn(glassOrigin, BlockDisplay.class);
                glass.setBlock(Material.GLASS.createBlockData());
                glass.addScoreboardTag(tag);
                base = itemCenter;
            }
            ItemDisplay itemDisplay = base.getWorld().spawn(base, ItemDisplay.class);
            itemDisplay.setItemStack(displayItem);
            itemDisplay.setItemDisplayTransform(shop.displayType() == PlayerShopDisplayType.ITEM_FRAME
                    ? ItemDisplay.ItemDisplayTransform.GUI
                    : ItemDisplay.ItemDisplayTransform.FIXED);
            itemDisplay.addScoreboardTag(tag);
            if (shop.displayType() == PlayerShopDisplayType.LARGE_ITEM) {
                itemDisplay.setDisplayWidth(1.4F);
                itemDisplay.setDisplayHeight(1.4F);
            }
        } catch (RuntimeException exception) {
            plugin.getPluginLogService().warn("Could not spawn player shop display for shop " + shop.id() + ": " + exception.getMessage());
        }
    }

    private void startDisplayAnimationTask() {
        if (displayAnimationTask != null && !displayAnimationTask.isCancelled()) {
            displayAnimationTask.cancel();
        }
        displayAnimationTask = null;
        if (!plugin.getConfig().getBoolean("playerShops.display.animation.item.enabled", true)) {
            return;
        }
        long interval = Math.max(1L, plugin.getConfig().getLong("playerShops.display.animation.item.intervalTicks", 2L));
        displayAnimationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animateItemDisplays, interval, interval);
    }

    private void animateItemDisplays() {
        double amplitude = Math.max(0.0D, plugin.getConfig().getDouble("playerShops.display.animation.item.hoverAmplitude", 0.12D));
        double hoverSpeed = Math.max(0.0D, plugin.getConfig().getDouble("playerShops.display.animation.item.hoverSpeed", 2.0D));
        double rotationDegreesPerTick = plugin.getConfig().getDouble("playerShops.display.animation.item.rotationDegreesPerTick", 3.0D);
        int interpolationTicks = clampDisplayDuration(plugin.getConfig().getInt("playerShops.display.animation.item.interpolationTicks", 4));
        int maxAnimated = Math.max(1, plugin.getConfig().getInt("playerShops.display.animation.item.maxAnimatedDisplaysPerTick", 150));
        double elapsedTicks = System.currentTimeMillis() / 50.0D;
        int animated = 0;
        for (PlayerShop shop : snapshotShops()) {
            if (shop.displayType() != PlayerShopDisplayType.ITEM) {
                continue;
            }
            ItemDisplay itemDisplay = findItemDisplay(shop);
            if (itemDisplay == null) {
                continue;
            }
            double phase = (elapsedTicks / 20.0D * hoverSpeed) + (shop.id() * 0.73D);
            double offset = Math.sin(phase) * amplitude;
            float yawRadians = (float) Math.toRadians((elapsedTicks * rotationDegreesPerTick + shop.id() * 37.0D) % 360.0D);
            itemDisplay.setInterpolationDelay(0);
            itemDisplay.setInterpolationDuration(interpolationTicks);
            itemDisplay.setTransformation(new Transformation(
                    new Vector3f(0.0F, (float) offset, 0.0F),
                    new AxisAngle4f(yawRadians, 0.0F, 1.0F, 0.0F),
                    new Vector3f(1.0F, 1.0F, 1.0F),
                    new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
            ));
            animated++;
            if (animated >= maxAnimated) {
                return;
            }
        }
    }

    private ItemDisplay findItemDisplay(PlayerShop shop) {
        if (Bukkit.getWorld(shop.world()) == null) {
            return null;
        }
        String tag = displayTag(shop);
        Location center = displayBaseLocation(shop);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 1.0D, 1.0D, 1.0D)) {
            if (entity instanceof ItemDisplay itemDisplay && entity.getScoreboardTags().contains(tag)) {
                return itemDisplay;
            }
        }
        return null;
    }

    private Location displayBaseLocation(PlayerShop shop) {
        return displayAnchorBlock(shop).getLocation().add(0.5D, 1.35D, 0.5D);
    }

    private int clampDisplayDuration(int ticks) {
        return Math.max(0, Math.min(59, ticks));
    }

    private void removeDisplay(PlayerShop shop) {
        if (Bukkit.getWorld(shop.world()) == null) {
            return;
        }
        String tag = displayTag(shop);
        Location center = displayAnchorBlock(shop).getLocation().add(0.5D, 1.2D, 0.5D);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 2.0D, 2.0D, 2.0D)) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }

    private Block displayAnchorBlock(PlayerShop shop) {
        Location fallback = shop.containerLocation();
        if (Bukkit.getWorld(shop.world()) == null) {
            return fallback.getBlock();
        }
        Block signBlock = new Location(Bukkit.getWorld(shop.world()), shop.signX(), shop.signY(), shop.signZ()).getBlock();
        BlockData signData = signBlock.getBlockData();
        if (signData instanceof WallSign wallSign) {
            Block attached = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
            if (attached.getState() instanceof Container) {
                return attached;
            }
        }
        return fallback.getBlock();
    }

    private String displayTag(PlayerShop shop) {
        return DISPLAY_TAG_PREFIX + shop.id();
    }

    private PlayerShopDisplayType nextDisplayType(PlayerShopDisplayType current) {
        PlayerShopDisplayType[] values = PlayerShopDisplayType.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private PlayerShopDisplayType defaultDisplayType() {
        return displayType(plugin.getConfig().getString("playerShops.display.defaultType", "ITEM"));
    }

    private PlayerShopDisplayType displayType(String value) {
        try {
            return PlayerShopDisplayType.valueOf(value == null ? "ITEM" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PlayerShopDisplayType.ITEM;
        }
    }

    private String displayItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "AIR";
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().getDisplayName();
        }
        return itemStack.getType().name();
    }

    private enum CreationStep {
        TYPE,
        AMOUNT,
        PRICE
    }

    private record ChatCreation(Location containerLocation, BlockFace clickedFace, ItemStack itemStack, PlayerShopType type, int amount, double price, CreationStep step) {
    }

    private record PendingCreation(UUID ownerUuid, String ownerName, PlayerShopType type, Location containerLocation, Location signLocation, int amount, double price) {
    }

    private record SignStatus(boolean available, String color, String text, int stock, int space) {
    }
}

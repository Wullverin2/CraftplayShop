package de.craftplay.shop.playershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.LocationUtil;
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
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PlayerShopService implements Listener {
    private static final String SIGN_MARKER_SHOP = "[shop]";
    private static final String SIGN_MARKER_CSHOP = "[cshop]";

    private final CraftplayShopPlugin plugin;
    private final Map<String, PlayerShop> byContainer = new HashMap<>();
    private final Map<String, PlayerShop> bySign = new HashMap<>();
    private final Map<String, PendingCreation> pendingCreations = new HashMap<>();
    private final Map<UUID, ChatCreation> chatCreations = new HashMap<>();

    public PlayerShopService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void load() {
        synchronized (byContainer) {
            byContainer.clear();
            bySign.clear();
            for (PlayerShop shop : loadShops()) {
                cache(shop);
            }
        }
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        PlayerShop shop = findByLocation(event.getClickedBlock().getLocation());
        if (shop == null || !shop.active()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getUniqueId().equals(shop.ownerUuid()) && player.isSneaking() && shop.isContainer(event.getClickedBlock().getLocation())) {
            return;
        }
        event.setCancelled(true);
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
        String table = plugin.getDatabaseService().table("player_shops");
        long now = System.currentTimeMillis();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (owner_uuid, owner_name, type, world, container_x, container_y, container_z, sign_x, sign_y, sign_z, item_data, material, amount, price, active, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                statement.setBoolean(15, true);
                statement.setLong(16, now);
                statement.setLong(17, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        PlayerShop shop = new PlayerShop(keys.getLong(1), player.getUniqueId(), player.getName(), type,
                                container.getWorld().getName(), container.getBlockX(), container.getBlockY(), container.getBlockZ(),
                                sign.getBlockX(), sign.getBlockY(), sign.getBlockZ(), itemStack, itemStack.getType().name(), amount, price, true, now, now);
                        synchronized (byContainer) {
                            cache(shop);
                        }
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
            for (ItemStack stack : leftovers.values()) {
                buyer.getWorld().dropItemNaturally(buyer.getLocation(), stack);
            }
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
            for (ItemStack stack : removed) {
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

    private void cache(PlayerShop shop) {
        byContainer.put(key(shop.world(), shop.containerX(), shop.containerY(), shop.containerZ()), shop);
        bySign.put(key(shop.world(), shop.signX(), shop.signY(), shop.signZ()), shop);
    }

    private void uncache(PlayerShop shop) {
        byContainer.remove(key(shop.world(), shop.containerX(), shop.containerY(), shop.containerZ()));
        bySign.remove(key(shop.world(), shop.signX(), shop.signY(), shop.signZ()));
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
            chatCreations.put(player.getUniqueId(), new ChatCreation(container.getLocation(), template, null, 0, 0.0D, CreationStep.TYPE));
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
            updateChatCreation(player, new ChatCreation(creation.containerLocation(), creation.itemStack(), type, 0, 0.0D, CreationStep.AMOUNT));
            plugin.getLanguageService().send(player, "playerShop.creationAmountPrompt");
            return;
        }
        if (creation.step() == CreationStep.AMOUNT) {
            int amount = parsePositiveInt(message);
            if (amount <= 0) {
                plugin.getLanguageService().send(player, "playerShop.invalidAmount");
                return;
            }
            updateChatCreation(player, new ChatCreation(creation.containerLocation(), creation.itemStack(), creation.type(), amount, 0.0D, CreationStep.PRICE));
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
        Location signLocation = placeAutoSign(creation.containerLocation());
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

    private Location placeAutoSign(Location containerLocation) {
        Block container = containerLocation.getBlock();
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            Block signBlock = container.getRelative(face);
            if (!signBlock.getType().isAir()) {
                continue;
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
        }
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
        if (configured.size() < 4) {
            configured = shop.type() == PlayerShopType.BUY
                    ? List.of("%stock_color%&l[shop]", "Buying; &l%amount%", "&a%price%", "%owner%")
                    : List.of("%stock_color%&l[shop]", "Selling; &l%amount%", "&a%price%", "%owner%");
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            lines.add(TextUtil.color(applySignPlaceholders(configured.get(i), null, shop)));
        }
        return lines;
    }

    private String applySignPlaceholders(String line, PendingCreation pending, PlayerShop shop) {
        String owner = shop != null ? shop.ownerName() : pending.ownerName();
        int amount = shop != null ? shop.amount() : pending.amount();
        double price = shop != null ? shop.price() : pending.price();
        String item = shop != null ? displayItem(shop.itemStack()) : "";
        String stockColor = "&4";
        if (shop != null) {
            BlockState state = shop.containerLocation().getBlock().getState();
            if (state instanceof Container container) {
                if (shop.type() == PlayerShopType.SELL && countMatching(container.getInventory(), shop.itemStack()) >= shop.amount()) {
                    stockColor = "&a";
                } else if (shop.type() == PlayerShopType.BUY
                        && hasSpace(container.getInventory(), shop.itemStack(), shop.amount())
                        && plugin.getEconomyService().has(Bukkit.getOfflinePlayer(shop.ownerUuid()), shop.price())) {
                    stockColor = "&a";
                }
            }
        }
        return line
                .replace("%owner%", owner)
                .replace("%amount%", Integer.toString(amount))
                .replace("%item%", item)
                .replace("%price%", plugin.getEconomyService().format(price))
                .replace("%stock_color%", stockColor)
                .replace("[owner]", owner)
                .replace("[amount]", Integer.toString(amount))
                .replace("[item]", item)
                .replace("[price]", plugin.getEconomyService().format(price))
                .replace("[stock color]", stockColor);
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

    private boolean hasSpace(Inventory inventory, ItemStack itemStack, int amount) {
        int remaining = amount;
        int maxStackSize = Math.min(itemStack.getMaxStackSize(), inventory.getMaxStackSize());
        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                remaining -= maxStackSize;
            } else if (content.isSimilar(itemStack)) {
                remaining -= Math.max(0, maxStackSize - content.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createStack(ItemStack template, int amount) {
        ItemStack stack = template.clone();
        stack.setAmount(amount);
        return stack;
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

    private record ChatCreation(Location containerLocation, ItemStack itemStack, PlayerShopType type, int amount, double price, CreationStep step) {
    }

    private record PendingCreation(UUID ownerUuid, String ownerName, PlayerShopType type, Location containerLocation, Location signLocation, int amount, double price) {
    }
}

package de.craftplay.shop.auctionhouse;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionHouseService {
    private final CraftplayShopPlugin plugin;
    private final Map<Long, AuctionHouseListing> listings = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> searchInputs = new ConcurrentHashMap<>();

    public AuctionHouseService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        listings.clear();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT * FROM " + plugin.getDatabaseService().table("auction_house_listings"));
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    AuctionHouseListing listing = mapListing(resultSet);
                    if (listing != null) {
                        listings.put(listing.id(), listing);
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not load AuctionHouse listings.", exception);
            }
        }
        expireDueListings();
    }

    public void openHome(Player player) {
        if (!enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return;
        }
        YamlConfiguration gui = loadGui(player);
        AuctionHouseHolder holder = new AuctionHouseHolder(AuctionHouseView.HOME, 0, "", Map.of());
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("home.size", 27)),
                TextUtil.color(parse(player, gui.getString("home.title", "&8AuctionHouse"), basePlaceholders(player, null, null, 0, 0))));
        holder.setInventory(inventory);
        fill(inventory, gui);
        addConfiguredButtons(player, inventory, gui, "home.buttons", basePlaceholders(player, null, null, 0, 0));
        player.openInventory(inventory);
    }

    public void openBrowse(Player player, int page, String query) {
        List<AuctionHouseListing> all = activeListings(query);
        openList(player, AuctionHouseView.BROWSE, page, query == null ? "" : query, all, "browse");
    }

    public void openMine(Player player, int page) {
        List<AuctionHouseListing> all = listings.values().stream()
                .filter(listing -> listing.sellerUuid().equals(player.getUniqueId()))
                .sorted(Comparator.comparingLong(AuctionHouseListing::createdAt).reversed())
                .toList();
        openList(player, AuctionHouseView.MINE, page, "", all, "mine");
    }

    public void openClaims(Player player, int page) {
        List<AuctionHouseListing> all = listings.values().stream()
                .filter(listing -> listing.sellerUuid().equals(player.getUniqueId()) && listing.isClaimable())
                .sorted(Comparator.comparingLong(AuctionHouseListing::createdAt).reversed())
                .toList();
        openList(player, AuctionHouseView.CLAIMS, page, "", all, "claims");
    }

    public void requestSearch(Player player) {
        searchInputs.put(player.getUniqueId(), true);
        player.closeInventory();
        plugin.getLanguageService().send(player, "auctionHouse.searchInput");
    }

    public boolean hasSearchInput(Player player) {
        return searchInputs.containsKey(player.getUniqueId());
    }

    public void handleSearchInput(Player player, String message) {
        searchInputs.remove(player.getUniqueId());
        if ("cancel".equalsIgnoreCase(message)) {
            plugin.getLanguageService().send(player, "auctionHouse.searchCancelled");
            openHome(player);
            return;
        }
        openBrowse(player, 0, message);
    }

    public void handleClick(Player player, AuctionHouseHolder holder, InventoryClickEvent event) {
        Long listingId = holder.listingAt(event.getRawSlot());
        if (listingId != null) {
            AuctionHouseListing listing = listings.get(listingId);
            if (listing == null) {
                plugin.getLanguageService().send(player, "auctionHouse.listingMissing");
                reopen(player, holder);
                return;
            }
            switch (holder.view()) {
                case BROWSE -> buyListing(player, listing);
                case MINE -> cancelListing(player, listing);
                case CLAIMS -> claimListing(player, listing);
                default -> { }
            }
            reopen(player, holder);
            return;
        }
        handleConfiguredButton(player, holder, event.getRawSlot(), event.isRightClick());
    }

    public void createListing(Player player, double price, int amount) {
        if (!enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return;
        }
        if (!player.hasPermission(PermissionNodes.AUCTION_HOUSE_SELL)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (price <= 0 || amount <= 0) {
            plugin.getLanguageService().send(player, "auctionHouse.invalidAmount");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() < amount) {
            plugin.getLanguageService().send(player, "auctionHouse.noItemInHand");
            return;
        }
        ItemStack listingStack = hand.clone();
        listingStack.setAmount(amount);
        double fee = calculateFee(price);
        if (fee > 0 && !plugin.getEconomyService().has(player, fee)) {
            plugin.getLanguageService().send(player, "auctionHouse.notEnoughFee", Map.of("fee", plugin.getEconomyService().format(fee)));
            return;
        }

        hand.setAmount(hand.getAmount() - amount);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        if (fee > 0 && !plugin.getEconomyService().withdraw(player, fee)) {
            refundItem(player, listingStack);
            plugin.getLanguageService().send(player, "auctionHouse.notEnoughFee", Map.of("fee", plugin.getEconomyService().format(fee)));
            return;
        }

        AuctionHouseListing listing = new AuctionHouseListing(
                0L,
                player.getUniqueId(),
                player.getName(),
                player.getWorld().getName(),
                listingStack.clone(),
                listingStack.getType().name(),
                listingStack.getAmount(),
                price,
                fee,
                AuctionHouseStatus.ACTIVE,
                null,
                "",
                System.currentTimeMillis(),
                System.currentTimeMillis() + listingDurationMillis(),
                0L,
                0L
        );
        AuctionHouseListing inserted = insertListing(listing);
        if (inserted == null) {
            if (fee > 0) {
                plugin.getEconomyService().deposit(player, fee);
            }
            refundItem(player, listingStack);
            plugin.getLanguageService().send(player, "general.databaseError");
            return;
        }
        listings.put(inserted.id(), inserted);
        plugin.getTransactionService().logAsync(TransactionType.AUCTION_HOUSE_SELL, player, "auctionhouse", listingStack, amount, price / amount, price);
        plugin.getLanguageService().send(player, "auctionHouse.listed", Map.of(
                "price", plugin.getEconomyService().format(price),
                "amount", Integer.toString(amount),
                "item", friendlyItemName(listingStack),
                "fee", plugin.getEconomyService().format(fee)
        ));
    }

    public boolean buyListing(Player player, AuctionHouseListing listing) {
        if (!player.hasPermission(PermissionNodes.AUCTION_HOUSE_BUY)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return false;
        }
        AuctionHouseListing current = listings.get(listing.id());
        if (current == null || current.status() != AuctionHouseStatus.ACTIVE) {
            plugin.getLanguageService().send(player, "auctionHouse.notAvailable");
            return false;
        }
        if (current.expiresAt() <= System.currentTimeMillis()) {
            expireListing(current.id());
            plugin.getLanguageService().send(player, "auctionHouse.notAvailable");
            return false;
        }
        if (current.sellerUuid().equals(player.getUniqueId())) {
            plugin.getLanguageService().send(player, "auctionHouse.ownListing");
            return false;
        }
        if (!plugin.getEconomyService().has(player, current.price())) {
            plugin.getLanguageService().send(player, "serverShop.notEnoughMoney");
            return false;
        }
        if (!canFit(player, current.itemStack())) {
            plugin.getLanguageService().send(player, "serverShop.inventoryFull");
            return false;
        }
        if (!plugin.getEconomyService().withdraw(player, current.price())) {
            plugin.getLanguageService().send(player, "serverShop.notEnoughMoney");
            return false;
        }
        ItemStack delivered = current.itemStack().clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(delivered);
        if (!leftover.isEmpty()) {
            plugin.getEconomyService().deposit(player, current.price());
            plugin.getLanguageService().send(player, "serverShop.inventoryFull");
            return false;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(current.sellerUuid());
        if (!plugin.getEconomyService().deposit(seller, current.price())) {
            player.getInventory().removeItem(delivered);
            plugin.getEconomyService().deposit(player, current.price());
            plugin.getLanguageService().send(player, "general.databaseError");
            return false;
        }

        AuctionHouseListing sold = new AuctionHouseListing(
                current.id(), current.sellerUuid(), current.sellerName(), current.world(), current.itemStack(), current.material(),
                current.amount(), current.price(), current.fee(), AuctionHouseStatus.SOLD, player.getUniqueId(), player.getName(),
                current.createdAt(), current.expiresAt(), System.currentTimeMillis(), current.claimedAt()
        );
        if (!updateListing(sold)) {
            player.getInventory().removeItem(delivered);
            plugin.getEconomyService().withdraw(seller, current.price());
            plugin.getEconomyService().deposit(player, current.price());
            plugin.getLanguageService().send(player, "general.databaseError");
            return false;
        }
        listings.put(sold.id(), sold);
        plugin.getTransactionService().logAsync(TransactionType.AUCTION_HOUSE_BUY, player, "auctionhouse", delivered, delivered.getAmount(), current.price() / Math.max(1, delivered.getAmount()), current.price());
        plugin.getLanguageService().send(player, "auctionHouse.bought", Map.of(
                "price", plugin.getEconomyService().format(current.price()),
                "amount", Integer.toString(current.amount()),
                "item", friendlyItemName(current.itemStack()),
                "seller", current.sellerName()
        ));
        Player sellerPlayer = Bukkit.getPlayer(current.sellerUuid());
        if (sellerPlayer != null && sellerPlayer.isOnline()) {
            plugin.getLanguageService().send(sellerPlayer, "auctionHouse.soldNotice", Map.of(
                    "price", plugin.getEconomyService().format(current.price()),
                    "amount", Integer.toString(current.amount()),
                    "item", friendlyItemName(current.itemStack()),
                    "buyer", player.getName()
            ));
        }
        return true;
    }

    public boolean cancelListing(Player player, AuctionHouseListing listing) {
        AuctionHouseListing current = listings.get(listing.id());
        if (current == null || current.status() != AuctionHouseStatus.ACTIVE) {
            plugin.getLanguageService().send(player, "auctionHouse.notAvailable");
            return false;
        }
        if (!current.sellerUuid().equals(player.getUniqueId()) && !player.hasPermission(PermissionNodes.AUCTION_HOUSE_ADMIN)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return false;
        }
        AuctionHouseListing cancelled = new AuctionHouseListing(
                current.id(), current.sellerUuid(), current.sellerName(), current.world(), current.itemStack(), current.material(),
                current.amount(), current.price(), current.fee(), AuctionHouseStatus.CANCELLED, current.buyerUuid(), current.buyerName(),
                current.createdAt(), current.expiresAt(), current.soldAt(), 0L
        );
        if (!updateListing(cancelled)) {
            plugin.getLanguageService().send(player, "general.databaseError");
            return false;
        }
        listings.put(cancelled.id(), cancelled);
        plugin.getLanguageService().send(player, "auctionHouse.cancelled");
        return true;
    }

    public boolean claimListing(Player player, AuctionHouseListing listing) {
        AuctionHouseListing current = listings.get(listing.id());
        if (current == null || !current.isClaimable()) {
            plugin.getLanguageService().send(player, "auctionHouse.notClaimable");
            return false;
        }
        if (!current.sellerUuid().equals(player.getUniqueId()) && !player.hasPermission(PermissionNodes.AUCTION_HOUSE_ADMIN)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return false;
        }
        if (!canFit(player, current.itemStack())) {
            plugin.getLanguageService().send(player, "serverShop.inventoryFull");
            return false;
        }
        player.getInventory().addItem(current.itemStack().clone());
        AuctionHouseListing claimed = new AuctionHouseListing(
                current.id(), current.sellerUuid(), current.sellerName(), current.world(), current.itemStack(), current.material(),
                current.amount(), current.price(), current.fee(), AuctionHouseStatus.CLAIMED, current.buyerUuid(), current.buyerName(),
                current.createdAt(), current.expiresAt(), current.soldAt(), System.currentTimeMillis()
        );
        if (!updateListing(claimed)) {
            player.getInventory().removeItem(current.itemStack());
            plugin.getLanguageService().send(player, "general.databaseError");
            return false;
        }
        listings.put(claimed.id(), claimed);
        plugin.getLanguageService().send(player, "auctionHouse.claimed");
        return true;
    }

    public void expireDueListings() {
        for (AuctionHouseListing listing : new ArrayList<>(listings.values())) {
            if (listing.status() == AuctionHouseStatus.ACTIVE && listing.expiresAt() <= System.currentTimeMillis()) {
                expireListing(listing.id());
            }
        }
    }

    private void expireListing(long id) {
        AuctionHouseListing current = listings.get(id);
        if (current == null || current.status() != AuctionHouseStatus.ACTIVE) {
            return;
        }
        AuctionHouseListing expired = new AuctionHouseListing(
                current.id(), current.sellerUuid(), current.sellerName(), current.world(), current.itemStack(), current.material(),
                current.amount(), current.price(), current.fee(), AuctionHouseStatus.EXPIRED, current.buyerUuid(), current.buyerName(),
                current.createdAt(), current.expiresAt(), current.soldAt(), current.claimedAt()
        );
        if (updateListing(expired)) {
            listings.put(expired.id(), expired);
        }
    }

    private void openList(Player player, AuctionHouseView view, int page, String query, List<AuctionHouseListing> source, String sectionName) {
        YamlConfiguration gui = loadGui(player);
        List<Integer> slots = gui.getIntegerList(sectionName + ".itemSlots");
        if (slots.isEmpty()) {
            for (int slot = 10; slot < 44; slot++) {
                if (slot % 9 != 0 && slot % 9 != 8) {
                    slots.add(slot);
                }
            }
        }
        int maxPage = Math.max(0, (source.size() - 1) / Math.max(1, slots.size()));
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        Map<Integer, Long> slotMap = new HashMap<>();
        AuctionHouseHolder holder = new AuctionHouseHolder(view, clampedPage, query, slotMap);
        Map<String, String> placeholders = basePlaceholders(player, query, null, source.size(), clampedPage + 1);
        placeholders.put("max_page", Integer.toString(maxPage + 1));
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt(sectionName + ".size", 54)),
                TextUtil.color(parse(player, gui.getString(sectionName + ".title", "&8AuctionHouse"), placeholders)));
        holder.setInventory(inventory);
        fill(inventory, gui);
        addConfiguredButtons(player, inventory, gui, sectionName + ".buttons", placeholders);

        int start = clampedPage * Math.max(1, slots.size());
        for (int index = 0; index < slots.size() && start + index < source.size(); index++) {
            int slot = slots.get(index);
            AuctionHouseListing listing = source.get(start + index);
            inventory.setItem(slot, listingItem(player, gui, listing, view));
            slotMap.put(slot, listing.id());
        }
        player.openInventory(inventory);
    }

    private ItemStack listingItem(Player player, YamlConfiguration gui, AuctionHouseListing listing, AuctionHouseView view) {
        ItemStack stack = listing.itemStack().clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = basePlaceholders(player, null, listing, 0, 0);
            meta.setDisplayName(TextUtil.color(parse(player, gui.getString("listings.itemName", "&e%item%"), placeholders)));
            String loreKey = switch (view) {
                case MINE -> "mine.itemLore";
                case CLAIMS -> "claims.itemLore";
                default -> "browse.itemLore";
            };
            meta.setLore(gui.getStringList(loreKey).stream()
                    .map(line -> TextUtil.color(parse(player, line, placeholders)))
                    .toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<AuctionHouseListing> activeListings(String query) {
        expireDueListings();
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        return listings.values().stream()
                .filter(listing -> listing.status() == AuctionHouseStatus.ACTIVE)
                .filter(listing -> normalized.isBlank() || matchesSearch(listing, normalized))
                .sorted(Comparator.comparingLong(AuctionHouseListing::createdAt).reversed())
                .toList();
    }

    private boolean matchesSearch(AuctionHouseListing listing, String query) {
        return listing.material().toLowerCase(Locale.ROOT).contains(query)
                || TextUtil.stripColor(friendlyItemName(listing.itemStack())).toLowerCase(Locale.ROOT).contains(query)
                || listing.sellerName().toLowerCase(Locale.ROOT).contains(query);
    }

    private AuctionHouseListing insertListing(AuctionHouseListing listing) {
        String table = plugin.getDatabaseService().table("auction_house_listings");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (seller_uuid, seller_name, world, item_data, material, amount, price, fee, status, buyer_uuid, buyer_name, created_at, expires_at, sold_at, claimed_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                fillListingStatement(statement, listing);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new AuctionHouseListing(
                                keys.getLong(1),
                                listing.sellerUuid(),
                                listing.sellerName(),
                                listing.world(),
                                listing.itemStack(),
                                listing.material(),
                                listing.amount(),
                                listing.price(),
                                listing.fee(),
                                listing.status(),
                                listing.buyerUuid(),
                                listing.buyerName(),
                                listing.createdAt(),
                                listing.expiresAt(),
                                listing.soldAt(),
                                listing.claimedAt()
                        );
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not insert AuctionHouse listing.", exception);
            }
        }
        return null;
    }

    private boolean updateListing(AuctionHouseListing listing) {
        String table = plugin.getDatabaseService().table("auction_house_listings");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "UPDATE " + table + " SET seller_uuid = ?, seller_name = ?, world = ?, item_data = ?, material = ?, amount = ?, price = ?, fee = ?, status = ?, buyer_uuid = ?, buyer_name = ?, created_at = ?, expires_at = ?, sold_at = ?, claimed_at = ? WHERE id = ?")) {
                fillListingStatement(statement, listing);
                statement.setLong(16, listing.id());
                return statement.executeUpdate() > 0;
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not update AuctionHouse listing.", exception);
                return false;
            }
        }
    }

    private AuctionHouseListing mapListing(ResultSet resultSet) throws SQLException {
        ItemStack itemStack = plugin.getItemSerializer().deserialize(resultSet.getString("item_data"));
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        String buyerUuidRaw = resultSet.getString("buyer_uuid");
        UUID buyerUuid = buyerUuidRaw == null || buyerUuidRaw.isBlank() ? null : UUID.fromString(buyerUuidRaw);
        return new AuctionHouseListing(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("seller_uuid")),
                resultSet.getString("seller_name"),
                resultSet.getString("world"),
                itemStack,
                resultSet.getString("material"),
                resultSet.getInt("amount"),
                resultSet.getDouble("price"),
                resultSet.getDouble("fee"),
                AuctionHouseStatus.valueOf(resultSet.getString("status")),
                buyerUuid,
                resultSet.getString("buyer_name"),
                resultSet.getLong("created_at"),
                resultSet.getLong("expires_at"),
                resultSet.getLong("sold_at"),
                resultSet.getLong("claimed_at")
        );
    }

    private void fillListingStatement(PreparedStatement statement, AuctionHouseListing listing) throws SQLException {
        statement.setString(1, listing.sellerUuid().toString());
        statement.setString(2, listing.sellerName());
        statement.setString(3, listing.world());
        statement.setString(4, plugin.getItemSerializer().serialize(listing.itemStack()));
        statement.setString(5, listing.material());
        statement.setInt(6, listing.amount());
        statement.setDouble(7, listing.price());
        statement.setDouble(8, listing.fee());
        statement.setString(9, listing.status().name());
        statement.setString(10, listing.buyerUuid() == null ? "" : listing.buyerUuid().toString());
        statement.setString(11, listing.buyerName() == null ? "" : listing.buyerName());
        statement.setLong(12, listing.createdAt());
        statement.setLong(13, listing.expiresAt());
        statement.setLong(14, listing.soldAt());
        statement.setLong(15, listing.claimedAt());
    }

    private void handleConfiguredButton(Player player, AuctionHouseHolder holder, int slot, boolean rightClick) {
        YamlConfiguration gui = loadGui(player);
        String sectionName = switch (holder.view()) {
            case HOME -> "home.buttons";
            case MINE -> "mine.buttons";
            case CLAIMS -> "claims.buttons";
            default -> "browse.buttons";
        };
        ConfigurationSection section = gui.getConfigurationSection(sectionName);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null || item.getInt("slot", -1) != slot) {
                continue;
            }
            List<String> actions = rightClick ? item.getStringList("rightClickActions") : item.getStringList("leftClickActions");
            if (actions.isEmpty() && rightClick) {
                actions = item.getStringList("leftClickActions");
            }
            for (String action : actions) {
                if ("AUCTIONHOUSE_BROWSE".equalsIgnoreCase(action)) {
                    openBrowse(player, 0, "");
                } else if ("AUCTIONHOUSE_SEARCH".equalsIgnoreCase(action)) {
                    requestSearch(player);
                } else if ("AUCTIONHOUSE_MINE".equalsIgnoreCase(action)) {
                    openMine(player, 0);
                } else if ("AUCTIONHOUSE_CLAIMS".equalsIgnoreCase(action)) {
                    openClaims(player, 0);
                } else if ("AUCTIONHOUSE_PREVIOUS".equalsIgnoreCase(action)) {
                    navigate(player, holder, -1);
                } else if ("AUCTIONHOUSE_NEXT".equalsIgnoreCase(action)) {
                    navigate(player, holder, 1);
                } else if ("AUCTIONHOUSE_SELL_HAND".equalsIgnoreCase(action)) {
                    createListing(player,
                            plugin.getConfig().getDouble("auctionHouse.defaults.price", 100.0D),
                            Math.max(1, player.getInventory().getItemInMainHand().getAmount()));
                } else {
                    plugin.getGuiActionExecutor().execute(player, action);
                }
            }
            return;
        }
    }

    private void navigate(Player player, AuctionHouseHolder holder, int delta) {
        switch (holder.view()) {
            case BROWSE -> openBrowse(player, holder.page() + delta, holder.query());
            case MINE -> openMine(player, holder.page() + delta);
            case CLAIMS -> openClaims(player, holder.page() + delta);
            default -> openHome(player);
        }
    }

    private void reopen(Player player, AuctionHouseHolder holder) {
        switch (holder.view()) {
            case BROWSE -> openBrowse(player, holder.page(), holder.query());
            case MINE -> openMine(player, holder.page());
            case CLAIMS -> openClaims(player, holder.page());
            default -> openHome(player);
        }
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

    private void addConfiguredButtons(Player player, Inventory inventory, YamlConfiguration gui, String sectionName, Map<String, String> placeholders) {
        ConfigurationSection section = gui.getConfigurationSection(sectionName);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null || !item.getBoolean("enabled", true)) {
                continue;
            }
            int slot = item.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, configuredItem(player, item, placeholders));
        }
    }

    private ItemStack configuredItem(Player player, ConfigurationSection section, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(parse(player, section.getString("name", ""), placeholders)));
            meta.setLore(section.getStringList("lore").stream()
                    .map(line -> TextUtil.color(parse(player, line, placeholders)))
                    .toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Map<String, String> basePlaceholders(Player player, String query, AuctionHouseListing listing, int resultCount, int page) {
        Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        placeholders.put("query", query == null ? "" : query);
        placeholders.put("result_count", Integer.toString(resultCount));
        placeholders.put("page", Integer.toString(page));
        if (listing != null) {
            placeholders.put("listing_id", Long.toString(listing.id()));
            placeholders.put("seller", listing.sellerName());
            placeholders.put("buyer", listing.buyerName() == null ? "" : listing.buyerName());
            placeholders.put("item", friendlyItemName(listing.itemStack()));
            placeholders.put("material", listing.material());
            placeholders.put("amount", Integer.toString(listing.amount()));
            placeholders.put("price", plugin.getEconomyService().format(listing.price()));
            placeholders.put("fee", plugin.getEconomyService().format(listing.fee()));
            placeholders.put("status", listing.status().name());
            placeholders.put("expires_in", formatDuration(Math.max(0L, listing.expiresAt() - System.currentTimeMillis())));
        }
        return placeholders;
    }

    private boolean canFit(Player player, ItemStack itemStack) {
        int remaining = itemStack.getAmount();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                return true;
            }
            if (!content.isSimilar(itemStack)) {
                continue;
            }
            remaining -= Math.max(0, content.getMaxStackSize() - content.getAmount());
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private void refundItem(Player player, ItemStack itemStack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack.clone());
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private String friendlyItemName(ItemStack itemStack) {
        if (itemStack == null) {
            return "Unknown";
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return TextUtil.color(meta.getDisplayName());
        }
        String raw = itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return java.util.Arrays.stream(raw.split(" "))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(itemStack.getType().name());
    }

    private long listingDurationMillis() {
        long hours = Math.max(1L, plugin.getConfig().getLong("auctionHouse.defaults.durationHours", 48L));
        return hours * 60L * 60L * 1000L;
    }

    private double calculateFee(double price) {
        double flat = Math.max(0.0D, plugin.getConfig().getDouble("auctionHouse.fees.flat", 0.0D));
        double percentage = Math.max(0.0D, plugin.getConfig().getDouble("auctionHouse.fees.percentage", 0.0D));
        return flat + (price * percentage);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("auctionHouse.enabled", true);
    }

    private String parse(Player player, String value, Map<String, String> placeholders) {
        return plugin.getPlaceholderApiHook().apply(player, PlaceholderUtil.apply(value, placeholders));
    }

    private YamlConfiguration loadGui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/auctionhouse.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/auctionhouse.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
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
}

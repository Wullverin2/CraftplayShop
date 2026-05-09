package de.craftplay.shop.referral;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
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

public class ReferralService {
    private final CraftplayShopPlugin plugin;
    private final ReferralCodeService codeService;
    private final RewardPackageService rewardPackageService;
    private final PendingRewardService pendingRewardService;
    private final Map<UUID, ReferralCodeEntry> codesByPlayer = new ConcurrentHashMap<>();
    private final Map<String, ReferralCodeEntry> codesByCode = new ConcurrentHashMap<>();
    private final Map<UUID, ReferralRedemption> redemptionsByRedeemer = new ConcurrentHashMap<>();
    private final Map<UUID, String> redeemInputs = new ConcurrentHashMap<>();

    public ReferralService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        this.codeService = new ReferralCodeService();
        this.rewardPackageService = new RewardPackageService(plugin);
        this.pendingRewardService = new PendingRewardService(plugin);
    }

    public void load() {
        rewardPackageService.load();
        codesByPlayer.clear();
        codesByCode.clear();
        redemptionsByRedeemer.clear();
        loadCodes();
        loadRedemptions();
    }

    public void onJoin(Player player) {
        plugin.getTaskService().runAsync(() -> {
            ensureCode(player);
            List<PendingReward> rewards = pendingRewardService.loadPending(player.getUniqueId());
            plugin.getTaskService().runSync(() -> {
                if (player.isOnline()) {
                    pendingRewardService.deliver(player, rewards);
                }
            });
        });
    }

    public void open(Player player) {
        if (!enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return;
        }
        YamlConfiguration gui = loadGui(player);
        Map<Integer, String> packageSlots = new LinkedHashMap<>();
        ReferralHolder holder = new ReferralHolder(packageSlots);
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("size", 36)), TextUtil.color(gui.getString("title", "&8Referral")));
        holder.setInventory(inventory);
        fill(inventory, gui);
        addStaticButton(player, inventory, gui, "items.code", codeItem(player));
        addStaticButton(player, inventory, gui, "items.redeem", staticItem(player, gui.getConfigurationSection("items.redeem"), Map.of()));
        addStaticButton(player, inventory, gui, "items.top", topItem(player, gui.getConfigurationSection("items.top")));
        for (ReferralRewardPackage rewardPackage : rewardPackageService.packages().stream().filter(ReferralRewardPackage::enabled).sorted(Comparator.comparingInt(ReferralRewardPackage::slot)).toList()) {
            if (rewardPackage.slot() < 0 || rewardPackage.slot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(rewardPackage.slot(), packageItem(player, rewardPackage));
            packageSlots.put(rewardPackage.slot(), rewardPackage.id());
        }
        player.openInventory(inventory);
    }

    public void handleClick(Player player, ReferralHolder holder, InventoryClickEvent event) {
        String packageId = holder.packageIdAt(event.getRawSlot());
        if (packageId != null) {
            requestRedeem(player, packageId);
            return;
        }
        YamlConfiguration gui = loadGui(player);
        if (matchesSlot(gui.getConfigurationSection("items.code"), event.getRawSlot())) {
            plugin.getLanguageService().send(player, "referral.code", Map.of("code", ensureCode(player).code()));
            return;
        }
        if (matchesSlot(gui.getConfigurationSection("items.redeem"), event.getRawSlot())) {
            requestRedeem(player, rewardPackageService.defaultPackageId());
            return;
        }
        if (matchesSlot(gui.getConfigurationSection("items.top"), event.getRawSlot())) {
            sendTop(player);
        }
    }

    public void requestRedeem(Player player, String packageId) {
        String resolvedPackage = (packageId == null || packageId.isBlank()) ? rewardPackageService.defaultPackageId() : packageId;
        redeemInputs.put(player.getUniqueId(), resolvedPackage);
        player.closeInventory();
        plugin.getLanguageService().send(player, "referral.redeemPrompt", Map.of("package", packageLabel(resolvedPackage)));
    }

    public boolean hasRedeemInput(Player player) {
        return redeemInputs.containsKey(player.getUniqueId());
    }

    public void handleRedeemInput(Player player, String message) {
        String packageId = redeemInputs.remove(player.getUniqueId());
        if ("cancel".equalsIgnoreCase(message)) {
            plugin.getLanguageService().send(player, "referral.inputCancelled");
            open(player);
            return;
        }
        redeem(player, message, packageId);
    }

    public boolean redeem(Player player, String rawCode, String packageId) {
        if (!enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return false;
        }
        if (!player.hasPermission(PermissionNodes.REFERRAL_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return false;
        }
        if (redemptionsByRedeemer.containsKey(player.getUniqueId())) {
            plugin.getLanguageService().send(player, "referral.alreadyRedeemed");
            return false;
        }
        ReferralCodeEntry ownCode = ensureCode(player);
        String normalized = normalizeCode(rawCode);
        if (normalized.isBlank()) {
            plugin.getLanguageService().send(player, "referral.invalidCode");
            return false;
        }
        if (configBoolean("referral.allowSelfRedeem", false) == false && ownCode.code().equalsIgnoreCase(normalized)) {
            plugin.getLanguageService().send(player, "referral.selfRedeemBlocked");
            return false;
        }
        ReferralCodeEntry referrerCode = codesByCode.get(normalized);
        if (referrerCode == null) {
            plugin.getLanguageService().send(player, "referral.invalidCode");
            return false;
        }
        if (referrerCode.playerUuid().equals(player.getUniqueId())) {
            plugin.getLanguageService().send(player, "referral.selfRedeemBlocked");
            return false;
        }
        int minMinutes = plugin.getConfig().getInt("referral.minPlaytimeMinutes", 0);
        if (minMinutes > 0) {
            int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            int minutes = ticks / 20 / 60;
            if (minutes < minMinutes) {
                plugin.getLanguageService().send(player, "referral.minPlaytime", Map.of("minutes", Integer.toString(minMinutes)));
                return false;
            }
        }
        ReferralRewardPackage rewardPackage = rewardPackageService.packageById(packageId);
        if (rewardPackage == null || !rewardPackage.enabled()) {
            rewardPackage = rewardPackageService.packageById(rewardPackageService.defaultPackageId());
        }
        if (rewardPackage == null) {
            plugin.getLanguageService().send(player, "general.databaseError");
            return false;
        }

        ReferralRedemption redemption = insertRedemption(referrerCode, player, rewardPackage.id());
        if (redemption == null) {
            plugin.getLanguageService().send(player, "general.databaseError");
            return false;
        }
        redemptionsByRedeemer.put(player.getUniqueId(), redemption);
        applyReward(Bukkit.getOfflinePlayer(referrerCode.playerUuid()), referrerCode.playerName(), rewardPackage.referrerReward(), "referrer", rewardPackage.id());
        applyReward(player, player.getName(), rewardPackage.redeemerReward(), "redeemer", rewardPackage.id());
        plugin.getLanguageService().send(player, "referral.redeemed", Map.of(
                "code", referrerCode.code(),
                "referrer", referrerCode.playerName(),
                "package", TextUtil.color(rewardPackage.displayName())
        ));
        Player referrerPlayer = Bukkit.getPlayer(referrerCode.playerUuid());
        if (referrerPlayer != null && referrerPlayer.isOnline()) {
            plugin.getLanguageService().send(referrerPlayer, "referral.referrerRewarded", Map.of(
                    "player", player.getName(),
                    "package", TextUtil.color(rewardPackage.displayName())
            ));
        }
        return true;
    }

    public void sendTop(Player player) {
        List<Map.Entry<UUID, Long>> top = topReferrers();
        plugin.getLanguageService().send(player, "referral.topHeader");
        int index = 1;
        for (Map.Entry<UUID, Long> entry : top) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            plugin.getLanguageService().send(player, "referral.topEntry", Map.of(
                    "place", Integer.toString(index++),
                    "player", name == null ? entry.getKey().toString() : name,
                    "count", Long.toString(entry.getValue())
            ));
        }
        if (top.isEmpty()) {
            plugin.getLanguageService().send(player, "referral.topEmpty");
        }
    }

    public String ownCode(Player player) {
        return ensureCode(player).code();
    }

    private void applyReward(OfflinePlayer player, String playerName, RewardDefinition rewardDefinition, String sourceType, String sourceId) {
        if (rewardDefinition.money() > 0.0D) {
            plugin.getEconomyService().deposit(player, rewardDefinition.money());
        }
        if (player instanceof Player onlinePlayer) {
            for (ItemStack stack : rewardDefinition.items()) {
                Map<Integer, ItemStack> leftover = onlinePlayer.getInventory().addItem(stack.clone());
                if (!leftover.isEmpty()) {
                    for (ItemStack pending : leftover.values()) {
                        pendingRewardService.addItemReward(onlinePlayer, sourceType, sourceId, pending);
                    }
                }
            }
        } else {
            for (ItemStack stack : rewardDefinition.items()) {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) {
                    Map<Integer, ItemStack> leftover = online.getInventory().addItem(stack.clone());
                    for (ItemStack pending : leftover.values()) {
                        pendingRewardService.addItemReward(online, sourceType, sourceId, pending);
                    }
                } else {
                    storeOfflinePendingReward(player, playerName, sourceType, sourceId, stack);
                }
            }
        }
        for (String command : rewardDefinition.commands()) {
            String parsed = PlaceholderUtil.apply(command, Map.of(
                    "player", playerName,
                    "player_uuid", player.getUniqueId().toString(),
                    "source_id", sourceId
            ));
            if (player instanceof Player online) {
                parsed = plugin.getPlaceholderApiHook().apply(online, parsed);
            }
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed.startsWith("/") ? parsed.substring(1) : parsed);
        }
    }

    private void storeOfflinePendingReward(OfflinePlayer player, String playerName, String sourceType, String sourceId, ItemStack itemStack) {
        String table = plugin.getDatabaseService().table("referral_pending_rewards");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (player_uuid, player_name, source_type, source_id, reward_kind, item_data, command, money, created_at, claimed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, playerName);
                statement.setString(3, sourceType);
                statement.setString(4, sourceId);
                statement.setString(5, "ITEM");
                statement.setString(6, plugin.getItemSerializer().serialize(itemStack));
                statement.setString(7, "");
                statement.setDouble(8, 0.0D);
                statement.setLong(9, System.currentTimeMillis());
                statement.setLong(10, 0L);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not store offline referral reward.", exception);
            }
        }
    }

    private ReferralCodeEntry ensureCode(Player player) {
        ReferralCodeEntry current = codesByPlayer.get(player.getUniqueId());
        if (current != null) {
            return current;
        }
        synchronized (plugin.getDatabaseService().lock()) {
            ReferralCodeEntry loaded = loadCode(player.getUniqueId());
            if (loaded != null) {
                cacheCode(loaded);
                return loaded;
            }
            ReferralCodeEntry created = createCode(player);
            cacheCode(created);
            return created;
        }
    }

    private ReferralCodeEntry createCode(Player player) {
        String table = plugin.getDatabaseService().table("referral_codes");
        long now = System.currentTimeMillis();
        String code = nextUniqueCode();
        ReferralCodeEntry entry = new ReferralCodeEntry(player.getUniqueId(), player.getName(), code, now, now);
        try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                "INSERT INTO " + table + " (player_uuid, player_name, code, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, player.getName());
            statement.setString(3, code);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getPluginLogService().error("Could not create referral code.", exception);
        }
        return entry;
    }

    private String nextUniqueCode() {
        String code;
        do {
            code = codeService.generate(plugin.getConfig().getInt("referral.codeLength", 8));
        } while (codesByCode.containsKey(code));
        return code;
    }

    private ReferralCodeEntry loadCode(UUID playerUuid) {
        String table = plugin.getDatabaseService().table("referral_codes");
        try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                "SELECT * FROM " + table + " WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new ReferralCodeEntry(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
                            resultSet.getString("code"),
                            resultSet.getLong("created_at"),
                            resultSet.getLong("updated_at")
                    );
                }
            }
        } catch (SQLException exception) {
            plugin.getPluginLogService().error("Could not load referral code.", exception);
        }
        return null;
    }

    private void loadCodes() {
        String table = plugin.getDatabaseService().table("referral_codes");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement("SELECT * FROM " + table);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    cacheCode(new ReferralCodeEntry(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
                            resultSet.getString("code"),
                            resultSet.getLong("created_at"),
                            resultSet.getLong("updated_at")
                    ));
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not load referral codes.", exception);
            }
        }
    }

    private void loadRedemptions() {
        String table = plugin.getDatabaseService().table("referral_redemptions");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement("SELECT * FROM " + table);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ReferralRedemption redemption = new ReferralRedemption(
                            resultSet.getLong("id"),
                            resultSet.getString("code"),
                            UUID.fromString(resultSet.getString("referrer_uuid")),
                            resultSet.getString("referrer_name"),
                            UUID.fromString(resultSet.getString("redeemer_uuid")),
                            resultSet.getString("redeemer_name"),
                            resultSet.getString("package_id"),
                            resultSet.getLong("created_at")
                    );
                    redemptionsByRedeemer.put(redemption.redeemerUuid(), redemption);
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not load referral redemptions.", exception);
            }
        }
    }

    private ReferralRedemption insertRedemption(ReferralCodeEntry referrerCode, Player redeemer, String packageId) {
        String table = plugin.getDatabaseService().table("referral_redemptions");
        long now = System.currentTimeMillis();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (code, referrer_uuid, referrer_name, redeemer_uuid, redeemer_name, package_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, referrerCode.code());
                statement.setString(2, referrerCode.playerUuid().toString());
                statement.setString(3, referrerCode.playerName());
                statement.setString(4, redeemer.getUniqueId().toString());
                statement.setString(5, redeemer.getName());
                statement.setString(6, packageId);
                statement.setLong(7, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new ReferralRedemption(keys.getLong(1), referrerCode.code(), referrerCode.playerUuid(), referrerCode.playerName(), redeemer.getUniqueId(), redeemer.getName(), packageId, now);
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not insert referral redemption.", exception);
            }
        }
        return null;
    }

    private List<Map.Entry<UUID, Long>> topReferrers() {
        Map<UUID, Long> counts = new HashMap<>();
        for (ReferralRedemption redemption : redemptionsByRedeemer.values()) {
            counts.merge(redemption.referrerUuid(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(Math.max(1, plugin.getConfig().getInt("referral.topLimit", 10)))
                .toList();
    }

    private String packageLabel(String packageId) {
        ReferralRewardPackage rewardPackage = rewardPackageService.packageById(packageId);
        return rewardPackage == null ? packageId : TextUtil.color(rewardPackage.displayName());
    }

    private String normalizeCode(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    }

    private YamlConfiguration loadGui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/referral.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/referral.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private ItemStack codeItem(Player player) {
        YamlConfiguration gui = loadGui(player);
        ConfigurationSection section = gui.getConfigurationSection("items.code");
        ItemStack itemStack = staticItem(player, section, Map.of("code", ownCode(player)));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && section != null) {
            Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
            placeholders.put("code", ownCode(player));
            meta.setLore(section.getStringList("lore").stream().map(line -> TextUtil.color(PlaceholderUtil.apply(line, placeholders))).toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack topItem(Player player, ConfigurationSection section) {
        ItemStack itemStack = staticItem(player, section, Map.of());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && section != null) {
            List<String> lore = new ArrayList<>();
            List<String> base = section.getStringList("lore");
            if (!base.isEmpty()) {
                lore.addAll(base);
            }
            lore.add("");
            List<Map.Entry<UUID, Long>> top = topReferrers();
            int place = 1;
            for (Map.Entry<UUID, Long> entry : top) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                lore.add("&7#" + place++ + " &e" + (name == null ? entry.getKey().toString() : name) + " &7- &f" + entry.getValue());
            }
            meta.setLore(lore.stream().map(TextUtil::color).toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack packageItem(Player player, ReferralRewardPackage rewardPackage) {
        ItemStack itemStack = rewardPackage.icon();
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
            placeholders.put("package", TextUtil.color(rewardPackage.displayName()));
            meta.setDisplayName(TextUtil.color(PlaceholderUtil.apply(rewardPackage.displayName(), placeholders)));
            meta.setLore(rewardPackage.lore().stream().map(line -> TextUtil.color(PlaceholderUtil.apply(line, placeholders))).toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void addStaticButton(Player player, Inventory inventory, YamlConfiguration gui, String path, ItemStack itemStack) {
        ConfigurationSection section = gui.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, itemStack);
        }
    }

    private ItemStack staticItem(Player player, ConfigurationSection section, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(section == null ? "PAPER" : section.getString("material", "PAPER"));
        ItemStack itemStack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && section != null) {
            Map<String, String> merged = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
            merged.putAll(placeholders);
            meta.setDisplayName(TextUtil.color(PlaceholderUtil.apply(section.getString("name", " "), merged)));
            meta.setLore(section.getStringList("lore").stream().map(line -> TextUtil.color(PlaceholderUtil.apply(line, merged))).toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private boolean matchesSlot(ConfigurationSection section, int slot) {
        return section != null && section.getInt("slot", -1) == slot;
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

    private int sanitizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }

    private void cacheCode(ReferralCodeEntry entry) {
        codesByPlayer.put(entry.playerUuid(), entry);
        codesByCode.put(entry.code().toUpperCase(Locale.ROOT), entry);
    }

    private boolean configBoolean(String path, boolean fallback) {
        return plugin.getConfig().getBoolean(path, fallback);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("referral.enabled", false);
    }
}

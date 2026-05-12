package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionResult;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import de.craftplay.shop.servershop.ServerShopCategory;
import de.craftplay.shop.servershop.ServerShopItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FloodgateHook {
    private final CraftplayShopPlugin plugin;

    public FloodgateHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getConfigService().floodgateEnabled()
                && plugin.getServer().getPluginManager().isPluginEnabled("floodgate");
    }

    public boolean isFloodgatePlayer(Player player) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return FloodgateApi.getInstance() != null && FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable throwable) {
            plugin.getPluginLogService().debug("general", "Floodgate player detection failed: " + throwable.getMessage());
            return false;
        }
    }

    public boolean openMainForm(Player player) {
        if (!isFloodgatePlayer(player) || !plugin.getConfigService().floodgateFormsEnabled()) {
            return false;
        }
        YamlConfiguration gui = gui(player);
        if (gui == null) {
            plugin.getPluginLogService().warn("Missing Bedrock GUI file for Floodgate main form.");
            return false;
        }
        List<BedrockButton> buttons = buttons(player, gui.getConfigurationSection("buttons"));
        if (buttons.isEmpty()) {
            return false;
        }
        Map<String, String> placeholders = plugin.getGuiPlaceholderService().placeholders(player);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(apply(gui.getString("title", "&8CraftplayShop"), placeholders))
                .content(apply(gui.getString("content", "&7Waehle einen Bereich."), placeholders));
        for (BedrockButton button : buttons) {
            builder.button(button.text());
        }
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            if (clicked < 0 || clicked >= buttons.size()) {
                return;
            }
            BedrockButton button = buttons.get(clicked);
            plugin.getTaskService().runSync(() -> {
                if (!button.permission().isBlank() && !player.hasPermission(button.permission())) {
                    plugin.getLanguageService().send(player, "general.noPermission");
                    return;
                }
                if (!button.formId().isBlank() && openForm(player, button.formId())) {
                    return;
                }
                plugin.getGuiService().open(player, button.guiId());
            });
        });
        try {
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable throwable) {
            plugin.getPluginLogService().error("Could not send Floodgate form.", throwable);
            return false;
        }
    }

    public boolean openForm(Player player, String formId) {
        if (!isFloodgatePlayer(player) || !plugin.getConfigService().floodgateFormsEnabled()) {
            return false;
        }
        return switch (formId.toLowerCase(java.util.Locale.ROOT)) {
            case "main", "home" -> openMainForm(player);
            case "servershop" -> openServerShopCategoriesForm(player);
            case "playershop" -> openPlayerShopForm(player);
            case "auctionhouse", "ah" -> openAuctionHouseForm(player);
            case "autosellchest", "asc" -> openAutoSellChestForm(player);
            case "rankshop" -> openInventoryBackedForm(player, PermissionNodes.RANK_SHOP_USE, () -> plugin.getRankShopService().open(player));
            case "permissionshop" -> openInventoryBackedForm(player, PermissionNodes.PERMISSION_SHOP_USE, () -> plugin.getPermissionProductService().open(player));
            case "referral" -> openInventoryBackedForm(player, PermissionNodes.REFERRAL_USE, () -> plugin.getReferralService().open(player));
            default -> false;
        };
    }

    private boolean openServerShopCategoriesForm(Player player) {
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        if (!plugin.getServerShopService().enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return true;
        }
        List<ServerShopCategory> categories = plugin.getServerShopRegistry().categories().stream()
                .filter(ServerShopCategory::enabled)
                .toList();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(message(player, "floodgate.serverShopTitle", "&8ServerShop"))
                .content(message(player, "floodgate.serverShopContent", "&7Waehle eine Kategorie."));
        for (ServerShopCategory category : categories) {
            builder.button(TextUtil.color(category.displayName()) + "\n" + category.items().size() + " Items");
        }
        builder.button(message(player, "floodgate.back", "&eZurueck"));
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            plugin.getTaskService().runSync(() -> {
                if (clicked == categories.size()) {
                    openMainForm(player);
                    return;
                }
                if (clicked >= 0 && clicked < categories.size()) {
                    openServerShopItemsForm(player, categories.get(clicked).id());
                }
            });
        });
        return send(player, builder);
    }

    private boolean openServerShopItemsForm(Player player, String categoryId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null || !category.enabled()) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return true;
        }
        List<ServerShopItem> items = category.items().stream()
                .filter(item -> item.buyEnabled() || item.sellEnabled())
                .toList();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(TextUtil.color(category.displayName()))
                .content(message(player, "floodgate.serverShopItemsContent", "&7Waehle ein Item."));
        for (ServerShopItem item : items) {
            builder.button(itemButton(player, item));
        }
        builder.button(message(player, "floodgate.back", "&eZurueck"));
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            plugin.getTaskService().runSync(() -> {
                if (clicked == items.size()) {
                    openServerShopCategoriesForm(player);
                    return;
                }
                if (clicked >= 0 && clicked < items.size()) {
                    openServerShopItemActionForm(player, categoryId, items.get(clicked).id());
                }
            });
        });
        return send(player, builder);
    }

    private boolean openServerShopItemActionForm(Player player, String categoryId, String itemId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        ServerShopItem item = category == null ? null : category.item(itemId);
        if (item == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return true;
        }
        List<ServerShopActionButton> buttons = new ArrayList<>();
        if (item.buyEnabled()) {
            buttons.add(new ServerShopActionButton(message(player, "floodgate.buyOne", "&a1 kaufen"), false, 1));
            buttons.add(new ServerShopActionButton(message(player, "floodgate.buyStack", "&aStack kaufen"), false, Math.max(1, item.material().getMaxStackSize())));
        }
        if (item.sellEnabled()) {
            buttons.add(new ServerShopActionButton(message(player, "floodgate.sellOne", "&c1 verkaufen"), true, 1));
            buttons.add(new ServerShopActionButton(message(player, "floodgate.sellStack", "&cStack verkaufen"), true, Math.max(1, item.material().getMaxStackSize())));
        }
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(TextUtil.color(item.displayName()))
                .content(itemDetails(player, item));
        for (ServerShopActionButton button : buttons) {
            builder.button(button.label());
        }
        builder.button(message(player, "floodgate.back", "&eZurueck"));
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            plugin.getTaskService().runSync(() -> {
                if (clicked == buttons.size()) {
                    openServerShopItemsForm(player, categoryId);
                    return;
                }
                if (clicked < 0 || clicked >= buttons.size()) {
                    return;
                }
                ServerShopActionButton button = buttons.get(clicked);
                TransactionResult result = button.sell()
                        ? plugin.getServerShopTransactionService().sell(player, item, button.amount(), TransactionType.SERVER_SELL)
                        : plugin.getServerShopTransactionService().buy(player, item, button.amount());
                sendTransactionMessage(player, item, button.amount(), result);
                openServerShopItemActionForm(player, categoryId, itemId);
            });
        });
        return send(player, builder);
    }

    private boolean openPlayerShopForm(Player player) {
        if (!player.hasPermission(PermissionNodes.PLAYER_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        List<BedrockButton> buttons = List.of(
                new BedrockButton("playershop_all", message(player, "floodgate.playerShopAll", "&aAlle Spielershops"), "", PermissionNodes.PLAYER_SHOP_USE),
                new BedrockButton("playershop_own", message(player, "floodgate.playerShopOwn", "&eMeine Spielershops"), "", PermissionNodes.PLAYER_SHOP_USE),
                new BedrockButton("playershop_nearby", message(player, "floodgate.playerShopNearby", "&6Shops in der Naehe"), "", PermissionNodes.PLAYER_SHOP_USE),
                new BedrockButton("playershop_search", message(player, "floodgate.playerShopSearch", "&bSpielershop-Suche"), "", PermissionNodes.PLAYER_SHOP_USE),
                new BedrockButton("main", message(player, "floodgate.back", "&eZurueck"), "", "")
        );
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(message(player, "floodgate.playerShopTitle", "&8Spielershops"))
                .content(message(player, "floodgate.playerShopContent", "&7Waehle eine Aktion."));
        for (BedrockButton button : buttons) {
            builder.button(button.text());
        }
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            plugin.getTaskService().runSync(() -> {
                if (clicked < 0 || clicked >= buttons.size()) {
                    return;
                }
                switch (buttons.get(clicked).guiId()) {
                    case "playershop_all" -> plugin.getPlayerShopService().openAll(player);
                    case "playershop_own" -> plugin.getPlayerShopService().openOwn(player);
                    case "playershop_nearby" -> plugin.getPlayerShopService().openNearby(player);
                    case "playershop_search" -> plugin.getPlayerShopService().requestSearch(player);
                    default -> openMainForm(player);
                }
            });
        });
        return send(player, builder);
    }

    private boolean openAuctionHouseForm(Player player) {
        if (!player.hasPermission(PermissionNodes.AUCTION_HOUSE_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        List<BedrockButton> buttons = List.of(
                new BedrockButton("ah_browse", message(player, "floodgate.auctionHouseBrowse", "&aAngebote"), "", PermissionNodes.AUCTION_HOUSE_USE),
                new BedrockButton("ah_search", message(player, "floodgate.auctionHouseSearch", "&bSuche"), "", PermissionNodes.AUCTION_HOUSE_USE),
                new BedrockButton("ah_mine", message(player, "floodgate.auctionHouseMine", "&eMeine Angebote"), "", PermissionNodes.AUCTION_HOUSE_USE),
                new BedrockButton("ah_claims", message(player, "floodgate.auctionHouseClaims", "&6Abholen"), "", PermissionNodes.AUCTION_HOUSE_USE),
                new BedrockButton("main", message(player, "floodgate.back", "&eZurueck"), "", "")
        );
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(message(player, "floodgate.auctionHouseTitle", "&8Auktionshaus"))
                .content(message(player, "floodgate.auctionHouseContent", "&7Waehle eine Aktion."));
        for (BedrockButton button : buttons) {
            builder.button(button.text());
        }
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            plugin.getTaskService().runSync(() -> {
                if (clicked < 0 || clicked >= buttons.size()) {
                    return;
                }
                switch (buttons.get(clicked).guiId()) {
                    case "ah_browse" -> plugin.getAuctionHouseService().openBrowse(player, 0, "");
                    case "ah_search" -> plugin.getAuctionHouseService().requestSearch(player);
                    case "ah_mine" -> plugin.getAuctionHouseService().openMine(player, 0);
                    case "ah_claims" -> plugin.getAuctionHouseService().openClaims(player, 0);
                    default -> openMainForm(player);
                }
            });
        });
        return send(player, builder);
    }

    private boolean openAutoSellChestForm(Player player) {
        if (!player.hasPermission(PermissionNodes.AUTOSELL_CHEST_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        List<BedrockButton> buttons = List.of(
                new BedrockButton("asc_list", message(player, "floodgate.autoSellChestList", "&aMeine Verkaufskisten"), "", PermissionNodes.AUTOSELL_CHEST_USE),
                new BedrockButton("main", message(player, "floodgate.back", "&eZurueck"), "", "")
        );
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(message(player, "floodgate.autoSellChestTitle", "&8AutoSellChest"))
                .content(message(player, "floodgate.autoSellChestContent", "&7Waehle eine Aktion."));
        for (BedrockButton button : buttons) {
            builder.button(button.text());
        }
        builder.validResultHandler((form, response) -> {
            int clicked = response.getClickedButtonId();
            plugin.getTaskService().runSync(() -> {
                if (clicked == 0) {
                    plugin.getAutoSellChestService().gui().openList(player);
                    return;
                }
                openMainForm(player);
            });
        });
        return send(player, builder);
    }

    private boolean openInventoryBackedForm(Player player, String permission, Runnable openAction) {
        if (!player.hasPermission(permission)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        openAction.run();
        return true;
    }

    private List<BedrockButton> buttons(Player player, ConfigurationSection section) {
        List<BedrockButton> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        Map<String, String> placeholders = plugin.getGuiPlaceholderService().placeholders(player);
        for (String key : section.getKeys(false)) {
            ConfigurationSection button = section.getConfigurationSection(key);
            if (button == null || !button.getBoolean("enabled", true)) {
                continue;
            }
            String guiId = button.getString("openGui", "");
            String formId = button.getString("openForm", "");
            if (guiId.isBlank() && formId.isBlank()) {
                continue;
            }
            String label = apply(button.getString("label", key), placeholders);
            List<String> lore = button.getStringList("lore").stream().map(line -> apply(line, placeholders)).toList();
            String text = lore.isEmpty() ? label : label + "\n" + String.join("\n", lore);
            result.add(new BedrockButton(guiId, text, formId, button.getString("permission", "")));
        }
        return result;
    }

    private String itemButton(Player player, ServerShopItem item) {
        String buy = item.buyEnabled()
                ? plugin.getEconomyService().format(plugin.getServerShopPricingService().buyUnitPrice(item))
                : message(player, "floodgate.disabled", "&cdeaktiviert");
        String sell = item.sellEnabled()
                ? plugin.getEconomyService().format(plugin.getServerShopPricingService().sellUnitPrice(item))
                : message(player, "floodgate.disabled", "&cdeaktiviert");
        return TextUtil.color(item.displayName()) + "\n" + message(player, "floodgate.itemPriceLine", "&7Kauf: %buy% &8| &7Verkauf: %sell%")
                .replace("%buy%", buy)
                .replace("%sell%", sell);
    }

    private String itemDetails(Player player, ServerShopItem item) {
        return message(player, "floodgate.itemDetails", "&7Kaufpreis: &e%buy%\n&7Verkaufspreis: &e%sell%\n&7Material: &f%material%")
                .replace("%buy%", item.buyEnabled() ? plugin.getEconomyService().format(plugin.getServerShopPricingService().buyUnitPrice(item)) : message(player, "floodgate.disabled", "&cdeaktiviert"))
                .replace("%sell%", item.sellEnabled() ? plugin.getEconomyService().format(plugin.getServerShopPricingService().sellUnitPrice(item)) : message(player, "floodgate.disabled", "&cdeaktiviert"))
                .replace("%material%", item.material().name());
    }

    private void sendTransactionMessage(Player player, ServerShopItem item, int amount, TransactionResult result) {
        if (!result.success()) {
            plugin.getLanguageService().send(player, result.messageKey(), result.placeholders());
            return;
        }
        plugin.getLanguageService().send(player, result.messageKey(), Map.of(
                "amount", Integer.toString(amount),
                "item", TextUtil.color(item.displayName()),
                "price", plugin.getEconomyService().format(result.totalPrice())
        ));
    }

    private boolean send(Player player, SimpleForm.Builder builder) {
        try {
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable throwable) {
            plugin.getPluginLogService().error("Could not send Floodgate form.", throwable);
            return false;
        }
    }

    private String message(Player player, String key, String fallback) {
        String message = plugin.getLanguageService().get(player, key);
        if (message.contains("Missing language key:")) {
            return TextUtil.color(fallback);
        }
        return message;
    }

    private YamlConfiguration gui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File exact = new File(plugin.getDataFolder(), "gui/" + language + "/bedrock_main.yml");
        if (exact.exists()) {
            return YamlConfiguration.loadConfiguration(exact);
        }
        File fallback = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/bedrock_main.yml");
        if (fallback.exists()) {
            return YamlConfiguration.loadConfiguration(fallback);
        }
        return null;
    }

    private String apply(String text, Map<String, String> placeholders) {
        return TextUtil.color(PlaceholderUtil.apply(text == null ? "" : text, placeholders));
    }

    private record BedrockButton(String guiId, String text, String formId, String permission) {
    }

    private record ServerShopActionButton(String label, boolean sell, int amount) {
    }
}

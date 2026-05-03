package de.craftplay.shop.servershop.admin;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import de.craftplay.shop.servershop.ServerShopCategory;
import de.craftplay.shop.servershop.ServerShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ServerShopAdminEditor {
    private static final int MATERIALS_PER_PAGE = 45;
    private static final String CREATE_CATEGORY_TARGET = "__create_category__";
    private static final String CREATE_ITEM_TARGET = "__create_item__";
    private static final String CATEGORY_EDITOR_TARGET = "__category_editor__";
    private static final List<Integer> EDITABLE_GRID_SLOTS = List.of(
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    );

    private final CraftplayShopPlugin plugin;
    private final List<Material> selectableMaterials;
    private final Map<UUID, MoveSelection> moveSelections = new HashMap<>();
    private final Map<UUID, TextEditSession> textEditSessions = new HashMap<>();

    public ServerShopAdminEditor(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        this.selectableMaterials = java.util.Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(material -> !material.isAir())
                .sorted(Comparator.comparing(Material::name))
                .toList();
    }

    public void openCategories(Player player) {
        YamlConfiguration gui = gui(player);
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.CATEGORIES, "", "", keys);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, "categories", Map.of()));
        holder.setInventory(inventory);
        fill(inventory);

        for (ServerShopCategory category : plugin.getServerShopRegistry().categories()) {
            int slot = clampSlot(category.slot(), inventory.getSize());
            inventory.setItem(slot, item(category.icon(), category.displayName(), lore(gui, "items.category.lore", Map.of(
                    "category_id", category.id(),
                    "item_count", Integer.toString(category.items().size())
            ))));
            keys.put(slot, category.id());
        }
        putConfigured(inventory, keys, gui, "slots.categories.createCategory", 53, "createCategory", "create_category", Map.of());
        putConfigured(inventory, keys, gui, "slots.categories.back", 49, "backAdmin", "back", Map.of());
        player.openInventory(inventory);
    }

    public void openItems(Player player, String categoryId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        YamlConfiguration gui = gui(player);
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.ITEMS, categoryId, "", keys);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, "items", Map.of("category", TextUtil.stripColor(category.displayName()))));
        holder.setInventory(inventory);
        fill(inventory);

        for (ServerShopItem shopItem : category.items()) {
            int slot = clampSlot(shopItem.slot(), inventory.getSize());
            inventory.setItem(slot, editorItem(gui, shopItem));
            keys.put(slot, shopItem.id());
        }
        putConfigured(inventory, keys, gui, "slots.items.createItem", 53, "createItem", "create_item", Map.of());
        putConfigured(inventory, keys, gui, "slots.items.back", 49, "backCategories", "back", Map.of());
        player.openInventory(inventory);
    }

    public void openCategoryEditor(Player player, String categoryId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        YamlConfiguration gui = gui(player);
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.CATEGORY_EDITOR, categoryId, "", keys);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, "categoryEditor", Map.of("category", TextUtil.stripColor(category.displayName()))));
        holder.setInventory(inventory);
        fill(inventory);

        int previewSlot = slot(gui, "slots.categoryEditor.preview", 4);
        inventory.setItem(previewSlot, categoryEditorItem(gui, category));
        keys.put(previewSlot, "category_preview");
        putConfigured(inventory, keys, gui, "slots.categoryEditor.editName", 20, "editName", "edit_name", Map.of());
        putConfigured(inventory, keys, gui, "slots.categoryEditor.editLore", 22, "editLore", "edit_lore", Map.of());
        putConfigured(inventory, keys, gui, "slots.categoryEditor.openItems", 24, "openItems", "open_items", Map.of());
        putConfigured(inventory, keys, gui, "slots.categoryEditor.materialPicker", 42, "materialPicker", "material_picker", Map.of());
        putConfigured(inventory, keys, gui, "slots.categoryEditor.back", 45, "backCategories", "back", Map.of());
        player.openInventory(inventory);
    }

    public void openItemEditor(Player player, String categoryId, String itemId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        ServerShopItem shopItem = category.item(itemId);
        if (shopItem == null) {
            plugin.getLanguageService().send(player, "gui.missingItem", Map.of("item", itemId));
            return;
        }

        YamlConfiguration gui = gui(player);
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.ITEM_EDITOR, categoryId, itemId, keys);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, "itemEditor", Map.of()));
        holder.setInventory(inventory);
        fill(inventory);

        int previewSlot = slot(gui, "slots.itemEditor.preview", 4);
        inventory.setItem(previewSlot, editorItem(gui, shopItem));
        keys.put(previewSlot, "item_preview");
        int toggleBuySlot = slot(gui, "slots.itemEditor.toggleBuy", 10);
        inventory.setItem(toggleBuySlot, toggleItem(gui, "toggleBuy", shopItem.buyEnabled()));
        keys.put(toggleBuySlot, "toggle_buy");
        priceButton(inventory, keys, gui, "slots.itemEditor.buyPrice", 13, "buyPrice", "buy_price", shopItem.buyPrice());

        int toggleSellSlot = slot(gui, "slots.itemEditor.toggleSell", 28);
        inventory.setItem(toggleSellSlot, toggleItem(gui, "toggleSell", shopItem.sellEnabled()));
        keys.put(toggleSellSlot, "toggle_sell");
        priceButton(inventory, keys, gui, "slots.itemEditor.sellPrice", 31, "sellPrice", "sell_price", shopItem.sellPrice());

        putConfigured(inventory, keys, gui, "slots.itemEditor.editName", 20, "editName", "edit_name", Map.of());
        putConfigured(inventory, keys, gui, "slots.itemEditor.editLore", 22, "editLore", "edit_lore", Map.of());
        putConfigured(inventory, keys, gui, "slots.itemEditor.setFromHand", 40, "setFromHand", "set_from_hand", Map.of());
        putConfigured(inventory, keys, gui, "slots.itemEditor.materialPicker", 42, "materialPicker", "material_picker", Map.of());
        putConfigured(inventory, keys, gui, "slots.itemEditor.back", 45, "backItems", "back", Map.of());
        player.openInventory(inventory);
    }

    public void handleClick(Player player, ServerShopAdminHolder holder, InventoryClickEvent event) {
        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize() && isUsableSourceItem(event.getCursor())) {
            handleSourceItemOnSlot(player, holder, event.getRawSlot(), event.getCursor());
            return;
        }
        String key = holder.keyAt(event.getRawSlot());
        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()
                && event.isShiftClick() && isMovableKey(holder, key)) {
            selectMove(player, holder, key);
            return;
        }
        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()
                && !isControlKey(key) && handleMoveTarget(player, holder, event.getRawSlot())) {
            return;
        }
        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize() && isControlKey(key)) {
            moveSelections.remove(player.getUniqueId());
        }
        if (key == null) {
            return;
        }
        switch (holder.view()) {
            case CATEGORIES -> handleCategoryClick(player, key, event.isRightClick());
            case ITEMS -> handleItemListClick(player, holder.categoryId(), key, event.isRightClick());
            case CATEGORY_EDITOR -> handleCategoryEditorClick(player, holder.categoryId(), key);
            case ITEM_EDITOR -> handleItemEditorClick(player, holder.categoryId(), holder.itemId(), key, event);
            case MATERIAL_PICKER -> handleMaterialPickerClick(player, holder, key);
        }
    }

    public void handleDrag(Player player, ServerShopAdminHolder holder, InventoryDragEvent event) {
        ItemStack dragged = event.getOldCursor();
        if (!isUsableSourceItem(dragged)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < event.getInventory().getSize()) {
                handleSourceItemOnSlot(player, holder, rawSlot, dragged);
                return;
            }
        }
    }

    private void handleCategoryClick(Player player, String key, boolean rightClick) {
        if ("back".equals(key)) {
            plugin.getGuiService().open(player, "admin");
            return;
        }
        if ("create_category".equals(key)) {
            openMaterialPicker(player, CREATE_CATEGORY_TARGET, "", 0);
            return;
        }
        if (rightClick) {
            openMaterialPicker(player, key, "", 0);
            return;
        }
        openCategoryEditor(player, key);
    }

    private void handleItemListClick(Player player, String categoryId, String key, boolean rightClick) {
        if ("back".equals(key)) {
            openCategories(player);
            return;
        }
        if ("create_item".equals(key)) {
            openMaterialPicker(player, categoryId, CREATE_ITEM_TARGET, 0);
            return;
        }
        if (rightClick) {
            openMaterialPicker(player, categoryId, key, 0);
            return;
        }
        openItemEditor(player, categoryId, key);
    }

    private void handleCategoryEditorClick(Player player, String categoryId, String key) {
        if ("back".equals(key)) {
            openCategories(player);
            return;
        }
        if ("category_preview".equals(key) || "material_picker".equals(key)) {
            openMaterialPicker(player, categoryId, CATEGORY_EDITOR_TARGET, 0);
            return;
        }
        if ("edit_name".equals(key)) {
            startTextEdit(player, TextEditType.CATEGORY_NAME, categoryId, "");
            return;
        }
        if ("edit_lore".equals(key)) {
            startTextEdit(player, TextEditType.CATEGORY_LORE, categoryId, "");
            return;
        }
        if ("open_items".equals(key)) {
            openItems(player, categoryId);
        }
    }

    private void handleItemEditorClick(Player player, String categoryId, String itemId, String key, InventoryClickEvent event) {
        if ("back".equals(key)) {
            openItems(player, categoryId);
            return;
        }
        if ("toggle_buy".equals(key)) {
            toggle(categoryId, itemId, "buyEnabled");
            saved(player, categoryId, itemId);
            return;
        }
        if ("toggle_sell".equals(key)) {
            toggle(categoryId, itemId, "sellEnabled");
            saved(player, categoryId, itemId);
            return;
        }
        if ("set_from_hand".equals(key)) {
            setFromHand(player, categoryId, itemId);
            openItemEditor(player, categoryId, itemId);
            return;
        }
        if ("edit_name".equals(key)) {
            startTextEdit(player, TextEditType.ITEM_NAME, categoryId, itemId);
            return;
        }
        if ("edit_lore".equals(key)) {
            startTextEdit(player, TextEditType.ITEM_LORE, categoryId, itemId);
            return;
        }
        if ("material_picker".equals(key) || "item_preview".equals(key)) {
            openMaterialPicker(player, categoryId, itemId, 0);
            return;
        }
        if ("buy_price".equals(key)) {
            adjustPrice(categoryId, itemId, "buyPrice", priceDelta(event));
            saved(player, categoryId, itemId);
            return;
        }
        if ("sell_price".equals(key)) {
            adjustPrice(categoryId, itemId, "sellPrice", priceDelta(event));
            saved(player, categoryId, itemId);
        }
    }

    private void handleMaterialPickerClick(Player player, ServerShopAdminHolder holder, String key) {
        if ("back".equals(key)) {
            if (isCreateCategoryTarget(holder)) {
                openCategories(player);
            } else if (isCreateItemTarget(holder)) {
                openItems(player, holder.categoryId());
            } else if (isCategoryEditorTarget(holder)) {
                openCategoryEditor(player, holder.categoryId());
            } else if (holder.itemId().isBlank()) {
                openItems(player, holder.categoryId());
            } else {
                openItemEditor(player, holder.categoryId(), holder.itemId());
            }
            return;
        }
        if ("previous".equals(key)) {
            openMaterialPicker(player, holder.categoryId(), holder.itemId(), Math.max(0, holder.page() - 1));
            return;
        }
        if ("next".equals(key)) {
            openMaterialPicker(player, holder.categoryId(), holder.itemId(), holder.page() + 1);
            return;
        }
        if (!key.startsWith("material:")) {
            return;
        }
        Material material = Material.matchMaterial(key.substring("material:".length()));
        if (material == null) {
            return;
        }
        if (isCreateCategoryTarget(holder)) {
            createCategoryFromMaterial(nextFreeCategorySlot(), material);
            plugin.getLanguageService().send(player, "adminShop.categoryCreated");
            openCategories(player);
            return;
        }
        if (isCreateItemTarget(holder)) {
            ServerShopCategory category = plugin.getServerShopRegistry().category(holder.categoryId());
            if (category == null) {
                plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
                return;
            }
            createItemFromMaterial(holder.categoryId(), nextFreeItemSlot(category), material);
            plugin.getLanguageService().send(player, "adminShop.itemCreated");
            openItems(player, holder.categoryId());
            return;
        }
        if (isCategoryEditorTarget(holder)) {
            setCategoryIcon(holder.categoryId(), material);
            plugin.getLanguageService().send(player, "adminShop.categoryUpdated");
            openCategoryEditor(player, holder.categoryId());
            return;
        }
        if (holder.itemId().isBlank()) {
            setCategoryIcon(holder.categoryId(), material);
            plugin.getLanguageService().send(player, "adminShop.categoryUpdated");
            openCategoryEditor(player, holder.categoryId());
            return;
        }
        setItemMaterial(holder.categoryId(), holder.itemId(), material, null);
        plugin.getLanguageService().send(player, "adminShop.itemUpdated");
        openItemEditor(player, holder.categoryId(), holder.itemId());
    }

    private void handleSourceItemOnSlot(Player player, ServerShopAdminHolder holder, int slot, ItemStack source) {
        if (isControlKey(holder.keyAt(slot))) {
            return;
        }
        switch (holder.view()) {
            case CATEGORIES -> placeCategorySource(player, slot, source);
            case ITEMS -> placeItemSource(player, holder.categoryId(), slot, source);
            case CATEGORY_EDITOR -> {
                if (slot == slot(gui(player), "slots.categoryEditor.preview", 4)) {
                    setCategoryIcon(holder.categoryId(), source.getType());
                    plugin.getLanguageService().send(player, "adminShop.categoryUpdated");
                    openCategoryEditor(player, holder.categoryId());
                }
            }
            case ITEM_EDITOR -> {
                if (slot == slot(gui(player), "slots.itemEditor.preview", 4)) {
                    setItemFromStack(holder.categoryId(), holder.itemId(), source);
                    plugin.getLanguageService().send(player, "adminShop.itemUpdated");
                    openItemEditor(player, holder.categoryId(), holder.itemId());
                }
            }
            default -> {
            }
        }
    }

    private void placeCategorySource(Player player, int slot, ItemStack source) {
        ServerShopCategory existing = categoryAtSlot(slot);
        if (existing == null) {
            createCategoryFromStack(slot, source);
            plugin.getLanguageService().send(player, "adminShop.categoryCreated");
            openCategories(player);
            return;
        }
        setCategoryIcon(existing.id(), source.getType());
        plugin.getLanguageService().send(player, "adminShop.categoryUpdated");
        openCategories(player);
    }

    private void placeItemSource(Player player, String categoryId, int slot, ItemStack source) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        ServerShopItem existing = itemAtSlot(category, slot);
        if (existing == null) {
            createItemFromStack(categoryId, slot, source);
            plugin.getLanguageService().send(player, "adminShop.itemCreated");
            openItems(player, categoryId);
            return;
        }
        setItemFromStack(categoryId, existing.id(), source);
        plugin.getLanguageService().send(player, "adminShop.itemUpdated");
        openItems(player, categoryId);
    }

    private void selectMove(Player player, ServerShopAdminHolder holder, String key) {
        moveSelections.put(player.getUniqueId(), new MoveSelection(holder.view(), holder.categoryId(), key));
        plugin.getLanguageService().send(player, "adminShop.moveSelected", Map.of("entry", key));
    }

    private boolean handleMoveTarget(Player player, ServerShopAdminHolder holder, int targetSlot) {
        MoveSelection selection = moveSelections.get(player.getUniqueId());
        if (selection == null || selection.view() != holder.view()) {
            return false;
        }
        if (selection.view() == ServerShopAdminView.ITEMS && !selection.categoryId().equals(holder.categoryId())) {
            return false;
        }
        moveSelections.remove(player.getUniqueId());
        if (selection.view() == ServerShopAdminView.CATEGORIES) {
            moveCategoryToSlot(selection.entryId(), targetSlot);
            plugin.getLanguageService().send(player, "adminShop.moveDone");
            openCategories(player);
            return true;
        }
        if (selection.view() == ServerShopAdminView.ITEMS) {
            moveItemToSlot(selection.categoryId(), selection.entryId(), targetSlot);
            plugin.getLanguageService().send(player, "adminShop.moveDone");
            openItems(player, selection.categoryId());
            return true;
        }
        return false;
    }

    private void moveCategoryToSlot(String categoryId, int targetSlot) {
        ServerShopCategory selected = plugin.getServerShopRegistry().category(categoryId);
        if (selected == null) {
            return;
        }
        ServerShopCategory target = categoryAtSlot(targetSlot);
        YamlConfiguration configuration = loadShopFile();
        if (target != null && !target.id().equals(categoryId)) {
            configuration.set("categories." + target.id() + ".slot", selected.slot());
        }
        configuration.set("categories." + categoryId + ".slot", targetSlot);
        save(configuration);
    }

    private void moveItemToSlot(String categoryId, String itemId, int targetSlot) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            return;
        }
        ServerShopItem selected = category.item(itemId);
        if (selected == null) {
            return;
        }
        ServerShopItem target = itemAtSlot(category, targetSlot);
        YamlConfiguration configuration = loadShopFile();
        if (target != null && !target.id().equals(itemId)) {
            configuration.set(itemPath(categoryId, target.id()) + ".slot", selected.slot());
        }
        configuration.set(itemPath(categoryId, itemId) + ".slot", targetSlot);
        save(configuration);
    }

    public boolean hasTextInput(Player player) {
        return textEditSessions.containsKey(player.getUniqueId());
    }

    public void handleTextInput(Player player, String message) {
        TextEditSession session = textEditSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if ("cancel".equalsIgnoreCase(message)) {
            plugin.getLanguageService().send(player, "adminShop.inputCancelled");
            reopenAfterTextEdit(player, session);
            return;
        }
        switch (session.type()) {
            case CATEGORY_NAME -> {
                setCategoryDisplayName(session.categoryId(), message);
                plugin.getLanguageService().send(player, "adminShop.categoryUpdated");
            }
            case CATEGORY_LORE -> {
                setCategoryLore(session.categoryId(), parseLore(message));
                plugin.getLanguageService().send(player, "adminShop.categoryUpdated");
            }
            case ITEM_NAME -> {
                setItemDisplayName(session.categoryId(), session.itemId(), message);
                plugin.getLanguageService().send(player, "adminShop.itemUpdated");
            }
            case ITEM_LORE -> {
                setItemLore(session.categoryId(), session.itemId(), parseLore(message));
                plugin.getLanguageService().send(player, "adminShop.itemUpdated");
            }
        }
        reopenAfterTextEdit(player, session);
    }

    private void startTextEdit(Player player, TextEditType type, String categoryId, String itemId) {
        textEditSessions.put(player.getUniqueId(), new TextEditSession(type, categoryId, itemId));
        player.closeInventory();
        String key = type == TextEditType.CATEGORY_LORE || type == TextEditType.ITEM_LORE ? "adminShop.inputLore" : "adminShop.inputName";
        plugin.getLanguageService().send(player, key);
    }

    private void reopenAfterTextEdit(Player player, TextEditSession session) {
        if (session.type() == TextEditType.CATEGORY_NAME || session.type() == TextEditType.CATEGORY_LORE) {
            openCategoryEditor(player, session.categoryId());
            return;
        }
        openItemEditor(player, session.categoryId(), session.itemId());
    }

    private List<String> parseLore(String message) {
        if ("clear".equalsIgnoreCase(message) || "-".equals(message)) {
            return List.of();
        }
        return java.util.Arrays.stream(message.split("\\|"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private void setCategoryDisplayName(String categoryId, String displayName) {
        YamlConfiguration configuration = loadShopFile();
        configuration.set("categories." + categoryId + ".displayName", displayName);
        save(configuration);
    }

    private void setCategoryLore(String categoryId, List<String> lore) {
        YamlConfiguration configuration = loadShopFile();
        configuration.set("categories." + categoryId + ".lore", lore);
        save(configuration);
    }

    private void setItemDisplayName(String categoryId, String itemId, String displayName) {
        YamlConfiguration configuration = loadShopFile();
        configuration.set(itemPath(categoryId, itemId) + ".displayName", displayName);
        save(configuration);
    }

    private void setItemLore(String categoryId, String itemId, List<String> lore) {
        YamlConfiguration configuration = loadShopFile();
        configuration.set(itemPath(categoryId, itemId) + ".lore", lore);
        save(configuration);
    }

    private ServerShopCategory categoryAtSlot(int slot) {
        for (ServerShopCategory category : plugin.getServerShopRegistry().categories()) {
            if (category.slot() == slot) {
                return category;
            }
        }
        return null;
    }

    private ServerShopItem itemAtSlot(ServerShopCategory category, int slot) {
        for (ServerShopItem item : category.items()) {
            if (item.slot() == slot) {
                return item;
            }
        }
        return null;
    }

    private void createCategoryFromStack(int slot, ItemStack source) {
        YamlConfiguration configuration = loadShopFile();
        String id = uniqueCategoryId(configuration, source.getType());
        String path = "categories." + id;
        configuration.set(path + ".displayName", displayName(source));
        configuration.set(path + ".lore", List.of());
        configuration.set(path + ".icon", source.getType().name());
        configuration.set(path + ".slot", slot);
        configuration.createSection(path + ".items");
        save(configuration);
    }

    private void createCategoryFromMaterial(int slot, Material material) {
        YamlConfiguration configuration = loadShopFile();
        String id = uniqueCategoryId(configuration, material);
        String path = "categories." + id;
        configuration.set(path + ".displayName", "&f" + formatMaterialName(material));
        configuration.set(path + ".lore", List.of());
        configuration.set(path + ".icon", material.name());
        configuration.set(path + ".slot", slot);
        configuration.createSection(path + ".items");
        save(configuration);
    }

    private void createItemFromStack(String categoryId, int slot, ItemStack source) {
        YamlConfiguration configuration = loadShopFile();
        String id = uniqueItemId(configuration, categoryId, source.getType());
        String path = itemPath(categoryId, id);
        configuration.set(path + ".material", source.getType().name());
        configuration.set(path + ".displayName", displayName(source));
        configuration.set(path + ".lore", List.of());
        configuration.set(path + ".buyPrice", 0.0D);
        configuration.set(path + ".sellPrice", 0.0D);
        configuration.set(path + ".buyEnabled", false);
        configuration.set(path + ".sellEnabled", false);
        configuration.set(path + ".slot", slot);
        save(configuration);
    }

    private void createItemFromMaterial(String categoryId, int slot, Material material) {
        YamlConfiguration configuration = loadShopFile();
        String id = uniqueItemId(configuration, categoryId, material);
        String path = itemPath(categoryId, id);
        configuration.set(path + ".material", material.name());
        configuration.set(path + ".displayName", "&f" + formatMaterialName(material));
        configuration.set(path + ".lore", List.of());
        configuration.set(path + ".buyPrice", 0.0D);
        configuration.set(path + ".sellPrice", 0.0D);
        configuration.set(path + ".buyEnabled", false);
        configuration.set(path + ".sellEnabled", false);
        configuration.set(path + ".slot", slot);
        save(configuration);
    }

    private void setCategoryIcon(String categoryId, Material material) {
        YamlConfiguration configuration = loadShopFile();
        configuration.set("categories." + categoryId + ".icon", material.name());
        save(configuration);
    }

    private void setItemFromStack(String categoryId, String itemId, ItemStack source) {
        setItemMaterial(categoryId, itemId, source.getType(), displayName(source));
    }

    private void setItemMaterial(String categoryId, String itemId, Material material, String displayName) {
        YamlConfiguration configuration = loadShopFile();
        String path = itemPath(categoryId, itemId);
        configuration.set(path + ".material", material.name());
        if (displayName != null) {
            configuration.set(path + ".displayName", displayName);
        }
        save(configuration);
    }

    private void saved(Player player, String categoryId, String itemId) {
        plugin.getLanguageService().send(player, "adminShop.saved");
        openItemEditor(player, categoryId, itemId);
    }

    private void toggle(String categoryId, String itemId, String key) {
        YamlConfiguration configuration = loadShopFile();
        String path = itemPath(categoryId, itemId) + "." + key;
        configuration.set(path, !configuration.getBoolean(path, false));
        save(configuration);
    }

    private void adjustPrice(String categoryId, String itemId, String key, double delta) {
        YamlConfiguration configuration = loadShopFile();
        String path = itemPath(categoryId, itemId) + "." + key;
        double current = configuration.getDouble(path, 0.0D);
        configuration.set(path, Math.max(0.0D, current + delta));
        save(configuration);
    }

    private void setFromHand(Player player, String categoryId, String itemId) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            plugin.getLanguageService().send(player, "adminShop.noHandItem");
            return;
        }
        YamlConfiguration configuration = loadShopFile();
        String path = itemPath(categoryId, itemId);
        configuration.set(path + ".material", hand.getType().name());
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            configuration.set(path + ".displayName", hand.getItemMeta().getDisplayName().replace(ChatColor.COLOR_CHAR, '&'));
        } else {
            configuration.set(path + ".displayName", "&f" + formatMaterialName(hand.getType()));
        }
        save(configuration);
        plugin.getLanguageService().send(player, "adminShop.saved");
    }

    private void openMaterialPicker(Player player, String categoryId, String itemId, int page) {
        YamlConfiguration gui = gui(player);
        int maxPage = Math.max(0, (selectableMaterials.size() - 1) / MATERIALS_PER_PAGE);
        int safePage = Math.max(0, Math.min(maxPage, page));
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.MATERIAL_PICKER, categoryId, itemId, keys, safePage);
        String titleKey = itemId == null || itemId.isBlank() ? "materialPickerCategory" : "materialPickerItem";
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, titleKey, Map.of(
                "page", Integer.toString(safePage + 1),
                "pages", Integer.toString(maxPage + 1)
        )));
        holder.setInventory(inventory);
        fill(inventory);

        int start = safePage * MATERIALS_PER_PAGE;
        int end = Math.min(start + MATERIALS_PER_PAGE, selectableMaterials.size());
        for (int index = start; index < end; index++) {
            Material material = selectableMaterials.get(index);
            int slot = index - start;
            inventory.setItem(slot, item(material, "&f" + formatMaterialName(material), lore(gui, "items.material.lore", Map.of("material", material.name()))));
            keys.put(slot, "material:" + material.name());
        }
        putConfigured(inventory, keys, gui, "slots.materialPicker.back", 45, "backItems", "back", Map.of());
        if (safePage > 0) {
            putConfigured(inventory, keys, gui, "slots.materialPicker.previousPage", 48, "previousPage", "previous", Map.of());
        }
        if (safePage < maxPage) {
            putConfigured(inventory, keys, gui, "slots.materialPicker.nextPage", 50, "nextPage", "next", Map.of());
        }
        player.openInventory(inventory);
    }

    private boolean isControlKey(String key) {
        return "back".equals(key)
                || "previous".equals(key)
                || "next".equals(key)
                || "create_category".equals(key)
                || "create_item".equals(key);
    }

    private boolean isMovableKey(ServerShopAdminHolder holder, String key) {
        if (key == null || isControlKey(key)) {
            return false;
        }
        return holder.view() == ServerShopAdminView.CATEGORIES || holder.view() == ServerShopAdminView.ITEMS;
    }

    private boolean isCreateCategoryTarget(ServerShopAdminHolder holder) {
        return CREATE_CATEGORY_TARGET.equals(holder.categoryId());
    }

    private boolean isCreateItemTarget(ServerShopAdminHolder holder) {
        return CREATE_ITEM_TARGET.equals(holder.itemId());
    }

    private boolean isCategoryEditorTarget(ServerShopAdminHolder holder) {
        return CATEGORY_EDITOR_TARGET.equals(holder.itemId());
    }

    private int nextFreeCategorySlot() {
        for (int slot : EDITABLE_GRID_SLOTS) {
            if (categoryAtSlot(slot) == null) {
                return slot;
            }
        }
        return 44;
    }

    private int nextFreeItemSlot(ServerShopCategory category) {
        for (int slot : EDITABLE_GRID_SLOTS) {
            if (itemAtSlot(category, slot) == null) {
                return slot;
            }
        }
        return 44;
    }

    private YamlConfiguration loadShopFile() {
        return YamlConfiguration.loadConfiguration(shopFile());
    }

    private void save(YamlConfiguration configuration) {
        try {
            configuration.save(shopFile());
            plugin.getServerShopRegistry().load();
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not save server_shop.yml.", exception);
        }
    }

    private File shopFile() {
        return new File(plugin.getDataFolder(), "server_shop.yml");
    }

    private String itemPath(String categoryId, String itemId) {
        return "categories." + categoryId + ".items." + itemId;
    }

    private String uniqueCategoryId(YamlConfiguration configuration, Material material) {
        String base = normalizeId(material.name());
        String id = base;
        int counter = 2;
        while (configuration.contains("categories." + id)) {
            id = base + "_" + counter++;
        }
        return id;
    }

    private String uniqueItemId(YamlConfiguration configuration, String categoryId, Material material) {
        String base = normalizeId(material.name());
        String id = base;
        int counter = 2;
        while (configuration.contains(itemPath(categoryId, id))) {
            id = base + "_" + counter++;
        }
        return id;
    }

    private String normalizeId(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private ItemStack categoryEditorItem(YamlConfiguration gui, ServerShopCategory category) {
        return item(category.icon(), category.displayName(), lore(gui, "items.categoryEditor.lore", Map.of(
                "category_id", category.id(),
                "item_count", Integer.toString(category.items().size()),
                "lore_lines", Integer.toString(category.lore().size())
        )));
    }

    private ItemStack editorItem(YamlConfiguration gui, ServerShopItem shopItem) {
        return item(shopItem.material(), shopItem.displayName(), lore(gui, "items.shopItem.lore", Map.of(
                "item_id", shopItem.id(),
                "material", shopItem.material().name(),
                "buy_price", money(shopItem.buyPrice()),
                "sell_price", money(shopItem.sellPrice()),
                "buy_status", status(gui, shopItem.buyEnabled()),
                "sell_status", status(gui, shopItem.sellEnabled())
        )));
    }

    private void priceButton(Inventory inventory, Map<Integer, String> keys, YamlConfiguration gui, String slotPath, int fallbackSlot, String itemKey, String actionKey, double price) {
        int buttonSlot = slot(gui, slotPath, fallbackSlot);
        inventory.setItem(buttonSlot, configuredItem(gui, itemKey, Map.of("price", money(price))));
        keys.put(buttonSlot, actionKey);
    }

    private void putConfigured(Inventory inventory, Map<Integer, String> keys, YamlConfiguration gui, String slotPath,
                               int fallbackSlot, String itemKey, String actionKey, Map<String, String> placeholders) {
        int buttonSlot = slot(gui, slotPath, fallbackSlot);
        inventory.setItem(buttonSlot, configuredItem(gui, itemKey, placeholders));
        keys.put(buttonSlot, actionKey);
    }

    private int priceDelta(InventoryClickEvent event) {
        int amount = event.isShiftClick() ? 10 : 1;
        return event.isRightClick() ? -amount : amount;
    }

    private ItemStack toggleItem(YamlConfiguration gui, String key, boolean enabled) {
        ConfigurationSection section = gui.getConfigurationSection("items." + key);
        Material material = material(section == null ? null : section.getString("material"), Material.STONE);
        String name = section == null ? key : section.getString(enabled ? "enabledName" : "disabledName", key);
        List<String> lore = section == null ? List.of() : section.getStringList("lore");
        return item(material, name, lore);
    }

    private ItemStack configuredItem(YamlConfiguration gui, String key, Map<String, String> placeholders) {
        ConfigurationSection section = gui.getConfigurationSection("items." + key);
        if (section == null) {
            return item(Material.STONE, key, List.of());
        }
        return item(
                material(section.getString("material"), Material.STONE),
                PlaceholderUtil.apply(section.getString("name", key), placeholders),
                lore(section, "lore", placeholders)
        );
    }

    private boolean isUsableSourceItem(ItemStack source) {
        return source != null && !source.getType().isAir() && source.getType().isItem();
    }

    private String displayName(ItemStack source) {
        if (source.hasItemMeta() && source.getItemMeta().hasDisplayName()) {
            return source.getItemMeta().getDisplayName().replace(ChatColor.COLOR_CHAR, '&');
        }
        return "&f" + formatMaterialName(source.getType());
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setLore(lore.stream().map(TextUtil::color).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private YamlConfiguration gui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/admin_servershop.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/admin_servershop.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private String title(YamlConfiguration gui, String key, Map<String, String> placeholders) {
        return TextUtil.color(PlaceholderUtil.apply(gui.getString("titles." + key, key), placeholders));
    }

    private List<String> lore(YamlConfiguration gui, String path, Map<String, String> placeholders) {
        return gui.getStringList(path).stream()
                .map(line -> PlaceholderUtil.apply(line, placeholders))
                .toList();
    }

    private List<String> lore(ConfigurationSection section, String path, Map<String, String> placeholders) {
        return section.getStringList(path).stream()
                .map(line -> PlaceholderUtil.apply(line, placeholders))
                .toList();
    }

    private String status(YamlConfiguration gui, boolean enabled) {
        return gui.getString(enabled ? "status.enabled" : "status.disabled", enabled ? "enabled" : "disabled");
    }

    private Material material(String value, Material fallback) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        return material == null ? fallback : material;
    }

    private int slot(YamlConfiguration gui, String path, int fallback) {
        int configuredSlot = gui.getInt(path, fallback);
        if (configuredSlot < 0 || configuredSlot >= 54) {
            return fallback;
        }
        return configuredSlot;
    }

    private int clampSlot(int slot, int size) {
        if (slot < 0 || slot >= size) {
            return 0;
        }
        return slot;
    }

    private String money(double value) {
        return plugin.getEconomyService().format(value);
    }

    private String formatMaterialName(Material material) {
        String lower = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record MoveSelection(ServerShopAdminView view, String categoryId, String entryId) {
    }

    private record TextEditSession(TextEditType type, String categoryId, String itemId) {
    }

    private enum TextEditType {
        CATEGORY_NAME,
        CATEGORY_LORE,
        ITEM_NAME,
        ITEM_LORE
    }
}

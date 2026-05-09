package de.craftplay.shop.importers;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EconomyShopGuiImporter {
    private static final List<Integer> DEFAULT_CATEGORY_SLOTS = List.of(10, 12, 14, 16, 28, 30, 32, 34, 20, 22, 24);

    private final CraftplayShopPlugin plugin;
    private final ImporterService importerService;

    public EconomyShopGuiImporter(CraftplayShopPlugin plugin, ImporterService importerService) {
        this.plugin = plugin;
        this.importerService = importerService;
    }

    public ImporterService.ImportExecution preview(File source) {
        ParseResult result = parse(source);
        return ImporterService.ImportExecution.of(result.report(), "", result.report().successful());
    }

    public ImporterService.ImportExecution apply(File source, ImportMode mode) {
        ParseResult result = parse(source);
        if (!result.report().successful() && result.categories().isEmpty()) {
            return ImporterService.ImportExecution.of(result.report(), "", false);
        }

        File target = new File(plugin.getDataFolder(), "server_shop.yml");
        File backup = importerService.createBackupFile("servershop", ".yml");
        try {
            Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not create server shop import backup.", exception);
            return ImporterService.ImportExecution.of(new ImportReport(
                    0,
                    result.report().warningCount(),
                    result.report().errorCount() + 1,
                    List.of("ServerShop backup could not be created."),
                    result.report().warnings(),
                    List.of("Backup failed: " + exception.getMessage())
            ), "", false);
        }

        YamlConfiguration output = mode == ImportMode.REPLACE
                ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(target);
        if (mode == ImportMode.REPLACE) {
            output.set("categories", null);
        }

        List<ImporterService.ImportMapping> mappings = new ArrayList<>();
        int slotIndex = 0;
        for (ImportedCategory category : result.categories().values()) {
            String categoryPath = "categories." + category.id();
            output.set(categoryPath + ".enabled", true);
            output.set(categoryPath + ".displayName", category.displayName());
            output.set(categoryPath + ".icon", category.icon().name());
            int existingSlot = output.getInt(categoryPath + ".slot", -1);
            output.set(categoryPath + ".slot", existingSlot >= 0 ? existingSlot : DEFAULT_CATEGORY_SLOTS.get(slotIndex % DEFAULT_CATEGORY_SLOTS.size()));
            output.set(categoryPath + ".lore", category.lore());
            output.set(categoryPath + ".items", null);
            for (ImportedItem item : category.items().values()) {
                String itemPath = categoryPath + ".items." + item.id();
                output.set(itemPath + ".material", item.material().name());
                output.set(itemPath + ".displayName", item.displayName());
                output.set(itemPath + ".lore", item.lore());
                output.set(itemPath + ".buyPrice", item.buyPrice());
                output.set(itemPath + ".sellPrice", item.sellPrice());
                output.set(itemPath + ".buyEnabled", item.buyEnabled());
                output.set(itemPath + ".sellEnabled", item.sellEnabled());
                output.set(itemPath + ".slot", item.slot());
                mappings.add(new ImporterService.ImportMapping(
                        "EconomyShopGUI-Premium",
                        category.sourceFileName() + "#" + item.sourceKey(),
                        "SERVER_SHOP_ITEM",
                        category.id() + ":" + item.id(),
                        mode.name()
                ));
            }
            mappings.add(new ImporterService.ImportMapping(
                    "EconomyShopGUI-Premium",
                    category.sourceFileName(),
                    "SERVER_SHOP_CATEGORY",
                    category.id(),
                    mode.name()
            ));
            slotIndex++;
        }

        try {
            output.save(target);
            plugin.getServer().getScheduler().runTask(plugin, plugin::reloadAll);
            return new ImporterService.ImportExecution(result.report(), backup.getAbsolutePath(), true, mappings);
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not save imported server shop.", exception);
            return ImporterService.ImportExecution.of(new ImportReport(
                    0,
                    result.report().warningCount(),
                    result.report().errorCount() + 1,
                    List.of("Saving server_shop.yml failed."),
                    result.report().warnings(),
                    List.of(exception.getMessage() == null ? "Unknown IO error" : exception.getMessage())
            ), backup.getAbsolutePath(), false);
        }
    }

    public boolean rollback(File backupFile) {
        if (backupFile == null || !backupFile.isFile()) {
            return false;
        }
        File target = new File(plugin.getDataFolder(), "server_shop.yml");
        try {
            Files.copy(backupFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getServer().getScheduler().runTask(plugin, plugin::reloadAll);
            return true;
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not rollback server shop import.", exception);
            return false;
        }
    }

    private ParseResult parse(File source) {
        File directory = resolveShopsDirectory(source);
        List<String> summary = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, ImportedCategory> categories = new LinkedHashMap<>();
        if (directory == null || !directory.isDirectory()) {
            errors.add("EconomyShopGUI source directory not found.");
            return new ParseResult(categories, new ImportReport(0, warnings.size(), errors.size(), summary, warnings, errors));
        }
        plugin.getPluginLogService().debug("importer", "Parsing EconomyShopGUI source directory " + directory.getAbsolutePath());

        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            errors.add("No EconomyShopGUI shop files found in " + directory.getAbsolutePath());
            return new ParseResult(categories, new ImportReport(0, warnings.size(), errors.size(), summary, warnings, errors));
        }
        java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        int importedItems = 0;
        int categorySlot = 0;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection pages = yaml.getConfigurationSection("pages");
            if (pages == null) {
                warnings.add("Skipped " + file.getName() + " because it has no pages section.");
                plugin.getPluginLogService().debug("importer", "Skipped ESG file without pages: " + file.getName());
                continue;
            }
            String categoryId = uniqueCategoryId(categories, sanitizeId(stripExtension(file.getName())));
            String displayName = yaml.getString("name", prettify(stripExtension(file.getName())));
            ImportedCategory category = new ImportedCategory(categoryId, displayName, Material.CHEST, new ArrayList<>(), file.getName(), new LinkedHashMap<>());
            int itemCounter = 0;
            for (String pageKey : pages.getKeys(false)) {
                ConfigurationSection page = pages.getConfigurationSection(pageKey);
                if (page == null) {
                    continue;
                }
                ConfigurationSection items = page.getConfigurationSection("items");
                if (items == null) {
                    continue;
                }
                for (String itemKey : items.getKeys(false)) {
                    ConfigurationSection itemSection = items.getConfigurationSection(itemKey);
                    if (itemSection == null) {
                        continue;
                    }
                    Material material = material(itemSection.getString("material", ""));
                    if (material == null || material.isAir()) {
                        warnings.add("Skipped " + file.getName() + " item " + itemKey + " because material is invalid.");
                        plugin.getPluginLogService().debug("importer", "Skipped ESG item with invalid material in " + file.getName() + "#" + itemKey);
                        continue;
                    }
                    if (category.items().isEmpty()) {
                        category = category.withIcon(material);
                    }
                    String itemId = uniqueItemId(category.items(), sanitizeId(itemSection.getString("id", material.name().toLowerCase(Locale.ROOT))));
                    double buy = itemSection.getDouble("buy", -1.0D);
                    double sell = itemSection.getDouble("sell", -1.0D);
                    boolean buyEnabled = buy >= 0.0D;
                    boolean sellEnabled = sell >= 0.0D;
                    if (!buyEnabled && !sellEnabled) {
                        warnings.add("Skipped " + file.getName() + " item " + itemKey + " because buy and sell are disabled.");
                        plugin.getPluginLogService().debug("importer", "Skipped ESG item because buy/sell disabled in " + file.getName() + "#" + itemKey);
                        continue;
                    }
                    category.items().put(itemId, new ImportedItem(
                            itemId,
                            material,
                            itemSection.getString("displayname", prettify(material.name())),
                            itemSection.getStringList("lore"),
                            buyEnabled ? buy : 0.0D,
                            sellEnabled ? sell : 0.0D,
                            buyEnabled,
                            sellEnabled,
                            parseSlot(itemKey, itemCounter),
                            itemKey
                    ));
                    itemCounter++;
                    importedItems++;
                }
            }
            if (!category.items().isEmpty()) {
                categories.put(categoryId, category);
                categorySlot++;
            } else {
                warnings.add("Skipped category " + file.getName() + " because no importable items were found.");
            }
        }

        summary.add("EconomyShopGUI categories: " + categories.size());
        summary.add("EconomyShopGUI items: " + importedItems);
        return new ParseResult(categories, new ImportReport(importedItems, warnings.size(), errors.size(), summary, warnings, errors));
    }

    private File resolveShopsDirectory(File source) {
        if (source == null) {
            return null;
        }
        if (source.isDirectory()) {
            File nested = new File(source, "shops");
            if (nested.isDirectory()) {
                return nested;
            }
            return source;
        }
        return null;
    }

    private Material material(String value) {
        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private int parseSlot(String itemKey, int fallbackIndex) {
        try {
            return Math.max(0, Integer.parseInt(itemKey) - 1);
        } catch (NumberFormatException exception) {
            return fallbackIndex;
        }
    }

    private String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? name : name.substring(0, index);
    }

    private String sanitizeId(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private String uniqueCategoryId(Map<String, ImportedCategory> categories, String base) {
        String value = base.isBlank() ? "category" : base;
        int suffix = 2;
        while (categories.containsKey(value)) {
            value = base + "_" + suffix++;
        }
        return value;
    }

    private String uniqueItemId(Map<String, ImportedItem> items, String base) {
        String value = base.isBlank() ? "item" : base;
        int suffix = 2;
        while (items.containsKey(value)) {
            value = base + "_" + suffix++;
        }
        return value;
    }

    private String prettify(String input) {
        String[] parts = input.toLowerCase(Locale.ROOT).split("[_\\s-]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? input : builder.toString();
    }

    private record ParseResult(Map<String, ImportedCategory> categories, ImportReport report) {
    }

    private record ImportedCategory(String id,
                                    String displayName,
                                    Material icon,
                                    List<String> lore,
                                    String sourceFileName,
                                    Map<String, ImportedItem> items) {
        private ImportedCategory withIcon(Material value) {
            return new ImportedCategory(id, displayName, value, lore, sourceFileName, items);
        }
    }

    private record ImportedItem(String id,
                                Material material,
                                String displayName,
                                List<String> lore,
                                double buyPrice,
                                double sellPrice,
                                boolean buyEnabled,
                                boolean sellEnabled,
                                int slot,
                                String sourceKey) {
    }
}

package de.craftplay.shop.importers;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ImporterService {
    private final CraftplayShopPlugin plugin;
    private final EconomyShopGuiImporter economyShopGuiImporter;
    private final ShopIntuitiveImporter shopIntuitiveImporter;

    public ImporterService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        this.economyShopGuiImporter = new EconomyShopGuiImporter(plugin, this);
        this.shopIntuitiveImporter = new ShopIntuitiveImporter(plugin, this);
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 4 || !"import".equalsIgnoreCase(args[1])) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return true;
        }
        if (!player.hasPermission(de.craftplay.shop.core.permission.PermissionNodes.ADMIN)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        if (args.length < 4) {
            plugin.getLanguageService().send(player, "importer.usage");
            return true;
        }
        String importerId = args[2].toLowerCase(Locale.ROOT);
        String action = args[3].toLowerCase(Locale.ROOT);
        if ("economyshopgui".equals(importerId) || "economyshopguipremium".equals(importerId) || "esg".equals(importerId)) {
            return handleEconomyShopGui(player, action, args);
        }
        if ("shopintuitive".equals(importerId) || "shop".equals(importerId) || "intuitiveshop".equals(importerId)) {
            return handleShopIntuitive(player, action, args);
        }
        plugin.getLanguageService().send(player, "importer.invalidImporter", Map.of("importer", importerId));
        return true;
    }

    private boolean handleEconomyShopGui(Player player, String action, String[] args) {
        File source = economyShopGuiSource();
        if ("preview".equals(action)) {
            execute(player, "EconomyShopGUI Premium", "EconomyShopGUI-Premium", source, ImportMode.PREVIEW,
                    () -> economyShopGuiImporter.preview(source));
            return true;
        }
        if ("apply".equals(action)) {
            ImportMode mode = applyMode(args, 4);
            execute(player, "EconomyShopGUI Premium", "EconomyShopGUI-Premium", source, mode,
                    () -> economyShopGuiImporter.apply(source, mode));
            return true;
        }
        if ("rollback".equals(action)) {
            if (args.length < 5) {
                plugin.getLanguageService().send(player, "importer.rollbackUsage");
                return true;
            }
            rollback(player, parseId(args[4]));
            return true;
        }
        plugin.getLanguageService().send(player, "importer.usage");
        return true;
    }

    private boolean handleShopIntuitive(Player player, String action, String[] args) {
        File source = shopIntuitiveSource();
        if ("preview".equals(action)) {
            execute(player, "Shop - the intuitive shop plugin", "Shop", source, ImportMode.PREVIEW,
                    () -> shopIntuitiveImporter.preview(source));
            return true;
        }
        if ("apply".equals(action)) {
            ImportMode mode = applyMode(args, 4);
            execute(player, "Shop - the intuitive shop plugin", "Shop", source, mode,
                    () -> shopIntuitiveImporter.apply(source, mode));
            return true;
        }
        if ("rollback".equals(action)) {
            if (args.length < 5) {
                plugin.getLanguageService().send(player, "importer.rollbackUsage");
                return true;
            }
            rollback(player, parseId(args[4]));
            return true;
        }
        plugin.getLanguageService().send(player, "importer.usage");
        return true;
    }

    private void execute(Player player, String importerName, String sourcePlugin, File source, ImportMode mode, ImportTask task) {
        if (source == null || !source.exists()) {
            plugin.getLanguageService().send(player, "importer.sourceMissing", Map.of("path", source == null ? "null" : source.getAbsolutePath()));
            return;
        }
        plugin.getLanguageService().send(player, "importer.started", Map.of(
                "importer", importerName,
                "mode", mode.name(),
                "path", source.getAbsolutePath()
        ));
        long importId = createImport(importerName, sourcePlugin, source, mode, player);
        plugin.getTaskService().runAsync(() -> {
            try {
                ImportExecution execution = task.run();
                String reportPath = writeReport(importId, importerName, mode, execution.report());
                finishImport(importId, execution.report(), execution.success() ? "SUCCESS" : "FAILED", execution.backupPath(), reportPath);
                saveMappings(importId, execution.mappings());
                plugin.getServer().getScheduler().runTask(plugin, () -> sendReport(player, importId, mode, execution.report(), reportPath));
            } catch (Exception exception) {
                plugin.getPluginLogService().error("Importer failed: " + importerName, exception);
                String reportPath = writeReport(importId, importerName, mode, new ImportReport(
                        0,
                        0,
                        1,
                        List.of("Importer failed with an exception."),
                        List.of(),
                        List.of(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                ));
                finishImport(importId, new ImportReport(0, 0, 1, List.of(), List.of(), List.of()), "FAILED", "", reportPath);
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLanguageService().send(player, "importer.failed", Map.of(
                        "importer", importerName,
                        "id", Long.toString(importId)
                )));
            }
        });
    }

    private void rollback(Player player, long importId) {
        if (importId <= 0) {
            plugin.getLanguageService().send(player, "importer.rollbackUsage");
            return;
        }
        ImportRow row = importRow(importId);
        if (row == null) {
            plugin.getLanguageService().send(player, "importer.rollbackMissing", Map.of("id", Long.toString(importId)));
            return;
        }
        if (row.backupPath().isBlank()) {
            plugin.getLanguageService().send(player, "importer.rollbackNoBackup", Map.of("id", Long.toString(importId)));
            return;
        }
        plugin.getLanguageService().send(player, "importer.rollbackStarted", Map.of("id", Long.toString(importId)));
        plugin.getTaskService().runAsync(() -> {
            boolean success;
            if ("EconomyShopGUI-Premium".equalsIgnoreCase(row.sourcePlugin())) {
                success = economyShopGuiImporter.rollback(new File(row.backupPath()));
            } else if ("Shop".equalsIgnoreCase(row.sourcePlugin())) {
                success = shopIntuitiveImporter.rollback(new File(row.backupPath()));
            } else {
                success = false;
            }
            boolean finalSuccess = success;
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLanguageService().send(player,
                    finalSuccess ? "importer.rollbackDone" : "importer.rollbackFailed",
                    Map.of("id", Long.toString(importId))));
        });
    }

    private void sendReport(Player player, long importId, ImportMode mode, ImportReport report, String reportPath) {
        plugin.getLanguageService().send(player, report.successful() ? "importer.done" : "importer.doneWithIssues", Map.of(
                "id", Long.toString(importId),
                "mode", mode.name(),
                "imported", Integer.toString(report.importedCount()),
                "warnings", Integer.toString(report.warningCount()),
                "errors", Integer.toString(report.errorCount())
        ));
        if (!reportPath.isBlank()) {
            plugin.getLanguageService().send(player, "importer.reportPath", Map.of("path", reportPath));
        }
        for (String line : report.summaryLines()) {
            player.sendMessage(line);
        }
    }

    public File createBackupFile(String prefix, String extension) {
        File folder = new File(plugin.getDataFolder(), "imports/backups");
        folder.mkdirs();
        return new File(folder, prefix + "-" + System.currentTimeMillis() + extension);
    }

    public void saveMappings(long importId, List<ImportMapping> mappings) {
        if (importId <= 0 || mappings.isEmpty()) {
            return;
        }
        String table = plugin.getDatabaseService().table("import_mappings");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (import_id, source_plugin, source_identifier, target_type, target_id, notes) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (ImportMapping mapping : mappings) {
                    statement.setLong(1, importId);
                    statement.setString(2, mapping.sourcePlugin());
                    statement.setString(3, mapping.sourceIdentifier());
                    statement.setString(4, mapping.targetType());
                    statement.setString(5, mapping.targetId());
                    statement.setString(6, mapping.notes());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not save import mappings.", exception);
            }
        }
    }

    private long createImport(String importerName, String sourcePlugin, File source, ImportMode mode, Player player) {
        String table = plugin.getDatabaseService().table("imports");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (importer_name, source_plugin, source_path, mode, status, created_by_uuid, created_by_name, created_at, finished_at, imported_count, warning_count, error_count, backup_path, report_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                long now = System.currentTimeMillis();
                statement.setString(1, importerName);
                statement.setString(2, sourcePlugin);
                statement.setString(3, source.getAbsolutePath());
                statement.setString(4, mode.name());
                statement.setString(5, "RUNNING");
                statement.setString(6, player.getUniqueId().toString());
                statement.setString(7, player.getName());
                statement.setLong(8, now);
                statement.setLong(9, 0L);
                statement.setInt(10, 0);
                statement.setInt(11, 0);
                statement.setInt(12, 0);
                statement.setString(13, "");
                statement.setString(14, "");
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not create import row.", exception);
            }
        }
        return -1L;
    }

    private void finishImport(long importId, ImportReport report, String status, String backupPath, String reportPath) {
        String table = plugin.getDatabaseService().table("imports");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "UPDATE " + table + " SET status = ?, finished_at = ?, imported_count = ?, warning_count = ?, error_count = ?, backup_path = ?, report_path = ? WHERE id = ?")) {
                statement.setString(1, status);
                statement.setLong(2, System.currentTimeMillis());
                statement.setInt(3, report.importedCount());
                statement.setInt(4, report.warningCount());
                statement.setInt(5, report.errorCount());
                statement.setString(6, backupPath == null ? "" : backupPath);
                statement.setString(7, reportPath == null ? "" : reportPath);
                statement.setLong(8, importId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not update import row.", exception);
            }
        }
    }

    private String writeReport(long importId, String importerName, ImportMode mode, ImportReport report) {
        File folder = new File(plugin.getDataFolder(), "imports/reports");
        folder.mkdirs();
        File reportFile = new File(folder, "import-" + importId + "-" + sanitize(importerName) + ".txt");
        List<String> lines = new ArrayList<>();
        lines.add("CraftplayShop Import Report");
        lines.add("Importer: " + importerName);
        lines.add("Mode: " + mode.name());
        lines.add("Imported: " + report.importedCount());
        lines.add("Warnings: " + report.warningCount());
        lines.add("Errors: " + report.errorCount());
        lines.add("");
        if (!report.summaryLines().isEmpty()) {
            lines.add("Summary:");
            lines.addAll(report.summaryLines());
            lines.add("");
        }
        if (!report.warnings().isEmpty()) {
            lines.add("Warnings:");
            lines.addAll(report.warnings());
            lines.add("");
        }
        if (!report.errors().isEmpty()) {
            lines.add("Errors:");
            lines.addAll(report.errors());
        }
        try {
            Files.write(reportFile.toPath(), lines, StandardCharsets.UTF_8);
            return reportFile.getAbsolutePath();
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not write import report.", exception);
            return "";
        }
    }

    private ImportRow importRow(long importId) {
        String table = plugin.getDatabaseService().table("imports");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT source_plugin, backup_path FROM " + table + " WHERE id = ?")) {
                statement.setLong(1, importId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new ImportRow(result.getString("source_plugin"), result.getString("backup_path"));
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not load import row.", exception);
            }
        }
        return null;
    }

    private File economyShopGuiSource() {
        String path = plugin.getConfig().getString("importers.economyShopGui.sourcePath", "");
        return path == null || path.isBlank() ? null : new File(path);
    }

    private File shopIntuitiveSource() {
        String path = plugin.getConfig().getString("importers.shopIntuitive.sourcePath", "");
        return path == null || path.isBlank() ? null : new File(path);
    }

    private ImportMode applyMode(String[] args, int index) {
        if (args.length <= index) {
            return ImportMode.MERGE;
        }
        String token = args[index].toLowerCase(Locale.ROOT).replace("--", "");
        if ("replace".equals(token)) {
            return ImportMode.REPLACE;
        }
        return ImportMode.MERGE;
    }

    private long parseId(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private String sanitize(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    public record ImportExecution(ImportReport report, String backupPath, boolean success, List<ImportMapping> mappings) {
        public ImportExecution {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
        }

        public static ImportExecution of(ImportReport report, String backupPath, boolean success) {
            return new ImportExecution(report, backupPath, success, Collections.emptyList());
        }
    }

    public record ImportMapping(String sourcePlugin, String sourceIdentifier, String targetType, String targetId, String notes) {
    }

    private record ImportRow(String sourcePlugin, String backupPath) {
    }

    @FunctionalInterface
    private interface ImportTask {
        ImportExecution run() throws Exception;
    }
}

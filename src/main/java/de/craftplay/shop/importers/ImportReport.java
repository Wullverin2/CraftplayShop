package de.craftplay.shop.importers;

import java.util.List;

public record ImportReport(int importedCount,
                           int warningCount,
                           int errorCount,
                           List<String> summaryLines,
                           List<String> warnings,
                           List<String> errors) {
    public ImportReport {
        summaryLines = List.copyOf(summaryLines);
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
    }

    public boolean successful() {
        return errorCount == 0;
    }
}

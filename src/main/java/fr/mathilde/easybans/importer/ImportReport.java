package fr.mathilde.easybans.importer;

import java.util.ArrayList;
import java.util.List;

public record ImportReport(int imported, int skippedDuplicates, int errors, List<String> errorMessages) {

    public static ImportReport empty() {
        return new ImportReport(0, 0, 0, List.of());
    }

    /** Mutable accumulator used while an importer runs; call {@link #toReport()} at the end. */
    public static final class Builder {
        private int imported;
        private int skippedDuplicates;
        private int errors;
        private final List<String> errorMessages = new ArrayList<>();

        public void imported() {
            imported++;
        }

        public void skipped() {
            skippedDuplicates++;
        }

        public void error(String message) {
            errors++;
            if (errorMessages.size() < 50) {
                errorMessages.add(message);
            }
        }

        public ImportReport toReport() {
            return new ImportReport(imported, skippedDuplicates, errors, List.copyOf(errorMessages));
        }
    }
}

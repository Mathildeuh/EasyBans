package fr.mathilde.easybans.importer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Importer {

    /**
     * @param location  a filesystem path or JDBC URL, depending on the source - see ImportSource javadoc and README.md
     * @param staffUuid the staff member running the import (recorded as the "staff" on a synthetic import note, not on
     *                  the imported punishments themselves, which keep their original staff attribution where available)
     */
    CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName);
}

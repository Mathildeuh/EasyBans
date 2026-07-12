package fr.mathilde.easybans.importer;

import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.database.dao.PunishmentDao;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dispatches {@code /easybans import} to the matching {@link Importer}. Imports run one at a time on their own thread. */
public final class ImportService {

    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EasyBans-Import");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<ImportSource, Importer> importers = new EnumMap<>(ImportSource.class);

    public ImportService(PunishmentDao punishmentDao, UuidResolver uuidResolver) {
        importers.put(ImportSource.VANILLA, new VanillaImporter(punishmentDao, importExecutor));
        importers.put(ImportSource.ESSENTIALS, new EssentialsImporter(punishmentDao, importExecutor));
        importers.put(ImportSource.MAXBANS, new MaxBansImporter(punishmentDao, uuidResolver, importExecutor));
        importers.put(ImportSource.BANMANAGER, new BanManagerImporter(punishmentDao, importExecutor));
        importers.put(ImportSource.ADVANCEDBAN, new AdvancedBanImporter(punishmentDao, importExecutor));
        importers.put(ImportSource.BUNGEEADMINTOOLS, new BungeeAdminToolsImporter(punishmentDao, uuidResolver, importExecutor));
        importers.put(ImportSource.LITEBANS, new LiteBansImporter(punishmentDao, importExecutor));
    }

    public CompletableFuture<ImportReport> importFrom(ImportSource source, String location, UUID staffUuid, String staffName) {
        Importer importer = importers.get(source);
        if (importer == null) {
            return CompletableFuture.completedFuture(ImportReport.empty());
        }
        return importer.importFrom(location, staffUuid, staffName);
    }

    public void shutdown() {
        importExecutor.shutdownNow();
    }
}

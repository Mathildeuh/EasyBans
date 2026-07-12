package fr.mathilde.easybans.importer;

import fr.mathilde.easybans.database.dao.PunishmentDao;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Imports EssentialsX userdata bans. {@code location} is the path to the
 * {@code Essentials/userdata} directory (one {@code <uuid>.yml} per player).
 *
 * <p><b>Known limitation:</b> classic Essentials bans have no expiry or reason-history concept
 * - every imported ban becomes a permanent EasyBans ban with whatever single reason string
 * Essentials had stored (or a default one). This is a property of the source format, not
 * something this importer can recover.
 */
public final class EssentialsImporter implements Importer {

    private final PunishmentDao punishmentDao;
    private final Executor executor;

    public EssentialsImporter(PunishmentDao punishmentDao, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.executor = executor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            ImportReport.Builder builder = new ImportReport.Builder();
            Path dir = Path.of(location);
            if (!Files.isDirectory(dir)) {
                builder.error("Not a directory: " + location);
                return builder.toReport();
            }

            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".yml"))::iterator) {
                    try {
                        UUID uuid = UUID.fromString(file.getFileName().toString().replace(".yml", ""));
                        Map<String, Object> data;
                        try (InputStream in = Files.newInputStream(file)) {
                            Object loaded = new Yaml().load(in);
                            data = loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
                        }

                        boolean banned = Boolean.TRUE.equals(data.get("banned"));
                        if (!banned) {
                            continue;
                        }
                        String reason = String.valueOf(data.getOrDefault("ban-reason", "Imported from Essentials"));

                        if (punishmentDao.findAnyActiveBan(uuid).get().isPresent()) {
                            builder.skipped();
                            continue;
                        }
                        punishmentDao.insertBan(uuid, Optional.empty(), false, reason, staffUuid, staffName,
                                Optional.empty(), null, Optional.empty(), false, Instant.now(), Optional.empty()).get();
                        builder.imported();
                    } catch (Exception e) {
                        builder.error(file.getFileName() + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                builder.error("Could not list " + location + ": " + e.getMessage());
            }
            return builder.toReport();
        }, executor);
    }
}

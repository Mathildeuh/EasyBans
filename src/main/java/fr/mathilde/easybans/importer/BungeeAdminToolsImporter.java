package fr.mathilde.easybans.importer;

import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.punishment.PunishmentConstants;
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

/**
 * Imports from a BungeeAdminTools YAML ban list. {@code location} is the path to the ban list
 * file (typically {@code BungeeAdminTools/banlist.yml}).
 *
 * <p><b>Schema caveat:</b> BAT's storage layout has changed across releases. This importer
 * assumes a top-level map keyed by player name, each entry having {@code reason},
 * {@code time} (unix seconds, {@code -1} = permanent) and {@code oper} (staff name). Adjust
 * {@link #importFrom} if your installed version differs.
 */
public final class BungeeAdminToolsImporter implements Importer {

    private final PunishmentDao punishmentDao;
    private final UuidResolver uuidResolver;
    private final Executor executor;

    public BungeeAdminToolsImporter(PunishmentDao punishmentDao, UuidResolver uuidResolver, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.uuidResolver = uuidResolver;
        this.executor = executor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            ImportReport.Builder builder = new ImportReport.Builder();
            Path file = Path.of(location);
            if (!Files.exists(file)) {
                builder.error("File not found: " + location);
                return builder.toReport();
            }

            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                Object loaded = new Yaml().load(in);
                root = loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
            } catch (IOException e) {
                builder.error("Could not read " + location + ": " + e.getMessage());
                return builder.toReport();
            }

            for (Map.Entry<String, Object> entry : root.entrySet()) {
                String name = entry.getKey();
                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                try {
                    String reason = String.valueOf(data.getOrDefault("reason", "Imported from BungeeAdminTools"));
                    String operator = String.valueOf(data.getOrDefault("oper", "Imported"));
                    long timeSeconds = data.get("time") instanceof Number n ? n.longValue() : -1;
                    Optional<Instant> expires = timeSeconds <= 0 ? Optional.empty() : Optional.of(Instant.ofEpochSecond(timeSeconds));

                    UUID uuid = uuidResolver.resolveUuid(name).get()
                            .orElseThrow(() -> new IllegalStateException("could not resolve uuid for " + name));
                    if (punishmentDao.findAnyActiveBan(uuid).get().isPresent()) {
                        builder.skipped();
                        continue;
                    }
                    punishmentDao.insertBan(uuid, Optional.empty(), false, reason, PunishmentConstants.CONSOLE_UUID,
                            operator, Optional.empty(), null, Optional.empty(), false, Instant.now(), expires).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("Entry for '" + name + "': " + e.getMessage());
                }
            }
            return builder.toReport();
        }, executor);
    }
}

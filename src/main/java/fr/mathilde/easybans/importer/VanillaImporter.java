package fr.mathilde.easybans.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.mathilde.easybans.database.dao.PunishmentDao;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Imports Mojang's own {@code banned-players.json} / {@code banned-ips.json}. {@code location}
 * is the path to the directory containing both files (a server's root folder).
 */
public final class VanillaImporter implements Importer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH);

    private final PunishmentDao punishmentDao;
    private final Executor executor;

    public VanillaImporter(PunishmentDao punishmentDao, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            ImportReport.Builder builder = new ImportReport.Builder();
            Path dir = Path.of(location);
            importPlayerBans(dir.resolve("banned-players.json"), staffUuid, staffName, builder);
            importIpBans(dir.resolve("banned-ips.json"), staffUuid, staffName, builder);
            return builder.toReport();
        }, executor);
    }

    private void importPlayerBans(Path file, UUID staffUuid, String staffName, ImportReport.Builder builder) {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                try {
                    JsonObject obj = element.getAsJsonObject();
                    UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                    String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Imported ban";
                    Optional<Instant> expires = parseExpiry(obj);

                    if (punishmentDao.findAnyActiveBan(uuid).get().isPresent()) {
                        builder.skipped();
                        continue;
                    }
                    punishmentDao.insertBan(uuid, Optional.empty(), false, reason, staffUuid, staffName,
                            Optional.empty(), null, Optional.empty(), false, Instant.now(), expires).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("banned-players.json entry: " + e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            builder.error("Could not read " + file + ": " + e.getMessage());
        }
    }

    private void importIpBans(Path file, UUID staffUuid, String staffName, ImportReport.Builder builder) {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                try {
                    JsonObject obj = element.getAsJsonObject();
                    String ip = obj.get("ip").getAsString();
                    String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Imported IP ban";
                    Optional<Instant> expires = parseExpiry(obj);

                    // Vanilla IP bans have no associated uuid - store under a synthetic per-ip marker uuid so the
                    // ban can still be looked up and displayed, deriving it deterministically from the IP itself.
                    UUID syntheticUuid = UUID.nameUUIDFromBytes(("easybans-ip-import:" + ip).getBytes());
                    punishmentDao.insertBan(syntheticUuid, Optional.of(ip), true, reason, staffUuid, staffName,
                            Optional.empty(), null, Optional.empty(), false, Instant.now(), expires).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("banned-ips.json entry: " + e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            builder.error("Could not read " + file + ": " + e.getMessage());
        }
    }

    private Optional<Instant> parseExpiry(JsonObject obj) {
        if (!obj.has("expires")) {
            return Optional.empty();
        }
        String expires = obj.get("expires").getAsString();
        if (expires == null || expires.equalsIgnoreCase("forever")) {
            return Optional.empty();
        }
        return Optional.of(OffsetDateTime.parse(expires, DATE_FORMAT).toInstant());
    }
}

package fr.mathilde.easybans.template;

import fr.mathilde.easybans.punishment.DurationParser;
import fr.mathilde.easybans.punishment.PunishmentType;
import fr.mathilde.easybans.warning.WarningCategory;
import fr.mathilde.easybans.warning.WarningThreshold;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads {@code templates.yml}: punishment escalation templates and warning categories/thresholds. */
public final class TemplateRegistry {

    private volatile Map<String, PunishmentTemplate> templates = Map.of();
    private volatile Map<String, WarningCategory> categories = Map.of();
    private final Path dataDirectory;
    private final Logger logger;

    public TemplateRegistry(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        reload();
    }

    /** Re-reads templates.yml from disk in place - used by {@code /easybans reload}. */
    public void reload() {
        load(dataDirectory);
    }

    public Optional<PunishmentTemplate> template(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Optional<WarningCategory> category(String id) {
        return Optional.ofNullable(categories.get(id));
    }

    public List<String> categoryIds() {
        return List.copyOf(categories.keySet());
    }

    public List<String> templateIds() {
        return List.copyOf(templates.keySet());
    }

    @SuppressWarnings("unchecked")
    private void load(Path dataDirectory) {
        try {
            Path path = dataDirectory.resolve("templates.yml");
            if (!Files.exists(path)) {
                try (InputStream in = TemplateRegistry.class.getResourceAsStream("/templates.yml")) {
                    if (in == null) {
                        throw new IllegalStateException("Missing bundled templates.yml resource");
                    }
                    Files.copy(in, path);
                }
            }

            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(path)) {
                Object loaded = new Yaml().load(in);
                root = loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
            }

            Map<String, PunishmentTemplate> loadedTemplates = new LinkedHashMap<>();
            Map<String, Object> punishmentTemplates = (Map<String, Object>) root.getOrDefault("punishment-templates", Map.of());
            for (Map.Entry<String, Object> entry : punishmentTemplates.entrySet()) {
                try {
                    parseTemplate(loadedTemplates, entry.getKey(), (Map<String, Object>) entry.getValue());
                } catch (RuntimeException e) {
                    // One malformed entry (bad enum, bad duration string, missing field, wrong YAML shape)
                    // must not prevent the plugin from starting - skip it and keep going.
                    logger.warn("Skipping malformed punishment template '{}': {}", entry.getKey(), e.toString());
                }
            }

            Map<String, WarningCategory> loadedCategories = new LinkedHashMap<>();
            Map<String, Object> warningCategories = (Map<String, Object>) root.getOrDefault("warning-categories", Map.of());
            for (Map.Entry<String, Object> entry : warningCategories.entrySet()) {
                try {
                    parseCategory(loadedCategories, entry.getKey(), (Map<String, Object>) entry.getValue());
                } catch (RuntimeException e) {
                    logger.warn("Skipping malformed warning category '{}': {}", entry.getKey(), e.toString());
                }
            }

            // Swap both references atomically so concurrent readers never see a partially-rebuilt registry.
            this.templates = Map.copyOf(loadedTemplates);
            this.categories = Map.copyOf(loadedCategories);

            logger.info("Loaded {} punishment template(s) and {} warning categorie(s)", templates.size(), categories.size());
        } catch (IOException e) {
            throw new IllegalStateException("Could not load templates.yml", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseTemplate(Map<String, PunishmentTemplate> target, String id, Map<String, Object> section) {
        PunishmentType type = PunishmentType.valueOf(String.valueOf(section.get("type")).toUpperCase());
        List<Map<String, Object>> rawStages = (List<Map<String, Object>>) section.get("stages");
        List<TemplateStage> stages = new ArrayList<>();
        for (Map<String, Object> rawStage : rawStages) {
            int after = ((Number) rawStage.get("after")).intValue();
            String durationRaw = String.valueOf(rawStage.get("duration"));
            String reason = String.valueOf(rawStage.get("reason"));
            Optional<String> kickscreen = Optional.ofNullable((String) rawStage.get("kickscreen"));
            stages.add(new TemplateStage(after, DurationParser.parse(durationRaw), reason, kickscreen));
        }
        target.put(id, new PunishmentTemplate(id, type, List.copyOf(stages)));
    }

    @SuppressWarnings("unchecked")
    private void parseCategory(Map<String, WarningCategory> target, String id, Map<String, Object> section) {
        String displayName = String.valueOf(section.getOrDefault("display-name", id));
        List<Map<String, Object>> rawThresholds = (List<Map<String, Object>>) section.getOrDefault("thresholds", List.of());
        List<WarningThreshold> thresholds = new ArrayList<>();
        for (Map<String, Object> rawThreshold : rawThresholds) {
            int count = ((Number) rawThreshold.get("count")).intValue();
            List<String> commands = (List<String>) rawThreshold.getOrDefault("commands", List.of());
            thresholds.add(new WarningThreshold(count, List.copyOf(commands)));
        }
        target.put(id, new WarningCategory(id, displayName, List.copyOf(thresholds)));
    }
}

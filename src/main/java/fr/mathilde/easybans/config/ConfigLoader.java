package fr.mathilde.easybans.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads {@code config.yml} from the plugin's data directory, writing the bundled default
 * on first run so the plugin works out of the box with H2 and no manual configuration.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static EasyBansConfig load(Path dataDirectory, Logger logger) {
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                copyDefault("/config.yml", configPath);
                logger.info("Generated default config.yml");
            }
            try (InputStream in = Files.newInputStream(configPath)) {
                Yaml yaml = new Yaml();
                Map<String, Object> raw = yaml.load(in);
                YamlSection root = new YamlSection(raw);
                return EasyBansConfig.fromYaml(root, logger);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load config.yml", e);
        }
    }

    static void copyDefault(String resourcePath, Path target) throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource: " + resourcePath);
            }
            Files.copy(in, target);
        }
    }
}

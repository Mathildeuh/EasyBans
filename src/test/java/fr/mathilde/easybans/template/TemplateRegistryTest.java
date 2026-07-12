package fr.mathilde.easybans.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A malformed entry in templates.yml must not prevent the plugin from starting - it should be
 * skipped (with a logged warning) while every well-formed entry still loads normally.
 */
class TemplateRegistryTest {

    @Test
    void malformedEntryIsSkippedWithoutThrowing(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("templates.yml"), """
                punishment-templates:
                  good-template:
                    type: ban
                    stages:
                      - after: 1
                        duration: 1d
                        reason: "first offense"
                  broken-template:
                    type: ban
                    stages:
                      - after: 1
                        duration: "not-a-real-duration"
                        reason: "this entry is malformed"

                warning-categories:
                  good-category:
                    display-name: "Good"
                    thresholds:
                      - count: 3
                        commands: ["mute %player% 1h test"]
                  broken-category:
                    display-name: "Broken"
                    thresholds:
                      - count: "not-a-number"
                        commands: []
                """);

        TemplateRegistry registry = assertDoesNotThrow(() -> new TemplateRegistry(tempDir, NOPLogger.NOP_LOGGER));

        assertTrue(registry.template("good-template").isPresent(), "well-formed template should still load");
        assertTrue(registry.template("broken-template").isEmpty(), "malformed template should be skipped, not crash");
        assertTrue(registry.category("good-category").isPresent(), "well-formed category should still load");
        assertTrue(registry.category("broken-category").isEmpty(), "malformed category should be skipped, not crash");
    }
}

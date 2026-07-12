package fr.mathilde.easybans.message;

import fr.mathilde.easybans.config.LocaleConfig;
import fr.mathilde.easybans.locale.SupportedLocale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every bundled {@code messages_<locale>.yml} must expose exactly the same set of keys as
 * English. This is what CONFIG.md promises translators and what {@link MessageService}'s
 * fallback chain assumes - a locale silently missing a key just falls back, but a *typo'd* key
 * (present under a slightly different path) would silently never be used, which this test
 * catches by requiring an exact set match rather than just "at least as many keys".
 */
class MessageKeyParityTest {

    private static final List<String> LOCALE_CODES = List.of("fr", "en", "es", "it", "ru", "ar", "de");

    @Test
    void everyBundledLocaleHasTheSameKeysAsEnglish(@TempDir Path tempDir) {
        LocaleConfig config = new LocaleConfig("en", false, LOCALE_CODES);
        MessageService messages = new MessageService(tempDir, config, NOPLogger.NOP_LOGGER);

        Set<String> englishKeys = messages.allKeys(SupportedLocale.EN);
        assertFalse(englishKeys.isEmpty(), "English message file should not be empty");

        for (String code : LOCALE_CODES) {
            SupportedLocale locale = SupportedLocale.fromCode(code).orElseThrow();
            Set<String> keys = messages.allKeys(locale);
            assertEquals(englishKeys, keys, () -> "Key set mismatch for locale '" + code + "'");
        }
    }
}

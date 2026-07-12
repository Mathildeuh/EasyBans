package fr.mathilde.easybans.config;

import java.util.List;

public record LocaleConfig(String defaultLocale, boolean autoDetectLocale, List<String> supportedLocales) {
    static LocaleConfig fromYaml(YamlSection section) {
        List<String> supported = section.getStringList("supported-locales");
        if (supported.isEmpty()) {
            supported = List.of("fr", "en", "es", "it", "ru", "ar", "de");
        }
        return new LocaleConfig(
                section.getString("default-locale", "fr"),
                section.getBoolean("auto-detect-locale", true),
                supported
        );
    }
}

package fr.mathilde.easybans.locale;

import java.util.Locale;
import java.util.Optional;

public enum SupportedLocale {
    FR("fr", false),
    EN("en", false),
    ES("es", false),
    IT("it", false),
    RU("ru", false),
    AR("ar", true),
    DE("de", false);

    private final String code;
    private final boolean rightToLeft;

    SupportedLocale(String code, boolean rightToLeft) {
        this.code = code;
        this.rightToLeft = rightToLeft;
    }

    public String code() {
        return code;
    }

    /** Whether this language is written right-to-left (Arabic) - see CONFIG.md for kickscreen/embed guidance. */
    public boolean isRightToLeft() {
        return rightToLeft;
    }

    public static Optional<SupportedLocale> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SupportedLocale locale : values()) {
            if (locale.code.equals(normalized)) {
                return Optional.of(locale);
            }
        }
        return Optional.empty();
    }

    /** Maps a client's Java locale (e.g. from Velocity's PlayerSettings) to a supported language by language tag. */
    public static Optional<SupportedLocale> fromJavaLocale(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return fromCode(locale.getLanguage());
    }
}

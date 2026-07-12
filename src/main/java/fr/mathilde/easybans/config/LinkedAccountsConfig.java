package fr.mathilde.easybans.config;

public record LinkedAccountsConfig(boolean notifyStaff, boolean autoBan) {
    static LinkedAccountsConfig fromYaml(YamlSection section) {
        return new LinkedAccountsConfig(
                section.getBoolean("notify-staff", true),
                section.getBoolean("auto-ban", false)
        );
    }
}

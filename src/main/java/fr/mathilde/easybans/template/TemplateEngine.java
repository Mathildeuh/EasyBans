package fr.mathilde.easybans.template;

import java.util.Comparator;
import java.util.List;

/**
 * Pure escalation logic, deliberately kept free of any Velocity/database dependency so it can
 * be unit tested directly: given how many times a template already fired for a player, decides
 * which {@link TemplateStage} applies to their next infraction. Once a player's infraction
 * count passes the last configured stage, that last stage keeps repeating indefinitely.
 */
public final class TemplateEngine {

    private TemplateEngine() {
    }

    public static TemplateStage resolveStage(PunishmentTemplate template, int previousInfractionCount) {
        List<TemplateStage> stages = template.stages();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Template '" + template.id() + "' has no stages configured");
        }
        int infractionNumber = previousInfractionCount + 1;

        List<TemplateStage> sorted = stages.stream()
                .sorted(Comparator.comparingInt(TemplateStage::after))
                .toList();

        TemplateStage chosen = sorted.get(0);
        for (TemplateStage stage : sorted) {
            if (stage.after() <= infractionNumber) {
                chosen = stage;
            } else {
                break;
            }
        }
        return chosen;
    }
}

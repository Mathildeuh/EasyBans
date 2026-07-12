package fr.mathilde.easybans.template;

import fr.mathilde.easybans.punishment.PunishmentType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateEngineTest {

    private final PunishmentTemplate template = new PunishmentTemplate("cheating", PunishmentType.BAN, List.of(
            new TemplateStage(1, Optional.of(Duration.ofDays(3)), "1st offense", Optional.empty()),
            new TemplateStage(2, Optional.of(Duration.ofDays(14)), "2nd offense", Optional.empty()),
            new TemplateStage(3, Optional.empty(), "repeat offense", Optional.of("cheating.final"))
    ));

    @Test
    void firstInfractionUsesFirstStage() {
        TemplateStage stage = TemplateEngine.resolveStage(template, 0);
        assertEquals(Duration.ofDays(3), stage.duration().orElseThrow());
        assertEquals("1st offense", stage.reason());
    }

    @Test
    void secondInfractionUsesSecondStage() {
        TemplateStage stage = TemplateEngine.resolveStage(template, 1);
        assertEquals(Duration.ofDays(14), stage.duration().orElseThrow());
    }

    @Test
    void thirdInfractionUsesFinalStage() {
        TemplateStage stage = TemplateEngine.resolveStage(template, 2);
        assertEquals(Optional.empty(), stage.duration());
        assertEquals("cheating.final", stage.kickscreenKey().orElseThrow());
    }

    @Test
    void infractionsBeyondLastStageRepeatTheLastStage() {
        TemplateStage tenth = TemplateEngine.resolveStage(template, 9);
        assertEquals("repeat offense", tenth.reason());
    }

    @Test
    void stagesOutOfOrderInConfigAreSortedByAfter() {
        PunishmentTemplate shuffled = new PunishmentTemplate("x", PunishmentType.MUTE, List.of(
                new TemplateStage(3, Optional.empty(), "third", Optional.empty()),
                new TemplateStage(1, Optional.of(Duration.ofHours(1)), "first", Optional.empty()),
                new TemplateStage(2, Optional.of(Duration.ofHours(6)), "second", Optional.empty())
        ));
        assertEquals("first", TemplateEngine.resolveStage(shuffled, 0).reason());
        assertEquals("second", TemplateEngine.resolveStage(shuffled, 1).reason());
        assertEquals("third", TemplateEngine.resolveStage(shuffled, 2).reason());
    }

    @Test
    void emptyTemplateThrows() {
        PunishmentTemplate empty = new PunishmentTemplate("empty", PunishmentType.BAN, List.of());
        assertThrows(IllegalArgumentException.class, () -> TemplateEngine.resolveStage(empty, 0));
    }
}

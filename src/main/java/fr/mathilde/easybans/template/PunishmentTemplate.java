package fr.mathilde.easybans.template;

import fr.mathilde.easybans.punishment.PunishmentType;

import java.util.List;

public record PunishmentTemplate(String id, PunishmentType type, List<TemplateStage> stages) {
}

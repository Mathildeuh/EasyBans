package fr.mathilde.easybans.warning;

import java.util.List;

public record WarningThreshold(int count, List<String> commands) {
}

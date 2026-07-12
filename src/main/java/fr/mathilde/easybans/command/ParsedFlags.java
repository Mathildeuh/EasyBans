package fr.mathilde.easybans.command;

import java.util.List;
import java.util.Optional;

/** Result of stripping {@code -s}, {@code -server:<name>} and {@code -t:<templateId>} flags out of a command's arguments. */
public record ParsedFlags(boolean silent, Optional<String> server, Optional<String> template, List<String> remaining) {
}

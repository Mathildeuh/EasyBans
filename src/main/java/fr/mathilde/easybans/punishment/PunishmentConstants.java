package fr.mathilde.easybans.punishment;

import java.util.UUID;

public final class PunishmentConstants {

    /** Sentinel UUID used for staff_uuid when a punishment was issued from the proxy console. */
    public static final UUID CONSOLE_UUID = new UUID(0L, 0L);
    public static final String CONSOLE_NAME = "CONSOLE";

    private PunishmentConstants() {
    }
}

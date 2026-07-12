package fr.mathilde.easybans.sync;

import fr.mathilde.easybans.config.SyncConfig;
import fr.mathilde.easybans.database.dao.SyncEventDao;
import org.slf4j.Logger;

public final class SyncServiceFactory {

    private SyncServiceFactory() {
    }

    public static SyncService create(SyncConfig config, SyncEventDao dao, Logger logger) {
        return switch (config.mode()) {
            case NONE -> new NoopSyncService();
            case REDIS -> new RedisSyncService(config, logger);
            case DATABASE -> new DatabaseSyncService(dao, config.pollIntervalSeconds(), logger);
        };
    }
}

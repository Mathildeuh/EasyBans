package fr.mathilde.easybans.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.mathilde.easybans.config.StorageConfig;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the HikariCP pool and the dedicated executor every database call runs on. No database
 * operation ever touches the Velocity network thread pool - {@link #supplyAsync} /
 * {@link #runAsync} always hop onto {@link #executor()} first.
 */
public final class DatabaseProvider implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final DatabaseType type;
    private final String tablePrefix;
    private final ExecutorService executor;

    public DatabaseProvider(StorageConfig config, Path dataDirectory, Logger logger) {
        this.type = config.type();
        this.tablePrefix = config.tablePrefix();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("EasyBans-Hikari");
        hikariConfig.setMaximumPoolSize(Math.max(2, config.poolSize()));
        hikariConfig.setConnectionTimeout(config.connectionTimeoutMs());

        switch (type) {
            case H2 -> {
                Path dbFile = dataDirectory.resolve(config.h2FileName());
                hikariConfig.setJdbcUrl("jdbc:h2:" + dbFile.toAbsolutePath()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
                hikariConfig.setDriverClassName("org.h2.Driver");
                hikariConfig.setMaximumPoolSize(1); // H2 file mode: single writer is simplest and avoids lock contention
            }
            case MYSQL, MARIADB -> {
                hikariConfig.setJdbcUrl("jdbc:mariadb://" + config.host() + ":" + config.port() + "/"
                        + config.database() + "?useSSL=" + config.useSsl() + "&useUnicode=true&characterEncoding=utf8");
                hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
                hikariConfig.setUsername(config.username());
                hikariConfig.setPassword(config.password());
            }
            case POSTGRESQL -> {
                hikariConfig.setJdbcUrl("jdbc:postgresql://" + config.host() + ":" + config.port() + "/"
                        + config.database() + "?ssl=" + config.useSsl());
                hikariConfig.setDriverClassName("org.postgresql.Driver");
                hikariConfig.setUsername(config.username());
                hikariConfig.setPassword(config.password());
            }
            default -> throw new IllegalStateException("Unsupported database type: " + type);
        }

        this.dataSource = new HikariDataSource(hikariConfig);

        AtomicInteger threadCount = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "EasyBans-DB-" + threadCount.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(Math.max(2, hikariConfig.getMaximumPoolSize()), threadFactory);

        logger.info("Database connection pool ready ({} backend)", type);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DatabaseType type() {
        return type;
    }

    /** Prefixes a bare table name (e.g. {@code "punishments"}) with the configured table prefix. */
    public String table(String name) {
        return tablePrefix + name;
    }

    public ExecutorService executor() {
        return executor;
    }

    public <T> CompletableFuture<T> supplyAsync(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                return function.apply(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> runAsync(SqlConsumer consumer) {
        return supplyAsync(connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        dataSource.close();
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}

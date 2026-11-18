package io.github.hrushibhatt.brewfocus.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;

import javax.sql.DataSource;

import org.sqlite.SQLiteConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The SQLite database, and the clock everything shares.
 *
 * <p>Design decisions:
 *
 * <ul>
 *   <li>SQLite, because one file needs no database server to install or run, which suits a small app
 *       with one process. Moving to PostgreSQL later means a new JDBC URL and driver; the SQL in the
 *       repositories is standard.
 *   <li>Plain JDBC through Spring's {@code JdbcClient} rather than JPA. The queries are few and simple,
 *       so the SQL stays visible in each repository, every value is a bound parameter (which rules out
 *       SQL injection), and there's no ORM dialect to fight on SQLite.
 *   <li>One pooled connection. SQLite serializes writes anyway, and {@code :memory:} gives each
 *       connection its own database. WAL mode lets reads proceed while a write is in flight.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    public static final String IN_MEMORY = ":memory:";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "close")
    DataSource dataSource(BrewFocusProperties props) {
        String path = props.dbPath();
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.enforceForeignKeys(true);
        sqlite.setBusyTimeout(5_000); // a writer waits for the lock instead of failing
        String url;
        if (IN_MEMORY.equals(path)) {
            url = "jdbc:sqlite::memory:";
        } else {
            restrictToOwner(Path.of(path));
            sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
            url = "jdbc:sqlite:" + path;
        }

        HikariConfig pool = new HikariConfig();
        pool.setPoolName("sqlite");
        pool.setJdbcUrl(url);
        pool.setDataSourceProperties(sqlite.toProperties());
        pool.setMaximumPoolSize(1);
        pool.setMaxLifetime(0); // never recycle: for :memory:, a new connection is an empty database
        return new HikariDataSource(pool);
    }

    /** JdbcClient and the repositories use this, so they all get SQLite-aware error translation. */
    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SqliteExceptionTranslator());
        return jdbc;
    }

    /**
     * Makes the database readable by this user alone, since it holds password hashes. SQLite gives its
     * -wal and -shm files the same permissions.
     */
    static void restrictToOwner(Path file) {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        try {
            var ownerOnly = PosixFilePermissions.fromString("rw-------");
            if (Files.notExists(file)) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(ownerOnly));
            }
            Files.setPosixFilePermissions(file, ownerOnly);
        } catch (IOException e) {
            throw new UncheckedIOException("can't prepare the database file " + file, e);
        }
    }
}

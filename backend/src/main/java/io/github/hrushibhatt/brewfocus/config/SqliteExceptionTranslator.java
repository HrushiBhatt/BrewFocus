package io.github.hrushibhatt.brewfocus.config;

import java.sql.SQLException;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;

/**
 * Teaches Spring SQLite's error codes. Spring knows most databases' codes but not SQLite's, so a
 * unique-index violation would otherwise arrive as an "uncategorized" error. With this it's a
 * {@link DuplicateKeyException}, which is how a lost race for an email is told apart from a real
 * failure.
 */
class SqliteExceptionTranslator implements SQLExceptionTranslator {

    private final SQLExceptionTranslator fallback = new SQLStateSQLExceptionTranslator();

    @Override
    public DataAccessException translate(String task, String sql, SQLException ex) {
        if (ex instanceof SQLiteException sqlite) {
            SQLiteErrorCode code = sqlite.getResultCode();
            String message = task + ": " + ex.getMessage();
            if (code == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE || code == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY) {
                return new DuplicateKeyException(message, ex);
            }
            if (code.name().startsWith("SQLITE_CONSTRAINT")) {
                return new DataIntegrityViolationException(message, ex);
            }
        }
        return fallback.translate(task, sql, ex);
    }
}

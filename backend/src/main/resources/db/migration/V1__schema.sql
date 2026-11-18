-- The Brew Focus schema. Flyway applies each V<n>__*.sql file once, in order, and records it, so
-- the schema is versioned with the code instead of being inferred from it.
--
-- Times are stored as epoch milliseconds (INTEGER). SQLite has no date type, and numbers compare and
-- sort correctly with no parsing.

CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT    NOT NULL UNIQUE,
    name          TEXT    NOT NULL DEFAULT '',
    -- bcrypt. NULL for an account made with OAuth 2.0, which then can't sign in with a password.
    password_hash TEXT,
    -- Preferences as JSON, so a new one needs no migration. Missing keys read as their defaults.
    settings      TEXT    NOT NULL,
    created_at    INTEGER NOT NULL
);

-- One row per signed-in browser. The session JWT names a row here, so ending a session is a delete:
-- signing out, changing a password or "sign out everywhere" stops a stolen cookie at once.
CREATE TABLE login_sessions (
    id         TEXT    PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX login_sessions_user ON login_sessions (user_id);

-- An account at an OAuth 2.0 provider, linked to one of ours. The provider's subject is stable;
-- its email can change.
CREATE TABLE oauth_identities (
    provider   TEXT    NOT NULL,
    subject    TEXT    NOT NULL,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (provider, subject)
);
CREATE INDEX oauth_identities_user ON oauth_identities (user_id);

-- A saved custom timing mode, such as "Thesis 75/15".
CREATE TABLE presets (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    focus_min  INTEGER NOT NULL,
    rest_min   INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX presets_user ON presets (user_id);

-- Something to get done over one or more focus blocks.
CREATE TABLE tasks (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      TEXT    NOT NULL,
    estimate   INTEGER NOT NULL, -- planned number of focus blocks
    done       INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);
CREATE INDEX tasks_user ON tasks (user_id);

-- One focus block (breaks aren't stored). A block is written when it starts and updated as it runs,
-- so a restarted server picks it back up. Only state = 'done' counts in the journal and stats.
-- task_id has no foreign key on purpose: deleting a task keeps the time logged against it.
CREATE TABLE focus_sessions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id      INTEGER,
    mode         TEXT    NOT NULL,
    planned      INTEGER NOT NULL,           -- seconds
    focused      INTEGER NOT NULL DEFAULT 0, -- seconds
    completed    INTEGER NOT NULL DEFAULT 0, -- ran to zero rather than ended early
    intention    TEXT    NOT NULL DEFAULT '',
    reflection   TEXT    NOT NULL DEFAULT '',
    distractions INTEGER NOT NULL DEFAULT 0,
    day          TEXT    NOT NULL,           -- the user's local YYYY-MM-DD, so streaks follow their midnight
    state        TEXT    NOT NULL,           -- running, paused or done
    ends_at      INTEGER,                    -- while running
    remaining_ms INTEGER NOT NULL DEFAULT 0, -- while paused
    created_at   INTEGER NOT NULL
);
CREATE INDEX focus_sessions_user_state ON focus_sessions (user_id, state);
CREATE INDEX focus_sessions_state ON focus_sessions (state);

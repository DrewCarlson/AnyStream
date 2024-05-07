/* <editor-fold desc="Table: Metadata"> */

CREATE TABLE IF NOT EXISTS metadata
(
    id                 VARCHAR(24) PRIMARY KEY NOT NULL,
    root_id            VARCHAR(24),
    parent_id          VARCHAR(24),
    parent_index       INTEGER,
    title              TEXT,
    overview           TEXT,
    tmdb_id            INTEGER,
    imdb_id            TEXT,
    runtime            INTEGER,
    'index'            INTEGER,
    content_rating     VARCHAR(30),
    poster_path        TEXT,
    backdrop_path      TEXT,
    first_available_at TEXT,
    created_at         DATETIME                NOT NULL,
    updated_at         DATETIME                NOT NULL,
    media_kind         INTEGER                 NOT NULL,
    media_type         INTEGER                 NOT NULL,
    tmdb_rating        INTEGER,
    UNIQUE (tmdb_id) ON CONFLICT IGNORE
);

CREATE TABLE IF NOT EXISTS tag
(
    id      VARCHAR(24) PRIMARY KEY NOT NULL,
    name    TEXT                    NOT NULL,
    tmdb_id INTEGER
);

CREATE TABLE IF NOT EXISTS metadata_company
(
    metadata_id VARCHAR(24) NOT NULL,
    company_id  VARCHAR(24) NOT NULL,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE CASCADE,
    FOREIGN KEY (company_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS metadata_genre
(
    metadata_id VARCHAR(24) NOT NULL,
    genre_id    VARCHAR(24) NOT NULL,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE VIRTUAL TABLE searchable_content USING fts5
(
    id,
    content,
    media_type,
);
/* </editor-fold> */

/* <editor-fold desc="Table: MediaLink"> */

CREATE TABLE IF NOT EXISTS library
(
    id         VARCHAR(24) PRIMARY KEY NOT NULL,
    media_kind INTEGER                 NOT NULl,
    name       TEXT                    NOT NULL
);

CREATE TABLE IF NOT EXISTS directory
(
    id         VARCHAR(24) PRIMARY KEY NOT NULL,
    parent_id  VARCHAR(24),
    library_id VARCHAR(24),
    file_path  TEXT                    NOT NULL,
    UNIQUE (file_path) ON CONFLICT IGNORE,
    FOREIGN KEY (library_id) REFERENCES library (id) ON UPDATE SET NULL
);

CREATE TABLE IF NOT EXISTS media_link
(
    id               VARCHAR(24) PRIMARY KEY NOT NULL,
    metadata_id      VARCHAR(24),
    root_metadata_id VARCHAR(24),
    directory_id     VARCHAR(24)             NOT NULL,
    created_at       DATETIME                NOT NULL,
    updated_at       DATETIME                NOT NULL,
    media_kind       INTEGER                 NOT NULL,
    type             INTEGER                 NOT NULL,
    file_path        TEXT,
    file_index       INTEGER,
    hash             TEXT,
    descriptor       INTEGER                 NOT NULL,
    UNIQUE (file_path) ON CONFLICT IGNORE,
    FOREIGN KEY (directory_id) REFERENCES directory (id) ON DELETE CASCADE,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE SET NULL,
    FOREIGN KEY (root_metadata_id) REFERENCES metadata (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS playback_state
(
    id            VARCHAR(24) PRIMARY KEY NOT NULL,
    media_link_id VARCHAR(24)             NOT NULL,
    metadata_id   VARCHAR(24)             NOT NULL,
    user_id       VARCHAR(24)             NOT NULL,
    position      DOUBLE                  NOT NULL,
    runtime       DOUBLE                  NOT NULL,
    created_at    DATETIME                NOT NULL,
    updated_at    DATETIME                NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    FOREIGN KEY (media_link_id) REFERENCES media_link (id) ON DELETE CASCADE,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS stream_encoding
(
    id              VARCHAR(24) PRIMARY KEY NOT NULL,
    stream_id       INTEGER,
    codec_name      TEXT                    NOT NULL,
    codec_long_name TEXT,
    'index'         INTEGER,
    language        TEXT,
    profile         TEXT,
    bit_rate        INTEGER,
    channels        INTEGER,
    channel_layout  TEXT,
    level           INTEGER,
    height          INTEGER,
    width           INTEGER,
    type            INTEGER                 NOT NULL,
    title           TEXT,
    pix_fmt         TEXT,
    color_space     TEXT,
    color_range     TEXT,
    color_transfer  TEXT,
    color_primaries TEXT,
    field_order     TEXT,
    sample_fmt      TEXT,
    sample_rate     INTEGER,
    duration        FLOAT,
    media_link_id   VARCHAR(24)             NOT NULL,
    'default'       BOOLEAN                 NOT NULL,
    FOREIGN KEY (media_link_id) REFERENCES media_link (id) ON DELETE CASCADE,
    UNIQUE ('index', type, media_link_id) ON CONFLICT REPLACE
);
/* </editor-fold> */

/* <editor-fold desc="Table: Users"> */

CREATE TABLE IF NOT EXISTS user
(
    id            VARCHAR(24) PRIMARY KEY NOT NULL,
    username      VARCHAR(255)            NOT NULL,
    display_name  VARCHAR(255)            NOT NULL,
    password_hash TEXT                    NOT NULL,
    created_at    DATETIME                NOT NULL,
    updated_at    DATETIME                NOT NULL,
    UNIQUE (username) ON CONFLICT IGNORE
);

CREATE TABLE IF NOT EXISTS user_permission
(
    user_id VARCHAR(24) NOT NULL,
    value   TEXT        NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS session
(
    id      VARCHAR(96) PRIMARY KEY NOT NULL,
    data    TEXT                    NOT NULL,
    user_id VARCHAR(24)             NOT NULL
);

CREATE TABLE IF NOT EXISTS invite_code
(
    secret             VARCHAR(255) PRIMARY KEY NOT NULL,
    permissions        TEXT                     NOT NULL,
    created_by_user_id VARCHAR(24)              NOT NULL,
    FOREIGN KEY (created_by_user_id) REFERENCES user (id) ON DELETE CASCADE
);
/* </editor-fold> */

/* <editor-fold desc="Triggers: Searchable Content"> */

CREATE TRIGGER IF NOT EXISTS create_searchable_content
    AFTER INSERT
    ON metadata
    FOR EACH ROW
BEGIN
    INSERT INTO searchable_content(id, media_type, content) VALUES (NEW.id, NEW.media_type, NEW.title);
END;

CREATE TRIGGER IF NOT EXISTS update_searchable_content
    AFTER UPDATE
    ON metadata
    FOR EACH ROW
BEGIN
    UPDATE searchable_content SET content = NEW.title WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS delete_searchable_content
    AFTER DELETE
    ON metadata
    FOR EACH ROW
BEGIN
    DELETE FROM searchable_content WHERE id = OLD.id;
END;

CREATE TRIGGER IF NOT EXISTS delete_metadata
    AFTER DELETE
    ON metadata
    FOR EACH ROW
BEGIN
    DELETE
    FROM metadata
    WHERE metadata.root_id = OLD.id
       OR metadata.parent_id = OLD.id;

    DELETE
    FROM searchable_content
    WHERE searchable_content.id = OLD.id;
END;
/* </editor-fold> */

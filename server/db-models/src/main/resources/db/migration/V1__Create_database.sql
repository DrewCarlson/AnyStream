/* <editor-fold desc="Table: Users"> */
CREATE TABLE user
(
    id            VARCHAR(24) PRIMARY KEY NOT NULL,
    username      VARCHAR(255)            NOT NULL,
    display_name  VARCHAR(255)            NOT NULL,
    password_hash TEXT,
    created_at    TEXT                    NOT NULL,
    updated_at    TEXT                    NOT NULL,
    auth_type     TEXT                    NOT NULL,
    UNIQUE (username) ON CONFLICT FAIL
);


CREATE TABLE user_permission
(
    user_id VARCHAR(24) NOT NULL,
    value   TEXT        NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
);

CREATE TABLE session
(
    id      VARCHAR(96) PRIMARY KEY NOT NULL,
    data    TEXT                    NOT NULL,
    user_id VARCHAR(24)             NOT NULL
);

CREATE TABLE invite_code
(
    secret             VARCHAR(255) PRIMARY KEY NOT NULL,
    permissions        TEXT                     NOT NULL,
    created_by_user_id VARCHAR(24)              NOT NULL,
    FOREIGN KEY (created_by_user_id) REFERENCES user (id) ON DELETE CASCADE
);
/* </editor-fold> */

/* <editor-fold desc="Table: Metadata"> */

CREATE TABLE metadata
(
    id                 VARCHAR(24) PRIMARY KEY NOT NULL,
    root_id            VARCHAR(24),
    parent_id          VARCHAR(24),
    parent_index       INTEGER,
    title              TEXT,
    overview           TEXT,
    tmdb_id            INTEGER,
    imdb_id            TEXT,
    runtime            TEXT,
    `index`            INTEGER,
    content_rating     VARCHAR(30),
    first_available_at TEXT,
    created_at         TEXT                    NOT NULL,
    updated_at         TEXT                    NOT NULL,
    media_kind         TEXT                    NOT NULL,
    media_type         TEXT                    NOT NULL,
    tmdb_rating        INTEGER,
    UNIQUE (tmdb_id, media_kind) ON CONFLICT FAIL
);

CREATE TABLE tag
(
    id      VARCHAR(24) PRIMARY KEY NOT NULL,
    name    TEXT                    NOT NULL,
    tmdb_id INTEGER
);

CREATE TABLE metadata_company
(
    metadata_id VARCHAR(24) NOT NULL,
    company_id  VARCHAR(24) NOT NULL,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE CASCADE,
    FOREIGN KEY (company_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE TABLE metadata_genre
(
    metadata_id VARCHAR(24) NOT NULL,
    genre_id    VARCHAR(24) NOT NULL,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id) REFERENCES tag (id) ON DELETE CASCADE
);
/* </editor-fold> */

/* <editor-fold desc="Table: MediaLink"> */

CREATE TABLE library
(
    id         VARCHAR(24) PRIMARY KEY NOT NULL,
    media_kind TEXT                    NOT NULl,
    name       TEXT                    NOT NULL
);

CREATE TABLE directory
(
    id         VARCHAR(24) PRIMARY KEY NOT NULL,
    parent_id  VARCHAR(24),
    library_id VARCHAR(24)             NOT NULL,
    file_path  TEXT                    NOT NULL,
    UNIQUE (file_path) ON CONFLICT FAIL,
    FOREIGN KEY (library_id) REFERENCES library (id) ON UPDATE SET NULL
);

CREATE TABLE media_link
(
    id               VARCHAR(24) PRIMARY KEY NOT NULL,
    metadata_id      VARCHAR(24),
    root_metadata_id VARCHAR(24),
    directory_id     VARCHAR(24)             NOT NULL,
    created_at       TEXT                    NOT NULL,
    updated_at       TEXT                    NOT NULL,
    media_kind       TEXT                    NOT NULL,
    type             TEXT                    NOT NULL,
    file_path        TEXT,
    file_index       INTEGER,
    hash             TEXT,
    descriptor       INTEGER                 NOT NULL,
    UNIQUE (file_path) ON CONFLICT FAIL,
    FOREIGN KEY (directory_id) REFERENCES directory (id) ON DELETE CASCADE,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE SET NULL,
    FOREIGN KEY (root_metadata_id) REFERENCES metadata (id) ON DELETE SET NULL
);

CREATE TABLE playback_state
(
    id            VARCHAR(24) PRIMARY KEY NOT NULL,
    media_link_id VARCHAR(24)             NOT NULL,
    metadata_id   VARCHAR(24)             NOT NULL,
    user_id       VARCHAR(24)             NOT NULL,
    position      TEXT                    NOT NULL,
    runtime       TEXT                    NOT NULL,
    created_at    TEXT                    NOT NULL,
    updated_at    TEXT                    NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    FOREIGN KEY (media_link_id) REFERENCES media_link (id) ON DELETE CASCADE,
    FOREIGN KEY (metadata_id) REFERENCES metadata (id) ON DELETE CASCADE
);

CREATE TABLE stream_encoding
(
    id              VARCHAR(24) PRIMARY KEY NOT NULL,
    stream_id       TEXT,
    codec_name      TEXT                    NOT NULL,
    codec_long_name TEXT,
    `index`         INTEGER,
    language        TEXT,
    profile         TEXT,
    bit_rate        INTEGER,
    channels        INTEGER,
    channel_layout  TEXT,
    level           INTEGER,
    height          INTEGER,
    width           INTEGER,
    type            TEXT                    NOT NULL,
    title           TEXT,
    pix_fmt         TEXT,
    color_space     TEXT,
    color_range     TEXT,
    color_transfer  TEXT,
    color_primaries TEXT,
    field_order     TEXT,
    sample_fmt      TEXT,
    sample_rate     INTEGER,
    duration        TEXT,
    media_link_id   VARCHAR(24)             NOT NULL,
    `default`       BOOLEAN                 NOT NULL,
    FOREIGN KEY (media_link_id) REFERENCES media_link (id) ON DELETE CASCADE,
    UNIQUE (`index`, type, media_link_id) ON CONFLICT REPLACE
);
/* </editor-fold> */

/* <editor-fold desc="Triggers: Searchable Content"> */
CREATE
VIRTUAL TABLE searchable_content USING fts5
(
    id,
    content,
    media_type
);

CREATE TRIGGER create_searchable_content
    AFTER INSERT
    ON metadata
    FOR EACH ROW
BEGIN
    INSERT INTO searchable_content(id, media_type, content) VALUES (NEW.id, NEW.media_type, NEW.title);
END;

CREATE TRIGGER update_searchable_content
    AFTER UPDATE
    ON metadata
    FOR EACH ROW
BEGIN
    UPDATE searchable_content SET content = NEW.title WHERE id = NEW.id;
END;

CREATE TRIGGER delete_searchable_content
    AFTER DELETE
    ON metadata
    FOR EACH ROW
BEGIN
    DELETE FROM searchable_content WHERE id = OLD.id;
END;

CREATE TRIGGER delete_metadata
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

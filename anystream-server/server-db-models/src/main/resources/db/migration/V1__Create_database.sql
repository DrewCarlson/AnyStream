CREATE TABLE IF NOT EXISTS media
(
    id               INTEGER PRIMARY KEY NOT NULL,
    gid              VARCHAR(24)         NOT NULL,
    rootId           INT,
    rootGid          TEXT,
    parentId         INT,
    parentGid        TEXT,
    parentIndex      INT,
    title            TEXT,
    overview         TEXT,
    tmdbId           INT,
    imdbId           TEXT,
    runtime          INT,
    'index'          INT,
    contentRating    VARCHAR(30),
    posterPath       TEXT,
    backdropPath     TEXT,
    firstAvailableAt TEXT,
    createdAt        TEXT                NOT NULL,
    updatedAt        TEXT                NOT NULL,
    addedByUserId    VARCHAR(24)         NOT NULL,
    mediaKind        INTEGER             NOT NULL,
    mediaType        INTEGER             NOT NULL,
    tmdbRating       INTEGER
);

CREATE TABLE IF NOT EXISTS mediaReferences
(
    id             INTEGER PRIMARY KEY NOT NULL,
    gid            TEXT                NOT NULL,
    contentGid     TEXT                NOT NULL,
    rootContentGid TEXT,
    addedAt        TEXT                NOT NULL,
    addedByUserId  INT                 NOT NULL,
    mediaKind      VARCHAR             NOT NULL,
    type           VARCHAR             NOT NULL,
    updatedAt      TEXT                NOT NULL,
    filePath       TEXT,
    directory      INTEGER,
    hash           TEXT,
    fileIndex      INTEGER
);

CREATE TABLE IF NOT EXISTS tags
(
    id INTEGER PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    tmdbId INT
);

CREATE TABLE IF NOT EXISTS mediaCompanies
(
    mediaId   INTEGER NOT NULL,
    companyId INTEGER NOT NULL,
    FOREIGN KEY (mediaId)   REFERENCES media (id) ON DELETE CASCADE,
    FOREIGN KEY (companyId) REFERENCES tags (id)  ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mediaGenres
(
    mediaId INTEGER NOT NULL,
    genreId INTEGER NOT NULL,
    FOREIGN KEY (mediaId) REFERENCES media (id) ON DELETE CASCADE,
    FOREIGN KEY (genreId) REFERENCES tags (id)  ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS users
(
    id           INTEGER PRIMARY KEY NOT NULL,
    username     VARCHAR(255)        NOT NULL,
    displayName  VARCHAR(255)        NOT NULL,
    passwordHash TEXT                NOT NULL,
    createdAt    TEXT                NOT NULL,
    updatedAt    TEXT                NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions
(
    userId INTEGER PRIMARY KEY NOT NULL,
    value  VARCHAR(255)        NOT NULL
);

CREATE TABLE IF NOT EXISTS playbackStates
(
    id               INTEGER PRIMARY KEY NOT NULL,
    gid              TEXT                NOT NULL,
    mediaReferenceId TEXT                NOT NULL,
    mediaGid         TEXT                NOT NULL,
    userId           INT                 NOT NULL,
    position         DOUBLE              NOT NULL,
    runtime          DOUBLE              NOT NULL,
    createdAt        TEXT                NOT NULL,
    updatedAt        TEXT                NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions
(
    id   VARCHAR(96) PRIMARY KEY NOT NULL,
    data TEXT                    NOT NULL
);

CREATE TABLE IF NOT EXISTS streamEncoding
(
    id           INTEGER PRIMARY KEY NOT NULL,
    codecName    TEXT                NOT NULL,
    'index'      INTEGER,
    language     TEXT,
    profile      TEXT,
    bitRate      INTEGER,
    channels     INTEGER,
    level        INTEGER,
    height       INTEGER,
    width        INTEGER,
    type         INTEGER,
    title        TEXT
);

CREATE TABLE IF NOT EXISTS streamEncodingLinks
(
    mediaRefId INTEGER NOT NULL,
    streamId   INTEGER NOT NULL,
    FOREIGN KEY (mediaRefId) REFERENCES media (id) ON DELETE CASCADE,
    FOREIGN KEY (streamId) REFERENCES streamEncoding (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS inviteCodes
(
    id              INTEGER PRIMARY KEY NOT NULL,
    secret          VARCHAR(255) NOT NULL,
    permissions     TEXT NOT NULL,
    createdByUserId INT NOT NULL
);
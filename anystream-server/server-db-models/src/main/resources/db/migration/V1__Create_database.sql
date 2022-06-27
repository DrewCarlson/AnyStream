CREATE TABLE IF NOT EXISTS metadata
(
    id               INTEGER PRIMARY KEY NOT NULL,
    gid              VARCHAR(24)         NOT NULL,
    rootId           INTEGER,
    rootGid          TEXT,
    parentId         INTEGER,
    parentGid        TEXT,
    parentIndex      INTEGER,
    title            TEXT,
    overview         TEXT,
    tmdbId           INTEGER,
    imdbId           TEXT,
    runtime          INTEGER,
    'index'          INTEGER,
    contentRating    VARCHAR(30),
    posterPath       TEXT,
    backdropPath     TEXT,
    firstAvailableAt TEXT,
    createdAt        TEXT                NOT NULL,
    updatedAt        TEXT                NOT NULL,
    addedByUserId    VARCHAR(24)         NOT NULL,
    mediaKind        INTEGER             NOT NULL,
    mediaType        INTEGER             NOT NULL,
    tmdbRating       INTEGER,
    UNIQUE (tmdbId) ON CONFLICT IGNORE
);

CREATE TABLE IF NOT EXISTS mediaLink
(
    id                 INTEGER PRIMARY KEY NOT NULL,
    gid                TEXT                NOT NULL,
    metadataId         INTEGER,
    metadataGid        TEXT,
    rootMetadataId     INTEGER,
    rootMetadataGid    TEXT,
    parentMediaLinkId  INTEGER,
    parentMediaLinkGid TEXT,
    addedAt            TEXT                NOT NULL,
    addedByUserId      INTEGER             NOT NULL,
    mediaKind          VARCHAR             NOT NULL,
    type               VARCHAR             NOT NULL,
    updatedAt          TEXT                NOT NULL,
    filePath           TEXT,
    directory          INTEGER,
    hash               TEXT,
    fileIndex          INTEGER,
    descriptor         VARCHAR,
    UNIQUE (filePath) ON CONFLICT IGNORE
);

CREATE TABLE IF NOT EXISTS tags
(
    id     INTEGER PRIMARY KEY NOT NULL,
    name   TEXT                NOT NULL,
    tmdbId INT
);

CREATE TABLE IF NOT EXISTS metadataCompanies
(
    metadataId INTEGER NOT NULL,
    companyId  INTEGER NOT NULL,
    FOREIGN KEY (metadataId) REFERENCES metadata (id) ON DELETE CASCADE,
    FOREIGN KEY (companyId) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS metadataGenres
(
    metadataId INTEGER NOT NULL,
    genreId    INTEGER NOT NULL,
    FOREIGN KEY (metadataId) REFERENCES metadata (id) ON DELETE CASCADE,
    FOREIGN KEY (genreId) REFERENCES tags (id) ON DELETE CASCADE
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
    id          INTEGER PRIMARY KEY NOT NULL,
    gid         TEXT                NOT NULL,
    mediaLinkId TEXT                NOT NULL,
    metadataGid TEXT                NOT NULL,
    userId      INTEGER             NOT NULL,
    position    DOUBLE              NOT NULL,
    runtime     DOUBLE              NOT NULL,
    createdAt   TEXT                NOT NULL,
    updatedAt   TEXT                NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions
(
    id   VARCHAR(96) PRIMARY KEY NOT NULL,
    data TEXT                    NOT NULL
);

CREATE TABLE IF NOT EXISTS streamEncoding
(
    id             INTEGER PRIMARY KEY NOT NULL,
    streamId       INTEGER,
    codecName      TEXT                NOT NULL,
    codecLongName  TEXT,
    'index'        INTEGER,
    language       TEXT,
    profile        TEXT,
    bitRate        INTEGER,
    channels       INTEGER,
    channelLayout  TEXT,
    level          INTEGER,
    height         INTEGER,
    width          INTEGER,
    type           INTEGER,
    title          TEXT,
    pixFmt         TEXT,
    colorSpace     TEXT,
    colorRange     TEXT,
    colorTransfer  TEXT,
    colorPrimaries TEXT,
    fieldOrder     TEXT,
    sampleFmt      TEXT,
    sampleRate     TEXT,
    duration       INTEGER,
    mediaLinkId    INTEGER             NOT NULL,
    'default'      INTEGER,
    FOREIGN KEY (mediaLinkId) REFERENCES mediaLink (id) ON DELETE CASCADE,
    UNIQUE ('index', type, mediaLinkId) ON CONFLICT REPLACE
);

CREATE TABLE IF NOT EXISTS inviteCodes
(
    id              INTEGER PRIMARY KEY NOT NULL,
    secret          VARCHAR(255)        NOT NULL,
    permissions     TEXT                NOT NULL,
    createdByUserId INTEGER             NOT NULL
);
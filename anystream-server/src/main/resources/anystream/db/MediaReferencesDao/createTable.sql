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

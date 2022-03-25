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
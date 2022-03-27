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

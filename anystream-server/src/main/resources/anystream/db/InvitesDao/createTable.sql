CREATE TABLE IF NOT EXISTS inviteCodes
(
    id              INTEGER PRIMARY KEY NOT NULL,
    secret          VARCHAR(255) NOT NULL,
    permissions     TEXT NOT NULL,
    createdByUserId INT NOT NULL
);
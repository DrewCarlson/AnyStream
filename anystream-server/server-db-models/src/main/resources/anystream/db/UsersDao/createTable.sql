CREATE TABLE IF NOT EXISTS users
(
    id           INTEGER PRIMARY KEY NOT NULL,
    username     VARCHAR(255)        NOT NULL,
    displayName  VARCHAR(255)        NOT NULL,
    passwordHash TEXT                NOT NULL,
    createdAt    TEXT                NOT NULL,
    updatedAt    TEXT                NOT NULL
);
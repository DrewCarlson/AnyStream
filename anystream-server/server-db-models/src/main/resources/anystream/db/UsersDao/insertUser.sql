INSERT INTO users
    (id, username, displayName, passwordHash, createdAt, updatedAt)
VALUES (NULL,
        :user.username,
        :user.displayName,
        :passwordHash,
        :createdAt,
        :createdAt)
CREATE TABLE IF NOT EXISTS streamEncodingLinks
(
    mediaRefId INTEGER NOT NULL,
    streamId   INTEGER NOT NULL,
    FOREIGN KEY (mediaRefId) REFERENCES media (id) ON DELETE CASCADE,
    FOREIGN KEY (streamId) REFERENCES streamEncoding (id) ON DELETE CASCADE
);
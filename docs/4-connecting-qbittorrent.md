# Connecting qBittorrent

*[qBittorrent](https://www.qbittorrent.org/) is a free and reliable P2P BitTorrent client.*

AnyStream can communicate with qBittorrent to manage importing and organizing new media into your library.

???+ warning

    You assume all responsibility and risk when using qBittorrent.
    The [BitTorrent](https://en.wikipedia.org/wiki/BitTorrent) protocol is not designed
    for privacy, ensure you've configured qBittorrent and network firewalls correctly.
    **It is recommended to use a VPN service like [AirVPN](https://airvpn.org/) or [Mullvad](https://mullvad.net/) with qBittorrent.**

## Configuration

qBittorrent integration is configured using three settings that point to the qBittorrent Web API:

| Setting  | Environment Variable | Description                                                 |
|----------|----------------------|-------------------------------------------------------------|
| URL      | `QBT_URL`            | The qBittorrent Web API URL (e.g. `http://localhost:9090`). |
| Username | `QBT_USER`           | The qBittorrent Web UI username.                            |
| Password | `QBT_PASSWORD`       | The qBittorrent Web UI password.                            |

These can also be set in the config file:

```hocon
app {
    qbittorrent {
        url = "http://localhost:9090"
        user = "admin"
        password = "adminadmin"
    }
}
```

If qBittorrent credentials are not configured, the torrent management features are disabled and will not appear
in the interface.

## Setting Up qBittorrent

### With Docker

The easiest way to run qBittorrent alongside AnyStream is with Docker Compose.
See [Installation > Docker](installation/docker.md) for a complete example with both services.

### Standalone

1. [Download and install qBittorrent](https://www.qbittorrent.org/download) on your system.
2. Enable the Web UI in qBittorrent: Tools > Preferences > Web UI.
3. Set a port for the Web UI (e.g. `9090`) and note the username and password.
4. Configure AnyStream with the qBittorrent connection settings as shown above.

### VPN Configuration

It is strongly recommended to run qBittorrent through a VPN. If you are using Docker, the
[drewcarlson/docker-qbittorrentvpn](https://github.com/DrewCarlson/docker-qbittorrentvpn) image includes
built-in VPN support.

## Using qBittorrent in AnyStream

Once connected, users with the **Manage Torrents** permission can:

- View all active torrents and their progress
- Add new torrents
- Pause and resume individual torrents
- Delete torrents (with or without downloaded files)
- Monitor global transfer statistics (upload/download speeds)

Torrent management is available in the AnyStream web client under the torrents section.

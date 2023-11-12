# Docker


A small Alpine based docker image is provided
at [ghcr.io/drewcarlson/anystream](https://github.com/DrewCarlson/AnyStream/pkgs/container/anystream).

!!! note

    In the future, a ubuntu based image with hardware transcoding support will be provided.

### Docker CLI

!!! example "Docker CLI example"

    ```shell
    docker run -d --name anystream \
        -v /path/to/anystream:/app/storage \
        -v /path/to/media:/app/media \
        -p 8888:8888 \
        ghcr.io/drewcarlson/anystream:main
    ```

### Docker Compose

Create a `docker-compose.yml` copy one of the following examples:

!!! example "Without qBittorrent"

    ```yaml
    version: '3.1'
    services:
      anystream:
        container_name: anystream
        image: ghcr.io/drewcarlson/anystream:main
        restart: unless-stopped
        ports:
          - "8888:8888"
        volumes:
          - /path/to/anystream:/app/storage
          - /path/to/media:/app/media
    ```

??? example "With qBittorrent"

    ```yaml
    version: '3.1'
    services:
      anystream:
        container_name: anystream
        image: ghcr.io/drewcarlson/anystream:main
        restart: unless-stopped
        ports:
          - "8888:8888"
        environment:
          QBT_URL: http://qbittorrent:9090
          QBT_USER: admin
          QBT_PASSWORD: adminadmin
        volumes:
          - /path/to/anystream:/app/storage
          - /path/to/media:/app/media
          - /path/to/qbittorrent/downloads:/app/downloads
        links:
          - qbittorrent
        depends_on:
          - qbittorrent
    
      qbittorrent:
        image: drewcarlson/docker-qbittorrentvpn
        container_name: qbittorrent
        restart: unless-stopped
        cap_add:
          - NET_ADMIN
        sysctls:
          - net.ipv6.conf.all.disable_ipv6=0
        privileged: true
        environment:
          - VPN_ENABLED=yes
          - NAME_SERVERS=1.1.1.1,8.8.8.8
          - WEBUI_PORT=9090
          - TZ=America/Los_Angeles
          - UMASK_SET=022
        volumes:
          - /path/to/qbittorrent/config:/config
          - /path/to/qbittorrent/downloads:/downloads
          - /path/to/media:/content
    ```

Once you've configured the `docker-compose.yml` file, start it with:

```shell
docker-compose up -d
```

## Configure Server

Now your server is running and ready to be used!
See [Installation > Configure Server](configure-server.md) for what to do next.

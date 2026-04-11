# Configure Server

_Is your server running? See [Installation > Getting Started](getting-started.md) first._

When the server has started without errors, the web client will be available
at [localhost:8888](http://localhost:8888) or whatever port you've specified.

## Create an Admin User

After starting a new AnyStream instance, the first user signup does not require an invitation code and will be granted
global permissions.

!!! danger

    It is important to register an admin user immediately after starting AnyStream for the first time.
    If left incomplete, unauthorized users may be able to modify system files via AnyStream.

## Config File

AnyStream supports configuration through a YAML config file in addition to environment variables and CLI arguments.
The config file path is set with the `CONFIG_PATH` environment variable or the `-config=` CLI argument.

Supported file extensions: `.yml` or `.yaml`.

If the specified config file does not exist, AnyStream will create one with default values on startup.

!!! example "Example YAML config (`anystream.yml`)"

    ```yaml
    app:
      data_path: /path/to/anystream/data
      database_url: /path/to/anystream/data/anystream.db
      ffmpeg_path: /usr/bin
      transcode_path: /tmp
      base_url: https://stream.example.com

      qbittorrent:
        url: http://localhost:9090
        user: admin
        password: adminadmin

      libraries:
        tv:
          directories:
            - /media/TV
        movies:
          directories:
            - /media/Movies
        music:
          directories:
            - /media/Music

      oidc:
        enable: false
        provider:
          name: my-provider
          endpoint: https://auth.example.com
          client_id: anystream
          client_secret: your-secret
    ```

Environment variables override config file values. This allows you to keep a base config file and override
specific settings per environment.

## Importing Media

Importing your media files starts by adding a directory to a Library.

Before adding a library directory, ensure your media files follow the guidelines in
[File and Directory Naming](../2-library-management.md#file-and-directory-naming).

1. Navigate to Settings > Libraries
2. Click "Edit" on the desired Library
3. Click "Add Folder"
4. Select or paste the folder path and click "Add"

That's it! All the media files will be scanned and metadata will be downloaded.
You can navigate to the home page to see your library populate in real time.

### Pre-configuring Library Directories

Library directories can also be set in the config file or environment, which is useful for Docker deployments
where the media paths are known ahead of time. See the `libraries` section in the config file examples above.

Directories configured this way are added automatically on startup.

## Inviting Users

You can add new users manually or create unique URLs to share which allow a single person to create an account with the
desired permissions.

To create an invitation URL:

1. Navigate to Settings > Users, then click "Manage Invites".
2. Select the permissions required by the new User, then click "Create Invite".
3. The new invite will be added to the list and a sharable signup link will be copied to your clipboard.

The available permissions are:

| Permission        | Description                                                   |
|-------------------|---------------------------------------------------------------|
| View Collection   | Browse and stream media from the library.                     |
| Manage Collection | Add, remove, and scan library directories.                    |
| Manage Torrents   | View, add, pause, resume, and delete torrents in qBittorrent. |
| Configure System  | Access admin settings, view active sessions and server logs.  |

An admin user (with Global permissions) has all permissions and can manage other users.

## Reverse Proxy

When running AnyStream behind a reverse proxy (e.g. Nginx, Caddy, Traefik), set the `BASE_URL` environment variable
or config option to your public URL so that redirects and OIDC callbacks work correctly.

AnyStream supports `X-Forwarded-For`, `X-Forwarded-Proto`, and standard `Forwarded` headers automatically when
`BASE_URL` is not set.

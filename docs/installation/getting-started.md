# Getting started

This page covers running the AnyStream server manually.
See [Installation > Docker](docker.md) to use the containerized server.

### Read this first

AnyStream is a private streaming service for your media files. Third party APIs and optional external applications are
used to improve the quality of your collection. You are responsible for operating any external applications that
AnyStream communicates with while managing your collection.

**To maintain privacy it is recommended that you run AnyStream with a VPN like [AirVPN](https://airvpn.org/)
or [Mullvad](https://mullvad.net/).**

## Requirements

### Java 21+

AnyStream requires Java 21 or later. Any compatible JDK or JRE distribution will work.
[Azul Zulu](https://www.azul.com/downloads/?version=java-21-lts&package=jre) builds are a good choice.

#### Windows

??? info "Install Manually"

    Download the [JRE 21](https://www.azul.com/downloads/?version=java-21-lts&os=windows&package=jre) MSI file and run the installer.
    Follow the instructions until the installation is complete. For more information
    see "[Install Azul Zulu with MSI installer](https://docs.azul.com/core/zulu-openjdk/install/windows#install-azul-zulu-with-msi-installer)"

??? info "Install with Chocolatey"

    ```shell
    choco install zulu21-jre
    ```

#### macOS

??? info "Install Manually"

    Download the [JRE 21 for Intel](https://www.azul.com/downloads/?version=java-21-lts&os=macos&architecture=x86-64-bit&package=jre)
    DMG file or [JRE 21 for Apple Silicon](https://www.azul.com/downloads/?version=java-21-lts&os=macos&architecture=arm-64-bit&package=jre).
    Double click the file and follow the instructions until the installation is complete.

??? info "Install with Homebrew"

    ```shell
    brew install --cask zulu@21
    ```

#### Linux

??? info "Install with APT (Debian/Ubuntu)"

    ```shell
    sudo apt install gnupg ca-certificates curl
    curl -s https://repos.azul.com/azul-repo.key | sudo gpg --dearmor -o /usr/share/keyrings/azul.gpg
    echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" | sudo tee /etc/apt/sources.list.d/zulu.list
    sudo apt update
    sudo apt install zulu21-jre-headless
    ```

??? info "Install with DNF (Fedora/RHEL)"

    ```shell
    sudo dnf install https://cdn.azul.com/zulu/bin/zulu-repo-1.0.0-1.noarch.rpm
    sudo dnf install zulu21-jre-headless
    ```

### FFmpeg

[FFmpeg](https://ffmpeg.org/) is required to [transcode](https://en.wikipedia.org/wiki/Transcoding) your media library
when streaming to certain devices and analyzing media files.

AnyStream will automatically search for FFmpeg in common installation directories.
If it cannot be found, set the `FFMPEG_PATH` environment variable or config option to the directory containing the
`ffmpeg` and `ffprobe` binaries.

#### Windows

??? info "Install Manually"

    Download a recent FFmpeg build from [gyan.dev](https://www.gyan.dev/ffmpeg/builds/) or
    [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds/releases).
    Extract the archive and add the `bin` folder to your system PATH, or set `FFMPEG_PATH` to point to the `bin` directory.

??? info "Install with Chocolatey"

    ```shell
    choco install ffmpeg
    ```

#### macOS

??? info "Install with Homebrew"

    ```shell
    brew install ffmpeg
    ```

??? info "Install with MacPorts"

    ```shell
    sudo port install ffmpeg
    ```

#### Linux

??? info "Install with APT (Debian/Ubuntu)"

    ```shell
    sudo apt install ffmpeg
    ```

??? info "Install with DNF (Fedora/RHEL)"

    ```shell
    sudo dnf install ffmpeg-free
    ```

## Download AnyStream

???+ tip "Stay up-to-date"

    It is recommended the latest version of AnyStream is used at all times, but older versions are available on the
    [Releases](https://github.com/DrewCarlson/AnyStream/releases) page if required.

??? tip "One download for any Operating System"

    The AnyStream server runs on Linux, macOS, or Windows with one download, you do not need a version specifically
    for your operating system.

The latest release can be viewed on the [Github Release](https://github.com/drewcarlson/AnyStream/releases/latest) page
or choose your preferred format below:

|                                                                                                     **Download ZIP**                                                                                                      |                                                                                                       Download TAR                                                                                                        |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| [<span style="font-size:45pt;">:material-zip-box-outline:</span><br/>anystream-server-{{as_version}}.zip](https://github.com/DrewCarlson/AnyStream/releases/download/v{{as_version}}/anystream-server-{{as_version}}.zip) | [<span style="font-size:45pt;">:material-zip-box-outline:</span><br/>anystream-server-{{as_version}}.tar](https://github.com/DrewCarlson/AnyStream/releases/download/v{{as_version}}/anystream-server-{{as_version}}.tar) |

## Installation

AnyStream tries to provide optimal default configuration and can be run without any configuration.

??? info "Configuration (Environment variables)"

    The following options can be modified on your first run to customize AnyStream for your system.
    
    | Name              | Default                                                                                                          | Description                                                                                                             |
    |-------------------|------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
    | `PORT`            | `8888`                                                                                                           | The port used to serve the web client and API.                                                                          |
    | `DATA_PATH`       | macos = `/Users/<user>/anystream`<br/>linux = `/home/<user>/anystream`<br/>windows = `C:\Users\<user>\anystream` | The folder where all data generated by AnyStream will be stored. This is not the folder for your media collection.      |
    | `DATABASE_URL`    | `<DATA_PATH>/anystream.db`                                                                                       | The file path where the SQLite database will be stored.                                                                 |
    | `CONFIG_PATH`     | (none)                                                                                                           | Path to a YAML configuration file (`.yml` or `.yaml`). See [Configure Server](configure-server.md) for details.        |
    | `BASE_URL`        | (none)                                                                                                           | The public URL of your AnyStream instance (e.g. `https://stream.example.com`). Required when behind a reverse proxy.   |
    | `FFMPEG_PATH`     | (auto-detected)                                                                                                  | The directory containing [FFmpeg](https://www.ffmpeg.org/download.html) and FFprobe binaries.                           |
    | `TRANSCODE_PATH`  | `/tmp`                                                                                                           | The directory used for temporary transcode output files.                                                                |
    | `WEB_CLIENT_PATH` | (none)                                                                                                           | The folder containing the web client files to serve. By default these files are provided by the server binary.          |

    For qBittorrent and OIDC configuration, see [Connecting qBittorrent](../4-connecting-qbittorrent.md) and [OIDC Authentication](../5-oidc-authentication.md).

??? info "Configuration (Program arguments)"

    If preferred, AnyStream accepts arguments instead of Environment variables.
    The table below maps the Environment variable name to the CLI argument, see "Configuration (Environment variables)" for descriptions of each option.
    
    | Env Name          | CLI Argument                 |
    |-------------------|------------------------------|
    | `PORT`            | `-port=8888`                 |
    | `DATA_PATH`       | `-app.data_path="..."`       |
    | `DATABASE_URL`    | `-app.database_url="..."`    |
    | `CONFIG_PATH`     | `-config="..."`              |
    | `BASE_URL`        | `-app.base_url="..."`        |
    | `FFMPEG_PATH`     | `-app.ffmpeg_path="..."`     |
    | `TRANSCODE_PATH`  | `-app.transcode_path="..."`  |
    | `WEB_CLIENT_PATH` | `-app.web_client_path="..."` |

### Run on Windows

Newer versions of Windows 10 include `curl` and `tar`, if you're running an older version of Windows, follow the
"Manual Download" section. Otherwise, see the "Command Prompt Download" section.

??? info "Manual Download"

    1. Download [anystream-server-{{ as_version }}.zip](https://github.com/DrewCarlson/AnyStream/releases/download/v{{ as_version }}/anystream-server-{{ as_version }}.zip)
    1. Right click `anystream-server-{{ as_version }}.zip`, click "Extract All...", then click "Extract" when the window appears
    1. Open the `anystream-server-{{ as_version }}/bin` folder
    1. Double click on `anystream.bat`

    You will see a Command Prompt window appear, displaying log messages from AnyStream.

??? info "Command Prompt Download"

    ```shell
    > curl -LO https://github.com/DrewCarlson/AnyStream/releases/download/v{{ as_version }}/anystream-server-{{ as_version }}.tar
    > tar -xvf anystream-server-{{ as_version }}.tar
    > cd anystream-{{ as_version }}/bin
    > anystream -port=8888
    ```

    AnyStream will be running and printing log messages until you close the window or press `ctrl + c`.

### Run on Linux or macOS

??? info "anystream from Terminal example"

    ```shell
    $ curl -LO https://github.com/DrewCarlson/AnyStream/releases/download/v{{ as_version }}/anystream-server-{{ as_version }}.tar
    $ tar -xvf anystream-server-{{ as_version }}.tar
    $ cd anystream-{{ as_version }}/bin
    $ ./anystream -port=8888
    ```

## Configure Server

Now your server is running and ready to be used!
See [Installation > Configure Server](configure-server.md) for what to do next.

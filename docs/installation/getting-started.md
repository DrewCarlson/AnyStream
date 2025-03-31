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

### Java 11+

#### Windows

??? info "Install Manually"

    Download the [JRE 11](https://www.azul.com/downloads/?version=java-11-lts&package=jre) MSI file and run the installer.
    Follow the instructions until the installation is complete. For more information
    see "[Install Azul Zulu with MSI installer](https://docs.azul.com/core/zulu-openjdk/install/windows#install-azul-zulu-with-msi-installer)"

??? info "Install with Chocolatey"

    ```shell
    choco install zulu --version=11.29.11
    ```

#### macOS

??? info "Install Manually"

    Download the [JRE 11 for Intel](https://www.azul.com/downloads/?version=java-11-lts&os=macos&architecture=x86-64-bit&package=jdk)
    DMG file or [JRE 11 for M1](https://www.azul.com/downloads/?version=java-11-lts&os=macos&architecture=arm-64-bit&package=jdk).
    Double click the file and follow the instructions until the installation is complete.

??? info "Install with Homebrew"

    ```shell
    brew tap mdogan/zulu
    brew install zulu-jdk11
    ```

??? info "Install with MacPorts"

    ```shell
    sudo port install openjdk11-zulu
    ```

### FFmpeg

[FFmpeg](https://ffmpeg.org/) is required to [transcode](https://en.wikipedia.org/wiki/Transcoding) your media library
when streaming to certain devices and analyzing media files.

#### Windows

??? info "Install Manually"

    [Click here](https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n4.4-latest-win64-gpl-4.4.zip) to download FFmpeg.
    Extract `fmpeg-n4.4-latest-win64-gpl-4.4.zip` and rename the `bin` folder to `ffmpeg` and move it to `C:\Program Files\ffmpeg`.

??? info "Install with Chocolatey"

    ```shell
    choco install ffmpeg
    ```

#### macOS

??? info "Install Manually"

    [Click here](https://evermeet.cx/pub/ffmpeg/ffmpeg-4.4.1.zip) to download FFmpeg and
    [here](https://evermeet.cx/pub/ffprobe/ffprobe-4.4.1.zip) to download FFprobe.
    Extract both files into `/usr/local/bin`

??? info "Install with Homebrew"

    ```shell
    brew install ffmpeg
    ```

??? info "Install with MacPorts"

    ```shell
    sudo port install ffmpeg
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

    The following optiions can be modified on your first run to customize AnyStream for your system.
    
    | name              | value                                                                                                            | description                                                                                                             |
    |-------------------|------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
    | `PORT`            | `8888`                                                                                                           | The port used to serve the web client and API.                                                                          |
    | `DATA_PATH`       | macos = `/Users/<user>/anystream`<br/>linux = `/home/<user>/anystream`<br/>windows = `C:\Users\<user>\anystream` | The folder where all data generated by anystream will be stored. Note this is not the folder for your media collection. |
    | `DATABASE_URL`    | `<DATA_PATH>/config/anystream.db`                                                                         | The file where the database will be stored. (Note the `sqlite:` prefix is required)                                     |
    | `FFMPEG_PATH`     | macos = `/usr/bin`<br/>linux = `/usr/bin`<br/>windows = `C:\Program Files\ffmpeg`                                | The directory which contains [FFmpeg](https://www.ffmpeg.org/download.html) and FFprobe binaries.                       |
    | `WEB_CLIENT_PATH` | (none)                                                                                                           | The folder which contains the Web client files to be served. By default these files are provided by the server binary.  |

??? info "Configuration (Program arguments)"

    If preferred, AnyStream accepts arguments instead of Environment variables.
    The table below maps the Environment variable name to the CLI argument, see "Configuration (Environment variables)" for descriptions of each option.
    
    | Env Name          | CLI Argument               |
    |-------------------|----------------------------|
    | `PORT`            | `-port=8888`               |
    | `DATA_PATH`       | `-app.dataPath="..."`      |
    | `DATABASE_URL`    | `-app.databaseUrl="..."`   |
    | `FFMPEG_PATH`     | `-app.ffmpegPath="..."`    |
    | `WEB_CLIENT_PATH` | `-app.webClientPath="..."` |

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
    > anystream -port=8888 -P:app.ffmpegPath="C:\Users\<user>\Downloads\ffmpeg"
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

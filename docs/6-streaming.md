# Streaming and Transcoding

AnyStream streams your media using [HLS (HTTP Live Streaming)](https://en.wikipedia.org/wiki/HTTP_Live_Streaming),
which is broadly supported across web browsers, mobile devices, and smart TVs.

## How It Works

When you start playing media, AnyStream analyzes the media file and determines the best way to deliver it
to your device:

1. **Direct Stream** -- If your device supports the video and audio codecs in the file, AnyStream streams
   it directly with no transcoding. This uses minimal server resources.
2. **Audio-only Transcode** -- If the video codec is supported but the audio is not, only the audio is transcoded.
3. **Video-only Transcode** -- If the audio codec is supported but the video is not, only the video is transcoded.
4. **Full Transcode** -- If neither the video nor audio codecs are supported, both are transcoded.

AnyStream automatically makes this decision based on the capabilities reported by the client device.

## Supported Codecs

AnyStream can handle media files with a wide variety of codecs. The following codecs are recognized
for compatibility detection:

**Video**: H.264 (AVC), H.265 (HEVC), VP8, VP9, AV1

**Audio**: AAC, MP3, Opus, FLAC, Vorbis

Files using other codecs will be fully transcoded to ensure playback.

## FFmpeg Requirement

All transcoding is performed by [FFmpeg](https://ffmpeg.org/), which must be installed on the server.
The Docker image includes FFmpeg pre-installed. For manual installations, see the
[FFmpeg requirements](installation/getting-started.md#ffmpeg) in the Getting Started guide.

Configure the FFmpeg location with the `FFMPEG_PATH` environment variable or the `app.ffmpeg_path` config option
if AnyStream cannot find it automatically.

## Transcode Path

Temporary transcode output files are written to the directory specified by `TRANSCODE_PATH` (default: `/tmp`).
For best performance, use a fast storage device (such as an SSD or tmpfs) for this directory.

In Docker, you can mount a dedicated volume for transcode output:

```yaml
services:
  anystream:
    environment:
      TRANSCODE_PATH: /app/transcode
    volumes:
      - /path/to/fast-storage:/app/transcode
```

## Playback State

AnyStream tracks your playback position so you can resume where you left off. Playback state is saved after
60 seconds of viewing. When you finish watching (90% or more of the media), the playback state is cleared
automatically.

## Active Sessions

Administrators with the **Configure System** permission can monitor all active playback sessions in real time
from the admin panel, including which users are watching, what they are watching, and the current transcode status.

FROM azul/zulu-openjdk:21-latest AS build

COPY . /build-project
WORKDIR /build-project

RUN ./gradlew :server:application:installShadowDist :client:web:jsBrowserProductionDist --console=plain --no-daemon

FROM azul/zulu-openjdk:21-jre-latest

WORKDIR /app

ENV FFMPEG_VERSION=7.1.1-7
RUN apt-get update && apt-get install -y wget bash \
    && ARCH=$(dpkg --print-architecture) \
    && FFMPEG_MAJOR_VERSION=$(echo "$FFMPEG_VERSION" | cut -d'.' -f1)"_" \
    && FFMPEG_DEB="https://github.com/jellyfin/jellyfin-ffmpeg/releases/download/v${FFMPEG_VERSION}/jellyfin-ffmpeg${FFMPEG_MAJOR_VERSION}${FFMPEG_VERSION}-jammy_${ARCH}.deb" \
    && wget -O jellyfin-ffmpeg.deb "$FFMPEG_DEB" \
    && apt-get install -y ./jellyfin-ffmpeg.deb \
    && rm jellyfin-ffmpeg.deb \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

ENV DATA_PATH=/app/storage/
ENV DATABASE_URL=/app/storage/anystream.db
ENV CONFIG_PATH=/app/storage/anystream.conf
ENV FFMPEG_PATH=/usr/lib/jellyfin-ffmpeg
ENV WEB_CLIENT_PATH=/app/client-web
ENV PORT=8888

COPY --from=build /build-project/server/application/build/install ./install/
COPY --from=build /build-project/client/web/build/vite/js/productionExecutable ./client-web/

ARG PUID=1000
ARG PGID=1000
ARG user=anystream

RUN addgroup --gid "$PGID" "$user" \
    && adduser  --gecos '' --uid "$PUID" --gid "$PGID" --disabled-password --shell /bin/bash "$user"
USER $user

ENTRYPOINT ["./install/anystream-server/bin/anystream"]

FROM azul/zulu-openjdk:21-latest AS build

COPY . /build-project
WORKDIR /build-project

RUN ./gradlew :server:application:installShadowDist :client:web:jsBrowserProductionWebpack --console=plain --no-daemon

FROM azul/zulu-openjdk:21-jre-latest

WORKDIR /app

COPY --from=build /build-project/server/application/build/install ./install/
COPY --from=build /build-project/client/web/build/dist/js/productionExecutable ./client-web/

ENV DATA_PATH=/app/storage/
ENV DATABASE_URL=/app/storage/config/anystream.db
ENV FFMPEG_PATH=/usr/bin
ENV WEB_CLIENT_PATH=/app/client-web
ENV PORT=8888
ENV FFMPEG_VERSION=7.0.2-9

RUN apt-get update && apt-get install -y wget bash \
    && ARCH=$(dpkg --print-architecture) \
    && FFMPEG_MAJOR_VERSION=$(echo "$FFMPEG_VERSION" | cut -d'.' -f1)"_" \
    && FFMPEG_DEB="https://github.com/jellyfin/jellyfin-ffmpeg/releases/download/v${FFMPEG_VERSION}/jellyfin-ffmpeg${FFMPEG_MAJOR_VERSION}${FFMPEG_VERSION}-jammy_${ARCH}.deb" \
    && wget -O jellyfin-ffmpeg.deb "$FFMPEG_DEB" \
    && apt-get install -y ./jellyfin-ffmpeg.deb \
    && rm jellyfin-ffmpeg.deb \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["./install/anystream-server/bin/anystream"]

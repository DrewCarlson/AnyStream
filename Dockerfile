FROM azul/zulu-openjdk-alpine:21-latest

COPY . /build-project
WORKDIR /build-project
RUN ./gradlew :server:application:installShadowDist :client:web:jsBrowserProductionWebpack --console=plain --no-daemon

FROM azul/zulu-openjdk-alpine:21-jre-latest

RUN apk add --update --no-cache bash ffmpeg

WORKDIR /app

COPY --from=0 /build-project/server/application/build/install ./install/
COPY --from=0 /build-project/client/web/build/dist/js/productionExecutable ./client-web/

ENV DATA_PATH=/app/storage/
ENV DATABASE_URL=sqlite:/app/storage/config/anystream.db
ENV FFMPEG_PATH=/usr/bin
ENV WEB_CLIENT_PATH=/app/client-web
ENV PORT=8888

ENTRYPOINT ["./install/application/bin/anystream"]

FROM openjdk:13-alpine

COPY . /build-project
WORKDIR /build-project
RUN ./gradlew installShadowDist browserProductionWebpack --no-daemon

FROM openjdk:13-alpine
RUN apk add --update \
    bash \
    ffmpeg \
  && rm -rf /var/cache/apk/*
WORKDIR /app
COPY --from=0 /build-project/server/build/install ./install
COPY --from=0 /build-project/client-web/build/distributions ./client-web
ENTRYPOINT ["./install/server-shadow/bin/server"]
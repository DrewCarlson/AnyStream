FROM openjdk:13-alpine

COPY . /build-project
WORKDIR /build-project
RUN ./gradlew :anystream-server:installShadowDist :anystream-client-web:jsBrowserProductionWebpack --console=plain --no-daemon

FROM openjdk:13-alpine
RUN apk add --update \
    bash \
    ffmpeg \
  && rm -rf /var/cache/apk/*
WORKDIR /app
COPY --from=0 /build-project/anystream-server/build/install ./install
COPY --from=0 /build-project/anystream-client-web/build/distributions ./client-web
ENTRYPOINT ["./install/anystream-server-shadow/bin/anystream-server"]
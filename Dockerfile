FROM mcr.microsoft.com/java/jdk:11-zulu-alpine

COPY . /build-project
WORKDIR /build-project
RUN ./gradlew :anystream-server:server-app:installShadowDist :anystream-client-web:jsBrowserProductionWebpack --console=plain --no-daemon

FROM mcr.microsoft.com/java/jre:11-zulu-alpine

RUN apk add --update --no-cache bash ffmpeg

WORKDIR /app

COPY --from=0 /build-project/anystream-server/build/install ./install/
COPY --from=0 /build-project/anystream-client-web/build/distributions ./client-web/

ENTRYPOINT ["./install/anystream-server-shadow/bin/anystream-server"]

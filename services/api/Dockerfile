FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13

# ffmpeg/ffprobe processam as midias do feed (H.264/AAC, HLS, WebP) e removem metadados.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system operadorzero \
    && useradd --system --gid operadorzero --home-dir /app --create-home operadorzero \
    && mkdir -p /data/media \
    && chown operadorzero:operadorzero /data/media
WORKDIR /app
COPY --from=build --chown=operadorzero:operadorzero /workspace/target/*.jar /app/app.jar
USER operadorzero

EXPOSE 10000
# JAVA_OPTS permite reservar parte da memoria do container para o ffmpeg.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

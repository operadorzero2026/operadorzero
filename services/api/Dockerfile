FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system operadorzero \
    && useradd --system --gid operadorzero --home-dir /app --create-home operadorzero
WORKDIR /app
COPY --from=build --chown=operadorzero:operadorzero /workspace/target/*.jar /app/app.jar
USER operadorzero

EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

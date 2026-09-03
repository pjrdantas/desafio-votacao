ARG DT_ONEAGENT_IMAGE=example.invalid/linux/oneagent-codemodules-musl:java
FROM ${DT_ONEAGENT_IMAGE} AS oneagent

FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S votacao && adduser -S votacao -G votacao
WORKDIR /app
RUN mkdir -p /app/keys && chown votacao:votacao /app/keys
ENV JWT_KEY_STORE_PATH=/app/keys/jwt.jwk
COPY --from=build --chown=votacao:votacao /workspace/target/desafio-votacao-service-0.0.1-SNAPSHOT.jar app.jar
USER votacao
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM runtime AS dynatrace
COPY --from=oneagent / /
ENV LD_PRELOAD=/opt/dynatrace/oneagent/agent/lib64/liboneagentproc.so

FROM runtime AS app

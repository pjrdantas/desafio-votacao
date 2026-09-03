ARG DT_ONEAGENT_IMAGE=example.invalid/linux/oneagent-codemodules-musl:java
FROM ${DT_ONEAGENT_IMAGE} AS oneagent

FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -g 10001 -S votacao && adduser -u 10001 -S votacao -G votacao
WORKDIR /app
RUN mkdir -p /app/keys && chown -R votacao:votacao /app

COPY --from=build --chown=votacao:votacao /workspace/target/desafio-votacao-service-0.0.1-SNAPSHOT.jar app.jar
COPY --chown=votacao:votacao docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh
USER votacao
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

FROM runtime AS dynatrace
COPY --from=oneagent / /
ENV LD_PRELOAD=/opt/dynatrace/oneagent/agent/lib64/liboneagentproc.so

FROM runtime AS app

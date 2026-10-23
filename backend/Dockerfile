# syntax=docker/dockerfile:1.7
# ---------- build ----------
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY config ./config
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -Dcheckstyle.skip -Dspotbugs.skip -Djacoco.skip \
    && java -Djarmode=tools -jar target/clinica.jar extract --layers --launcher --destination /extraido

# ---------- runtime ----------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
    && apk add --no-cache wget
WORKDIR /app
COPY --from=build --chown=app:app /extraido/dependencies/ ./
COPY --from=build --chown=app:app /extraido/spring-boot-loader/ ./
COPY --from=build --chown=app:app /extraido/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /extraido/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseCompactObjectHeaders -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

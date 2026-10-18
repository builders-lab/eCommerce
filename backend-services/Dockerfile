# ---------- build ----------
# Deliberately avoids BuildKit-only syntax (RUN --mount) so this builds with the
# classic builder as well as BuildKit, which keeps it working on older Docker
# installs and on engines without the buildx component.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Dependencies are fetched in their own layer so that editing src/ does not
# re-download the whole tree.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:25-jre AS runtime

# curl is only here so the compose healthcheck can probe /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /workspace --create-home app \
    && mkdir -p /var/www/imageResource \
    && chown -R app:app /var/www /workspace

COPY --from=build --chown=app:app /workspace/target/*.jar /workspace/app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /workspace/app.jar"]
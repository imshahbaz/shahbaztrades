# =====================================================
# Build Stage
# =====================================================
FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

COPY pom.xml .
COPY mvnw .
COPY .mvn ./.mvn

RUN chmod +x mvnw

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B


# =====================================================
# Runtime Stage
# =====================================================
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN useradd --create-home --shell /bin/bash nonroot

USER nonroot

COPY --from=builder --chown=nonroot:nonroot /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="\
-XX:+UseG1GC \
-XX:+UseStringDeduplication \
-XX:+AlwaysPreTouch \
-XX:MaxRAMPercentage=60 \
-XX:InitialRAMPercentage=10 \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/tmp \
-Djava.security.egd=file:/dev/urandom \
-Dfile.encoding=UTF-8 \
-Djava.awt.headless=true"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
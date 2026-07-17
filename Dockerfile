# -------- BUILD STAGE --------
FROM --platform=linux/amd64 ibm-semeru-runtimes:open-25-jdk-jammy AS builder

WORKDIR /app

# Limit Maven memory (VERY IMPORTANT for 1GB VPS)
#ENV MAVEN_OPTS="-Xms128m -Xmx384m"

# Cache dependencies first
COPY pom.xml .
COPY mvnw .
COPY .mvn ./.mvn

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source
COPY src ./src

# Build app
RUN ./mvnw clean package -DskipTests -B


# -------- RUNTIME STAGE --------
FROM --platform=linux/amd64 ibm-semeru-runtimes:open-25-jre-jammy

WORKDIR /app

# Create non-root user
RUN useradd -m nonroot && \
    mkdir -p /app/scc && \
    chown -R nonroot:nonroot /app

USER nonroot

# Copy jar
COPY --chown=nonroot:nonroot --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Optimized OpenJ9 settings for 1GB VPS
ENV JAVA_OPTS="\
-Xshareclasses:name=appcache,cacheDir=/app/scc \
-Xscmx64M \
-Xgcpolicy:gencon \
-Xtune:virtualized \
-Xquickstart \
-Xms128M \
-Xmx384M \
-Xss256K \
-XX:+IdleTuningGcOnIdle \
-XX:+IdleTuningCompactOnIdle \
-Xcpuweighted"

# Warm up shared class cache (optional optimization)
RUN /bin/bash -c '\
java $JAVA_OPTS -jar app.jar & \
PID=$!; \
sleep 15; \
kill $PID 2>/dev/null || true'

# Run application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
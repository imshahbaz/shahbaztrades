# -------- BUILD STAGE --------
FROM ibm-semeru-runtimes:open-21-jdk-jammy AS builder

WORKDIR /app

# Cache dependencies first to speed up rebuilds.
COPY pom.xml .
COPY mvnw .
COPY .mvn ./.mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
# Build the Spring Boot application
RUN ./mvnw clean package -DskipTests -B

# -------- RUNTIME STAGE --------
FROM ibm-semeru-runtimes:open-21-jre-jammy

WORKDIR /app

# Non-root user setup
RUN useradd -m nonroot && chown nonroot:nonroot .
RUN mkdir -p /app/scc && chown -R nonroot:nonroot /app/scc

USER nonroot

# Copy jar
COPY --chown=nonroot:nonroot --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Environment variables for OpenJ9 configuration to reduce RAM consumption
# -Xshareclasses: Re-uses AOT compiled code memory across restarts
# -Xgcpolicy:gencon: Great for short lived object throughput
# -Xtune:virtualized: Reduces JVM thread footprint specifically for cloud/containers
# IdleTuning flags: Forces JVM to return RAM back to OS (Render) during idle times!
# -Xss256K: Slashes memory per thread from 1MB to 256KB
# -Xquickstart: Prevents deep JIT compilation at boot, massively speeding up start time!
ENV JAVA_OPTS="-Xshareclasses:name=appcache,cacheDir=/app/scc -Xscmx64M -Xgcpolicy:gencon -Xtune:virtualized -Xquickstart -Xmns8M -Xmnx16M -Xms32M -Xmx64M -Xss256K -XX:+IdleTuningGcOnIdle -XX:+IdleTuningCompactOnIdle"

# Pre-populate the Shared Classes Cache (CDS equivalent)
# Run once and exit to populate cache
RUN /bin/bash -c 'java $JAVA_OPTS -jar app.jar & PID=$!; sleep 15; kill $PID 2>/dev/null || true'

# Run with OpenJ9 optimizations 
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

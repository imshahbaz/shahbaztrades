## ===== BUILD STAGE =====
#FROM maven:3.9.8-eclipse-temurin-21 AS build
#
#WORKDIR /app
#
## Copy source
#COPY . .
#
## Build WAR
#RUN mvn clean package -DskipTests
#
#
## ===== RUNTIME STAGE =====
#FROM tomcat:10.1-jdk21-temurin
#
## Remove default apps
#RUN rm -rf /usr/local/tomcat/webapps/*
#
## Copy WAR
#COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war
#
## Explode WAR at build time
#RUN mkdir /usr/local/tomcat/webapps/ROOT \
#    && cd /usr/local/tomcat/webapps/ROOT \
#    && jar -xf ../ROOT.war \
#    && rm ../ROOT.war
#
## Disable JSP dev mode (important for prod)
#ENV CATALINA_OPTS="-Dorg.apache.jasper.compiler.disablejsr199=false"
#
#EXPOSE 8080
#
#CMD ["catalina.sh", "run"]



# ===== BUILD STAGE =====
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# 1. OPTIMIZATION: Dependency Layer Caching
# We copy only the pom.xml first. This is why your 6.8GB downloads
# will stop happening every time you change a line of code.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Build the app (using Batch mode to keep logs clean)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== RUNTIME STAGE =====
FROM tomcat:10.1-jdk21-temurin

WORKDIR /usr/local/tomcat

# 3. OPTIMIZATION: Stripping Tomcat & Speeding up Extraction
# We clean everything and ensure 'unzip' is present (unzip is faster than 'jar -xf')
RUN rm -rf webapps/* temp/* work/* \
    && apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

# 4. Copy and Explode WAR
COPY --from=build /app/target/*.war webapps/ROOT.war
RUN mkdir webapps/ROOT \
    && unzip webapps/ROOT.war -d webapps/ROOT \
    && rm webapps/ROOT.war

# 5. OPTIMIZATION: Hard-coding Tomcat Thread Limits
# Default Tomcat uses 200 threads. On 512MB, this causes OOM. We cap it at 25.
RUN sed -i 's/connector port="8080" protocol="HTTP\/1.1"/connector port="8080" protocol="HTTP\/1.1" maxThreads="25" minSpareThreads="2" acceptCount="5" connectionTimeout="10000"/' conf/server.xml

# 6. OPTIMIZATION: Advanced JVM Memory Tuning
# -Xmx180m: Max Heap (Small to leave room for the heavy JDK/Tomcat stack)
# -Xss256k: Thread stack size (Reduces memory per thread by 75%)
# -XX:ReservedCodeCacheSize: Caps memory for compiled code (Saves ~100MB)
# -XX:+UseSerialGC: The most RAM-efficient Garbage Collector for small containers
ENV CATALINA_OPTS="-Xmx180m \
                   -Xms128m \
                   -XX:MaxMetaspaceSize=96m \
                   -XX:ReservedCodeCacheSize=64m \
                   -XX:MaxDirectMemorySize=32m \
                   -XX:+UseSerialGC \
                   -Xss256k \
                   -Dorg.apache.jasper.compiler.disablejsr199=false \
                   -Djava.security.egd=file:/dev/./urandom \
                   -Dspring.main.lazy-initialization=true"

EXPOSE 8080

CMD ["catalina.sh", "run"]
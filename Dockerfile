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



# ===== STAGE 1: BUILD =====
FROM maven:3.9.8-eclipse-temurin-21-alpine AS build
WORKDIR /app

# 1. Cache dependencies: Copy only the pom.xml first.
# This ensures that 'go-offline' only runs if you change your pom.xml.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copy the actual source code and build.
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== STAGE 2: RUNTIME =====
FROM tomcat:10.1-jdk21-alpine

WORKDIR /usr/local/tomcat

# 3. Strip Tomcat and install unzip in one layer to keep the image slim.
RUN rm -rf webapps/* \
    && rm -rf temp/* \
    && rm -rf work/* \
    && apk add --no-cache unzip

# 4. Copy and Explode WAR (Removes zip-buffer RAM overhead)
COPY --from=build /app/target/*.war webapps/ROOT.war
RUN mkdir webapps/ROOT \
    && unzip webapps/ROOT.war -d webapps/ROOT \
    && rm webapps/ROOT.war

# 5. Native Memory Safety: Limit threads to 25.
# This prevents the OS from killing the app when multiple users visit.
RUN sed -i 's/connector port="8080" protocol="HTTP\/1.1"/connector port="8080" protocol="HTTP\/1.1" maxThreads="25" minSpareThreads="2" acceptCount="5" connectionTimeout="10000"/' conf/server.xml

# 6. JVM GUARDRAILS FOR 512MB RAM
# We cap the CodeCache and DirectMemory to leave "breathing room" for the OS.
ENV CATALINA_OPTS="-Xmx180m \
                   -Xms128m \
                   -XX:MaxMetaspaceSize=96m \
                   -XX:ReservedCodeCacheSize=64m \
                   -XX:MaxDirectMemorySize=32m \
                   -XX:+UseSerialGC \
                   -Xss256k \
                   -Dorg.apache.jasper.compiler.disablejsr199=true \
                   -Djava.security.egd=file:/dev/./urandom \
                   -Dspring.main.lazy-initialization=true"

EXPOSE 8080
CMD ["catalina.sh", "run"]
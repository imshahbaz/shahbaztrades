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
FROM maven:3.9.8-eclipse-temurin-21-alpine AS build
WORKDIR /app
# Only copy pom.xml first to cache dependencies (Faster builds)
COPY pom.xml .
RUN mvn dependency:go-offline
COPY . .
RUN mvn clean package -DskipTests

# ===== RUNTIME STAGE =====
# We use the 'alpine' JRE to keep the OS footprint under 50MB
FROM tomcat:10.1-jre21-alpine

# 1. Strip Tomcat down to the bare essentials
RUN rm -rf /usr/local/tomcat/webapps/* \
    && rm -rf /usr/local/tomcat/temp/* \
    && rm -rf /usr/local/tomcat/work/*

# 2. Copy and Explode WAR
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war
RUN mkdir /usr/local/tomcat/webapps/ROOT \
    && unzip /usr/local/tomcat/webapps/ROOT.war -d /usr/local/tomcat/webapps/ROOT \
    && rm /usr/local/tomcat/webapps/ROOT.war

# 3. Tomcat Internal Tuning (Lowering thread pools and disabling heavy features)
# We limit maxThreads to 25. For a trade app on 512MB, 25 simultaneous users is plenty.
RUN sed -i 's/connector port="8080" protocol="HTTP\/1.1"/connector port="8080" protocol="HTTP\/1.1" maxThreads="25" minSpareThreads="2" acceptCount="5" connectionTimeout="10000" disableUploadTimeout="true"/' /usr/local/tomcat/conf/server.xml

# 4. EXTREME JVM TUNING
# -Xmx180m: Even tighter heap to ensure native memory doesn't overflow 512MB
# -XX:ReservedCodeCacheSize=64m: Limits memory for compiled code
# -XX:MaxDirectMemorySize=32m: Limits memory for NIO buffers
# -Djava.lang.Integer.IntegerCache.high=128: Shrinks the integer cache
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
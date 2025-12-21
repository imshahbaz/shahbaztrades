# ===== BUILD STAGE =====
FROM maven:3.9.8-eclipse-temurin-21 AS build

WORKDIR /app

# Copy source
COPY . .

# Build WAR
RUN mvn clean package -DskipTests


# ===== RUNTIME STAGE =====
FROM tomcat:10.1-jdk21-temurin

# Remove default apps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy WAR
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

# Explode WAR at build time
RUN mkdir /usr/local/tomcat/webapps/ROOT \
    && cd /usr/local/tomcat/webapps/ROOT \
    && jar -xf ../ROOT.war \
    && rm ../ROOT.war

# Disable JSP dev mode (important for prod)
ENV CATALINA_OPTS="-Dorg.apache.jasper.compiler.disablejsr199=false"

EXPOSE 8080

CMD ["catalina.sh", "run"]

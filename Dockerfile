## First stage: Build the application
#FROM maven:3.9.8-eclipse-temurin-21 AS build
#
#WORKDIR /app
#COPY . .
#RUN mvn clean package -DskipTests
#
## Second stage: Create the final image
#FROM eclipse-temurin:21-jre
#
#COPY --from=build /app/target/*.jar /app.jar
#
#EXPOSE 8080
#
#ENTRYPOINT ["java", "-jar", "/app.jar"]

# ===== BUILD STAGE =====
FROM maven:3.9.8-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy all source files
COPY . .

# Build the WAR inside Docker
RUN mvn clean package -DskipTests

# ===== RUNTIME STAGE =====
FROM tomcat:10-jdk21

# Remove default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy WAR from build stage as ROOT.war
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

# Expose port
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]


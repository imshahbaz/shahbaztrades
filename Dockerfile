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

# Use official Tomcat 10 image with JDK 21
FROM tomcat:10-jdk21

# Remove default webapps to avoid conflicts
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy your WAR file built by Maven to ROOT.war so it serves at /
COPY target/*.war /usr/local/tomcat/webapps/ROOT.war

# Expose Tomcat default port
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]

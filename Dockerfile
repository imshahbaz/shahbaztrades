# First stage: Build the application
FROM maven:3.9.8-eclipse-temurin-21 AS build

# Copy the current directory to /app in the container
COPY . /app

# Set the working directory
WORKDIR /app

# Build the application (skip tests)
RUN mvn clean package -DskipTests

# Second stage: Create the final image
FROM openjdk:21-jdk-slim

# Copy the JAR file from the build stage
COPY --from=build /app/target/shahbaztrades-1.0.jar /app.jar

# Expose the port the app will run on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]

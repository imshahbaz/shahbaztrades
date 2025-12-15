# First stage: Build the application
FROM maven:3.9.8-eclipse-temurin-21 AS build

WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Second stage: Create the final image
FROM eclipse-temurin:21-jre

COPY --from=build /app/target/*.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]

FROM maven:3.9.8-eclipse-temurin-21 AS build
COPY ..
RUN mvn clean package -DskipTests

FROM openjdk:21-jdk-slim
COPY --from=build /target/shahbaztrades-1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

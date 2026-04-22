# B1: Build ứng dụng bằng Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# B2: Chạy ứng dụng Java
FROM eclipse-temurin:21-jre
COPY --from=build /target/Chat-1.0-SNAPSHOT.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]

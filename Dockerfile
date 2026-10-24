FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/ielts-platform-0.1.0.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

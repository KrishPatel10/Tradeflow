FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp verify -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S tradeflow && adduser -S tradeflow -G tradeflow
COPY --from=build /workspace/target/backend-*.jar app.jar
USER tradeflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10002 appuser
COPY --from=build /workspace/target/traffic-dashboard-0.1.0-SNAPSHOT.jar app.jar
USER 10002
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD java -version || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY service-security ./service-security
RUN mvn -B -ntp -f service-security/pom.xml install
COPY traffic-dashboard ./traffic-dashboard
RUN mvn -B -ntp -f traffic-dashboard/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10002 appuser
COPY --from=build /workspace/traffic-dashboard/target/traffic-dashboard-0.1.0-SNAPSHOT.jar app.jar
USER 10002
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD java -version || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

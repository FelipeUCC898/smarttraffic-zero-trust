FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY service-security /workspace/service-security
RUN mvn -q -f service-security/pom.xml install
COPY query-client/pom.xml query-client/pom.xml
COPY query-client/src query-client/src
RUN mvn -q -f query-client/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10004 appuser
COPY --from=build /workspace/query-client/target/query-client-0.1.0-SNAPSHOT.jar app.jar
USER 10004
ENTRYPOINT ["java","-jar","app.jar"]

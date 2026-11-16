FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY service-security /workspace/service-security
RUN mvn -q -f service-security/pom.xml install
COPY alert-dispatcher/pom.xml alert-dispatcher/pom.xml
COPY alert-dispatcher/src alert-dispatcher/src
RUN mvn -q -f alert-dispatcher/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10003 appuser
COPY --from=build /workspace/alert-dispatcher/target/alert-dispatcher-0.1.0-SNAPSHOT.jar app.jar
USER 10003
ENTRYPOINT ["java","-jar","app.jar"]

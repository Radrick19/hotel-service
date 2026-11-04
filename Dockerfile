FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY hotel-service/pom.xml hotel-service/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY common/src common/src
COPY eureka-server/src eureka-server/src
COPY hotel-service/src hotel-service/src
COPY booking-service/src booking-service/src
COPY api-gateway/src api-gateway/src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
ARG SERVICE
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-1.0.0.jar app.jar
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "/app/app.jar"]

FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY proto proto
COPY servidor/pom.xml servidor/pom.xml
COPY servidor/src servidor/src
RUN mvn -q -f servidor/pom.xml package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /build/servidor/target/servidor.jar servidor.jar

CMD ["java", "-jar", "servidor.jar"]

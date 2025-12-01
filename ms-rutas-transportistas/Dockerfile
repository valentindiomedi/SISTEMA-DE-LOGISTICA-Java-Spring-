# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy parent pom first
COPY pom.xml /build-parent/pom.xml
RUN cd /build-parent && mvn install -N

# Copy module pom and source
COPY ms-rutas-transportistas/pom.xml .
COPY ms-rutas-transportistas/src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
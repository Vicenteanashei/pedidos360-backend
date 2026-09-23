# ---------- Build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Instala curl: el mvnw simplificado de este repo descarga Maven con curl
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Dependencias primero para aprovechar la cache de capas
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=build /app/target/*.jar app.jar

ENV SERVER_PORT=8081 \
    SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

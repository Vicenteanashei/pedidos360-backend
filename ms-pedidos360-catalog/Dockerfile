FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG MODULE=ms-pedidos360-catalog
RUN mvn -B -pl ${MODULE} -am package -DskipTests && cp ${MODULE}/target/${MODULE}-0.2.0-SNAPSHOT.jar /app.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app && mkdir -p /app/data && chown -R app:app /app
COPY --from=build /app.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -Duser.timezone=UTC"
ENTRYPOINT ["java","-jar","/app/app.jar"]

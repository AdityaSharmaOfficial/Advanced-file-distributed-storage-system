FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY os ./os
COPY dbms ./dbms
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/chunks && chown -R app:app /data
WORKDIR /app
COPY --from=build /src/target/storage-backend.jar /app/app.jar
USER app
EXPOSE 8080
# Default: coordinator. Storage nodes override the command in docker-compose.yml.
ENTRYPOINT ["java"]
CMD ["-jar", "/app/app.jar"]

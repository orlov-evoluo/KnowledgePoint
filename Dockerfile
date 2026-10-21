FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon build

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/knowledge-point.jar app.jar
COPY public ./public
ENV PORT=4173 \
    DATA_DIR=/data \
    PAGE=/app/public/index.html
EXPOSE 4173
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY common/build.gradle.kts common/
COPY eureka-server/build.gradle.kts eureka-server/
COPY api-gateway/build.gradle.kts api-gateway/
COPY hotel-service/build.gradle.kts hotel-service/
COPY booking-service/build.gradle.kts booking-service/
COPY e2e-tests/build.gradle.kts e2e-tests/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :${MODULE}:dependencies > /dev/null

COPY common/src common/src
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :${MODULE}:bootJar -x test \
    && cp ${MODULE}/build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

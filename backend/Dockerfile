FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
COPY backend/src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system sigak && useradd --system --gid sigak --home /app sigak
WORKDIR /app
COPY --from=build --chown=sigak:sigak /build/build/libs/*.jar ./app.jar
# Research API는 DB가 아닌 보존된 실험 artifact를 읽으므로 함께 배포한다.
COPY --chown=sigak:sigak experiments/results ./experiments/results
COPY --chown=sigak:sigak experiments/datasets/raw ./experiments/datasets/raw
USER sigak
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

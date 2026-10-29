# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean test bootJar jacocoTestReport --no-daemon

RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=sonar_token,required=true \
    SONAR_TOKEN="$(cat /run/secrets/sonar_token)" \
    ./gradlew sonar --no-daemon \
    -Dsonar.qualitygate.wait=true \
    --stacktrace --info

FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder --chown=appuser:appgroup \
    /build/build/libs/app.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
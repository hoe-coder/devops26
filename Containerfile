FROM docker.io/library/eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

ENV GRADLE_USER_HOME=/root/.gradle

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

RUN --mount=type=cache,id=gradle-cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src src

RUN --mount=type=cache,id=gradle-cache,target=/root/.gradle \
    ./gradlew bootJar --build-cache --no-daemon -x test

FROM docker.io/library/eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

EXPOSE 8080

COPY --from=builder --chown=appuser:appgroup /build/build/libs/*.jar app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]

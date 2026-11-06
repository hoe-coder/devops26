## Lecture 03

The `build.gradle` file:

```groovy
plugins {
  id 'java'
  id 'org.springframework.boot' version '4.1.1'
  id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.devops'
version = '0.0.1-SNAPSHOT'

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation 'org.springframework.boot:spring-boot-starter-webmvc'
  implementation 'org.springframework.boot:spring-boot-starter-actuator'

  developmentOnly 'org.springframework.boot:spring-boot-devtools'
  developmentOnly 'org.springframework.boot:spring-boot-docker-compose'

  testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('bootJar') {
    archiveFileName = 'app.jar'
}
```

As you can see, we've added two testing dependencies for executing the Unit tests.

Furthermore we created a Dockerfile at the root of our application folder.

This multi-stage `Dockerfile` builds a Java 21 Spring Boot application using `eclipse-temurin:21-jdk-alpine`, leveraging Docker layer and BuildKit cache mounts (`/root/.gradle`) to download Gradle dependencies before compiling the source code into an executable JAR. It then transitions to a lightweight `eclipse-temurin:21-jre-alpine` runtime stage that excludes build tools and source code to minimize the final image size and attack surface. Finally, it creates a dedicated non-root user (`appuser`), copies the compiled JAR into `/app/app.jar`, exposes port `8080`, and configures the container to launch the application securely under that unprivileged user.


```dockerfile
# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew test bootJar jacocoTestReport --no-daemon

ENTRYPOINT ["./gradlew"]
CMD ["integrationTest", "--no-daemon"]

FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder --chown=appuser:appgroup \
    /build/build/libs/app.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```
This simply runs the gradle task for testing and compiles the bootJar into the `build` folder.
# Lecture 03 - CI Build Pipeline on the Runner

This document covers the Lecture 03 goals: runners installed and configured, runners idling and waiting for jobs, jobs executed on push, and basic output produced by jobs. It describes the first real CI pipeline that builds and tests the Spring Boot application on the team's self-hosted runner.

## Goals Overview

| Requirement | Implementation |
|---|---|
| Runners installed and configured | Runner installed as a service, registered with team labels |
| Runners idling and waiting for jobs | Runner visible as *Idle* in Settings → Actions → Runners |
| Jobs executed on push | Workflow triggers on every push to `main` |
| Basic output produced by jobs | Unit test results, `app.jar`, Docker image, and pipeline logs |

## Runners Installed and Configured

The team runner is installed on the build agent (Lecture 02) and configured as a systemd service, so it:

- starts automatically when the server boots,
- restarts after crashes,
- runs permanently in the background.

It is registered against the repository `hoe-coder/devops26` with the labels `self-hosted`, `linux`, and the team label `devops2026-build`. Workflows select the runner through these labels:

```yaml
runs-on: [self-hosted, linux, devops2026-build]
```

## Runners Idling and Waiting for Jobs

The runner's idle state is verified in two places:

1. **GitHub** — repository → Settings → Actions → Runners shows the runner as *Idle*, waiting for matching jobs.
2. **Server** — the service status reports active (running):

   ```shell
   sudo ./svc.sh status
   ```

An idle runner consumes no jobs until a workflow with matching `runs-on` labels is triggered.

## Jobs Executed on Push

The workflow is triggered by every push to `main` (and manually via `workflow_dispatch`):

```yaml
on:
  push:
    branches: [main]
  workflow_dispatch:
```

On each push, GitHub dispatches the job to the idle runner, which:

1. Checks out the repository into `~/actions-runner/_work/devops26/devops26`.
2. Runs the build steps defined in the workflow.
3. Reports the results back to GitHub, where they appear under **Actions**.

## The Build: Gradle Inside Docker

The application defines its dependencies in `build.gradle` — Spring Boot WebMVC, Actuator, and the test dependencies for unit tests:

```groovy
dependencies {
  implementation 'org.springframework.boot:spring-boot-starter-webmvc'
  implementation 'org.springframework.boot:spring-boot-starter-actuator'

  developmentOnly 'org.springframework.boot:spring-boot-devtools'
  developmentOnly 'org.springframework.boot:spring-boot-docker-compose'

  testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

The `bootJar` task produces the executable JAR as `build/libs/app.jar`.

## The Multi-Stage Dockerfile

The build itself runs inside a multi-stage Dockerfile on the runner — no Java or Gradle installation is needed on the host:

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

FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder --chown=appuser:appgroup \
    /build/build/libs/app.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

- **Builder stage** — copies the Gradle wrapper and build files first, so Docker layer caching and the BuildKit cache mount (`/root/.gradle`) keep dependency downloads fast on repeated builds. `./gradlew test bootJar jacocoTestReport` runs the unit tests and produces the JAR.
- **Runner stage** — a slim JRE-only image without build tools or source code, minimizing size and attack surface.
- **Security** — the application runs as the unprivileged `appuser` instead of root, port 8080 is exposed, and the container launches `java -jar app.jar`.

## Basic Output Produced by Jobs

Each pipeline run produces visible output:

- **Pipeline logs** (GitHub → Actions → run): the echoed commands, Gradle's test output (`passed` / `failed` per test), and build step results.
- **Unit test results** — Gradle reports under `build/reports/tests/test/` in the checkout, including the JaCoCo coverage report from `jacocoTestReport`.
- **`app.jar`** — the executable JAR in `build/libs/`, produced by the builder stage.
- **Docker image** — `docker build` output listing the built stages and tags (e.g. `team2/app:0.x.y`).
- **Working directory** — the checkout and build outputs under `~/actions-runner/_work/devops26/devops26` on the server, proving the job ran locally.

If a step fails (e.g. a failing unit test), the job stops, the run is marked red in GitHub, and the log shows the failing test — the basic feedback loop of CI.

## Validation

1. Push a commit to `main` (or run the workflow manually).
2. The runner transitions from *Idle* to *Active* in the GitHub runner list.
3. Under **Actions**, the job log shows the build steps and their output.
4. On the server, `ls ~/actions-runner/_work/devops26/devops26` shows the checked-out project with build outputs.
5. The Docker image exists locally: `docker images team2/app`.

## Requirements Checklist

- [x] Runners installed and configured (systemd service, team labels)
- [x] Runners idling and waiting for jobs (visible as *Idle*, `svc.sh status` active)
- [x] Jobs executed on push (workflow triggers on every push to `main`)
- [x] Basic output produced by jobs (test results, `app.jar`, Docker image, pipeline logs)

# Podman

## Introduction

Podman is a "new" alternative to Docker, reaching almost feature parity and offering integration with Docker via `podman-compose` and being able to use `Dockerfile`. Primary difference is that Podman uses the internal service management to run the applications, which allows for rootless deployment, fixing one of Dockers critical issues. Podman is built to use `systemd` which currently is the default init system for most Linux distributions.

Because Podman leverages the native service system it has less resource overhead when running and deploying containers.

Podman containers are managed via `Quadlets`, generally with a `.container` file. Which acts similar to a `compose.yaml` file, a services options are specified, the primary difference being that a Quadlet defines one service, not multiple. The Quadlet file can then be copied (`cp`) or linked (`ln`) to the user specific podman config directory (`~/.config/containers/systemd/`). Then the service can be enabled via `systemctl`.

## Setup

Containerfile:

```dockerfile
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
```

app.container:

```quadlet
[Unit]
Description=Springboot Podman Application

[Container]
ContainerName=devops-app-pod
Image=localhost/app:latest
PublishPort=8090:8080
AutoUpdate=local


[Service]
Restart=always

[Install]
WantedBy=default.target

```

```sh
podman build -f Containerfile -t localhost/app:latest .

mkdir -p ~/.config/containers/systemd/

cp -av app.container ~/.config/containers/systemd/
# ln -sf $PWD/app.container ~/.config/containers/systemd/

systemctl --user daemon-reload
systemctl --user start --now app.service
```

## Comparison with Docker

### Quadlets

### Containerfile

### Complexity

### Performance

### Summary

## Notes

When deploying via a Action Runner, it is recommended to copy the `.container` file into the `config` directory and not to (sym)link it, as the source directory of the Quadlet may change.

# Documentation

## Init

Setup the GitHub [repository](https://github.com/hoe-coder/devops26).

Setup the project using [Spring Initializer](https://start.spring.io/) with the following dependencies:

1. Spring Devtools
2. Spring Docker Compose
3. Spring Web

Setup SSH keys for server access with:

```sh
ssh-keygen -t rsa -b 4096 -m pem
```

And sent the public keys to Andreas Gottardi. Also added the public keys to our GitHub accounts.

Created this Controller class to test functionality:


## Install Github Action workflow agent on the dedicated server

### 1. On GitHub on your repository, open:

Settings -> Actions -> Runners -> New self-hosted runner

### 2. Download and register the runner

SSH into the server and execute following commands:

```shell
mdkir -p ~/actions-runner
cd ~/actions-runner
```
This creates a folder, which contains the GitHub Action runner at the root of server.

```shell
./config.sh \
  --url https://github.com/OWNER/REPOSITORY \
  --token YOUR_REGISTRATION_TOKEN
```

### 3. Install and start the background service

```shell
sudo ./svc.sh install
sudo ./svc.sh start
sudo ./svc.sh status
```

### 4. Test it with a workflow

Create following

```markdown
.github/workflows/deploy.yml
```
in your repository.

A simplified pipeline file looks like e.g.: 

```yaml
name: Test self-hosted runner

on:
  workflow_dispatch:

jobs:
  test:
    runs-on: self-hosted
    steps:
      - name: Show runner information
        run: |
          echo "Runner is working"
          hostname
          whoami
          uname -a
```

## Build the Java application and run it in Docker container

Usually the commands bellow are executed for building the application e.g: app.jar

```shell
./gradlew build
```
or in our case:

```shell
./gradlew bootJar
```
This creates a jar File (app.jar) under the build folder at the applcation root.

But we need it to run in Docker environment. So the next steps are:

### 1. Add a Dockerfile

We created a Dockerfile at the root of our application folder.

This multi-stage `Dockerfile` builds a Java 21 Spring Boot application using `eclipse-temurin:21-jdk-alpine`, leveraging Docker layer and BuildKit cache mounts (`/root/.gradle`) to download Gradle dependencies before compiling the source code into an executable JAR. It then transitions to a lightweight `eclipse-temurin:21-jre-alpine` runtime stage that excludes build tools and source code to minimize the final image size and attack surface. Finally, it creates a dedicated non-root user (`appuser`), copies the compiled JAR into `/app/app.jar`, exposes port `8080`, and configures the container to launch the application securely under that unprivileged user.

```dockerfile
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
./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

EXPOSE 8080

COPY --from=builder --chown=appuser:appgroup /build/build/libs/*.jar app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
```
This copies the pre-build application  into the Docker image

### 2. Update the workflow file with:

This step builds the production Docker image from the root `Dockerfile` and tags it with both the newly generated semantic version (`${IMAGE_NAME}:${IMAGE_TAG}`) and `latest`.

```yaml
- name: Build Docker
  run: |
      docker build -f Dockerfile \
        -t ${IMAGE_NAME}:${IMAGE_TAG} \
        -t ${IMAGE_NAME}:latest .
```

This step builds an OCI container image using Podman from the root `Containerfile` and stores it locally under both the new semantic version (`localhost/${IMAGE_NAME}:${IMAGE_TAG}`) and `latest` tags.
```yaml
- name: Build Podman
  run: |
      podman build -f Containerfile \
        -t localhost/${IMAGE_NAME}:${IMAGE_TAG} \
        -t localhost/${IMAGE_NAME}:latest .
```
If the GitHub workflow is executed, it will create a docker image which contains our application on the server.

```sh
> curl http://127.0.0.1:8080/
Hello!⏎
```

### 3. Testing

```java
package com.devops.devops2026.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(Controller.class)
class ControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Should return Hello!")
    void hello_ShouldReturnOkAndGreeting() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hello!"));
    }

    @Test
    @DisplayName("Should return provided Name")
    void test_ShouldReturnProvidedName() throws Exception {
        String testName = "Alice";
        mvc.perform(get("/test/{name}", testName))
            .andExpect(status().isOk())
            .andExpect(content().string(testName));
    }
}
```

### Podman and Docker

Reworked the `deploy.yml` file to run the following steps:

1. build docker image
2. build podman image
3. run docker container on port 8080
4. run podman service on port 8090

Removed the docker deployment from `compose.yaml` and separated the build and run steps for a fairer comparison between docker and podman.

**deploy.yml:**

```yaml
name: Build and Deploy locally

on:
  push:
    branches: ["main"]

  workflow_dispatch:

jobs:
  build:
    runs-on: self-hosted

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Build Docker
        run: |
          docker build -f Dockerfile -t app:latest .

      - name: Build Podman
        run: |
          podman build -f Containerfile -t localhost/app:latest .

      - name: Deploy Docker
        run: |
          docker rm -f devops-app 2>/dev/null || true

          docker run -d \
            --name devops-app \
            -p 8080:8080 \
            app:latest

      - name: Deploy Podman
        run: |
          export XDG_RUNTIME_DIR="/run/user/$(id -u)"
          export DBUS_SESSION_BUS_ADDRESS="unix:path=${XDG_RUNTIME_DIR}/bus"

          mkdir -p ~/.config/containers/systemd/
          cp -av app.container ~/.config/containers/systemd/

          systemctl --user daemon-reload
          systemctl --user restart app.service
```

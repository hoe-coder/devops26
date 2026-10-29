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

Usually the commands bellow are executed for building the application e.g: app.jar in the `/build` folder:

```shell
./gradlew build
```
The `build` task also runs integration tests and therefore needs a running instance.
To create the application JAR before starting the instance, use:

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
./gradlew clean test bootJar --no-daemon

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

Integration tests run against a running application instance. They send real HTTP
requests and verify the application with its active runtime configuration. The
simplest test compares an endpoint response with a defined string.

Our original controller test example uses `@WebMvcTest` and `MockMvc`. It checks
the controller inside a test context:

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
		mvc.perform(get("/{name}", testName))
				.andExpect(status().isOk())
				.andExpect(content().string(testName));
	}
}
```

For integration testing, `ControllerIntegrationTest` sends real HTTP requests to
the running application. This is the implementation used by the project:

```java
package com.devops.devops2026.rest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerIntegrationTest {
	private void assertEndpoint(String path, String expected) throws Exception {
		String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8080");
		HttpResponse<String> response = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10)).build().send(
						HttpRequest.newBuilder(URI.create(baseUrl + path))
								.timeout(Duration.ofSeconds(10)).GET().build(),
						HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertEquals(expected, response.body());
	}

	@Test
	void helloReturnsGreeting() throws Exception {
		assertEndpoint("/", "Hello!");
	}

	@Test
	void nameReturnsProvidedName() throws Exception {
		assertEndpoint("/Alice", "Alice");
	}
}
```

Both examples check status `200` and the expected response strings. The controller
maps the name endpoint to `/{name}`, so the example uses `/Alice`.

Build an image with a unique tag, then start a test instance on port 18080:

```sh
IMAGE_NAME=team2/app
IMAGE_TAG="$(date -u +%Y%m%d%H%M%S)-$(git rev-parse --short HEAD)"

docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .
docker run -d --name devops-integration \
  -p 18080:8080 "${IMAGE_NAME}:${IMAGE_TAG}"
```

Wait for the instance to become available before running tests:

```sh
ready=false
for attempt in $(seq 1 60); do
  if curl --fail --silent --max-time 2 http://localhost:18080/ > /dev/null; then
    ready=true
    break
  fi
  sleep 2
done
if [ "$ready" != true ]; then
  docker logs devops-integration
  exit 1
fi
```

Run only integration tests, or all checks including unit tests and the JaCoCo report:

```sh
APP_BASE_URL=http://localhost:18080 ./gradlew integrationTest --no-daemon
APP_BASE_URL=http://localhost:18080 ./gradlew check --no-daemon
```

For the deployed Docker instance use `APP_BASE_URL=http://localhost:8080`;
for the Podman instance use `APP_BASE_URL=http://localhost:8090`.
HTTP integration tests use the server's configuration; the test JVM's JaCoCo agent
does not measure code running in that external server.

A manual request should return `Hello!`:

```sh
curl --fail http://localhost:18080/
```

After testing, remove the temporary instance:

```sh
docker rm -f devops-integration
```

### 4. Tagging

An image reference identifies the registry, repository and tag:

```text
<registry>/<namespace>/<image>:<tag>
```

The registry hostname selects the destination, while the tag identifies a version.
An image can have multiple tags. These act as aliases to the same image and layers
and do not create additional copies of the image data.

The workflow currently generates semantic versions. To distinguish every workflow
build, including reruns, a run number and attempt can be used instead:

```yaml
- name: Set image tag
  run: echo "IMAGE_TAG=run-${GITHUB_RUN_NUMBER}-${GITHUB_RUN_ATTEMPT}" >> "$GITHUB_ENV"
```

For manual builds, the timestamp and commit tag from the testing steps identifies
the build. Replace the example registry and namespace below with your destination:

```sh
REGISTRY=registry.example.com
REMOTE_IMAGE="${REGISTRY}/team2/app"

docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "${REMOTE_IMAGE}:${IMAGE_TAG}"
docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "${REMOTE_IMAGE}:latest"
```

The unique tag identifies the tested build. `latest` is a moving alias for the
most recently published successful build.

### 5. Pushing

After all required tests succeed, log in and push the tested image:

```sh
docker login "$REGISTRY"
docker push "${REMOTE_IMAGE}:${IMAGE_TAG}"
docker push "${REMOTE_IMAGE}:latest"
```

In CI, place authentication and push steps after every required test step and keep
the default success condition so failed tests prevent publication. Push the image
that was tested, without rebuilding it. The commands above are manual instructions;
the current workflow does not yet publish images to a remote registry.

### Podman and Docker

Reworked the `deploy.yml` file to run the following steps:

1. build docker image
2. build podman image
3. run docker container on port 8080
4. run podman service on port 8090

Removed the docker deployment from `compose.yaml` and separated the build and run steps for a fairer comparison between docker and podman.

The following simplified example shows the original deployment steps. See
`.github/workflows/deploy.yml` for the current versioned build and test workflow.

**deploy.yml (simplified):**

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

## Integration tests and SonarQube Cloud in CI

The deployment workflow waits for the Docker instance on port 8080 and the Podman
instance on port 8090. It runs `check` against Docker (unit tests, real HTTP
integration tests and JaCoCo XML coverage), then integration tests against Podman.
A failed readiness check or test prevents the analysis step from running.
Deployments are serialized because both use the same server ports.

After tests pass, Gradle uploads analysis and coverage to SonarQube Cloud and waits
for the quality gate. A failed gate fails the workflow. Test and coverage reports
are uploaded as workflow artifacts even when a preceding step fails.

### Configure SonarQube Cloud

1. Import the GitHub repository into SonarQube Cloud.
2. Verify that the organization is `hoe-coder` and the project key is
   `hoe-coder_devops26`, matching `build.gradle`.
3. Generate an analysis token with access to that project.
4. In GitHub, open **Settings > Secrets and variables > Actions** and add the
   repository secret `SONAR_TOKEN` with that token.
5. If automatic analysis is enabled for this project, disable it before using
   CI-based analysis.
6. Run the deployment workflow from the Actions tab or push to `main`.

The self-hosted runner needs Docker, Podman, curl and access to Java downloads,
Gradle dependencies and `https://sonarcloud.io`. The workflow installs Java 21.
`SONAR_HOST_URL` is unnecessary because the Cloud URL is set in `build.gradle`.

To reproduce the test and analysis commands with the application already running:

```sh
APP_BASE_URL=http://localhost:8080 sh ./gradlew check --no-daemon
APP_BASE_URL=http://localhost:8090 sh ./gradlew integrationTest --no-daemon
# Set SONAR_TOKEN securely in your environment before running this command.
sh ./gradlew sonar --no-daemon -Dsonar.qualitygate.wait=true
```

Coverage describes application code executed in the test JVM. Measuring code in
running containers requires separate JaCoCo instrumentation in those containers.
The workflow currently deploys locally before testing; a failed quality gate does
not roll back those deployments.
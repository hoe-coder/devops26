# Lecture 04 - Running the Application on the Team VM

Lecture 04 documentation: how the pipeline runs the Spring Boot application on the team VM (`10.0.40.171`), including stop, cleanup and start logic, restart-on-reboot settings, network and port configuration, container naming, the integration test against the running instance, and the gated push to the local registry (`10.0.40.171:5000`).

## Goals Overview

| Requirement | Implementation |
|---|---|
| Stop, cleanup, start in the pipeline | `docker rm -f team2-app` before every deploy, image cleanup after |
| Automatic restart on system reboot | `--restart unless-stopped` |
| Network access and port definition | `-p 8080:8080` plus `EXPOSE 8080` in the image |
| Container naming so it can be stopped by name | `--name team2-app` |
| Integration test against the running instance | Separate pipeline step running the `integration-tests` image stage against the live container |
| Push to registry only after tests pass | Push step ordered after the integration test step |

## Container Lifecycle: Stop, Cleanup, Start

### Naming the Container

The container gets a fixed name so the pipeline can always address it — and stop it — by name:

```shell
docker run -d \
  --name team2-app \
  --restart unless-stopped \
  -p 8080:8080 \
  10.0.40.171:5000/team2/app:${IMAGE_TAG}
```

Because the name is deterministic, `docker stop team2-app` and `docker rm team2-app` always target the right container, no matter which image version is currently running. The name includes the team number (`team2-app`), so on shared hosts containers of different teams never collide.

### Stop Logic

Stopping and removing the previous container is a single step that tolerates a not-yet-existing container:

```shell
docker stop team2-app 2>/dev/null || true
docker rm team2-app 2>/dev/null || true
```

The `2>/dev/null || true` suffix keeps the pipeline from failing when there is nothing to stop — for example on the very first deployment. `docker rm -f team2-app` does the same in one command (stop + remove).

### Cleanup Logic

Old images from previous builds are removed so the VM's disk does not fill up:

```shell
# remove dangling images from previous builds
docker image prune -f

# remove old team2/app images, keep the 3 most recent
docker images 10.0.40.171:5000/team2/app --format '{{.ID}}' | tail -n +4 | xargs -r docker rmi -f
```

- `docker image prune -f` removes dangling layers left over from intermediate builds.
- The second command lists the team's images (newest first), skips the 3 most recent with `tail -n +4`, and force-removes the rest — so rollbacks to the last two versions remain possible while older builds are discarded.

### Start Logic

After stop and cleanup, the new version is started in detached mode:

```shell
docker run -d \
  --name team2-app \
  --restart unless-stopped \
  -p 8080:8080 \
  10.0.40.171:5000/team2/app:${IMAGE_TAG}
```

The pipeline then waits for the application to become healthy before any test runs:

```shell
for attempt in {1..30}; do
  if curl --fail --silent http://localhost:8080/actuator/health >/dev/null; then
    exit 0
  fi
  sleep 2
done
docker logs team2-app
exit 1
```

Up to 30 attempts with 2-second intervals give Spring Boot a minute to boot; on timeout, the container logs are printed for debugging and the step fails.

## Automatic Restart on System Reboot

The `--restart unless-stopped` flag registers the container with Docker's restart policy. The Docker daemon restarts the container automatically after a VM reboot or a container crash. `unless-stopped` (rather than `always`) means the container stays down if it was deliberately stopped before the reboot — the pipeline stays in control.

## Network Access and Port Definition

Three layers define how the application is reachable:

1. **Image metadata** — the runtime stage of the `Dockerfile` documents the application port:

   ```dockerfile
   FROM eclipse-temurin:21-jre-alpine AS runner
   ...
   EXPOSE 8080
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

2. **Port publishing** — `-p 8080:8080` maps the VM's host port to the container port, making the app reachable from the network at `10.0.40.171:8080`.

3. **Test network access** — the integration test container joins the host network so it can reach the published port:

   ```shell
   docker run --rm --network host \
     -e APP_BASE_URL=http://localhost:8080 \
     team2/app:integration-tests
   ```

## Integration Test Against the Running Instance

The integration test is deliberately kept out of build time and runs as its own pipeline step against the already-deployed container.

### Separate Gradle Task

`build.gradle` defines a dedicated `integrationTest` task, and the unit test task explicitly excludes integration tests:

```groovy
tasks.named('test', Test) {
  exclude '**/*IntegrationTest.class'
}

def integrationTest = tasks.register('integrationTest', Test) {
  testClassesDirs = sourceSets.test.output.classesDirs
  classpath = sourceSets.test.runtimeClasspath
  useJUnitPlatform()
  include '**/*IntegrationTest.class'
  shouldRunAfter(tasks.named('test'))
}
```

### Separate Image Stage

The `Dockerfile` has an `integration-tests` stage that contains the Gradle wrapper and test sources, with `integrationTest` as its default command:

```dockerfile
FROM builder AS integration-tests
ENTRYPOINT ["./gradlew"]
CMD ["integrationTest", "--no-daemon"]
```

### Separate Pipeline Step

After deployment and the health gate, the test image runs against the live application:

```yaml
- name: Wait for application health
  ...

- name: Run integration tests against the running instance
  run: |
    docker run --rm --network host \
      -e APP_BASE_URL=http://localhost:8080 \
      "${IMAGE_NAME}:integration-tests"
```

The test compares the HTTP responses of the running instance against expected strings — `/` returns `Hello!` and `/{name}` echoes the name — so the deployed HTTP behavior itself is verified, not just isolated application logic.

## Push to Registry After Successful Test

The push step only executes after the integration test step has succeeded — GitHub Actions runs steps sequentially and aborts the job on the first failure. The versioned image that was just tested is what gets pushed:

```yaml
- name: Push Docker image to custom registry
  run: |
    set -euo pipefail
    VERSIONED_IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
    docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "$VERSIONED_IMAGE"
    docker push "$VERSIONED_IMAGE"
```

With `REGISTRY: 10.0.40.171:5000` and `IMAGE_NAME: team2/app`, this publishes `10.0.40.171:5000/team2/app:<version>` — the tested, uniquely tagged image. If the integration test step fails, the pipeline stops before pushing, so the registry only ever contains verified builds.

## Pipeline Step Order

```
├── checkout
├── build docs, unit tests, analysis
├── bump version → IMAGE_TAG
├── build runtime image           (not pushed yet)
├── stop + cleanup: docker rm -f team2-app, image prune
├── start: docker run -d --name team2-app --restart unless-stopped -p 8080:8080
├── wait for /actuator/health
├── integration tests against the running container   (separate step)
├── push tested image to 10.0.40.171:5000             (only on green tests)
└── cleanup old images (keep the 3 most recent)
```

## Requirements Checklist

- [x] Stop, cleanup and start logic implemented in the pipeline (`docker stop`/`rm team2-app`, `image prune`, keep-3 cleanup)
- [x] Automatic restart on system reboot (`--restart unless-stopped`)
- [x] Network access and port definition (`EXPOSE 8080`, `-p 8080:8080`, `--network host` for tests)
- [x] Container named so it can be stopped by name (`--name team2-app`)
- [x] Integration test against the running instance, not at build time (separate `integrationTest` task, separate image stage, separate pipeline step)
- [x] Image pushed to the registry only after the integration test step succeeds

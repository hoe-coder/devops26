# Lecture 05

## Unique Image Tag in the Registry

The workflow already derives a new version for every push to `main`:

```yaml
- name: Bump version
  id: version
  uses: anothrNick/github-tag-action@4ed44965e0db8dab2b466a16da04aec3cc312fd8 # v1.75.0
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    DEFAULT_BRANCH: main
    DEFAULT_BUMP: patch
    INITIAL_VERSION: 0.1.0
```

Every pipeline run produces a distinct `IMAGE_TAG` (e.g. `0.1.0`, `0.1.1`, `0.1.2`, ...). Combined with the team namespace, the full reference `10.0.40.171:5000/team2/app:0.1.1` is unique in the registry — both per team and per build.

To satisfy "no generic names", the push step drops the `latest` tag and publishes only the unique versioned tag:

```yaml
- name: Push Docker image to custom registry
  shell: bash
  run: |
    set -euo pipefail
    VERSIONED_IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
    docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "$VERSIONED_IMAGE"
    docker push "$VERSIONED_IMAGE"
```

If even more uniqueness is wanted (e.g. two runs of the same commit), the short commit SHA can be appended: `${IMAGE_TAG}-${GITHUB_SHA::7}`.

## Setting Up SonarQube Community

### Connect via SSH

Connect to the team's build agent as the `student` user, where `NN` is the ending of the build agent's IP address:

```bash
ssh student@10.0.40.164
```

## SonarCloud Project Setup

SonarCloud is used as the team's analysis server, so nothing has to be hosted on the build agent:

1. Log in at [sonarcloud.io](https://sonarcloud.io) with the GitHub account and create/select the `hoe-coder` organization.
2. Import the repository as a new project — the resulting project key is `hoe-coder_devops26`.
3. **Disable automatic analysis** (Administration → Analysis Method), because the pipeline performs the analysis itself.
4. Under *My Account → Security → Generate Tokens*, create a token for CI use (e.g. named `github-actions`).
5. Store this token as the `SONAR_TOKEN` secret in the GitHub repository settings.

## JaCoCo in the Build

The `jacoco` plugin is applied in `build.gradle`:

```groovy
plugins {
  id 'java'
  id 'org.springframework.boot' version '4.1.1'
  id 'io.spring.dependency-management' version '1.1.7'
  id 'org.sonarqube' version '7.5.0.8588'
  id 'jacoco'
}
```

Gradle automatically provides new tasks once the plugin is applied:

```bash
./gradlew test jacocoTestReport                        # unit test coverage
./gradlew integrationTest jacocoIntegrationTestReport  # integration test coverage
```

The generated reports land in the build/target folders:

- `build/reports/jacoco/test/jacocoTestReport.xml` — unit test coverage (XML, consumed by SonarCloud)
- `build/reports/jacoco/test/html/index.html` — unit test coverage (human-readable)
- `build/reports/jacoco/jacocoIntegrationTestReport/html/index.html` — integration test coverage

The XML report path is wired into the SonarCloud configuration in `build.gradle`:

```groovy
sonar {
  properties {
    property 'sonar.host.url', 'https://sonarcloud.io'
    property 'sonar.organization', 'hoe-coder'
    property 'sonar.projectKey', 'hoe-coder_devops26'
    property 'sonar.coverage.jacoco.xmlReportPaths',
            'build/reports/jacoco/test/jacocoTestReport.xml'
  }
}
```

## Pipeline Integration

### Coverage in the Build Process

Tests and the JaCoCo report are part of the builder stage of the `Dockerfile`, so every image build produces coverage as a side effect:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
...
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew test bootJar jacocoTestReport --no-daemon
```

### Scan Against SonarCloud

The dedicated `sonar` stage extends the builder and runs the Gradle analysis task. The token is passed as a build secret, never written into the image or the repository:

```dockerfile
FROM builder AS sonar

RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=sonar_token,required=true \
    SONAR_TOKEN="$(cat /run/secrets/sonar_token)" \
    ./gradlew sonar --no-daemon
```

The pipeline runs this stage with `docker/build-push-action` before the version bump and image build, forwarding the repository secret:

```yaml
- name: Test and analyze with SonarCloud
  uses: docker/build-push-action@v6
  with:
    context: .
    file: Dockerfile
    target: sonar
    no-cache-filters: sonar
    push: false
    secrets: |
      sonar_token=${{ secrets.SONAR_TOKEN }}
    cache-from: type=gha,scope=app
    cache-to: type=gha,mode=max,scope=app
```

No `SONAR_HOST_URL` needs to be set in the workflow — the server URL, organization, and project key all come from the `sonar {}` block in `build.gradle`.

### Resulting Step Order

```
├── checkout
├── build docs (MkDocs, Pandoc PDFs)
├── unit tests + JaCoCo report + SonarCloud analysis   (Dockerfile sonar stage)
├── bump version → IMAGE_TAG
├── build runtime image
├── deploy, health check, integration tests against the running instance
└── push unique versioned tag to 10.0.40.171:5000 (no generic names)
```

## Verify the Results

After a pipeline run, open the project at [sonarcloud.io](https://sonarcloud.io), select the `hoe-coder` organization and the `devops26` project:

- **Overview** — new analysis entry with the date of the pipeline run and the Quality Gate status
- **Measures → Coverage** — line/branch coverage percentages computed from the JaCoCo XML report
- **Code** — per-file coverage highlighting (green = covered, red = uncovered lines)

If the project shows no coverage, check that the builder stage actually ran `jacocoTestReport` and that `sonar.coverage.jacoco.xmlReportPaths` matches the report path inside the build container.



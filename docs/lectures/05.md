# Lecture 05 - SonarCloud Analysis and Unique Image Tags

This document covers the analysis goals of this task. Instead of a self-hosted SonarQube Community instance on the build agent, the team uses **SonarCloud** — the SaaS version of SonarQube — so no server has to be installed or maintained. Every requirement of the original task is fulfilled by its SonarCloud equivalent, and the JaCoCo coverage integration and pipeline scan work the same way.

## Goals Overview

| Original requirement | SonarCloud equivalent |
|---|---|
| Unique image tag, no generic names | Version-bumped tag per push (`team2/app:0.x.y`); no `latest` push |
| SSH to the machine as `student` | Not needed — SonarCloud is a hosted service |
| Start SonarQube as Docker container with `--restart always` | Not needed — no server to run or keep alive |
| Wait a minute for the container | Not needed — the service is always available |
| Default login `admin`/`admin`, set new password | Login with the GitHub account; account security is handled by GitHub |
| Access via `http://10.0.40.NN:9000` | Access via <https://sonarcloud.io> |
| JaCoCo plugin in `build.gradle` | Same — `jacoco` plugin in `build.gradle` |
| Coverage in build/target folders | Same — reports under `build/reports/jacoco/` |
| Integrate into the pipeline | Same — `sonar` stage in the Dockerfile, run by the workflow |
| Scan with `sonarscanner-cli` container | Scan with the Gradle `sonar` task inside the `sonar` image stage |
| Check the project for results | SonarCloud project dashboard at sonarcloud.io |

## Unique Image Tag in the Registry

The workflow derives a new version for every push to `main`:

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

## SonarCloud Configuration Steps

SonarCloud replaces the self-hosted SonarQube Community server. The setup is done once in the browser:

### 1. Create the Organization and Project

1. Go to <https://sonarcloud.io> and click **Login with GitHub** — this replaces the `admin`/`admin` default login of a self-hosted instance; the password is the GitHub account password (ideally secured with 2FA).
2. Authorize SonarCloud to access the GitHub account.
3. Create or select the organization **`hoe-coder`** (choose "Create organization" → import from GitHub, plan: free).
4. Import the repository **`devops26`** as a new project ("Analyze new project"). The resulting project key is **`hoe-coder_devops26`**.
5. In the new project: **Administration → Analysis Method → disable Automatic Analysis** — otherwise SonarCloud's own scanner would interfere with the pipeline's analysis and produce duplicate analyses.

### 2. Generate the Analysis Token

The token replaces the admin password handling of the self-hosted variant:

1. In SonarCloud: **My Account → Security → Generate Tokens**.
2. Enter a name (e.g. `github-actions`) and type *User token*, then **Generate**.
3. Copy the token immediately — it is shown only once.

### 3. Store the Token in GitHub

1. In the repository: **Settings → Secrets and variables → Actions → New repository secret**.
2. Name: `SONAR_TOKEN`, Value: the generated token.

The workflow reads it as `${{ secrets.SONAR_TOKEN }}`; it is never written into the repository or an image.

### 4. Configure the Project in `build.gradle`

The SonarQube Gradle plugin connects to SonarCloud instead of a local `http://10.0.40.NN:9000` — the host URL, organization, and project key live in `build.gradle`:

```groovy
plugins {
  id 'org.sonarqube' version '7.5.0.8588'
}

sonar {
  properties {
    property 'sonar.host.url', 'https://sonarcloud.io'
    property 'sonar.organization', 'hoe-coder'
    property 'sonar.projectKey', 'hoe-coder_devops26'
    property 'sonar.scm.disabled', 'true'
    property 'sonar.coverage.jacoco.xmlReportPaths',
            'build/reports/jacoco/test/jacocoTestReport.xml'
  }
}
```

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

The dedicated `sonar` stage extends the builder and runs the Gradle analysis task. This is the equivalent of scanning with the `sonarscanner-cli` container — here the SonarQube Gradle plugin performs the scan, using the same token-based authentication. The token is passed as a build secret, never written into the image or the repository:

```dockerfile
FROM builder AS sonar

RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=sonar_token,required=true \
    SONAR_TOKEN="$(cat /run/secrets/sonar_token)" \
    ./gradlew sonar --no-daemon
```

The pipeline runs this stage with `docker/build-push-action`, forwarding the repository secret:

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

No `SONAR_HOST_URL` needs to be set in the workflow — the server URL, organization, and project key all come from the `sonar {}` block in `build.gradle`. The analysis uploads the JaCoCo XML report automatically via `sonar.coverage.jacoco.xmlReportPaths`.

## Verify the Results

After a pipeline run, open <https://sonarcloud.io>, select the `hoe-coder` organization and the `devops26` project:

- **Overview** — new analysis entry with the date of the pipeline run and the Quality Gate status
- **Measures → Coverage** — line/branch coverage percentages computed from the JaCoCo XML report
- **Code** — per-file coverage highlighting (green = covered, red = uncovered lines)

If the project shows no coverage, check that the builder stage actually ran `jacocoTestReport` and that `sonar.coverage.jacoco.xmlReportPaths` matches the report path inside the build container. If analyses appear twice, automatic analysis was not disabled in step 1.5.

## Requirements Checklist

- [x] Unique image tag in the registry (version bump per push, team namespace, no generic `latest` push)
- [x] Analysis server for the team — SonarCloud instead of SonarQube Community (no SSH, no container, no admin password needed)
- [x] SonarCloud configured (organization `hoe-coder`, project `hoe-coder_devops26`, automatic analysis disabled, `SONAR_TOKEN` stored as GitHub secret)
- [x] JaCoCo plugin in `build.gradle` with report tasks and outputs in the build folders
- [x] Coverage integrated into the build process in the workflow
- [x] Scan from the pipeline against SonarCloud (`sonar` Dockerfile stage with `./gradlew sonar` and the token build secret)
- [x] Results checked in the SonarCloud project dashboard

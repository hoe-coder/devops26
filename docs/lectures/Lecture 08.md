# Lecture 08 - Deployment to Production

Lecture 08 documentation: deploying the tested application to the production server in a **separate pipeline job** that runs after the build job, using the `needs` keyword. The build job produces the versioned image and pushes it to the registry; only then does the production job pull and run it.

## Goals Overview

| Requirement | Implementation |
|---|---|
| Deployment to production | Dedicated `deploy-production` job |
| Separate job that runs after building | `needs: build` — the job waits for the build job to succeed |
| Handover of the built artifact | Build output `image_tag` passed via `needs.build.outputs` |

## Why a Separate Job

Splitting build and deployment into separate jobs separates concerns:

- **Build job** — runs on the build agent (`devops2026-build` runner): tests, analysis, version bump, image build, integration tests, registry push.
- **Production job** — runs on the production VM (`devops2026` runner): pulls the tested image from the registry and runs it.

The `needs` keyword creates the dependency between them:

```yaml
jobs:
  build:
    runs-on: [self-hosted, linux, devops2026-build]
    outputs:
      image_tag: ${{ steps.version.outputs.new_tag }}
    ...

  deploy-production:
    name: Deploy to production server
    needs: build
    if: github.ref == 'refs/heads/main'
    runs-on: [self-hosted, linux, devops2026]
    ...
```

Effects of `needs: build`:

- The production job **only starts after the build job finished successfully** — if tests, analysis, or the registry push fail, production is never touched.
- The jobs can run on **different runners** — the build happens on the build agent, the deployment on the production machine.
- Values computed in the build job (here: the new version tag) are available in the production job via `needs.build.outputs`.

Without `needs`, both jobs would start in parallel and the deployment would race the build — pulling an image version that does not exist yet.

## Guardrails of the Production Job

```yaml
  deploy-production:
    name: Deploy to production server
    needs: build
    if: github.ref == 'refs/heads/main'
    runs-on: [self-hosted, linux, devops2026]
    environment: production
    timeout-minutes: 15

    permissions:
      contents: read

    concurrency:
      group: production-deployment
      cancel-in-progress: false
```

- `if: github.ref == 'refs/heads/main'` — production deploys happen only from `main`, never from feature branches.
- `environment: production` — marks the run with the production environment; environments can require manual approval or protected secrets before the job starts.
- `concurrency` — one production deployment at a time; a new run waits instead of interrupting a running deployment (`cancel-in-progress: false`).
- `timeout-minutes: 15` — a stuck deployment fails instead of blocking the runner forever.
- `permissions: contents: read` — the deployment job needs no write access to the repository.

## Handover: the Image Tag

The build job publishes its computed version as a job output:

```yaml
  build:
    outputs:
      image_tag: ${{ steps.version.outputs.new_tag }}
```

The production job consumes it — this guarantees both jobs work with exactly the same image version:

```yaml
    steps:
      - name: Pull Docker image
        env:
          IMAGE_TAG: ${{ needs.build.outputs.image_tag }}
        run: |
          IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
          docker pull "$IMAGE"
```

Because the build job pushed this exact version to `10.0.40.171:5000` only after the integration tests passed, the production job deploys precisely the tested artifact — a build-once, deploy-many flow.

## Deployment Steps on the Production VM

The production job repeats the proven stop/start cycle from Lecture 04, now against the registry image:

### Stop and Cleanup

```yaml
      - name: Stop previous container
        run: |
          docker rm -f devops-app 2>/dev/null || true
```

Removing the previous container by its fixed name in one step — tolerant of a first deployment.

### Start

```yaml
      - name: Run production container
        env:
          IMAGE_TAG: ${{ needs.build.outputs.image_tag }}
        run: |
          IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
          docker run -d \
            --name devops-app \
            --restart unless-stopped \
            -p 8080:8080 \
            -e SPRING_PROFILES_ACTIVE=production \
            "$IMAGE"
```

- `--name devops-app` — fixed name, so the container can be stopped by name on the next deployment.
- `--restart unless-stopped` — automatic restart after crashes and VM reboots.
- `-p 8080:8080` — network access and port definition.
- `SPRING_PROFILES_ACTIVE=production` — the production profile of the Spring Boot application.

### Health Check

```yaml
      - name: Verify production health
        run: |
          for attempt in {1..30}; do
            if curl -fsS http://127.0.0.1:8080/actuator/health; then
              echo "Production application is healthy"
              exit 0
            fi
            sleep 2
          done
          docker logs --tail 100 devops-app || true
          exit 1
```

The job only finishes green when the production application actually responds on the actuator health endpoint; otherwise the last 100 log lines are printed for debugging.

## Full Flow

```
push to main
└── build job                    (build agent, devops2026-build)
    ├── tests, analysis, image build
    ├── deploy + integration tests on the build agent
    └── push tested image to 10.0.40.171:5000
        │
        │  needs: build          (only if the build job succeeded)
        ▼
    deploy-production job        (production VM, devops2026)
    ├── pull team2/app:<IMAGE_TAG> from the registry
    ├── docker rm -f devops-app (stop + cleanup)
    ├── docker run -d --name devops-app --restart unless-stopped -p 8080:8080
    └── verify /actuator/health
```

## Requirements Checklist

- [x] Deployment to production in its own job (`deploy-production`)
- [x] Separate job that runs after building (`needs: build`)
- [x] Build artifact handed over via job outputs (`needs.build.outputs.image_tag`)
- [x] Only the tested image version reaches production (registry pull after integration-tested push)
- [x] Production protected (main-only, environment, concurrency, timeout)
- [x] Stop/cleanup/start logic and health verification on the production VM

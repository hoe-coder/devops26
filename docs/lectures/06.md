# Lecture 06 - MkDocs Documentation Image

Lecture 06 documentation: initializing the MkDocs project in the repository, generating the site with the `squidfunk/mkdocs-material` Docker image, packaging it into an nginx-based image tagged with the team number, running it on the pipeline with stop/cleanup/start logic, checking it from a local browser, and pushing the documentation image to the registry.

## Goals Overview

| Requirement | Implementation |
|---|---|
| Initialize a MkDocs project | `mkdocs.yml` and `docs/` folder in the repository root |
| Notes as Markdown in the docs folder | Lecture notes and tooling docs under `docs/` |
| Generate the page with the mkdocs-material image | `docker run squidfunk/mkdocs-material build` producing the `site/` folder |
| Nginx-based documentation image | `Dockerfile.docs` with `FROM nginx:alpine`, site copied to the web root |
| Tag with team number and identifier | `team2-docs:<version>` |
| Start/stop/cleanup logic in the pipeline | `docker rm -f team2-docs` before every start |
| Check from local browser | `http://10.0.40.NN:8081` (build agent IP + chosen port) |
| Push to registry | `10.0.40.171:5000/team2-docs:<version>` |

## MkDocs Project in the Repository

The MkDocs project is initialized at the repository root:

```
├── mkdocs.yml      # configuration: site name and theme
└── docs/           # all documentation pages (Markdown)
    ├── MKDOCS.md
    ├── PANDOC.md
    ├── PODMAN.md
    ├── images/
    └── project/
        ├── Lecture 01.md
        ├── Lecture 02.md
        ├── Lecture 03.md
        └── Lecture 04.md
```

The configuration in `mkdocs.yml`:

```yaml
site_name: Team 2 Devops

theme:
  name: "material"
```

All notes are plain Markdown files inside `docs/` — MkDocs converts every `.md` file into a page of the site and builds the navigation automatically. Pages in subfolders (like `docs/project/`) become nested navigation entries.

## Generate the Site With the mkdocs-material Image

The site is generated with the `squidfunk/mkdocs-material` Docker image — no local MkDocs installation is needed:

```bash
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -v "$PWD:/docs" \
  squidfunk/mkdocs-material \
  build --clean --site-dir site
```

The same container can also serve the site live during development (`mkdocs serve` on port 8000) to preview changes before committing them.

- `-v "$PWD:/docs"` mounts the repository; MkDocs reads `mkdocs.yml` and `docs/` from there.
- `--site-dir site` writes the static HTML output into the `site/` folder.
- `--clean` removes stale files from previous builds.
- `-u "$(id -u):$(id -g)"` prevents root-owned files in the workspace.

The result is a fully static site (HTML, CSS, JS) in `site/` that any web server can serve.

## Nginx Documentation Image

A new `Dockerfile.docs` builds a dedicated image that serves the generated site with nginx:

```dockerfile
FROM nginx:alpine

COPY site/ /usr/share/nginx/html/

EXPOSE 80
```

- `nginx:alpine` is a small, production-ready web server image.
- `/usr/share/nginx/html/` is nginx's default web root — copying the `site/` folder there is all that is needed.
- `EXPOSE 80` documents the internal port.

The image is tagged with the team number and an identifier, using the same version the pipeline computes for the application image (e.g. `0.0.1`, then `0.0.2`, ...):

```bash
docker build -f Dockerfile.docs -t team2-docs:${IMAGE_TAG} .
```

The result — for example `team2-docs:0.0.1` — identifies both the team and the documentation build.

## Run on the Pipeline

The pipeline gets the same stop/cleanup/start logic as the application deployment, because a previous build may already have the documentation container running. The steps run after the site generation:

```yaml
- name: Build documentation image
  run: |
    docker run --rm \
      -u "$(id -u):$(id -g)" \
      -v "$PWD:/docs" \
      squidfunk/mkdocs-material \
      build --clean --site-dir site
    docker build -f Dockerfile.docs -t "team2-docs:${IMAGE_TAG}" .

- name: Deploy documentation container
  run: |
    docker rm -f team2-docs 2>/dev/null || true
    docker run -d \
      --name team2-docs \
      --restart unless-stopped \
      -p 8081:80 \
      "team2-docs:${IMAGE_TAG}"

- name: Wait for documentation to be reachable
  run: |
    for attempt in {1..15}; do
      if curl --fail --silent http://localhost:8081/ >/dev/null; then
        exit 0
      fi
      sleep 2
    done
    docker logs team2-docs
    exit 1
```

- **Stop and cleanup** — `docker rm -f team2-docs 2>/dev/null || true` removes any container from a previous build in one step; the pipeline never fails because of a leftover container and never serves two versions side by side.
- **Fixed name** — `--name team2-docs` makes the container addressable by name for stop, logs, and cleanup.
- **Restart on reboot** — `--restart unless-stopped` keeps the documentation available after a build agent reboot.
- **Port definition** — `-p 8081:80` publishes the nginx port 80 on host port 8081. Port 8080 is already taken by the application container, so the documentation gets its own port.
- **Health check** — the pipeline waits until nginx responds before continuing, so a broken build fails early.

Note: the `site/` folder also receives `documentation.pdf` from the PDF generation step (see PDF-DOCUMENTATION.md), so the documentation image serves both the HTML site and the PDF. Because MkDocs builds with `--clean`, the PDF step must run after the MkDocs build.

## Check From the Local Browser

Once the pipeline step has run, the documentation is reachable from any local browser via the build agent's IP address and the chosen port:

```
http://10.0.40.NN:8081
```

where `NN` is the ending of the build agent's IP address. The Material for MkDocs site shows the navigation with all notes from the `docs/` folder.

## Push the Documentation Image to the Registry

After the documentation container is verified, the image is pushed to the team registry with a unique versioned tag — consistent with the no-generic-names rule, no `latest` tag:

```yaml
- name: Push documentation image to registry
  run: |
    set -euo pipefail
    DOCS_IMAGE="${REGISTRY}/team2-docs:${IMAGE_TAG}"
    docker tag "team2-docs:${IMAGE_TAG}" "$DOCS_IMAGE"
    docker push "$DOCS_IMAGE"
```

This publishes `10.0.40.171:5000/team2-docs:<version>` — for example `10.0.40.171:5000/team2-docs:0.0.1` — so the exact documentation build that was just verified is preserved in the registry alongside the application image.

## Requirements Checklist

- [x] MkDocs project initialized in the repository (`mkdocs.yml`, `docs/` folder)
- [x] Notes (Markdown files) placed in the docs folder
- [x] Site generated with the `squidfunk/mkdocs-material` image (`site/` folder)
- [x] Nginx-based image serving the documentation (`Dockerfile.docs`)
- [x] Image tagged with team number and identifier (`team2-docs:0.0.1`)
- [x] Documentation container started on the pipeline with stop and cleanup logic
- [x] Documentation checked from the local browser via build agent IP and port 8081
- [x] Documentation image pushed to the registry (`10.0.40.171:5000/team2-docs:<version>`)

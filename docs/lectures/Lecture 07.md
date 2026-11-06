# Lecture 07 - PDF Documentation Generation

Lecture 07 documentation: generating one PDF file from the already existing Markdown documentation, doing this before the documentation image is generated and before the application is built, placing the PDF into the `site/` folder, copying it into the application's static resources, and serving it both from the documentation server/container and from the application.

## Goals Overview

| Requirement | Implementation |
|---|---|
| PDF from the already existing documentation | All Markdown docs combined into one `documentation.pdf` with `pandoc/extra` |
| Before the documentation image is generated | PDF step runs before `docker build -f Dockerfile.docs` |
| Before the application is built | PDF is copied into `src/main/resources/static/` before the application image build |
| PDF in the `site` folder | `site/documentation.pdf`, next to the MkDocs HTML output |
| PDF in the static resources | `src/main/resources/static/documentation.pdf` |
| Served by the documentation container | nginx serves it at port 8081 |
| Served by the application | Spring Boot serves it from the static resources at port 8080 |

## What Goes Into the PDF

The PDF is generated from the already existing Markdown documentation of the repository — the top-level project documentation and the tooling notes:

- `README.md`
- `DOC.md`
- `docs/MKDOCS.md`
- `docs/PANDOC.md`
- `docs/PODMAN.md`

Passing multiple input files to pandoc concatenates them into a single document, so the result is one self-contained PDF with a table of contents covering all the documentation.

## Pipeline Placement

The generation step must run at the right point in the build job — after the MkDocs site is built (so `--clean` cannot delete the PDF) and before both image builds:

```
├── checkout
├── prepare documentation sources
├── build MkDocs site            → site/               (HTML)
├── generate documentation PDF   → site/documentation.pdf
│                                  src/main/resources/static/documentation.pdf
├── build documentation image    (Dockerfile.docs, nginx — site/ contains the PDF)
├── deploy documentation container, verify
├── build application image      (Dockerfile — static resources contain the PDF)
├── deploy application, health check, integration tests
└── push images to the registry
```

This ordering guarantees the requirement: the PDF exists before the documentation image is generated and before the application is built, so both images contain it without any later copying.

## Generate the PDF

The step uses the same `pandoc/extra` container pattern as the rest of the workflow — no local LaTeX installation is needed on the build agent:

```yaml
- name: Generate documentation PDF
  run: |
    mkdir -p site src/main/resources/static
    docker run --rm \
      -u "$(id -u):$(id -g)" \
      -v "$PWD:/data" \
      pandoc/extra \
      README.md DOC.md docs/MKDOCS.md docs/PANDOC.md docs/PODMAN.md \
      --pdf-engine=xelatex \
      --toc \
      -o documentation.pdf
    cp documentation.pdf site/documentation.pdf
    cp documentation.pdf src/main/resources/static/documentation.pdf
```

- `--pdf-engine=xelatex` produces the PDF and handles special characters (e.g. umlauts in the author names).
- `--toc` adds a table of contents at the start, since the document combines several files.
- The `cp` calls place the same file into both target folders:
  - `site/documentation.pdf` — picked up by the documentation image.
  - `src/main/resources/static/documentation.pdf` — picked up by the application image.

If the workflow previously built one PDF per Markdown file, this single combined PDF replaces those steps.

## The PDF in the Site Folder

The `site/` folder is the input of the documentation image. With the PDF inside it, the nginx image build already includes it — no change to `Dockerfile.docs` is needed:

```dockerfile
FROM nginx:alpine

COPY site/ /usr/share/nginx/html/

EXPOSE 80
```

`COPY site/` copies everything: the MkDocs HTML pages and `documentation.pdf`.

## The PDF in the Application's Static Resources

Spring Boot serves everything under `src/main/resources/static/` automatically at the application's HTTP port. Because the `cp` in the pipeline step runs before the application image is built, the Dockerfile's `COPY src src` (builder stage) already carries the PDF into the image — again no Dockerfile change needed.

## Serving the PDF

### With the Documentation Container

The nginx documentation container serves the PDF from its web root:

```
http://10.0.40.164:8081/documentation.pdf
```

where `NN` is the ending of the build agent's IP address and 8081 is the documentation port.

### With the Application

The application serves the same PDF from its static resources:

```
http://10.0.40.164:8080/documentation.pdf
```

Both URLs return the identical file — the single PDF generated once per pipeline run — so the documentation is available regardless of which server is reached.

## Verify the Results

After a pipeline run:

1. Open `http://10.0.40.164:8081` — the MkDocs site loads, and `http://10.0.40.164:8081/documentation.pdf` downloads the PDF.
2. Open `http://10.0.40.164:8080/documentation.pdf` — the application serves the same PDF.
3. Check the PDF contains the table of contents and all source documents.
4. `docker exec team2-docs ls /usr/share/nginx/html` shows `documentation.pdf` inside the documentation container.

If a URL returns 404, check the step order in the workflow: the PDF generation must come after the MkDocs build (because of `--clean`) and before both image builds.

## Requirements Checklist

- [x] PDF generated from the already existing documentation (combined Markdown via `pandoc/extra`)
- [x] Generation runs before the documentation image is built
- [x] Generation runs before the application is built
- [x] PDF placed into the `site/` folder
- [x] PDF copied into `static` inside the resources (`src/main/resources/static/`)
- [x] PDF served by the documentation server/container (nginx, port 8081)
- [x] PDF served by the application (Spring Boot static resources, port 8080)

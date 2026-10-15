# Pandoc

Pandoc is a N-M conversion library, built to convert many types of documents and filetypes into another, for our use case we use it to build PDF files from our Markdown files.

## Usage

Pandoc can be installed on the host machine and run like that, we want to integrate them into our Github Actions pipeline though, so we run it via the available Docker image.

This is the shell command to build `file.md` into `file.pdf`.

```sh
docker run --rm \
pandoc/extra "file.md" -o "file.pdf"
```

Because we have multiple files in different places that all need to be converted into PDFs we have decided to collect them in the `/docs` directory. Files created outside of this are symlinked using `ln -sf ../file.md file.md`.

Now, for our Action we just need to iterate over all `md` files in the `/docs` directory and run `pandoc` over them.

```sh
# iterate
for f in docs/*.md; do
    # if file exists
    if [ -f "$f" ]; then
      # extract pure filename file.md -> file
      filename=$(basename -- "$f" .md)

      # build using pandoc
      # NOTE: also passes the user permission to make sure the PDFs are available
      docker run --rm \
        -u $(id -u):$(id -g) \
        -v "$PWD":/data \
        pandoc/extra "$f" -o "src/main/resources/static/docs/${filename}.pdf"
    fi
done
```

You might notice the output folder of the pdf being `src/main/resources/static/docs/`, this is because we want the pdf files to be baked into the Docker image of our `java` application. Being placed into `/static/docs/` the pdfs will be available at `http://localhost:8080/docs/file.pdf`.

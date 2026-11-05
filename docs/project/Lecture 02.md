# Project DevOps 2026
## by Hüseyin Öner, David Rüscher, Isabel Nemec

## Lecture 02

### Connect to the server

We connected to the prepared server

```shell
ssh -i ~/.ssh/id_github -l student 10.0.40.164
```
or

```shell
ssh -i ~/.ssh/id_github student@10.0.40.164
```
Both of the commands use `student` user and add `-i`for referencing the private key respectively to the user.

The `public key` had to be stored at `GitHub`for referencing and being able to push to the repository.

Here a simple `hello.yml` for displaying `HELLO WORLD`.

```yaml
name: Hello World

on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  hello:
    runs-on: ubuntu-latest

    steps:
      - name: Say hello
        run: echo "Hello, World!"

      - name: Show runner info
        run: |
          echo "Running on: $RUNNER_NAME"
          uname -a
```

Afterwards we installed the runner, in our case the GitHub Runner, following this guide on the dedicated server:

```text
https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners
```
And lastly, we familiarized us with `Docker` commands and ran the basic runner.




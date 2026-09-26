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

And sent the public keys to Gottardi. Also added the public keys to our GitHub accounts.

Created this Controller class to test functionality:

```java
package com.devops.devops2026.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @GetMapping("/")
    public String hello() {
        return "Hello!";
    }
}
```

```sh
> curl http://127.0.0.1:8080/
Hello!⏎
```

## 2026-09-25

### Testing

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

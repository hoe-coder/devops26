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

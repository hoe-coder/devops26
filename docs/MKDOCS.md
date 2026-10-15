# MkDocs

MkDocs is a Static-Site-Generator (SSG) that builds a simple, nice looking website from a directory of Markdown files.

## Usage

Usually MkDocs is used as its own Docker container, placed on an endpoint making the documentation accessible completely separated from the actual application.
Using this setup MkDocs inherently supports a file watcher, meaning any filechanges are directly reflected in the UI.

In our case this could simply be achieved by placing the container configuration into the `compose.yaml` file and mounting the `/docs` volume.

But this is not what we want, we want to serve the documentation as part of our `java` application, that means the UI needs to be built into the Docker image.

Luckily MkDocs supports exactly that with the `mkdocs build` command available after installing, or in our case, using the Docker image.

In our Action the build step looks like this:

```sh
docker run --rm \
    -u $(id -u):$(id -g) \
    -v "$PWD":/docs \
    squidfunk/mkdocs-material \
    build --clean --site-dir src/main/resources/static/docs
```

As you can see we place the output of the build into the `src/main/resources/static/docs` directory of our java application. Because MkDocs is an SSG, all content generated is just `HTML, CSS, JS` and Tomcat (Springboots Webserver) can serve it just fine.

## Problems

After buiding our java application, we notices that the documentation is available, but only on acccess to `/docs/index.html`, not `/docs/` which is our intended behaviour, and unluckily also how MkDocs expects it to work. Because of this all links inside the MkDocs webpage break.

To fix this we need to add this java class to our project:

```java
package com.devops.devops2026;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DirectoryIndexFilter extends OncePerRequestFilter {

    private final ResourceLoader resourceLoader;

    public DirectoryIndexFilter(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.length() > 1 && path.endsWith("/")) {
            String resourcePath = "classpath:/static" + path + "index.html";
            Resource resource = resourceLoader.getResource(resourcePath);

            if (resource.exists() && resource.isReadable()) {
                request.getRequestDispatcher(path + "index.html").forward(request, response);
                return;
            }
        }

        if (!path.endsWith("/") && !path.contains(".")) {
            Resource resource = resourceLoader.getResource("classpath:/static" + path + "/index.html");
            if (resource.exists() && resource.isReadable()) {
                response.sendRedirect(path + "/");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

This class just redirects all requests to an endpoint ending in `/` to the respective `index.html`, if it exists.

With this adjustment we can build the java application again and the documentation endpoint on `http://localhost:8080/docs/` should work.

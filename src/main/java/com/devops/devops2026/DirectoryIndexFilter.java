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

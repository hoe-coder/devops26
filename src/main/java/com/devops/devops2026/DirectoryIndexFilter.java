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
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!"GET".equals(request.getMethod())
                && !"HEAD".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String contextPath = request.getContextPath();
        String path = request.getRequestURI()
                .substring(contextPath.length());

        // Only simple directory segments are handled by this filter.
        // Rejects dots, %, backslashes, semicolons and repeated slashes.
        if (!path.matches("/[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/?")) {
            filterChain.doFilter(request, response);
            return;
        }

        String directory = path.endsWith("/") ? path : path + "/";
        String indexPath = directory + "index.html";

        Resource resource =
                resourceLoader.getResource("classpath:/static" + indexPath);

        if (!resource.exists() || !resource.isReadable()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.endsWith("/")) {
            request.getRequestDispatcher(indexPath)
                    .forward(request, response);
            return;
        }

        response.sendRedirect(contextPath + directory);
    }
}
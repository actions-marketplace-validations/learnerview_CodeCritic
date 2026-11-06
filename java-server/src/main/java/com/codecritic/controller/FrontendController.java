package com.codecritic.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page dashboard and its static assets from the application classpath.
 *
 * <p>Each page route returns the same HTML shell, and the client renders the tab matching
 * the requested path.</p>
 */
@Controller
public class FrontendController {

    /**
     * Creates the dashboard controller.
     */
    public FrontendController() {
    }

    // The dashboard is a single-page app; every page route serves it and the
    // client shows the matching tab (Review / Repository / Debug).
    /**
     * Serves the dashboard shell for every page route.
     *
     * @return HTTP 200 with the HTML shell, or HTTP 404 when the template is missing
     */
    @GetMapping(value = { "/", "/index.html", "/review", "/review.html",
            "/repository", "/repository.html", "/debug", "/debug.html" }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> dashboard() {
        return resourceResponse("templates/index.html", MediaType.TEXT_HTML);
    }

    /**
     * Answers favicon requests with an empty response.
     *
     * @return HTTP 204 with no body
     */
    @GetMapping(value = "/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Serves the dashboard stylesheet.
     *
     * @return HTTP 200 with the CSS resource, or HTTP 404 when it is missing
     */
    @GetMapping(value = "/css/style.css", produces = "text/css")
    public ResponseEntity<Resource> style() {
        return resourceResponse("static/css/style.css", MediaType.valueOf("text/css"));
    }

    /**
     * Serves the dashboard client script.
     *
     * @return HTTP 200 with the JavaScript resource, or HTTP 404 when it is missing
     */
    @GetMapping(value = "/js/app.js", produces = "application/javascript")
    public ResponseEntity<Resource> script() {
        return resourceResponse("static/js/app.js", MediaType.valueOf("application/javascript"));
    }

    /**
     * Builds a non-cached response for a classpath resource.
     *
     * @param classpathLocation location of the resource relative to the classpath root
     * @param contentType media type to advertise for the resource
     * @return HTTP 200 with the resource and no-cache headers, or HTTP 404 when absent
     */
    private ResponseEntity<Resource> resourceResponse(String classpathLocation, MediaType contentType) {
        Resource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        // HTML/JS/CSS change with every deploy: never serve stale copies.
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.noCache())
                .body(resource);
    }
}

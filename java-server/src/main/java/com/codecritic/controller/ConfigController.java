package com.codecritic.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public runtime configuration for the browser. The Python agent is deliberately
 * not exposed here; Java is the only service-to-service boundary.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /**
     * Creates the controller; Spring supplies the configured scheduler flag.
     */
    public ConfigController() {
    }

    @Value("${simplydone4j.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /**
     * Returns the public runtime configuration consumed by the browser.
     *
     * @return a map containing the scheduler-enabled flag and whether a Redis URL is
     *         configured
     */
    @GetMapping
    public Map<String, Object> config() {
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", "");
        return Map.of(
                "schedulerEnabled", schedulerEnabled,
                "redisConfigured", !redisUrl.isBlank()
        );
    }
}

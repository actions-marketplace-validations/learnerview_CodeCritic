package com.codecritic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.learnerview.simplydone4j.autoconfigure.SimplyDoneRedisAutoConfiguration;

/**
 * Bootstrap entry point for the CodeCritic Java server.
 *
 * <p>Starts the Spring Boot application that exposes the synchronous and asynchronous
 * code-analysis endpoints. The SimplyDone4J Redis auto-configuration is excluded because
 * this application supplies its own {@code RedisConnectionFactory} from the configured
 * Redis URL, while Spring Boot's own Redis auto-configuration is retained to contribute
 * the shared {@code redisTemplate}.</p>
 */
@SpringBootApplication(exclude = {
        // We provide the single RedisConnectionFactory ourselves (from REDIS_URL / spring.redis.url),
        // so SimplyDone4J must not spin up its own standalone factory that ignores that config.
        // Boot's RedisAutoConfiguration is intentionally NOT excluded: it still contributes the
        // `redisTemplate` bean (used by Spring Data Redis infrastructure) without creating a second
        // connection factory, because ours already satisfies @ConditionalOnMissingBean.
        SimplyDoneRedisAutoConfiguration.class
})
public class CodeCriticApplication {

    /**
     * Creates the Spring Boot application bootstrap class.
     */
    public CodeCriticApplication() {
    }

    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(CodeCriticApplication.class, args);
    }
}

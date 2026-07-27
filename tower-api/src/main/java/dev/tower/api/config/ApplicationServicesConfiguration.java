package dev.tower.api.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.service.EnvironmentService;
import dev.tower.application.service.PromotionPathService;

/**
 * Wires the framework-free application layer into Spring.
 *
 * <p>EnvironmentService and PromotionPathService (dev.tower.application.service) carry no
 * framework annotation on purpose, so tower-api - the composition root - is where they are
 * declared as beans and handed their repository adapters (tower-persistence, on the runtime
 * classpath only) and a {@link Clock}.
 */
@Configuration
public class ApplicationServicesConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EnvironmentService environmentService(
            EnvironmentRepository environmentRepository, PromotionPathRepository promotionPathRepository) {
        return new EnvironmentService(environmentRepository, promotionPathRepository);
    }

    @Bean
    public PromotionPathService promotionPathService(
            PromotionPathRepository promotionPathRepository, EnvironmentRepository environmentRepository, Clock clock) {
        return new PromotionPathService(promotionPathRepository, environmentRepository, clock);
    }
}

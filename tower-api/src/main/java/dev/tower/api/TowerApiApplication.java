package dev.tower.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring Boot application. Implementation-Plan.md "Module Responsibilities - tower-api":
 * this module contains REST endpoints, wiring and security configuration.
 *
 * Component scanning covers the whole dev.tower base package so that tower-config and
 * tower-persistence configuration classes (e.g. TowerHomeAutoConfiguration,
 * TowerDataSourceConfiguration) are picked up without each module needing an explicit
 * @Import here.
 */
@SpringBootApplication(scanBasePackages = "dev.tower")
public class TowerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TowerApiApplication.class, args);
    }
}

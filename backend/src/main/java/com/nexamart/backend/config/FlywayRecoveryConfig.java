package com.nexamart.backend.config;

import java.util.Arrays;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRecoveryConfig {
  @Bean
  FlywayMigrationStrategy flywayMigrationStrategy() {
    return flyway -> {
      boolean hasFailedMigration = Arrays.stream(flyway.info().all())
          .anyMatch(info -> info.getState().isFailed());
      boolean hasValidationMismatch = !flyway.validateWithResult().validationSuccessful;
      if (hasFailedMigration || hasValidationMismatch) {
        flyway.repair();
      }
      flyway.migrate();
    };
  }
}

package com.nexamart.backend.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VJoyKartProperties.class)
public class DeliveryConfig {
  public DeliveryConfig(VJoyKartProperties properties) {
    properties.validate();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}

package edu.research.scb.internal;

import edu.research.scb.internal.config.CircuitBreakerRetryConfiguration;
import io.github.resilience4j.retry.Retry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = { "edu.research.scb.internal", "edu.research.scb.internal.bloomfilter", "edu.research.scb.internal.config" }) //
public class CircuitBreakerConfiguration {

    /**
     * The custom retry configuration for the circuit breaker.
     * @return {@code Retry} resiliency instance
     */
    @Bean
    public Retry circuitBreakerRetryConfig(CircuitBreakerRetryConfiguration retryConfig){
        return retryConfig.createCustomRetryConfiguration();
    }
}

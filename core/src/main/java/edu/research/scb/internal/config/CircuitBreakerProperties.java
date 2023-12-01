package edu.research.scb.internal.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Circuit Breaker Configuration Properties
 * @see <a href="https://docs.spring.io/spring-boot/docs/2.1.13.RELEASE/reference/html/boot-features-external-config.html#:~:text=Spring%20Boot%20lets%20you%20externalize,line%20arguments%20to%20externalize%20configuration.>Externalized Configuration, Points 24.8</a>
 */
@Data
@RequiredArgsConstructor
@ToString
@Component
@ConfigurationProperties(prefix = "circuit-breaker", ignoreInvalidFields = true)
public class CircuitBreakerProperties {

    private String strategy;
    private BloomFilterProperties bloomFilter;
    private BackoffStrategyProperties backoffStrategy;
    private CircuitThresholds thresholds;

    public String getStrategy() {
        return strategy;
    }

    public BloomFilterProperties getBloomFilter() {
        return bloomFilter != null ? bloomFilter : new BloomFilterProperties();
    }

    public BackoffStrategyProperties getBackoffStrategy() {
        return backoffStrategy != null ? backoffStrategy : new BackoffStrategyProperties();
    }

    public CircuitThresholds getThresholds() {
        return thresholds != null ? thresholds : new CircuitThresholds();
    }
}

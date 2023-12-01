package edu.research.scb.internal.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.stereotype.Component;

import static edu.research.scb.internal.DefaultConstants.*;

@Data
@RequiredArgsConstructor
@ToString
@Component
public class CircuitThresholds {

    private Integer transientFaultCountThreshold;
    private Integer failureCountThreshold;
    private Long openStateWaitDurationInSeconds;
    private Long openStateServiceLevelWaitDurationInSeconds;
    private Double serviceFailureRateThreshold;

    // Allows certain failed requests to pass through, discounting them initially
    // as transient faults only if the circuit for the requests is closed. Post
    // the transient faults, the failed requests should be registered as failed
    // requests and circuit breaker should operate according to the implementation
    public Integer getTransientFaultCountThreshold() {
        return transientFaultCountThreshold != null ? transientFaultCountThreshold : DEFAULT_TRANSIENT_FAULT_COUNT_THRESHOLD;
    }

    // The overall threshold beyond which the circuit breaker is opened.
    public Integer getFailureCountThreshold() {
        return failureCountThreshold != null ? failureCountThreshold : DEFAULT_FAILURE_COUNT_THRESHOLD;
    }

    // The time period in seconds beyond which the circuit breaker will be half-opened.
    public Long getOpenStateWaitDurationInSeconds() {
        return openStateWaitDurationInSeconds != null ? openStateWaitDurationInSeconds : DEFAULT_OPEN_STATE_WAIT_DURATION_IN_SECS;
    }

    // The time period in seconds beyond which the circuit breaker will be half-opened in the situation when the
    // service level failure threshold has been crossed.
    public Long getOpenStateServiceLevelWaitDurationInSeconds() {
        return openStateServiceLevelWaitDurationInSeconds != null ? openStateServiceLevelWaitDurationInSeconds : DEFAULT_SERVICE_COOLING_PERIOD_IN_SECS;
    }

    // Service level failure threshold percent, beyond which the circuit for all requests
    // pertaining to that service will be opened.
    public Double getServiceFailureRateThreshold() {
        return serviceFailureRateThreshold != null ? serviceFailureRateThreshold : DEFAULT_SERVICE_FAILURE_RATE_THRESHOLD;
    }
}

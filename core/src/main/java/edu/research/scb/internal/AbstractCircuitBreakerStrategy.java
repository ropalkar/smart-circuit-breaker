package edu.research.scb.internal;

import edu.research.scb.internal.config.CircuitBreakerProperties;
import edu.research.scb.internal.config.CircuitThresholds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import static edu.research.scb.internal.DefaultConstants.*;

public abstract class AbstractCircuitBreakerStrategy {

    private static final Logger logger = LogManager.getLogger(AbstractCircuitBreakerStrategy.class);

    public enum States {
        OPEN, CLOSED, HALF_OPEN;
    }

    // Service Counter for storing the success rate of any particular service.
    protected final ConcurrentHashMap<String, ServiceCounter> serviceCounter = new ConcurrentHashMap<>();

    // The overall properties of the circuit breaker configuration
    protected final CircuitBreakerProperties config;

    // Internal flag for enabling or disabling the circuit breaker.
    protected final boolean enableCircuitBreaker;

    // The thresholds properties which are exposed as configuration properties.
    protected final CircuitThresholds thresholds;

    // Request Signature
    // ------------------------------------------
    // Primary function that creates the signature using the method name and the request data. Trims each of the input
    // strings and removes and leading and trailing white-spaces before creating the signature. If either of the
    // method-name or the data is null, then returns an empty string.
    protected  static final BiFunction<String, String, String> CREATE_SIGNATURE = (methodName, data) -> {
        if(methodName != null && data != null){
            methodName = methodName.trim().replaceAll("^\\s+|\\s+$","");
            data = data.trim().replaceAll("^\\s+|\\s+$","");
            return String.join("-", methodName, data);
        }
        return "";
    };

    // Circuit Evaluation
    // ------------------------------------------
    // Primary predicate for evaluating if the circuit is open at a signature level
    // Following are the circuit breaker states and the predicate conditions involved
    //
    // (1) CLOSED : If the transient faults are below the transient-faults-threshold
    // (2) CLOSED : If transient faults have crossed the threshold, but the failure count
    //              is still below the failure threshold.
    // (3) CLOSED : If failure threshold for the signature has been crossed, but the time elapsed
    //              since the last request is more than the cooling period in seconds.
    // (4) OPEN   : If none of the above conditions are met, it implies that the circuit is open
    //              for the particular signature and no requests will be allowed to proceed
    //
    protected static final BiPredicate<RequestMetrics, CircuitThresholds> CIRCUIT_EVALUATION = (reqMetric, threshold) -> {
        if(reqMetric.getTransientFaultCount() <= threshold.getTransientFaultCountThreshold() ||
                reqMetric.getFailureCount() <= threshold.getFailureCountThreshold()){
            return true;
        } else return ChronoUnit.SECONDS.between(reqMetric.getLastFailureTimestamp(), LocalDateTime.now()) > threshold.getOpenStateWaitDurationInSeconds();
    };

    // Prometheus Metric Counter for the states.
    // ------------------------------------------
    // The counters cumulative and store an increment count of the values only, however, the final details that
    // are displayed in grafana, there we calculate the ratio of the open / total or closed / total percentage,
    // which helps to track which state are the incoming request signatures landing in and therefore it shows the
    // trend of the requests over the timeline.
    // Also, it is difficult to track which request signature moved from which exact state and when, and hence
    // showing this as a ratio is a more practical approach.
    protected final Counter OPEN_STATE_COUNTER;
    protected final Counter CLOSED_STATE_COUNTER;
    protected final Counter TOTAL_COUNTER;

    /**
     * Super Constructor
     *
     * @param config the {@code CircuitBreakerProperties} properties
     */
    protected AbstractCircuitBreakerStrategy(CircuitBreakerProperties config, MeterRegistry meterRegistry) {
        this.config = config;
        this.enableCircuitBreaker = config.getStrategy() != null;
        this.thresholds = config.getThresholds();
        logger.info("Thresholds: {}", thresholds);
        OPEN_STATE_COUNTER = meterRegistry.counter(METRIC_OPEN_STATE_COUNTER_NAME);
        CLOSED_STATE_COUNTER = meterRegistry.counter(METRIC_CLOSED_STATE_COUNTER_NAME);
        TOTAL_COUNTER = meterRegistry.counter(METRIC_TOTAL_COUNTER_NAME);
    }

    /**
     * Increments the total count of requests for the given service
     *
     * @param serviceName the name of the service.
     */
    protected void incrTotalCountForService(String serviceName){
        ServiceCounter sc = serviceCounter.getOrDefault(serviceName, new ServiceCounter(serviceName));
        sc.incTotalCount();
        serviceCounter.put(serviceName, sc);
    }

    /**
     * Increments the count of failures encountered for the given service
     *
     * @param serviceName the name of the service.
     */
    protected void incrFailureCountForService(String serviceName){
        ServiceCounter sc = serviceCounter.getOrDefault(serviceName, new ServiceCounter(serviceName));
        sc.incFailureCount();
        serviceCounter.put(serviceName, sc);
    }

    /**
     * Decrements the count of failures for the given service
     *
     * @param serviceName the name of the service
     */
    protected void decrFailureCountForService(String serviceName){
        ServiceCounter sc = serviceCounter.getOrDefault(serviceName, new ServiceCounter(serviceName));
        sc.decrFailureCount();
        serviceCounter.put(serviceName, sc);
    }

    /**
     * Deduces the failure percentage for a given service
     *
     * @param serviceName the name of the service
     * @return the failure percentage
     */
    protected double getFailurePercentage(String serviceName){
        logger.debug("Service-counter: {}", serviceCounter);
        return serviceCounter.getOrDefault(serviceName, new ServiceCounter(serviceName)).getFailurePercentage();
    }

    protected boolean isServiceHalfOpen(String serviceName){
        return ChronoUnit.SECONDS.between(serviceCounter.getOrDefault(serviceName, new ServiceCounter(serviceName))
                .getLastTimeStamp(), LocalDateTime.now()) > thresholds.getOpenStateServiceLevelWaitDurationInSeconds();
    }
}

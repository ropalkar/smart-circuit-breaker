package edu.research.scb.internal;

import edu.research.scb.internal.config.CircuitBreakerProperties;
import edu.research.scb.sdk.CircuitBreakerStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "circuit-breaker.strategy", havingValue = "count-min-sketch")
public class CountMinSketchCircuitBreakerStrategy extends AbstractCircuitBreakerStrategy implements CircuitBreakerStrategy {


    /**
     * Super Constructor
     *
     * @param config the {@code CircuitBreakerProperties} properties
     */
    protected CountMinSketchCircuitBreakerStrategy(CircuitBreakerProperties config, MeterRegistry meterRegistry) {
        super(config, meterRegistry);
    }

    /**
     * Determines if the client request should be allowed to be sent to the server, based on the
     * state of the circuit breaker.
     *
     * @param serviceName the name of the service
     * @param methodName  the name of the method
     * @param data        the string content used to determine the request signature
     * @return {@code true} if request can be sent.
     */
    @Override
    public boolean allowRequest(String serviceName, String methodName, String data) {
        return false;
    }

    /**
     * Updates the circuit breaker to move to <i>half-open</i> or <i>closed</i> state when the
     * request succeeds
     *
     * @param serviceName the name of the service
     * @param methodName  the name of the method
     * @param data        the string content used to determine the request signature
     */
    @Override
    public void onSuccess(String serviceName, String methodName, String data) {

    }

    /**
     * Updates the circuit breaker to move to <i>open</i> state when the requets fails or errors out.
     *
     * @param serviceName the name of the service
     * @param methodName  the name of the method
     * @param data        the string content used to determine the request signature
     */
    @Override
    public void onFailure(String serviceName, String methodName, String data) {

    }
}

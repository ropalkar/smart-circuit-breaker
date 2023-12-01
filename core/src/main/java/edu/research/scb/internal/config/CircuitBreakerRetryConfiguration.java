package edu.research.scb.internal.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static edu.research.scb.internal.DefaultConstants.*;

@Configuration
public class CircuitBreakerRetryConfiguration {

    private static final Logger logger = LogManager.getLogger(CircuitBreakerRetryConfiguration.class);

    @Autowired
    private CircuitBreakerProperties config;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private MeterRegistry meterRegistry;

    private Counter retryCounterSuc;
    private Counter retryCounterFail;

    /**
     * Creates a custom configuration {@code Bean} for the smart circuit breaker. The retry config handles
     * the {@code StatusRuntimeException} status codes and {@code ServiceInvocationNotPermittedException}
     * when the circuit is in opened state.
     *
     * <p>The options to use the {@code IntervalFunction} and other parameters is provided via the
     * {@code CircuitBreakerProperties} config properties that are specified by the clients via the
     * {@code application.properties} or {@code application.yaml}
     *
     * <p>The primary handler that configures how and under what conditions the inter-service
     * calls will be retried. If the throwable instance is of type {@code StatusRuntimeException}
     * and the {@code Status.Code} belongs to the set of the re-try-able status codes, then the
     * predicate here returns {@code true}. For the exception that marks the circuit is open via
     * {@code ServiceInvocationNotPermittedException}, the predicate returns {@code false}.
     *
     * <p>All other exceptions are currently not handled.
     *
     * @return the custom {@code Retry} instance
     */
    public Retry createCustomRetryConfiguration(){
        logger.info("Config: {}", config);
        logger.info("Creating the retry config for the Smart-Circuit-Breaker");

        if(retryCounterSuc == null)
            retryCounterSuc = meterRegistry.counter("service.retry_counter.suc");

        if(retryCounterFail == null)
            retryCounterFail = meterRegistry.counter("service.retry_counter.fail");

        // Configuring the status codes on which the circuit breaker retries should be conducted.
        List<Status.Code> retryCodes = config.getBackoffStrategy().getGrpcRetryCodes();
        logger.info("Retry-Codes: {}", retryCodes);

        // The maximum retry attempts need to be higher than the transient faults thresholds and the failure
        // count threshold in order for the circuit to be managed for that request signature. Else, if the
        // retry attempts are less than those 2 combined, then the request will eventually be not sent to
        // the circuit breaker after the exhaustion of the retry attempts, and therefore we won't get any
        // ServiceInvocationNotPermittedException as the retry mechanism itself will throw an exception
        // right away, resulting in an Internal Server Error with the original StatusRuntimeException.
        CircuitThresholds thresholds = config.getThresholds();
        int baselineValue = thresholds.getFailureCountThreshold() + thresholds.getTransientFaultCountThreshold() + DEFAULT_BUFFER_COUNT_FOR_RETRY_ATTEMPTS;
        logger.info("Baseline value of total failures :  {}", baselineValue);

        Integer maxRetryAttempts = config.getBackoffStrategy().getMaxRetryAttempts();
        if(maxRetryAttempts == null || maxRetryAttempts < baselineValue)
            maxRetryAttempts = baselineValue;
        logger.info("Maximum retry attempts: {}", maxRetryAttempts);

        boolean enableSmartCircuitBreaker = config.getStrategy() != null;
        logger.info("Smart Circuit Breaker enabled ? - {}", enableSmartCircuitBreaker);

        // Getting the IntervalFunction from the factory
        IntervalFunction backoffStrategyFunction = BackoffStrategyFactory.getBackoffStrategy(config);

        // Creating the custom configuration.
        RetryConfig retryCustomConfig = enableSmartCircuitBreaker ?
                RetryConfig.custom()
                .intervalFunction(backoffStrategyFunction)
                .maxAttempts(maxRetryAttempts)
                .retryOnException(t -> {
                    boolean result = false;
                    if(t instanceof StatusRuntimeException s){
                        logger.debug("Resiliency check for StatusRuntimeException with status='{}'", s.getStatus());
                        result = retryCodes.contains(s.getStatus().getCode());
                    }
                    logger.debug("Retrying - {}", result);
                    if(result)
                        retryCounterSuc.increment();
                    else
                        retryCounterFail.increment();
                    return result;
                })
                .build()
                : RetryConfig.ofDefaults();

        // Creating the configuration.
        return retryRegistry
                .retry(CIRCUIT_BREAKER_NAME, retryCustomConfig);
    }

    public static class BackoffStrategyFactory {

        /**
         * Factory method for providing the appropriate backoff strategy on the basis of the
         * configuration properties passed. The strategies are evaluated in a particular
         * order, which is
         * 1. Exponential backoff
         * 2. Randomized backoff
         * 3. Fixed Width backoff.
         * In the absence of any backoff strategy specified, it uses the fixed-width backoff
         * strategy with the default values.
         *
         * <p>The default values for each of the backoff strategies are applied within the
         * respective property classes itself, using the defaults from the Resiliency library
         * itself.
         *
         * @param config {@code CircuitBreakerProperties} circuit breaker configuration properties
         * @return {@code IntervalFunction} instance with the strategy applied.
         */
        public static IntervalFunction getBackoffStrategy(CircuitBreakerProperties config){
            BackoffStrategyProperties backoffStrategyProperties = config.getBackoffStrategy();

            // For the scenario when no configuration properties are defined in the yaml
            // Using the default fixed width strategy or when the backoff strategy is not exponential one.
            if(backoffStrategyProperties == null || backoffStrategyProperties.getExponential() == null) {
                logger.info("Using the default backoff strategy");
                return getDefaultBackoffStrategy(config);
            }

            // Using the exponential one.
            BackoffStrategyProperties.Exponential exp = backoffStrategyProperties.getExponential();
            logger.info("Using the exponential backoff strategy: {}", exp);
            long iim = exp.getInitialIntervalMillis() != null ? exp.getInitialIntervalMillis() : IntervalFunction.DEFAULT_INITIAL_INTERVAL;
            double m = exp.getMultiplier() != null ? exp.getMultiplier() : IntervalFunction.DEFAULT_MULTIPLIER;
            return IntervalFunction.ofExponentialBackoff(iim, m);
        }

        /**
         * Gets the default interval function, based on the fixed interval.
         * @param config config properties
         * @return @{code IntervalFunction}
         */
        public static IntervalFunction getDefaultBackoffStrategy(CircuitBreakerProperties config){
            BackoffStrategyProperties.Fixed f = config.getBackoffStrategy().getFixed() != null ?
                    config.getBackoffStrategy().getFixed() : new BackoffStrategyProperties.Fixed();
            int millis = f.getFixedIntervalMillis() != null ? f.getFixedIntervalMillis() : (int) IntervalFunction.DEFAULT_INITIAL_INTERVAL;
            logger.info("Fixed interval millis: {}", millis);
            return IntervalFunction.of(millis);
        }

    }
}

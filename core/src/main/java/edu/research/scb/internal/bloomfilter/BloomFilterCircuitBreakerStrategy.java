package edu.research.scb.internal.bloomfilter;

import edu.research.scb.internal.AbstractCircuitBreakerStrategy;
import edu.research.scb.internal.RequestMetrics;
import edu.research.scb.internal.config.BloomFilterProperties;
import edu.research.scb.internal.config.CircuitBreakerProperties;
import edu.research.scb.sdk.CircuitBreakerStrategy;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import static edu.research.scb.internal.DefaultConstants.*;

/**
 * The bloom-filter based implementation of the {@code CircuitBreakerStrategy}.
 * The circuit breaker strategy is governed by the config property {@code circuit-breaker.strategy} with the value equals
 * {@code bloom-filter}. Even if no strategy is specified, this is the default strategy to be used, however, in that
 * situation, the retry configuration won't instantiate the circuit breaker for any retries.
 */
@Service
@ConditionalOnProperty(name = "circuit-breaker.strategy", havingValue = "bloom-filter", matchIfMissing = true)
public class BloomFilterCircuitBreakerStrategy extends AbstractCircuitBreakerStrategy implements CircuitBreakerStrategy {

    private static final Logger logger = LogManager.getLogger(BloomFilterCircuitBreakerStrategy.class);

    // The primary bloom filter using the guava implementation used for storing the signatures
    // of the inter-service requests.
    private final BloomFilterAdapter bloomFilterAdapter;

    // The failure store for housing all the failed requests.
    private final HashStoreAdapter hashStoreAdapter;

    // The buffer queue for storing the requests that have recovered from failures.
    private final ResetBufferQueueAdapter resetQueueAdapter;

    // The finalizer for resetting and re-instantiating the bloom filter.
    private final BloomFilterFinalizer bfFinalizer;

    @Autowired
    public BloomFilterCircuitBreakerStrategy(CircuitBreakerProperties config, MeterRegistry meterRegistry) {
        super(config, meterRegistry);

        // Setting up the configuration properties of the bloom filter.
        BloomFilterProperties bfp = config.getBloomFilter();
        logger.info("Bloom Filter Properties: {}", bfp);
        this.bloomFilterAdapter = new BloomFilterAdapter(bfp.getCharset(), bfp.getCapacity(), bfp.getFpp());
        this.hashStoreAdapter = new HashStoreAdapter(bfp.getStoreCapacity());
        this.resetQueueAdapter = new ResetBufferQueueAdapter();
        this.bfFinalizer = new BloomFilterFinalizer(resetQueueAdapter, bloomFilterAdapter, hashStoreAdapter, bfp.getResetBufferCapacity(), bfp.getResetThreshold(), bfp.getResetTimeThresholdInMinutes(), meterRegistry);

        // Setting up custom metrics for capturing the false positive rate and approximate element count
        // for the bloom filter.
        Gauge.builder(METRIC_FPP_NAME, bloomFilterAdapter::getExpectedFalsePositiveRate)
                .description("The expected false positive probability of the bloom filter")
                .register(meterRegistry);

        Gauge.builder(METRIC_DISTINCT_COUNT_NAME, bloomFilterAdapter::getApproxDistinctElementCount)
                .description("The approximate distinct element count of the bloom filter")
                .register(meterRegistry);

        Gauge.builder(METRIC_FINALIZER_QUEUE_NAME, () -> resetQueueAdapter.getResetQueue().size())
                .description("The size of the reset buffer queue")
                .register(meterRegistry);

        Gauge.builder(METRIC_FINALIZER_AUX_QUEUE_NAME, () -> resetQueueAdapter.getAuxResetQueue().size())
                .description("The size of the auxiliary reset buffer queue")
                .register(meterRegistry);
    }

    /**
     * Determines if the client request should be allowed to be sent to the server, based on the
     * state of the circuit breaker.
     * <i>For bloom filter strategy</i> : if the {@code mightContain()} returns {@code false},
     * then the request could be allowed to be sent to the server. If it returns {@code true}, then the
     * data may or may not be present in the bloom filter, in which situation, it is further evaluated.
     *
     * @param serviceName the name of the service
     * @param methodName the name of the method
     * @param data        the string content used to determine the request signature
     * @return {@code true} if request can be sent.
     */
    @Override
    public boolean allowRequest(String serviceName, String methodName, String data) {
        // If circuit breaker is disabled, all calls will be let through.
        if(!enableCircuitBreaker)
            return true;

        incrTotalCountForService(serviceName);
        TOTAL_COUNTER.increment();

        final String signature = CREATE_SIGNATURE.apply(methodName, data);
        logger.debug(hashStoreAdapter.printRequestMetric(signature));

        // If the data is not present within the bloom filter, then the request can be allowed right away.
        // If present, it will be further evaluated.
        if(bloomFilterAdapter.mightContain(signature)){

            // STATE : OPEN
            // Checking for server level circuit breaker state. If the failure percentage is
            // above the threshold, the circuit is open, and thus requests won't be allowed.
            // The service level circuit is also evaluated based on the last time stamp request.
            if(getFailurePercentage(serviceName) > thresholds.getServiceFailureRateThreshold() && !isServiceHalfOpen(serviceName)) {
                logger.error("CIRCUIT-OPEN: Circuit is opened for the service {} due to failure percentage above the service-failure-threshold: {}", serviceName, thresholds.getServiceFailureRateThreshold());
                OPEN_STATE_COUNTER.increment();
                return false;
            }


            // Check the definition for the CIRCUIT_EVALUATION predicate
            // If the local request metric cache contains the data, then it is further evaluated
            // to determine if the circuit is open or closed.
            if(hashStoreAdapter.containsKey(signature)) {
                boolean status = CIRCUIT_EVALUATION.test(hashStoreAdapter.get(signature), thresholds);
                if(!status)
                    OPEN_STATE_COUNTER.increment();
                /*else
                    HALF_OPEN_STATE_COUNTER.increment();*/
                return status;
            }

        }

        // STATE : CLOSED
        CLOSED_STATE_COUNTER.increment();
        return true;
    }

    /**
     * Updates the circuit breaker to move to <i>half-open</i> or <i>closed</i> state when the
     * request succeeds
     *
     * @param serviceName the name of the service
     * @param methodName the name of the method
     * @param data        the string content used to determine the request signature
     */
    @Override
    public void onSuccess(String serviceName, String methodName, String data) {
        // If circuit breaker is disabled, no records are maintained.
        if(!enableCircuitBreaker)
            return;

        final String signature = CREATE_SIGNATURE.apply(methodName, data);
        logger.debug("** SUCCEEDED **: gRPC call succeeded for service='{}' with signature='{}'", serviceName, signature);

        // Checking for finalizer process if applicable
        bfFinalizer.runFinalizer();

        // The overall success count for the service is recorded by decrementing the overall failure count.
        decrFailureCountForService(serviceName);

        // Upon succeeding if there were previous failures present for the signature, the
        // failure count is decremented. If the failure count reaches 0, then the request
        // is pushed to the queue for resetting during the bloom filter brown-out
        if(hashStoreAdapter.containsKey(signature)) {
            final RequestMetrics rm = hashStoreAdapter.get(signature);
            rm.resetTransientFaultCount();

            // Decrementing the failure count for the request
            // If the count is found to be 0, that implies that the circuit can be moved to the
            // closed state for the request.
            if(rm.decrFailureCount() == 0){

                // Adding the request to the reset queue.
                resetQueueAdapter.add(rm);
            }
        }
    }

    /**
     * Updates the circuit breaker to move to <i>open</i> state when the requests fails or errors out.
     *
     * @param serviceName the name of the service
     * @param methodName the name of the method
     * @param data        the string content used to determine the request signature
     */
    @Override
    public void onFailure(String serviceName, String methodName, String data) {
        // If circuit breaker is disabled, no records are maintained.
        if(!enableCircuitBreaker)
            return;

        final String signature = CREATE_SIGNATURE.apply(methodName, data);
        logger.debug(" ** FAILED ** : gRPC call failed for service='{}' with signature='{}'", serviceName, signature);

        // Checking for finalizer process if applicable
        bfFinalizer.runFinalizer();

        // The request metric is created only in situation of a failure
        RequestMetrics rm = hashStoreAdapter.getOrDefault(signature);

        // The failure timestamp is always updated, irrespective of transient faults or the
        // primary failure count
        rm.updateLastFailureTimestamp();

        // Checking for the transient faults. Transient faults are ignored as long as they
        // are below the threshold. Transient faults are reset every time a success occurs
        // but incremented initially till they do not cross the threshold
        if(rm.getTransientFaultCount() <= thresholds.getTransientFaultCountThreshold()){
            rm.incTransientFaultCount();
            hashStoreAdapter.put(signature, rm);
            return;
        }

        // If the transient faults have cross the threshold, then it is regarded as a failure.
        // The failure count and the failure time stamp are updated for the request.
        // Also, an entry is registered in the bloom filter for the same.
        // If the failure count has already surpassed the failure threshold, then we don't increment the counter
        // This will help in moving the circuit to half-open state when the service does a self-recovery.
        if(rm.getFailureCount() <= thresholds.getFailureCountThreshold()) {
            rm.incFailureCount();
            bloomFilterAdapter.put(signature);
        }
        hashStoreAdapter.put(signature, rm);

        // The overall failure count for the service is incremented.
        incrFailureCountForService(serviceName);
    }
}

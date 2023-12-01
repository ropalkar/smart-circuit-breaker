package edu.research.scb.internal;

import io.grpc.Status;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import static io.grpc.Status.Code.*;

public class DefaultConstants {

    // ============== DEFAULT : Bloom Filter Properties ==============
    // The default capacity of the bloom filter if not specified via the config properties.
    // This will be over-ridden using the property 'circuit-breaker.bloom-filter.capacity'
    public static final Long DEFAULT_BLOOM_FILTER_CAPACITY = 10000L;

    // The default false positive probability of the bloom filter with which it is initialized,
    // if not specified via the config properties. The actual fpp is going to vary over the
    // course of time of usage, where a high fpp indicates that the bloom filter is housing
    // too many elements.
    // This will be over-ridden using the property 'circuit-breaker.bloom-filter.fpp'
    public static final Double DEFAULT_BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY = 0.05;

    // The default charset to be used during initialization of the bloom-filter if not specified
    // via the config properties.
    // This will be over-ridden using the property 'circuit-breaker.bloom-filter.charset'
    public static final Charset DEFAULT_BLOOM_FILTER_CHARSET = Charset.defaultCharset();

    // The static capacity of the failure store that will house all the failed requests.
    // If the failure capacity is full, then it is considered to be a service level
    // failure and the circuit should be opened.
    public static final Integer DEFAULT_REQUEST_FAILURE_CAPACITY = 10000;

    // The queue buffer that will store the requests that have now recovered from failures
    // and need to be reset in the bloom filter. Once the queue is full, the auxiliary queue
    // is used for storing additional requests and meanwhile this queue is used to reset the
    // bloom filter.
    public static final Integer DEFAULT_BLOOM_FILTER_RESET_QUEUE_BUFFER = 100;

    // If the reset queue buffer is above this threshold, then it will be reset and
    // the finalizer process will be performed.
    public static final Double DEFAULT_BLOOM_FILTER_QUEUE_BUFFER_RESET_THRESHOLD = 80.0;

    // The reset queue buffer will be reset based on which ever evaluates to true first
    // the above threshold or this time in minutes.
    public static final Double DEFAULT_BLOOM_FILTER_QUEUE_BUFFER_TIME_THRESHOLD_IN_MINUTES = 2.0;

    // ============== DEFAULT : Overall Circuit Breaker Properties ==============
    // Allows certain failed requests to pass through, discounting them initially
    // as transient faults only if the circuit for the requests is closed. Post
    // the transient faults, the failed requests should be registered as failed
    // requests and circuit breaker should operate according to the implementation
    public static final Integer DEFAULT_TRANSIENT_FAULT_COUNT_THRESHOLD = 2;

    // The overall threshold beyond which the circuit breaker is opened.
    public static final Integer DEFAULT_FAILURE_COUNT_THRESHOLD = 3;

    // The buffer for the retry attempts that is to be added to the total count of the transient faults and
    // the failure thresholds. See the @{code CircuitBreakerRetryConfiguration} for more explanation.
    public static final Integer DEFAULT_BUFFER_COUNT_FOR_RETRY_ATTEMPTS = 3;

    // The time period in seconds beyond which the circuit breaker will be half-opened.
    public static final Long DEFAULT_OPEN_STATE_WAIT_DURATION_IN_SECS = 5L;

    // The time period in seconds beyond which the circuit breaker will be half-opened in the situation when the
    // service level failure threshold has been crossed.
    public static final Long DEFAULT_SERVICE_COOLING_PERIOD_IN_SECS = 15L;

    // Server level failure threshold percent, beyond which the circuit for all requests
    // pertaining to that service will be opened.
    public static final Double DEFAULT_SERVICE_FAILURE_RATE_THRESHOLD = 20.0;

    // The name of the circuit breaker that is used in several config parameters
    // This is specified by the consuming services as the name of the retry config.
    public static final String CIRCUIT_BREAKER_NAME = "SmartCircuitBreaker";

    // For comparison, introducing the native resiliency4j based retry and circuit breaker.
    public static final String RESILIENCY_CIRCUIT_BREAKER_NAME = "Resiliency4jBasedCircuitBreaker";
    public static final String RESILIENCY_RETRY_NAME = "Resiliency4jBasedRetry";

    // The list of the grpc status codes which are the retry candidates for the circuit breaker
    // or the resiliency mechanism.
    public static final List<Status.Code> DEFAULT_RETRY_STATUS_CODES =
            Arrays.asList(UNKNOWN, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, INTERNAL, UNAVAILABLE, ABORTED, DATA_LOSS);

    // The list of the grpc static codes which are NOT to be retried during failures.
    public static final List<Status.Code> NO_RETRY_STATUS_CODES =
            Arrays.asList(OK, CANCELLED, INVALID_ARGUMENT, NOT_FOUND, ALREADY_EXISTS, PERMISSION_DENIED,
                    FAILED_PRECONDITION, UNIMPLEMENTED, UNAUTHENTICATED);

    // ============= Metrics : Prometheus Custom Metrics =================
    // False Positive Rate, Approx Element Count of the bloom filter
    // Defining a gauge with this name for the actuators and monitoring
    public static final String METRIC_FPP_NAME = "bloom_filter_false_positive_probability";
    public static final String METRIC_DISTINCT_COUNT_NAME = "bloom_filter_approx_distinct_elements_count";
    public static final String METRIC_FINALIZER_QUEUE_NAME = "bloom_filter_finalizer_queue_size";
    public static final String METRIC_FINALIZER_AUX_QUEUE_NAME = "bloom_filter_finalizer_aux_queue_size";
    public static final String METRIC_BLOOM_FILTER_FINALIZER_NAME = "bloom_filter_finalizer_time";
    public static final String METRIC_BLOOM_FILTER_FINALIZER_COUNTER_NAME = "bloom_filter_finalizer_counter";
    public static final String METRIC_BLOOM_FILTER_RESET_NAME = "bloom_filter_reset_time";
    public static final String METRIC_OPEN_STATE_COUNTER_NAME = "smart_circuit_breaker_open_state_counter";
    public static final String METRIC_CLOSED_STATE_COUNTER_NAME = "smart_circuit_breaker_closed_state_counter";
    public static final String METRIC_HALF_OPEN_STATE_COUNTER_NAME = "smart_circuit_breaker_half_open_state_counter";
    public static final String METRIC_TOTAL_COUNTER_NAME = "smart_circuit_breaker_total_counter";

}















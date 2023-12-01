package edu.research.scb.internal.bloomfilter;

import edu.research.scb.internal.RequestMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import static edu.research.scb.internal.DefaultConstants.*;

public class BloomFilterFinalizer {

    private static final Logger logger = LogManager.getLogger(BloomFilterFinalizer.class);

    private final ResetBufferQueueAdapter resetQueueAdapter;
    private final BloomFilterAdapter bloomFilterAdapter;
    private final HashStoreAdapter hashStoreAdapter;
    private final Integer queueBuffer;
    private final Double resetThreshold;
    private final Double resetTimeInMinutes;
    private LocalDateTime lastResetTimestamp;
    private final Semaphore mutex;
    private final AtomicLong finalizerTime = new AtomicLong(0);
    private final AtomicLong resetTime = new AtomicLong(0);
    private final Counter finalizerCounter;

    public BloomFilterFinalizer(ResetBufferQueueAdapter resetQueueAdapter, BloomFilterAdapter bloomFilterAdapter, HashStoreAdapter hashStoreAdapter, Integer queueBuffer, Double resetThreshold, Double resetTimeInMinutes, MeterRegistry meterRegistry) {
        this.mutex = new Semaphore(1);
        this.resetQueueAdapter = resetQueueAdapter;
        this.bloomFilterAdapter = bloomFilterAdapter;
        this.hashStoreAdapter = hashStoreAdapter;
        this.queueBuffer = queueBuffer;
        this.resetThreshold = resetThreshold;
        this.resetTimeInMinutes = resetTimeInMinutes;
        this.lastResetTimestamp = LocalDateTime.now();

        Gauge.builder(METRIC_BLOOM_FILTER_RESET_NAME, resetTime::get)
                .description("Timer for recording the bloom filter reset process")
                .register(meterRegistry);

        Gauge.builder(METRIC_BLOOM_FILTER_FINALIZER_NAME, finalizerTime::get)
                .description("Timer for recording the bloom filter finalizing process")
                .register(meterRegistry);

        finalizerCounter = meterRegistry.counter(METRIC_BLOOM_FILTER_FINALIZER_COUNTER_NAME);
    }

    /**
     * Primary finalizer method that performs the reset of the bloom filter and the hash store
     * <p>The finalizing operation is performed using a mutex, i.e. only a single thread/request will have
     * access to this implementation.</p>
     *
     * <p>The finalizing operation is only performed when the reset queue is filled above the reset-threshold,
     * which is fixed at around 80% i.e. only when the queue is for e.g. 80% filled, the finalizing operation
     * will be performed else it will be a no-op. This is done in order to not waste too many cycles in the
     * finalizing operation only.</p>
     *
     */

    public void runFinalizer(){
        try {
            mutex.acquire();

            // If the queue is filled above the reset-threshold, then only the finalizing operation will be
            // performed. The threshold is fixed at 80%.
            logger.trace("Size='{}', Queue-Buffer-Threshold='{}'", resetQueueAdapter.size(), queueBuffer * resetThreshold * 0.01);
            if((resetQueueAdapter.size() > queueBuffer * resetThreshold * 0.01)) //|| (ChronoUnit.MINUTES.between(lastResetTimestamp, LocalDateTime.now()) > resetTimeInMinutes)
                initiateProcess();

        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        } finally {
            mutex.release();
        }
    }

    void initiateProcess(){
        finalizerCounter.increment();
        finalizerTime.set(System.nanoTime());
        logger.trace("----------------------------- Finalizer -----------------------------");
        lastResetTimestamp = LocalDateTime.now();
        logger.debug("FINALIZER: Initiating the finalizing process for the bloom filter and hash store.");

        // If threshold is met, all the adapters are switched to the auxiliary data structures.
        hashStoreAdapter.switchToAux();
        bloomFilterAdapter.switchToAux();
        resetQueueAdapter.switchToAux();

        // The current hast store, containing the failed requests is fetched. Also, the current queue
        // holding the requests that are now recovered from previous failures is fetched, that would
        // eventually be removed from the hash-store and the bloom filter.
        final ConcurrentHashMap<String, RequestMetrics> orgReq = hashStoreAdapter.getRequestMetrics();
        logger.trace("FINALIZER: Original Hash Store size: {}", orgReq.size());
        final ConcurrentLinkedQueue<RequestMetrics> orgQueue = resetQueueAdapter.getResetQueue();
        logger.trace("FINALIZER: Original queue size: {}", orgQueue.size());

        // The queue is emptied out and the corresponding succeeded requests are then removed from the
        // hash store.
        while(!orgQueue.isEmpty()){
            orgReq.remove(orgQueue.poll().getData());
        }
        logger.trace("FINALIZER: Original Hash Store size (after removal): {}", orgReq.size());

        // Storing any new failures that have been recorded in the auxiliary hash store in parallel,
        // meanwhile this finalizing operation was being done.
        logger.trace("FINALIZER: Putting any more 'failure' request metrices, recorded in the aux store to primary store: {}", hashStoreAdapter.getAuxRequestMetrics().size());
        orgReq.putAll(hashStoreAdapter.getAuxRequestMetrics());

        // The bloom filter is reset here. The existing filter is re-instantiated with the same
        // configuration as before. All the remaining requests in the hash store which are still
        // failing are then added to the bloom filter by iterating over the entire hash store.
        // The aux switch is switched back to the primary filter and the aux filter is then again
        // re-instantiated. There is no need to capture the entries in the aux filter as they are
        // captured by the aux has store also.
        logger.trace("FINALIZER: Resetting the bloom filter");
        resetTime.set(System.nanoTime());
        bloomFilterAdapter.resetBloomFilter(new HashSet<>(orgReq.values()));
        resetTime.updateAndGet(t -> System.nanoTime() - t);

        // The queue and the hash store are switched back to the primary data structures from the
        // auxiliary one.
        logger.trace("FINALIZER: Switching the hashstore and reset-buffer-queue to the primary.");
        resetQueueAdapter.resetSwitch();
        hashStoreAdapter.resetSwitch();

        logger.debug("FINALIZER: Finalizer process Completed !!!");
        finalizerTime.updateAndGet(t -> System.nanoTime() - t);
    }
}

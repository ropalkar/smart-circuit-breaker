package edu.research.scb.internal.bloomfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import edu.research.scb.internal.RequestMetrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class BloomFilterAdapter {

    private static final Logger logger = LogManager.getLogger(BloomFilterAdapter.class);

    private final Charset charset;
    private final Long capacity;
    private final Double fpp;
    private BloomFilter<String> bloomFilter;
    private BloomFilter<String> auxBloomFilter;
    private final AtomicBoolean switchToAux;

    public BloomFilterAdapter(Charset charset, Long capacity, Double fpp) {
        this.charset = charset;
        this.capacity = capacity;
        this.fpp = fpp;
        this.bloomFilter = BloomFilter.create(Funnels.stringFunnel(charset), capacity, fpp);
        this.auxBloomFilter = BloomFilter.create(Funnels.stringFunnel(charset), capacity, fpp);
        this.switchToAux = new AtomicBoolean(false);
    }

    public boolean mightContain(String data){
        return switchToAux.get() ? auxBloomFilter.mightContain(data) : bloomFilter.mightContain(data);
    }

    public boolean put(String data){
        return switchToAux.get() ? auxBloomFilter.put(data) : bloomFilter.put(data);
    }

    void switchToAux(){
        this.switchToAux.set(true);
    }

    synchronized void resetBloomFilter(Set<RequestMetrics> requestMetrics){
        logger.info("FINALIZER: Resetting the bloom filter within the Adapter. Before fpp: {}", bloomFilter.expectedFpp());
        this.bloomFilter = BloomFilter.create(Funnels.stringFunnel(charset), capacity, fpp);
        requestMetrics.forEach(m -> bloomFilter.put(m.getData()));
        logger.info("FINALIZER: Bloom filter reset. After fpp: {}", bloomFilter.expectedFpp());
        this.switchToAux.set(false);
        this.auxBloomFilter = BloomFilter.create(Funnels.stringFunnel(charset), capacity, fpp);
    }

    public double getExpectedFalsePositiveRate(){
        return bloomFilter.expectedFpp(); // * 10000;
    }

    public long getApproxDistinctElementCount(){
        return bloomFilter.approximateElementCount();
    }
}

package edu.research.scb.internal.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;

import static edu.research.scb.internal.DefaultConstants.*;

@Data
@RequiredArgsConstructor
@ToString
@Component
public class BloomFilterProperties {

    private Long capacity;
    private Double fpp;
    private Charset charset;
    private Double resetThreshold;
    private Double resetTimeThresholdInMinutes;
    private Integer resetBufferCapacity;
    private Integer storeCapacity;

    public Long getCapacity() {
        return capacity != null ? capacity : DEFAULT_BLOOM_FILTER_CAPACITY;
    }

    public Double getFpp() {
        return fpp != null ? fpp : DEFAULT_BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY;
    }

    public Charset getCharset() {
        return charset != null ? charset : DEFAULT_BLOOM_FILTER_CHARSET;
    }

    public Double getResetThreshold() {
        return resetThreshold != null ? resetThreshold : DEFAULT_BLOOM_FILTER_QUEUE_BUFFER_RESET_THRESHOLD;
    }

    public Double getResetTimeThresholdInMinutes() {
        return resetTimeThresholdInMinutes != null ? resetTimeThresholdInMinutes : DEFAULT_BLOOM_FILTER_QUEUE_BUFFER_TIME_THRESHOLD_IN_MINUTES;
    }

    public Integer getResetBufferCapacity() {
        return resetBufferCapacity != null ? resetBufferCapacity : DEFAULT_BLOOM_FILTER_RESET_QUEUE_BUFFER;
    }

    public Integer getStoreCapacity() {
        return storeCapacity != null ? storeCapacity : DEFAULT_REQUEST_FAILURE_CAPACITY;
    }
}

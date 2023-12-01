package edu.research.scb.internal;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestMetrics {

    private final String data;
    private final AtomicInteger transientFaultCount;
    private final AtomicInteger failureCount;
    private LocalDateTime lastFailureTimestamp;

    public RequestMetrics(String data) {
        this.data = data;
        this.transientFaultCount = new AtomicInteger(0);
        this.failureCount = new AtomicInteger(0);
        this.lastFailureTimestamp = LocalDateTime.now();
    }

    public String getData() {
        return data;
    }

    public Integer getTransientFaultCount() {
        return transientFaultCount.get();
    }

    public void incTransientFaultCount() {
        this.transientFaultCount.incrementAndGet();
    }

    public Integer decrTransientFaultCount() {
        return this.transientFaultCount.get() > 0 ? this.transientFaultCount.decrementAndGet() : this.transientFaultCount.get();
    }

    public void resetTransientFaultCount(){
        this.transientFaultCount.set(0);
    }

    public Integer getFailureCount() {
        return failureCount.get();
    }

    public void incFailureCount() {
        this.failureCount.incrementAndGet();
    }

    public Integer decrFailureCount() {
        return this.failureCount.get() > 0 ? this.failureCount.decrementAndGet() : this.failureCount.get();
    }

    public LocalDateTime getLastFailureTimestamp() {
        return lastFailureTimestamp;
    }

    public synchronized void updateLastFailureTimestamp() {
        this.lastFailureTimestamp = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestMetrics that = (RequestMetrics) o;
        return data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @Override
    public String toString() {
        return "RequestMetrics{" +
                "\n\tdata='" + data + '\'' +
                ",\n\ttransientFaultCount=" + transientFaultCount +
                ",\n\tfailureCount=" + failureCount +
                ",\n\tlastFailureTimestamp=" + lastFailureTimestamp +
                "\n}";
    }
}

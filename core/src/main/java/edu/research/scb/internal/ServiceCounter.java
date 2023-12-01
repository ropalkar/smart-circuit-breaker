package edu.research.scb.internal;

import com.google.common.util.concurrent.AtomicDouble;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public class ServiceCounter {

    private final String serviceName;
    private final AtomicInteger totalCount;
    private final AtomicInteger failureCount;
    private final AtomicDouble failurePercentage;
    private LocalDateTime lastTimeStamp;

    public ServiceCounter(String serviceName) {
        this.serviceName = serviceName;
        this.totalCount = new AtomicInteger(0);
        this.failureCount = new AtomicInteger(0);
        this.failurePercentage = new AtomicDouble(0.0);
    }

    public String getServiceName() {
        return serviceName;
    }

    public Integer getTotalCount() {
        return totalCount.get();
    }

    public synchronized void incTotalCount(){
        lastTimeStamp = LocalDateTime.now();
        totalCount.incrementAndGet();
    }

    public Integer decrTotalCount(){
        return totalCount.get() > 0 ? totalCount.decrementAndGet() : totalCount.get();
    }

    public Integer getFailureCount() {
        return failureCount.get();
    }

    public synchronized void incFailureCount() {
        lastTimeStamp = LocalDateTime.now();
        failurePercentage.set(totalCount.get() != 0 ? ((double) failureCount.incrementAndGet() / totalCount.get()) * 100 : 0.0);
    }

    public synchronized void decrFailureCount() {
        lastTimeStamp = LocalDateTime.now();
        failurePercentage.set(totalCount.get() != 0 ?
                ((failureCount.get() > 0 ? failureCount.decrementAndGet() : (double) failureCount.get()) / totalCount.get()) * 100
                : 0.0);
    }

    public double getFailurePercentage(){
        return failurePercentage.get();
    }

    public LocalDateTime getLastTimeStamp(){
        return lastTimeStamp;
    }

    @Override
    public String toString() {
        return "ServiceCounter{" +
                "\n\tserviceName='" + serviceName + '\'' +
                ",\n\ttotalCount=" + totalCount +
                ",\n\tfailureCount=" + failureCount +
                ",\n\tfailurePercentage=" + failurePercentage +
                ",\n\tlastTimeStamp=" + lastTimeStamp +
                "\n}";
    }
}

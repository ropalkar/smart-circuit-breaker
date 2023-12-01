package edu.research.scb.internal.bloomfilter;

import edu.research.scb.internal.RequestMetrics;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResetBufferQueueAdapter {

    private ConcurrentLinkedQueue<RequestMetrics> resetQueue;
    private ConcurrentLinkedQueue<RequestMetrics> auxResetQueue;
    private final AtomicBoolean switchToAux;

    public ResetBufferQueueAdapter() {
        this.resetQueue = new ConcurrentLinkedQueue<>();
        this.auxResetQueue = new ConcurrentLinkedQueue<>();
        this.switchToAux = new AtomicBoolean(false);
    }

    public void add(RequestMetrics requestMetric){
        if(switchToAux.get() && !auxResetQueue.contains(requestMetric)) {
            auxResetQueue.add(requestMetric);
        } else if(!resetQueue.contains(requestMetric)) {
            resetQueue.add(requestMetric);
        }
    }

    public int size(){
        return resetQueue.size();
    }

    synchronized ConcurrentLinkedQueue<RequestMetrics> switchToAux(){
        this.switchToAux.set(true);
        return resetQueue;
    }

    void resetSwitch(){
        this.resetQueue = new ConcurrentLinkedQueue<>();
        this.switchToAux.set(false);
        this.resetQueue.addAll(auxResetQueue);
        this.auxResetQueue = new ConcurrentLinkedQueue<>();
    }

    ConcurrentLinkedQueue<RequestMetrics> getResetQueue() {
        return resetQueue;
    }

    public ConcurrentLinkedQueue<RequestMetrics> getAuxResetQueue() {
        return auxResetQueue;
    }
}

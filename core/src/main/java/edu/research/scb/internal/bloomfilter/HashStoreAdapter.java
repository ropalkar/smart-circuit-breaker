package edu.research.scb.internal.bloomfilter;

import edu.research.scb.internal.RequestMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class HashStoreAdapter {

    // The failure store for housing all the failed requests.
    private final ConcurrentHashMap<String, RequestMetrics> requestMetrics;
    private final ConcurrentHashMap<String, RequestMetrics> auxRequestMetrics;
    private final AtomicBoolean switchToAux;

    public HashStoreAdapter(int capacity) {
        this.requestMetrics = new ConcurrentHashMap<>(capacity);
        this.auxRequestMetrics = new ConcurrentHashMap<>(capacity);
        switchToAux = new AtomicBoolean(false);
    }

    public boolean containsKey(String data){
        return switchToAux.get() ? auxRequestMetrics.containsKey(data) : requestMetrics.containsKey(data);
    }

    public RequestMetrics getOrDefault(String data){
        return switchToAux.get() ?
                auxRequestMetrics.getOrDefault(data, new RequestMetrics(data)) :
                requestMetrics.getOrDefault(data, new RequestMetrics(data));
    }

    public void put(String data, RequestMetrics requestMetric){
        if(switchToAux.get())
            auxRequestMetrics.put(data, requestMetric);
        else
            requestMetrics.put(data, requestMetric);
    }

    public void remove(String data){
        if(switchToAux.get())
            auxRequestMetrics.remove(data);
        else
            requestMetrics.remove(data);

    }

    public RequestMetrics get(String data){
        return switchToAux.get() ? auxRequestMetrics.get(data) : requestMetrics.get(data);
    }

    public String printRequestMetric(String data){
        return get(data) != null ? get(data).toString() : "";
    }

    ConcurrentHashMap<String, RequestMetrics> getRequestMetrics() {
        return requestMetrics;
    }

    ConcurrentHashMap<String, RequestMetrics> getAuxRequestMetrics() {
        return auxRequestMetrics;
    }

    void switchToAux(){
        this.switchToAux.set(true);
    }

    void resetSwitch(){
        this.switchToAux.set(false);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(switchToAux.get() && !auxRequestMetrics.isEmpty()){
            sb.append("AUXILIARY-HASH-STORE: ").append(auxRequestMetrics);
        } else if(!requestMetrics.isEmpty()){
            sb.append("PRIMARY-HASH-STORE: ").append(requestMetrics);
        } else {
            sb.append("Hash stores are empty !!!");
        }
        return sb.toString();
    }
}

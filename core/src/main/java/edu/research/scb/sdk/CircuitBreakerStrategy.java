package edu.research.scb.sdk;

public interface CircuitBreakerStrategy {

    /**
     * Determines if the client request should be allowed to be sent to the server, based on the
     * state of the circuit breaker.
     *
     * @param serviceName the name of the service
     * @param methodName the name of the method
     * @param data the string content used to determine the request signature
     * @return {@code true} if request can be sent.
     */
    boolean allowRequest(String serviceName, String methodName, String data);

    /**
     * Updates the circuit breaker to move to <i>half-open</i> or <i>closed</i> state when the
     * request succeeds
     *
     * @param serviceName the name of the service
     * @param methodName the name of the method
     * @param data the string content used to determine the request signature
     */
    void onSuccess(String serviceName, String methodName, String data);

    /**
     * Updates the circuit breaker to move to <i>open</i> state when the requets fails or errors out.
     *
     * @param serviceName the name of the service
     * @param methodName the name of the method
     * @param data the string content used to determine the request signature
     */
    void onFailure(String serviceName, String methodName, String data);
}

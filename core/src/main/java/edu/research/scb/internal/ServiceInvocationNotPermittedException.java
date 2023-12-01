package edu.research.scb.internal;

public class ServiceInvocationNotPermittedException extends RuntimeException {

    /**
     * Primary method for the service invocation exception. Provides a detailed message about the exception
     * occurring on which service and which method accordingly.
     *
     * @param serviceName the name of the service for which the circuit is open now
     * @param methodName the method of that above service for which the circuit is open now.
     */
    public ServiceInvocationNotPermittedException(String serviceName, String methodName) {
        super("Invocation of service '" + serviceName + "' on method '" + methodName + "' is NOT permitted !!!");
    }
}

package edu.research.scb.internal;

import edu.research.scb.sdk.CircuitBreakerStrategy;
import io.grpc.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SmartCircuitBreaker implements ClientInterceptor {

    private static final Logger logger = LogManager.getLogger(SmartCircuitBreaker.class);

    /**
     * The conditional implementation of the circuit-breaker strategy which is based on the application properties provided.
     * - The property {@code scb-strategy="bloom-filter"} activates the {@code BloomFilterCircuitBreakerStrategy}.
     * This is the default strategy to be used even if the application property is missing.
     * - The property {@code scb-strategy="count-min-sketch"} activates the {@code CountMinSketchCircuitBreakerStrategy}.
     */
    private final CircuitBreakerStrategy circuitBreaker;

    public SmartCircuitBreaker(CircuitBreakerStrategy circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Intercept {@link ClientCall} creation by the {@code next} {@link Channel}.
     *
     * <p>Many variations of interception are possible. Complex implementations may return a wrapper
     * around the result of {@code next.newCall()}, whereas a simpler implementation may just modify
     * the header metadata prior to returning the result of {@code next.newCall()}.
     *
     * <p>{@code next.newCall()} <strong>must not</strong> be called under a different {@link Context}
     * other than the current {@code Context}. The outcome of such usage is undefined and may cause
     * memory leak due to unbounded chain of {@code Context}s.
     *
     * <p>The circuit breaker's is applied here where the data signature is evaluated and the request
     * is allowed to be forwarded to the service if the circuit is <i>closed</i>. If the circuit is
     * <i>open</i>, then the request is blocked and the {@code ServiceInvocationNotPermittedException}
     * is thrown to notify the client about the situation.
     *
     * <p>The circuit breaker records the succeeded and failed requests on the basis of the
     * {@code Status.Code} returned by the grpc request.
     *
     * @param method      the remote method to be called.
     * @param callOptions the runtime options to be applied to this call.
     * @param next        the channel which is being intercepted.
     * @return the call object for the remote operation, never {@code null}.
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            private final String serviceName = method.getServiceName();
            private final String methodName = method.getFullMethodName();
            private String requestMessage;

            @Override
            protected ClientCall<ReqT, RespT> delegate() {
                return super.delegate();
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        logger.debug("Closing the request: {} with status: {}", requestMessage, status.getCode());
                        if(!status.isOk()){
                            circuitBreaker.onFailure(serviceName, methodName, requestMessage);
                        } else
                            circuitBreaker.onSuccess(serviceName, methodName, requestMessage);
                        super.onClose(status, trailers);
                    }
                }, headers);
            }


            @Override
            public void sendMessage(ReqT message) {
                requestMessage = message.toString();
                if(circuitBreaker.allowRequest(serviceName, methodName, requestMessage)) {
                    logger.debug("ALLOW-REQUEST: ** APPROVED **. \nSending request for service='{}', method='{}', message='{}'", serviceName, methodName, requestMessage);
                    super.sendMessage(message);
                } else {
                    logger.debug("ALLOW-REQUEST: ** REJECTED **. \nCircuit is '## OPEN ##' for service='{}', method='{}', message='{}'", serviceName, methodName, requestMessage);
                    throw new ServiceInvocationNotPermittedException(serviceName, methodName);
                }
            }
        };
    }
}

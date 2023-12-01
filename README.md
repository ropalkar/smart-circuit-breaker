# Smart Circuit Breaker

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A java based implementation of a Circuit Breaker Design Pattern using Bloom Filters, as part of the research paper _Smart Circuit Breakers_.
This implementation is particularly done -
- only for inter service/application gRPC calls
- only for gRPC unary calls.
- without gRPC based deadlines.

It primarily uses the spring boot framework, along with the [grpc-sprint-boot-starter plugin](https://github.com/yidongnan/grpc-spring-boot-starter) for configuration of gRPC clients and servers.
Version compatibility as follows : 

 - Java 17
 - gRPC Spring Boot Starter version - `2.15.0.RELEASE`
 - Spring Boot Plugin version  - `3.1.4`
 - Spring Dependency Mgmt version - `1.1.3`
 - Protocol Buffers Plugin version - `0.9.4`
 - Protocol Buffers version - `1.58.0`

<br> 

### Enabling the circuit breaker in the application code :

The circuit breaker functions as a client interceptor, intercepting any outgoing gRPC requests from the application-service on which it is enabled in. Thus, include the following in your Spring Boot Application class. 
```java
/**
 * Using the Circuit Breaker Strategy, configurable via the application properties. 
 * As of current version, only 1 strategy - {@code bloom-filter} is used.
 */
@Autowired
private CircuitBreakerStrategy circuitBreakerStrategy;

/**
 * Enabling the Circuit breaker via the Client Interceptor.
 * The client interceptor (aka SmartCircuitBreaker) is an oob interface, part of the grpc-java module.
 * The client interceptor is implemented in the 'smart-circuit-breaker' module and applied here in
 * every application as  bean.
 * The annotation {@code GrpcGlobalClientInterceptor} is provided by the 'net.devh-grpc-sprint-boot-starter'
 * plugin, and it attaches this interceptor the grpc server instantiation that is manages internally.
 * @return a custom {@code ClientInterceptor}
 */
@GrpcGlobalClientInterceptor
SmartCircuitBreaker smartCircuitBreaker(){
    return new SmartCircuitBreaker(circuitBreakerStrategy);
}
```

**For example :**
```java
@SpringBootApplication
public class AnyApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnyApplication.class, args);
    }

    @Autowired
    private CircuitBreakerStrategy circuitBreakerStrategy;


    @GrpcGlobalClientInterceptor
    SmartCircuitBreaker smartCircuitBreaker(){
        return new SmartCircuitBreaker(circuitBreakerStrategy);
    }
}
```

### Applying Circuit Breaker to Client calls

The Smart Circuit Breaker uses the retry mechanism from the `Resiliency4j` library but provides its own implementation for the Circuit Breaker. 
Since the Smart Circuit Breaker provides method level granularity, every gRPC client request needs to be marked with the `@Retry` annotation with the circuit breaker name.
Place the following annotation over the method that need to enable the circuit breaker and perform the retries until the circuit is not open.
```java
@Retry(name = "SmartCircuitBreaker")
```

**For example :**
```java
@Service
public class MediaStreamClientService {

    @GrpcClient("media-stream-service")
    private MediaStreamServiceGrpc.MediaStreamServiceBlockingStub mediaSyncStub;

    // NOTE: Do not handle StatusRuntimeException in the implementation here.
    @Retry(name = "SmartCircuitBreaker")
    public Media getMediaByTitle(String mediaTitle){
        if(mediaTitle != null){
            logger.info("Searching for media from the streaming-service by title: {}", mediaTitle);
            SearchByTitleResponse response = mediaSyncStub.searchByTitle(
                    SearchByTitleRequest.newBuilder()
                            .setTitle(mediaTitle)
                            .build());

            return response != null ? response.getMedia() : null;
        }

        return null;
    }
}
```

<br>
<br>


### Circuit Breaker Configuration Properties

The parent property to define the configuration properties is `circuit-breaker`. It should be prefixed to all other properties accordingly.

**Example :** Following is an example of the `application.yaml` with the configuration properties -
```yaml
circuit-breaker:
  # The circuit breaker strategy. Valid values are 'bloom-filter` and `count-min-sketch`
  strategy: bloom-filter
  
  # the configuration of the bloom filter
  bloomFilter:
    # Fixed capacity of the bloom-filter. Default: 10,000
    capacity: 999
    # The desired False Positive Probability of the bloom-filter. Default: 0.05
    fpp: 0.15
    # The hash store capacity for storing failed signatures. Default: 10,000
    storeCapacity: 10000
    # The buffer capacity to hold recovered signatures. Default: 100
    resetBufferCapacity: 100
    # The threshold as a percent of the above buffer at which the bloom-filter is reset. Default: 80.0
    resetThreshold: 80.0
    
  # the back off strategy for retries.  Valid values are `fixed` OR `exponential`
  backoffStrategyProperties:
    # The maximum retry attempts for each request. Defaults: 3 
    maxRetryAttempts: 5
    
    # fixed interval based backoff
    fixed:
      # Fixed wait interval in milli seconds.  
      fixedIntervalMillis: 100        
      
    # exponential interval based backoff
    exponential:
      # The initial interval to start with. Default 500  
      initialIntervalMillis: 50
      # The multiplier value for the retry attempts. Default 1.5   
      multiplier: 1.0       
         
  # the circuit breaker thresholds         
  thresholds:
    # Ignorable transient faults for each signature. Default: 2     
    transientFaultCountThreshold: 1
    # Failure threshold above which circuit is opened for each signature. Default: 3
    failureCountThreshold: 2
    # Cooling period for each signature for which circuit remains open, before moving to half-open state. Default: 5 
    openStateWaitDurationInSeconds: 10
    # Cooling period for overall service for which circuit remains open, before moving to half-open state. Default: 15
    openStateServiceLevelWaitDurationInSeconds: 30
    # Overall service level failure threshold percent for opening the circuit. Default: 20.0    
    serviceFailureRateThreshold: 30.0                    
```
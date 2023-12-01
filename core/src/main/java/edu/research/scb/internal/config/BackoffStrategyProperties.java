package edu.research.scb.internal.config;

import io.github.resilience4j.core.IntervalFunction;
import io.grpc.Status;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.List;

import static edu.research.scb.internal.DefaultConstants.DEFAULT_RETRY_STATUS_CODES;

@Data
@RequiredArgsConstructor
@ToString
@Component
public class BackoffStrategyProperties {

    private List<Status.Code> grpcRetryCodes;
    private Integer maxRetryAttempts;
    private Fixed fixed;
    private Exponential exponential;

    public List<Status.Code> getGrpcRetryCodes() {
        return grpcRetryCodes != null ? grpcRetryCodes : DEFAULT_RETRY_STATUS_CODES;
    }

    @Data
    @RequiredArgsConstructor
    @ToString
    public static class Fixed {
        private Integer fixedIntervalMillis;
        private Integer maxElapseTimeMillis;

        public Integer getFixedIntervalMillis() {
            return fixedIntervalMillis != null ? fixedIntervalMillis : (int) IntervalFunction.DEFAULT_INITIAL_INTERVAL;
        }
    }

    @Data
    @RequiredArgsConstructor
    @ToString
    public static class Exponential {
        private Long initialIntervalMillis;
        private Double multiplier;
        private Long maxIntervalMillis;

        public Long getInitialIntervalMillis() {
            return initialIntervalMillis != null ? initialIntervalMillis : (int) IntervalFunction.DEFAULT_INITIAL_INTERVAL;
        }

        public Double getMultiplier() {
            return multiplier != null ? multiplier : IntervalFunction.DEFAULT_MULTIPLIER;
        }
    }
}

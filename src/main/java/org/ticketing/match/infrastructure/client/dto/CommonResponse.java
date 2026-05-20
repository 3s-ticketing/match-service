package org.ticketing.match.infrastructure.client.dto;

public record CommonResponse<T>(
        boolean success,
        String message,
        T data,
        String traceId
) {
}

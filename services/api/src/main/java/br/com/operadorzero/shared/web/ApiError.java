package br.com.operadorzero.shared.web;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String code, String message, String correlationId, List<FieldError> fields) {
    public record FieldError(String field, String message) {}
}

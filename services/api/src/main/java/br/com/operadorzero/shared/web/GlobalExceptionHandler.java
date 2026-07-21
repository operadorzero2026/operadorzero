package br.com.operadorzero.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage()))
            .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revise os campos informados.", fields);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletResponse response) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Não foi possível concluir a solicitação.", List.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, MDC.get("correlationId"), fields));
    }
}

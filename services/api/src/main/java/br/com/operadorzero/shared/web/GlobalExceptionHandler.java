package br.com.operadorzero.shared.web;

import br.com.operadorzero.identity.AuthException;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiError> authentication(AuthException exception) {
        return response(exception.status(), exception.code(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException exception) {
        return response(exception.status(), exception.code(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage()))
            .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revise os campos informados.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedRequest(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "O corpo da solicitacao e invalido.", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "A imagem deve ter no máximo 2 MB.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado.", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletResponse response) {
        log.error("Erro interno em requisicao; correlationId={}", MDC.get("correlationId"), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Não foi possível concluir a solicitação.", List.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, MDC.get("correlationId"), fields));
    }
}

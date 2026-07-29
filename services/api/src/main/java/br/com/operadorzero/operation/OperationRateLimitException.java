package br.com.operadorzero.operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class OperationRateLimitException extends ResponseStatusException {
    public OperationRateLimitException(){super(HttpStatus.TOO_MANY_REQUESTS,"Aguarde antes de enviar outra mensagem.");}
}

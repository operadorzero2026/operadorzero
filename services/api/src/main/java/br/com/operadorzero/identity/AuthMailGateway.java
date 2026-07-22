package br.com.operadorzero.identity;

interface AuthMailGateway {
    void send(Message message);

    record Message(String from, String to, String subject, String text, String kind, String idempotencyKey) {}
}

final class AuthMailDeliveryException extends RuntimeException {
    AuthMailDeliveryException(String message) {
        super(message);
    }

    AuthMailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

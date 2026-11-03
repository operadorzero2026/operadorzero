package br.com.operadorzero.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assinatura das notificacoes do Mercado Pago (cabecalho x-signature "ts=...,v1=..."): HMAC-SHA256 com a assinatura secreta
 * sobre "id:{data.id};request-id:{x-request-id};ts:{ts};" (partes ausentes ficam de fora).
 */
final class WebhookSignature {
    private WebhookSignature() {}

    static boolean valid(String secret, String signatureHeader, String requestId, String dataId) {
        if (secret == null || secret.isBlank() || signatureHeader == null) return false;
        String ts = null, v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("ts".equals(kv[0].trim())) ts = kv[1].trim();
            if ("v1".equals(kv[0].trim())) v1 = kv[1].trim();
        }
        if (ts == null || v1 == null) return false;
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.matches("[0-9A-Za-z]+") ? dataId.toLowerCase(Locale.ROOT) : dataId).append(';');
        }
        if (requestId != null && !requestId.isBlank()) manifest.append("request-id:").append(requestId).append(';');
        manifest.append("ts:").append(ts).append(';');
        return MessageDigest.isEqual(hmac(secret.strip(), manifest.toString()).getBytes(StandardCharsets.US_ASCII),
            v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    static String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 indisponivel", exception);
        }
    }
}

package br.com.operadorzero.payment;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.payment.FinanceRepository.ConnectionStats;
import br.com.operadorzero.payment.FinanceRepository.Filters;
import br.com.operadorzero.payment.FinanceRepository.OperationTotals;
import br.com.operadorzero.payment.FinanceRepository.PaymentLine;
import br.com.operadorzero.payment.FinanceRepository.Totals;
import br.com.operadorzero.payment.MarketplaceService.CommissionSettings;
import br.com.operadorzero.payment.MarketplaceService.ConnectionView;
import br.com.operadorzero.payment.PaymentDtos.CommissionRequest;
import br.com.operadorzero.shared.web.BusinessException;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Conexao do organizador com o Mercado Pago, financeiro do organizador e area financeira do administrador. */
@RestController
public class MarketplaceController {
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private final MarketplaceService marketplace;
    private final FinanceRepository finance;

    public MarketplaceController(MarketplaceService marketplace, FinanceRepository finance) {
        this.marketplace = marketplace;
        this.finance = finance;
    }

    public record OrganizerFinance(ConnectionView connection, List<OperationTotals> operations, List<PaymentLine> payments) {}

    public record AdminFinance(Totals totals, ConnectionStats connections, List<PaymentLine> payments) {}

    // ---- organizador ----

    @GetMapping("/api/me/finance")
    @Transactional(readOnly = true)
    OrganizerFinance organizer(@AuthenticationPrincipal AuthenticatedUser user) {
        return new OrganizerFinance(marketplace.status(user), finance.operationTotals(user.internalId()),
            finance.organizerLines(user.internalId(), 200));
    }

    @PostMapping("/api/payments/mercadopago/connect")
    Map<String, String> connect(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("authorizationUrl", marketplace.startConnection(user).toString());
    }

    @DeleteMapping("/api/payments/mercadopago/connection")
    MessageResponse disconnect(@AuthenticationPrincipal AuthenticatedUser user) {
        marketplace.disconnect(user);
        return new MessageResponse("Conta Mercado Pago desconectada. Para encerrar o acesso também no Mercado Pago, remova o Operador Zero "
            + "em Seu perfil → Segurança → Aplicações conectadas.");
    }

    /** Retorno do OAuth (navegacao do navegador): sempre redireciona para a tela Financeiro com o resultado. */
    @GetMapping("/api/payments/mercadopago/callback")
    ResponseEntity<Void> callback(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        String target = marketplace.callback(user, code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    // ---- administrador (ROLE_ADMIN, conferido no SecurityConfig) ----

    @GetMapping("/api/admin/payments/commissions")
    CommissionSettings commissions() {
        return marketplace.commissionSettings();
    }

    @PutMapping("/api/admin/payments/commissions")
    MessageResponse setCommission(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody CommissionRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            if (request.percent() == null) throw BusinessException.badRequest("INVALID_COMMISSION", "Informe o percentual padrão.");
            marketplace.setDefaultCommission(admin, request.percent(), request.note());
            return new MessageResponse("Comissão padrão atualizada. Vale para os próximos pagamentos.");
        }
        marketplace.setOrganizerCommission(admin, request.username(), request.percent(), request.note());
        return new MessageResponse(request.percent() == null ? "O organizador voltou a seguir a comissão padrão."
            : "Comissão do organizador atualizada. Vale para os próximos pagamentos.");
    }

    @GetMapping("/api/admin/payments/overview")
    @Transactional(readOnly = true)
    AdminFinance overview(@RequestParam(required = false) String organizer, @RequestParam(required = false) UUID operation,
                          @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                          @RequestParam(required = false) String method, @RequestParam(required = false) String status) {
        Filters filters = filters(organizer, operation, from, to, method, status);
        return new AdminFinance(finance.adminTotals(filters), finance.connectionStats(), finance.adminLines(filters, 500));
    }

    @GetMapping("/api/admin/payments/export.csv")
    @Transactional(readOnly = true)
    ResponseEntity<byte[]> export(@RequestParam(required = false) String organizer, @RequestParam(required = false) UUID operation,
                                  @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                  @RequestParam(required = false) String method, @RequestParam(required = false) String status) {
        List<PaymentLine> lines = finance.adminLines(filters(organizer, operation, from, to, method, status), 20_000);
        StringBuilder csv = new StringBuilder("﻿data;operacao;organizador;jogador;provedor;metodo;meio;status;valor;comissao_pct;comissao;"
            + "tarifa_mp;reembolsado;liquido_organizador;tarifas_confirmadas;transacao\n");
        for (PaymentLine l : lines) {
            csv.append(l.createdAt()).append(';').append(cell(l.operationName())).append(';').append(cell(l.organizer())).append(';')
                .append(cell(l.player())).append(';').append(l.provider()).append(';').append(l.method()).append(';')
                .append(cell(l.paymentType())).append(';').append(l.status()).append(';').append(num(l.amount())).append(';')
                .append(num(l.commissionPercent())).append(';').append(num(l.commission())).append(';').append(num(l.providerFee())).append(';')
                .append(num(l.refunded())).append(';').append(num(l.net())).append(';').append(l.feesConfirmed() ? "sim" : "estimado").append(';')
                .append(cell(l.transactionId())).append('\n');
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pagamentos-operador-zero.csv\"")
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Filters filters(String organizer, UUID operation, String from, String to, String method, String status) {
        try {
            return new Filters(organizer, operation, from == null || from.isBlank() ? null : LocalDate.parse(from).atStartOfDay(BRAZIL).toInstant(),
                to == null || to.isBlank() ? null : LocalDate.parse(to).plusDays(1).atStartOfDay(BRAZIL).toInstant(), method, status);
        } catch (DateTimeParseException invalid) {
            throw BusinessException.badRequest("INVALID_DATE", "Use datas no formato AAAA-MM-DD.");
        }
    }

    /** Evita injecao de formula ao abrir o CSV em planilhas. */
    private static String cell(String value) {
        if (value == null) return "";
        String clean = value.replace(";", ",").replace("\n", " ").replace("\r", " ");
        return clean.matches("^[=+\\-@].*") ? "'" + clean : clean;
    }

    private static String num(java.math.BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }
}

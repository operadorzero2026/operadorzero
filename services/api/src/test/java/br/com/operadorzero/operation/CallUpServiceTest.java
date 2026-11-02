package br.com.operadorzero.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.CallUpRepository.CallUpRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallUpServiceTest {
    // 02:00 UTC de 3/11 ainda e 2/11 em Brasilia: a janela de jogos usa a data de Brasilia.
    private static final Instant NOW = Instant.parse("2026-11-03T02:00:00Z");
    private final CallUpRepository repository = mock(CallUpRepository.class);
    private final CallUpService service = new CallUpService(repository, "https://operadorzero.com.br/", Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser user = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op", "Op", "OP", List.of("USER"));

    @Test
    void highlightsTheLastSpotsAndUsesTheBrasiliaDate() {
        when(repository.ranked(eq(7L), eq(LocalDate.of(2026, 11, 2)), eq(10))).thenReturn(List.of(
            row(17, 20, null, null, null, "APPROVED"), row(20, 20, null, null, null, null), row(5, null, null, null, null, null)));
        when(repository.teams(any())).thenReturn(Map.of());

        var items = service.forFeed(user, null).items();

        assertThat(items.get(0).remaining()).isEqualTo(3);
        assertThat(items.get(0).lastSpots()).isTrue();
        assertThat(items.get(0).viewerStatus()).isEqualTo("APPROVED");
        assertThat(items.get(1).remaining()).isZero();
        assertThat(items.get(1).lastSpots()).as("lotado nao e 'ultimas vagas'").isFalse();
        assertThat(items.get(2).remaining()).as("sem limite").isNull();
        assertThat(items.get(0).shareUrl()).startsWith("https://operadorzero.com.br/c/");
    }

    @Test
    void directionsPreferTheFieldGpsLinkAndOnlyAcceptHttps() {
        assertThat(CallUpService.directions(row(0, 10, "https://maps.app.goo.gl/abc", null, null, null))).isEqualTo("https://maps.app.goo.gl/abc");
        assertThat(CallUpService.directions(row(0, 10, "javascript:alert(1)", new BigDecimal("-25.4284"), new BigDecimal("-49.2733"), null)))
            .isEqualTo("https://www.google.com/maps/search/?api=1&query=-25.4284,-49.2733");
        assertThat(CallUpService.directions(row(0, 10, null, null, null, null))).isNull();
    }

    @Test
    void thePublicVersionNeverCarriesTheViewerStatus() {
        UUID id = UUID.randomUUID();
        when(repository.one(eq(id), anyLong(), any())).thenReturn(List.of(row(3, 10, null, null, null, "REQUESTED")));
        when(repository.teams(any())).thenReturn(Map.of());
        assertThat(service.single(id, -1, false)).get().satisfies(callUp -> assertThat(callUp.viewerStatus()).isNull());
        verify(repository).one(id, -1, LocalDate.of(2026, 11, 2));
    }

    private static CallUpRow row(long occupied, Integer limit, String locationUrl, BigDecimal lat, BigDecimal lng, String viewerStatus) {
        return new CallUpRow(occupied + 100, UUID.randomUUID(), "Op " + occupied, LocalDate.of(2026, 11, 8), null, LocalTime.of(9, 0),
            "Curitiba", "PR", "Campo", locationUrl, lat, lng, "DOMINATION", null, "SMALL", "REGISTRATION_OPEN", occupied, limit,
            null, true, true, viewerStatus, 1.0);
    }
}

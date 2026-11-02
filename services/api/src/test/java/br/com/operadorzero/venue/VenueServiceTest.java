package br.com.operadorzero.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.BrazilLocationValidator;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import br.com.operadorzero.venue.VenueDtos.FieldResponse;
import br.com.operadorzero.venue.VenueDtos.SaveFieldRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VenueServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private final VenueRepository repository = mock(VenueRepository.class);
    private final BrazilLocationValidator locations = mock(BrazilLocationValidator.class);
    private final VenueService service = new VenueService(repository, locations, mock(AuditEventRepository.class),
        Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser user = new AuthenticatedUser(7L, UUID.randomUUID(), "campo@example.test", "campo",
        "Campo", "CAMPO", List.of("USER"));
    private final UUID fieldId = UUID.randomUUID();

    @Test
    void onlyTheResponsibleCanEditAField() {
        when(repository.field(fieldId, 7L)).thenReturn(Optional.of(field(false)));

        assertThatThrownBy(() -> service.updateField(user, fieldId, 1, request()))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ACCESS_DENIED"));
        verify(repository, never()).updateField(any(), anyLong(), anyLong(), any(), anyString(), anyString(), any());
    }

    @Test
    void staleVersionIsRejected() {
        when(repository.field(fieldId, 7L)).thenReturn(Optional.of(field(true)));
        when(locations.validate("SP", "Campinas")).thenReturn(new BrazilLocationValidator.Location("SP", "Campinas"));
        when(repository.updateField(eq(fieldId), eq(7L), eq(1L), any(), eq("Campinas"), eq("SP"), eq(NOW))).thenReturn(0);

        assertThatThrownBy(() -> service.updateField(user, fieldId, 1, request()))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("STALE_VERSION"));
    }

    private FieldResponse field(boolean managed) {
        return new FieldResponse(fieldId, "Campo", null, null, null, null, null, null, null, "Campinas", "SP", null, null,
            null, null, null, null, null, null, null, managed, 2);
    }

    private SaveFieldRequest request() {
        return new SaveFieldRequest("Campo novo", null, null, null, null, null, null, null, "Campinas", "SP", null, null,
            null, null, null, null, null, null, null);
    }
}

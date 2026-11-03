package br.com.operadorzero.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaSettings;
import br.com.operadorzero.operation.OperationDocumentRepository.Access;
import br.com.operadorzero.operation.OperationDocumentRepository.Doc;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperationDocumentServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant START = Instant.parse("2026-11-15T12:00:00Z");
    private final OperationDocumentRepository repository = mock(OperationDocumentRepository.class);
    private final OperationRepository operations = mock(OperationRepository.class);
    private final AuthenticatedUser player = new AuthenticatedUser(20L, UUID.randomUUID(), "p@example.test", "p", "P", "P", List.of("USER"));
    private final AuthenticatedUser organizer = new AuthenticatedUser(7L, UUID.randomUUID(), "o@example.test", "o", "O", "O", List.of("USER"));
    private final UUID operationId = UUID.randomUUID();

    private Access access(String participation, boolean paid, boolean paymentConfirmed, Instant startsAt) {
        return new Access(5L, 7L, "REGISTRATION_OPEN", startsAt, paid, participation, paymentConfirmed);
    }

    @Test
    void accessFollowsRegistrationPaymentAndStartTime() {
        assertThat(OperationDocumentService.state(access(null, false, false, START), false, NOW)).isEqualTo("NOT_REGISTERED");
        assertThat(OperationDocumentService.state(access("REQUESTED", false, false, START), false, NOW)).isEqualTo("NOT_REGISTERED");
        assertThat(OperationDocumentService.state(access("APPROVED", false, false, START), false, NOW)).isEqualTo("ALLOWED");
        assertThat(OperationDocumentService.state(access("APPROVED", true, false, START), false, NOW)).isEqualTo("PAYMENT_PENDING");
        assertThat(OperationDocumentService.state(access("CONFIRMED", true, true, START), false, NOW)).isEqualTo("ALLOWED");
        // Quando o jogo comeca, ninguem alem do organizador abre os documentos.
        assertThat(OperationDocumentService.state(access("CONFIRMED", true, true, NOW), false, NOW)).isEqualTo("STARTED");
        assertThat(OperationDocumentService.state(access(null, false, false, NOW), true, NOW)).isEqualTo("MANAGER");
    }

    @Test
    void playersWithoutAccessSeeOnlyHowManyDocumentsExist(@TempDir Path dir) {
        var service = service(dir);
        when(repository.access(operationId, 20L)).thenReturn(Optional.of(access(null, false, false, START)));
        when(repository.count(5L)).thenReturn(2L);

        var listing = service.list(player, operationId);

        assertThat(listing.access()).isEqualTo("NOT_REGISTERED");
        assertThat(listing.count()).isEqualTo(2);
        assertThat(listing.items()).isEmpty();
        verify(repository, never()).list(anyLong());
        assertThatThrownBy(() -> service.file(player, operationId, UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("exclusivos para jogadores inscritos"));
    }

    @Test
    void onlyTheOrganizerUploadsAndOnlyRealPdfsAreAccepted(@TempDir Path dir) throws Exception {
        var service = service(dir);
        when(repository.access(operationId, 20L)).thenReturn(Optional.of(access("APPROVED", false, false, START)));
        assertThatThrownBy(() -> service.upload(player, operationId, "Missão", "m.pdf", new ByteArrayInputStream(pdf()), 20))
            .isInstanceOf(BusinessException.class);

        when(repository.access(operationId, 7L)).thenReturn(Optional.of(access(null, false, false, START)));
        byte[] fake = "<html>não é pdf</html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.upload(organizer, operationId, "Missão", "m.pdf", new ByteArrayInputStream(fake), fake.length))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DOCUMENT_NOT_PDF"));
        assertThatThrownBy(() -> service.upload(organizer, operationId, "Missão", "m.pdf", new ByteArrayInputStream(pdf()), 10L * 1024 * 1024))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DOCUMENT_TOO_LARGE"));

        when(repository.insert(anyLong(), any(), any(), anyLong(), any(), anyLong(), any())).thenReturn(UUID.randomUUID());
        var item = service.upload(organizer, operationId, null, "../../Missão Alfa.pdf", new ByteArrayInputStream(pdf()), pdf().length);
        assertThat(item.fileName()).isEqualTo(".. .. Missão Alfa.pdf");
        assertThat(item.title()).isEqualTo(".. .. Missão Alfa");
        try (var files = Files.list(dir.resolve("private").resolve("operation-docs"))) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    @Test
    void replacingKeepsTheDocumentAndRemovesTheOldFile(@TempDir Path dir) throws Exception {
        var service = service(dir);
        UUID documentId = UUID.randomUUID();
        when(repository.access(operationId, 7L)).thenReturn(Optional.of(access(null, false, false, START)));
        var storage = new OperationDocumentStorage(settings(dir));
        var old = storage.save(new ByteArrayInputStream(pdf()), pdf().length);
        when(repository.one(5L, documentId)).thenReturn(Optional.of(new Doc(9L, documentId, "Missão", "a.pdf", 20, old.key(), 1, NOW, NOW, "O")));

        var item = service.replace(organizer, operationId, documentId, "b.pdf", new ByteArrayInputStream(pdf()), pdf().length);

        assertThat(item.id()).isEqualTo(documentId);
        assertThat(item.version()).isEqualTo(2);
        assertThat(Files.exists(storage.path(old.key()))).isFalse();
        verify(repository).replace(any(Long.class), any(), any(), anyLong(), any(), anyLong(), any());
    }

    private OperationDocumentService service(Path dir) {
        return new OperationDocumentService(repository, new OperationDocumentStorage(settings(dir)), operations, mock(AuditEventRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MediaSettings settings(Path dir) {
        return new MediaSettings(dir.resolve("public").toString(), dir.resolve("work").toString(), "ffmpeg", "ffprobe", false);
    }

    private static byte[] pdf() {
        return "%PDF-1.4\n%fake minimal\n".getBytes(StandardCharsets.US_ASCII);
    }
}

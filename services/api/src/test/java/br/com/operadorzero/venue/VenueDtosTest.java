package br.com.operadorzero.venue;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.operadorzero.venue.VenueDtos.SaveFieldRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class VenueDtosTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsGoogleMapsLinkWithoutStreetAddress() {
        SaveFieldRequest request = request(null, "https://maps.app.goo.gl/abc123");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsNonHttpsOrUnknownLocationLinks() {
        assertThat(validator.validate(request(null, "javascript:alert(1)"))).isNotEmpty();
        assertThat(validator.validate(request(null, "https://example.test/phishing"))).isNotEmpty();
    }

    private SaveFieldRequest request(String address, String locationUrl) {
        return new SaveFieldRequest("Campo Seguro", null, null, null, address, null, null, null,
            "São Paulo", "SP", null, null, locationUrl, null, null, null, null, null, null);
    }
}

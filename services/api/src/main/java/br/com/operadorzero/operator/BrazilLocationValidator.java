package br.com.operadorzero.operator;

import br.com.operadorzero.shared.web.BusinessException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BrazilLocationValidator {
    private static final Set<String> STATES = Set.of(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
        "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    );

    public Location validate(String stateCode, String city) {
        String state = normalize(stateCode).toUpperCase(Locale.ROOT);
        String municipality = normalize(city);
        if (state.isEmpty() && municipality.isEmpty()) return new Location(null, null);
        if (!STATES.contains(state) || municipality.length() < 2 || municipality.length() > 80) {
            throw BusinessException.badRequest("INVALID_LOCATION", "Selecione um estado e uma cidade válidos.");
        }
        if (!municipality.matches("[\\p{L} .'-]+")) {
            throw BusinessException.badRequest("INVALID_LOCATION", "Selecione uma cidade válida.");
        }
        return new Location(state, municipality);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public record Location(String stateCode, String city) {}
}


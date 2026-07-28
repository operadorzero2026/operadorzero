package br.com.operadorzero.team;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.operadorzero.team.TeamDtos.CreateTeamRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class TeamDtosTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAcronymWithDots() {
        assertThat(validator.validate(request("A.T.A.C."))).isEmpty();
    }

    @Test
    void rejectsAcronymMadeOnlyOfPunctuation() {
        assertThat(validator.validate(request("..."))).anyMatch(error -> "acronym".equals(error.getPropertyPath().toString()));
    }

    private CreateTeamRequest request(String acronym) {
        return new CreateTeamRequest("Alpha Team Airsoft Company", acronym, "Santo Antônio da Platina", "PR",
            "Milsim, SAR", true, "Equipe de airsoft", "INVITE_ONLY");
    }
}

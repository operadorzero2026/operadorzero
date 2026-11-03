package br.com.operadorzero.recon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.operadorzero.recon.ReconComposition.Element;
import br.com.operadorzero.recon.ReconComposition.Stroke;
import br.com.operadorzero.shared.web.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconCompositionTest {
    private static Element text(String id, String text, double x, String font) {
        return new Element(id, "text", x, 0.4, 1.5, 270.0, 2, text, font, "#ff8800", "left", "soft", 0.09,
            null, null, null, null, null, null);
    }

    private static Element mention(String id, String username) {
        // operatorId/available vindos do navegador sao descartados.
        return new Element(id, "MENTION", 0.5, 0.5, 1.0, 0.0, 1, null, null, null, null, null, null,
            username, "highlight", UUID.randomUUID(), true, null, null);
    }

    @Test
    void textIsNormalizedAndClampedInsideTheFrame() {
        Element result = ReconComposition.normalize(List.of(text("t1", "  Patrulha  ", 1.8, "typewriter"))).getFirst();

        assertThat(result.type()).isEqualTo("TEXT");
        assertThat(result.text()).isEqualTo("Patrulha");
        assertThat(result.x()).isEqualTo(1.0);
        assertThat(result.rotation()).isEqualTo(-90.0);
        assertThat(result.font()).isEqualTo("TYPEWRITER");
        assertThat(result.color()).isEqualTo("#FF8800");
        assertThat(result.background()).isEqualTo("SOFT");
    }

    @Test
    void unknownStylesFallBackAndUnknownStickersAreRefused() {
        assertThat(ReconComposition.normalize(List.of(text("t1", "Oi", 0.5, "comic"))).getFirst().font()).isEqualTo("MODERN");
        Element sticker = new Element("s1", "STICKER", 0.5, 0.5, 1.0, 0.0, 0, null, null, null, null, null, null,
            null, null, null, null, "<script>", null);
        assertThatThrownBy(() -> ReconComposition.normalize(List.of(sticker))).isInstanceOf(BusinessException.class);
    }

    @Test
    void mentionsDropClientIdentifiersAndAreLimited() {
        Element result = ReconComposition.normalize(List.of(mention("m1", "@Ghost"))).getFirst();
        assertThat(result.username()).isEqualTo("ghost");
        assertThat(result.style()).isEqualTo("HIGHLIGHT");
        assertThat(result.operatorId()).isNull();
        assertThat(result.available()).isNull();

        List<Element> many = new ArrayList<>();
        for (int i = 0; i < ReconComposition.MAX_MENTIONS + 1; i++) many.add(mention("m" + i, "op" + i));
        assertThatThrownBy(() -> ReconComposition.normalize(many))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("RECON_ELEMENTS"));
        assertThatThrownBy(() -> ReconComposition.normalize(List.of(mention("m1", "a b")))).isInstanceOf(BusinessException.class);
    }

    @Test
    void drawingsKeepRelativePointsAndRejectOversizedStrokes() {
        Element drawing = new Element("d1", "DRAWING", 0.1, 0.1, 3.0, 45.0, 0, null, null, null, null, null, null,
            null, null, null, null, null, List.of(new Stroke("#00ff00", 0.5, List.of(0.1, 0.2, 1.5, -0.2))));
        Element result = ReconComposition.normalize(List.of(drawing)).getFirst();
        assertThat(result.x()).isEqualTo(0.5);
        assertThat(result.rotation()).isEqualTo(0.0);
        assertThat(result.strokes().getFirst().width()).isEqualTo(0.06);
        assertThat(result.strokes().getFirst().points()).containsExactly(0.1, 0.2, 1.0, 0.0);

        Element huge = new Element("d2", "DRAWING", 0.5, 0.5, 1.0, 0.0, 0, null, null, null, null, null, null, null, null, null, null, null,
            List.of(new Stroke("#FFFFFF", 0.01, Collections.nCopies(ReconComposition.MAX_STROKE_POINTS * 2 + 2, 0.5))));
        assertThatThrownBy(() -> ReconComposition.normalize(List.of(huge))).isInstanceOf(BusinessException.class);
    }

    @Test
    void duplicatedIdsAndTooManyElementsAreRefused() {
        assertThatThrownBy(() -> ReconComposition.normalize(List.of(text("a", "1", 0.5, null), text("a", "2", 0.5, null))))
            .isInstanceOf(BusinessException.class);
        List<Element> many = new ArrayList<>();
        for (int i = 0; i <= ReconComposition.MAX_ELEMENTS; i++) many.add(text("t" + i, "x", 0.5, null));
        assertThatThrownBy(() -> ReconComposition.normalize(many)).isInstanceOf(BusinessException.class);
    }
}

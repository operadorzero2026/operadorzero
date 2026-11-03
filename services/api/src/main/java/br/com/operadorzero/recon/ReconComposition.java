package br.com.operadorzero.recon;

import br.com.operadorzero.shared.web.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Elementos que o editor coloca sobre a midia do Recon. Posicao (x, y) e o centro do elemento relativo ao quadro 9:16
 * (0..1), escala e rotacao em graus; tamanhos de texto e traco sao fracoes da largura do quadro.
 * Tudo e revalidado aqui: o servidor nunca grava valores fora dos limites nem estilos desconhecidos.
 */
public final class ReconComposition {
    static final int MAX_ELEMENTS = 40;
    static final int MAX_MENTIONS = 10;
    static final int MAX_STROKES = 80;
    static final int MAX_STROKE_POINTS = 1200;
    static final int MAX_TOTAL_POINTS = 12_000;
    static final int MAX_TEXT = 300;
    static final Set<String> TYPES = Set.of("TEXT", "DRAWING", "MENTION", "STICKER");
    static final Set<String> FONTS = Set.of("CLASSIC", "MODERN", "BOLD", "TYPEWRITER", "HANDWRITTEN");
    static final Set<String> ALIGNS = Set.of("LEFT", "CENTER", "RIGHT");
    static final Set<String> BACKGROUNDS = Set.of("NONE", "SOLID", "SOFT");
    static final Set<String> MENTION_STYLES = Set.of("MINIMAL", "HIGHLIGHT", "ZERO");
    /** Mesma lista do editor (src/feed/recon/stickers.ts). */
    static final Set<String> STICKERS = Set.of("🎯", "💥", "🔥", "💀", "🪖", "🔫", "🧨", "🛡️", "⚔️", "🎖️", "🏆", "📍", "🚁", "🌲",
        "🌙", "☀️", "🌧️", "👊", "🤝", "💪", "😎", "😂", "😱", "🫡", "👀", "✅", "❌", "⚠️", "📸", "🎥");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{1,40}");

    private ReconComposition() {}

    public record Stroke(String color, Double width, List<Double> points) {}

    /** operatorId e available sao preenchidos pelo servidor; o que vier do navegador nesses campos e ignorado. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Element(String id, String type, Double x, Double y, Double scale, Double rotation, Integer z,
                          String text, String font, String color, String align, String background, Double size,
                          String username, String style, UUID operatorId, Boolean available,
                          String emoji, List<Stroke> strokes) {}

    /** Valida e normaliza; devolve os nomes de usuario mencionados (minusculos, sem repeticao) para o servico conferir. */
    static List<Element> normalize(List<Element> elements) {
        if (elements == null || elements.isEmpty()) return List.of();
        if (elements.size() > MAX_ELEMENTS) throw invalid("Use no máximo " + MAX_ELEMENTS + " elementos no Recon.");
        List<Element> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int totalPoints = 0;
        for (Element element : elements) {
            if (element == null || element.id() == null || !ID.matcher(element.id()).matches() || !ids.add(element.id())) throw invalid("Elemento inválido.");
            String type = upper(element.type());
            if (!TYPES.contains(type)) throw invalid("Tipo de elemento desconhecido.");
            double x = clamp(finite(element.x(), 0.5), 0, 1);
            double y = clamp(finite(element.y(), 0.5), 0, 1);
            double scale = clamp(finite(element.scale(), 1), 0.2, 6);
            double rotation = normalizeAngle(finite(element.rotation(), 0));
            int z = element.z() == null ? 0 : Math.max(0, Math.min(1000, element.z()));
            Element normalized = switch (type) {
                case "TEXT" -> {
                    String text = element.text() == null ? "" : element.text().replace("\r", "").strip();
                    if (text.isEmpty() || text.length() > MAX_TEXT || text.lines().count() > 12) throw invalid("Texto vazio ou longo demais.");
                    yield new Element(element.id(), type, round(x), round(y), round(scale), round(rotation), z, text,
                        oneOf(element.font(), FONTS, "MODERN"), color(element.color(), "#FFFFFF"), oneOf(element.align(), ALIGNS, "CENTER"),
                        oneOf(element.background(), BACKGROUNDS, "NONE"), round(clamp(finite(element.size(), 0.07), 0.03, 0.2)),
                        null, null, null, null, null, null);
                }
                case "MENTION" -> {
                    String username = element.username() == null ? "" : element.username().strip().replaceFirst("^@", "");
                    if (!USERNAME.matcher(username).matches()) throw invalid("Menção inválida.");
                    yield new Element(element.id(), type, round(x), round(y), round(scale), round(rotation), z, null, null, null, null, null, null,
                        username.toLowerCase(Locale.ROOT), oneOf(element.style(), MENTION_STYLES, "ZERO"), null, null, null, null);
                }
                case "STICKER" -> {
                    if (element.emoji() == null || !STICKERS.contains(element.emoji())) throw invalid("Adesivo desconhecido.");
                    yield new Element(element.id(), type, round(x), round(y), round(scale), round(rotation), z, null, null, null, null, null, null,
                        null, null, null, null, element.emoji(), null);
                }
                default -> {
                    List<Stroke> strokes = element.strokes() == null ? List.of() : element.strokes();
                    if (strokes.isEmpty() || strokes.size() > MAX_STROKES) throw invalid("Desenho vazio ou com traços demais.");
                    List<Stroke> clean = new ArrayList<>();
                    for (Stroke stroke : strokes) {
                        List<Double> points = stroke == null || stroke.points() == null ? List.of() : stroke.points();
                        if (points.size() < 2 || points.size() % 2 != 0 || points.size() > MAX_STROKE_POINTS * 2) throw invalid("Traço inválido.");
                        totalPoints += points.size() / 2;
                        if (totalPoints > MAX_TOTAL_POINTS) throw invalid("Desenho grande demais.");
                        clean.add(new Stroke(color(stroke.color(), "#FFFFFF"), round(clamp(finite(stroke.width(), 0.01), 0.003, 0.06)),
                            points.stream().map(value -> round(clamp(finite(value, 0), 0, 1))).toList()));
                    }
                    // Desenho ocupa o quadro inteiro: posicao, escala e rotacao fixas.
                    yield new Element(element.id(), "DRAWING", 0.5, 0.5, 1.0, 0.0, z, null, null, null, null, null, null,
                        null, null, null, null, null, clean);
                }
            };
            result.add(normalized);
        }
        if (mentions(result).size() > MAX_MENTIONS) throw invalid("Marque no máximo " + MAX_MENTIONS + " operadores por Recon.");
        return result;
    }

    static List<String> mentions(List<Element> elements) {
        return elements.stream().filter(e -> "MENTION".equals(e.type())).map(Element::username).distinct().toList();
    }

    static Element withMention(Element mention, String username, UUID operatorId, Boolean available) {
        return new Element(mention.id(), mention.type(), mention.x(), mention.y(), mention.scale(), mention.rotation(), mention.z(),
            null, null, null, null, null, null, username, mention.style(), operatorId, available, null, null);
    }

    private static BusinessException invalid(String message) {
        return BusinessException.badRequest("RECON_ELEMENTS", message);
    }

    private static String upper(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String oneOf(String value, Set<String> allowed, String fallback) {
        String normalized = upper(value);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static String color(String value, String fallback) {
        return value != null && COLOR.matcher(value).matches() ? value.toUpperCase(Locale.ROOT) : fallback;
    }

    private static double finite(Double value, double fallback) {
        return value == null || value.isNaN() || value.isInfinite() ? fallback : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double normalizeAngle(double degrees) {
        double value = degrees % 360;
        if (value > 180) value -= 360;
        if (value <= -180) value += 360;
        return value;
    }

    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}

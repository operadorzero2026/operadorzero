package br.com.operadorzero.feed;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extrai #hashtags e @mencoes da descricao do post. */
public final class FeedText {
    static final int MAX_HASHTAGS = 30;
    static final int MAX_MENTIONS = 20;
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]{1,60})");
    private static final Pattern MENTION = Pattern.compile("(?<![\\p{L}\\p{N}_.@-])@([a-zA-Z0-9][a-zA-Z0-9._-]{1,40})");

    private FeedText() {}

    /** #CQB, #cqb e #Cêrco viram "cqb" e "cerco": sem acento, minusculas, so [a-z0-9_]. */
    public static List<String> hashtags(String text) {
        Set<String> tags = new LinkedHashSet<>();
        if (text == null) return List.of();
        Matcher matcher = HASHTAG.matcher(text);
        while (matcher.find() && tags.size() < MAX_HASHTAGS) {
            String tag = normalizeTag(matcher.group(1));
            if (!tag.isEmpty()) tags.add(tag);
        }
        return List.copyOf(tags);
    }

    public static String normalizeTag(String raw) {
        String tag = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return tag.length() > 40 ? tag.substring(0, 40) : tag;
    }

    public static List<String> mentions(String text) {
        Set<String> usernames = new LinkedHashSet<>();
        if (text == null) return List.of();
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find() && usernames.size() < MAX_MENTIONS) {
            // Pontuacao no fim da frase ("@joao.") nao faz parte do nome.
            usernames.add(matcher.group(1).replaceAll("[._-]+$", "").toLowerCase(Locale.ROOT));
        }
        return List.copyOf(usernames);
    }
}

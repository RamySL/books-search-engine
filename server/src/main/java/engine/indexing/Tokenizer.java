package engine.indexing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Découpage d'un texte en mots indexables. Les livres, à l'indexation, et les
 * requêtes, à la recherche, passent par ce même découpage : un mot saisi par
 * l'utilisateur s'écrit donc exactement comme dans l'index.
 * <ol>
 *   <li>normalisation Unicode NFC : une lettre accentuée devient un seul
 *       caractère, même si le texte l'écrit lettre puis accent combinant ;</li>
 *   <li>découpage sur tout ce qui n'est pas une lettre Unicode (espaces,
 *       ponctuation, chiffres, apostrophes) : les mots accentués restent entiers ;</li>
 *   <li>passage en minuscules ;</li>
 *   <li>retrait des mots d'une lettre et des mots vides anglais.</li>
 * </ol>
 */
public final class Tokenizer {

    private static final Pattern SEPARATORS = Pattern.compile("\\P{L}+");

    /** Un mot : une suite de lettres, entre deux séparateurs. */
    private static final Pattern WORD = Pattern.compile("\\p{L}+");

    /**
     * Les mots d'une lettre sont surtout des restes d'apostrophes (king's,
     * don't) et des initiales ; dès deux lettres, il y a des mots utiles comme
     * « ur » ou « ox ».
     */
    private static final int MIN_LENGTH = 2;

    private static final Set<String> STOP_WORDS = load("/stopwords-en.txt");

    private Tokenizer() {
    }

    /** @return les mots indexables du texte, dans l'ordre du texte, répétitions comprises */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        for (String piece : SEPARATORS.split(Normalizer.normalize(text, Normalizer.Form.NFC))) {
            // la locale racine : le résultat ne dépend pas de la langue de la machine
            String word = piece.toLowerCase(Locale.ROOT);
            if (word.length() >= MIN_LENGTH && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Taille d'un texte en mots : même découpage que {@link #words}, mais
     * mots vides et mots d'une lettre compris. C'est la taille du livre, pas
     * celle de sa table d'index.
     */
    public static int countWords(String text) {
        Matcher matcher = WORD.matcher(Normalizer.normalize(text, Normalizer.Form.NFC));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Un mot par ligne ; les lignes vides et celles qui commencent par # sont ignorées. */
    private static Set<String> load(String resource) {
        InputStream stream = Tokenizer.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Ressource introuvable : " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

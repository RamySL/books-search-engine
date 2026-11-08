package engine.corpus;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texte d'un livre sans l'en-tête ni le pied de page de Project Gutenberg.
 * Ils sont les mêmes d'un livre à l'autre (présentation, licence) : gardés,
 * ils ajouteraient environ 3 000 mots communs à tous les livres.
 */
final class GutenbergText {

    // *** START OF THE PROJECT GUTENBERG EBOOK <TITRE> *** ; THIS dans les fichiers anciens
    private static final Pattern START = marker("START");
    private static final Pattern END = marker("END");

    private GutenbergText() {
    }

    /**
     * @param raw fichier texte tel que Gutenberg le sert
     * @return le texte entre les deux marqueurs, avec des fins de ligne {@code \n} ;
     *         vide s'il manque un marqueur
     */
    static Optional<String> body(String raw) {
        String text = raw.replace("\r\n", "\n");
        Matcher start = START.matcher(text);
        if (!start.find()) {
            return Optional.empty();
        }
        Matcher end = END.matcher(text);
        if (!end.find(start.end())) {
            return Optional.empty();
        }
        // sans les lignes blanches du début, mais avec le retrait de la première ligne
        String body = text.substring(start.end(), end.start()).replaceFirst("^\\s*\n", "").stripTrailing();
        return Optional.of(body + "\n");
    }

    private static Pattern marker(String word) {
        return Pattern.compile("^\\*\\*\\* ?" + word + " OF (THE|THIS) PROJECT GUTENBERG E-?BOOK.*$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    }
}

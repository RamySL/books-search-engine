package engine.indexing;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/**
 * Index inversé construit en mémoire : d'abord la table pondérée de chaque
 * livre (index direct), puis son inversion.
 * <p>
 * Les termes sont numérotés à partir de 1 par ordre alphabétique : les mêmes
 * textes donnent toujours les mêmes numéros. Les lignes (terme, livre,
 * fréquence) sont rangées par terme puis par livre, dans l'ordre de la clé
 * primaire de occurrence.
 */
final class InvertedIndex {

    /** Vocabulaire trié : le terme d'indice t a le numéro t + 1. */
    private final String[] words;

    /** Les lignes du terme t vont de start[t] à start[t + 1] exclu. */
    private final int[] start;

    private final int[] books;
    private final int[] frequencies;

    private InvertedIndex(String[] words, int[] start, int[] books, int[] frequencies) {
        this.words = words;
        this.start = start;
        this.books = books;
        this.frequencies = frequencies;
    }

    /** Table pondérée d'un livre : ses mots distincts et leurs nombres d'occurrences. */
    private record Table(int bookId, String[] words, int[] counts) {
    }

    /**
     * @param bookIds numéros des livres
     * @param texts   texte d'un livre, lu à la demande
     */
    static InvertedIndex build(List<Integer> bookIds, IntFunction<String> texts) {
        // une seule instance de chaque mot pour tout le corpus ; les clés forment le vocabulaire
        Map<String, String> vocabulary = new ConcurrentHashMap<>();
        List<Table> tables = bookIds.parallelStream()
                .sorted()
                .map(id -> table(id, texts.apply(id), vocabulary))
                .toList();
        String[] words = vocabulary.keySet().toArray(String[]::new);
        Arrays.parallelSort(words);
        return invert(words, tables);
    }

    private static Table table(int bookId, String text, Map<String, String> vocabulary) {
        Map<String, Integer> counts = new HashMap<>();
        for (String word : Tokenizer.words(text)) {
            counts.merge(word, 1, Integer::sum);
        }
        String[] words = new String[counts.size()];
        int[] frequencies = new int[counts.size()];
        int i = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            words[i] = vocabulary.computeIfAbsent(entry.getKey(), word -> word);
            frequencies[i++] = entry.getValue();
        }
        return new Table(bookId, words, frequencies);
    }

    /**
     * Tri par dénombrement : on compte les livres de chaque terme, on en
     * déduit où commencent ses lignes, puis on range chaque ligne à sa place.
     */
    private static InvertedIndex invert(String[] words, List<Table> tables) {
        Map<String, Integer> ids = new HashMap<>(2 * words.length);
        for (int t = 0; t < words.length; t++) {
            ids.put(words[t], t);
        }
        int[][] terms = new int[tables.size()][];
        int[] start = new int[words.length + 1];
        for (int b = 0; b < tables.size(); b++) {
            String[] bookWords = tables.get(b).words();
            terms[b] = new int[bookWords.length];
            for (int i = 0; i < bookWords.length; i++) {
                terms[b][i] = ids.get(bookWords[i]);
                start[terms[b][i] + 1]++;
            }
        }
        for (int t = 0; t < words.length; t++) {
            start[t + 1] += start[t];
        }
        int[] books = new int[start[words.length]];
        int[] frequencies = new int[books.length];
        int[] next = Arrays.copyOf(start, words.length);
        // les livres sont parcourus par numéro croissant : chaque terme reçoit les siens dans l'ordre
        for (int b = 0; b < tables.size(); b++) {
            Table table = tables.get(b);
            for (int i = 0; i < terms[b].length; i++) {
                int row = next[terms[b][i]]++;
                books[row] = table.bookId();
                frequencies[row] = table.counts()[i];
            }
        }
        return new InvertedIndex(words, start, books, frequencies);
    }

    int termCount() {
        return words.length;
    }

    int rowCount() {
        return books.length;
    }

    // Format texte de COPY : une ligne par enregistrement, champs séparés par
    // des tabulations. Les mots n'ont que des lettres : rien à échapper.

    /** Lignes de term : id, word, book_count. */
    void writeTerms(Writer out) throws IOException {
        for (int t = 0; t < words.length; t++) {
            out.write(Integer.toString(t + 1));
            out.write('\t');
            out.write(words[t]);
            out.write('\t');
            out.write(Integer.toString(start[t + 1] - start[t]));
            out.write('\n');
        }
    }

    /** Lignes de occurrence : term_id, book_id, frequency. */
    void writeOccurrences(Writer out) throws IOException {
        for (int t = 0; t < words.length; t++) {
            String term = Integer.toString(t + 1);
            for (int row = start[t]; row < start[t + 1]; row++) {
                out.write(term);
                out.write('\t');
                out.write(Integer.toString(books[row]));
                out.write('\t');
                out.write(Integer.toString(frequencies[row]));
                out.write('\n');
            }
        }
    }
}

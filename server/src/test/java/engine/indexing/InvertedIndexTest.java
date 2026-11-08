package engine.indexing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sur la mini-bibliothèque D1 à D3, l'index doit redonner les tables du jeu d'essai. */
class InvertedIndexTest {

    private static final Map<Integer, String> TEXTS = Map.of(
            1, "Sargon of Akkad founded an empire. The empire of Sargon fell.",
            2, "Hammurabi of Babylon wrote laws. Babylon was the city of Hammurabi.",
            3, "The empire of Akkad and the city of Babylon.");

    private static InvertedIndex build(Integer... ids) {
        return InvertedIndex.build(List.of(ids), TEXTS::get);
    }

    private static String terms(InvertedIndex index) throws IOException {
        StringWriter out = new StringWriter();
        index.writeTerms(out);
        return out.toString();
    }

    private static String occurrences(InvertedIndex index) throws IOException {
        StringWriter out = new StringWriter();
        index.writeOccurrences(out);
        return out.toString();
    }

    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    void termsAreNumberedAlphabeticallyWithTheirBookCount() throws IOException {
        assertEquals(lines("1\takkad\t2", "2\tbabylon\t2", "3\tcity\t2", "4\tempire\t2", "5\tfell\t1",
                        "6\tfounded\t1", "7\thammurabi\t1", "8\tlaws\t1", "9\tsargon\t1", "10\twrote\t1"),
                terms(build(1, 2, 3)));
    }

    @Test
    void occurrencesComeInPrimaryKeyOrder() throws IOException {
        assertEquals(lines("1\t1\t1", "1\t3\t1", "2\t2\t2", "2\t3\t1", "3\t2\t1", "3\t3\t1", "4\t1\t2",
                        "4\t3\t1", "5\t1\t1", "6\t1\t1", "7\t2\t2", "8\t2\t1", "9\t1\t2", "10\t2\t1"),
                occurrences(build(1, 2, 3)));
    }

    @Test
    void orderOfBooksDoesNotMatter() throws IOException {
        assertEquals(terms(build(1, 2, 3)), terms(build(3, 1, 2)));
        assertEquals(occurrences(build(1, 2, 3)), occurrences(build(3, 1, 2)));
    }
}

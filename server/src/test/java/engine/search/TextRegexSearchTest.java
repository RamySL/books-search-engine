package engine.search;

import engine.api.Excerpt;
import engine.corpus.CorpusProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extraits, sans base : {@code excerpts} ne lit que le fichier du livre. La
 * recherche sur tous les livres est testée avec la base dans
 * {@code engine.api.TextSearchTest}.
 */
class TextRegexSearchTest {

    @TempDir
    private Path dataDir;

    private TextRegexSearch search(String text) throws IOException {
        Path books = Files.createDirectories(dataDir.resolve("books"));
        Files.writeString(books.resolve("1.txt"), text, StandardCharsets.UTF_8);
        return new TextRegexSearch(null, new CorpusProperties(dataDir, dataDir.resolve("ids.txt"),
                URI.create("https://example.org")));
    }

    @Test
    void linesAreNumberedFromOneAndKeepTheirCase() throws IOException {
        List<Excerpt> excerpts = search("Sargon of Akkad.\nNothing here.\nThe empire of Sargon.\n")
                .excerpts(1, "sargon", 50, false);
        assertEquals(2, excerpts.size());
        assertEquals(1, excerpts.get(0).number());
        assertEquals("Sargon of Akkad.", excerpts.get(0).line());
        assertArrayEquals(new int[]{0, 6}, excerpts.get(0).occurrences().get(0));
        assertEquals(3, excerpts.get(1).number());
        assertArrayEquals(new int[]{14, 20}, excerpts.get(1).occurrences().get(0));
    }

    @Test
    void carriageReturnIsRemoved() throws IOException {
        // fichiers Gutenberg en CRLF : sans ce retrait, « n. » reconnaîtrait le '\r' final
        List<Excerpt> excerpts = search("Sargon\r\n").excerpts(1, "n.", 50, false);
        assertTrue(excerpts.isEmpty());
        assertEquals("Sargon", search("Sargon\r\n").excerpts(1, "sargon", 50, false).get(0).line());
    }

    @Test
    void severalOccurrencesOnOneLineAndLimit() throws IOException {
        TextRegexSearch search = search("babylon and babylon\nbabylon\n");
        List<Excerpt> excerpts = search.excerpts(1, "bab(y|i)lon", 1, false);
        assertEquals(1, excerpts.size());
        assertEquals(2, excerpts.get(0).occurrences().size());
        assertArrayEquals(new int[]{12, 19}, excerpts.get(0).occurrences().get(1));
    }

    @Test
    void lineShownInLowerCaseWhenLowerCasingChangesItsLength() throws IOException {
        // « İ » (I pointé turc) donne deux caractères en minuscules : les positions
        // ne correspondent plus à la ligne d'origine
        Excerpt excerpt = search("İstanbul sargon\n").excerpts(1, "sargon", 50, false).get(0);
        assertEquals("i̇stanbul sargon", excerpt.line());
        assertArrayEquals(new int[]{10, 16}, excerpt.occurrences().get(0));
    }

    @Test
    void missingTextIsEmpty() throws IOException {
        assertTrue(search("").excerpts(42, "sargon", 50, false).isEmpty());
    }

    @Test
    void wordsModeKeepsOnlyWholeWords() throws IOException {
        // recherche par mot-clé « prin » : ni spring, ni princess, ni print
        Excerpt excerpt = search("spring, princess, prin and print\n").excerpts(1, "prin", 50, true).get(0);
        assertEquals(1, excerpt.occurrences().size());
        assertArrayEquals(new int[]{18, 22}, excerpt.occurrences().get(0));
    }

    @Test
    void wordsModeWithSeveralWordsAndPunctuation() throws IOException {
        // l'apostrophe sépare les mots, comme dans le tokeniseur
        Excerpt excerpt = search("Babylon's empire\n").excerpts(1, "babylon|empire", 50, true).get(0);
        assertArrayEquals(new int[]{0, 7}, excerpt.occurrences().get(0));
        assertArrayEquals(new int[]{10, 16}, excerpt.occurrences().get(1));
    }

    @Test
    void wordsModeSkipsLinesWithoutTheWholeWord() throws IOException {
        assertTrue(search("spring only\n").excerpts(1, "prin", 50, true).isEmpty());
        assertEquals(1, search("spring only\n").excerpts(1, "prin", 50, false).size());
    }

    @Test
    void malformedRegex() throws IOException {
        TextRegexSearch search = search("sargon\n");
        assertThrows(IllegalArgumentException.class, () -> search.excerpts(1, "(sargon", 50, false));
    }
}

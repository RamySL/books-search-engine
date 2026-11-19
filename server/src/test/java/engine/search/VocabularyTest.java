package engine.search;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Recherche par préfixe dans le vocabulaire, sans base. */
class VocabularyTest {

    /** Rangés du plus répandu au moins répandu, comme le chargement depuis term. */
    private static final String[] WORDS = {"print", "spring", "prince", "princess", "prin", "principle", "pro", "a"};

    private static final Vocabulary.Terms TERMS =
            Vocabulary.Terms.of(new int[]{10, 11, 12, 13, 14, 15, 16, 17}, WORDS);

    private static String[] words(int[] indices) {
        return Arrays.stream(indices).mapToObj(i -> WORDS[i]).toArray(String[]::new);
    }

    @Test
    void prefixFindsTheWordAndItsExtensionsMostWidespreadFirst() {
        // spring contient « prin », mais ne commence pas par lui
        assertArrayEquals(new String[]{"print", "prince", "princess", "prin", "principle"},
                words(TERMS.withPrefix("prin")));
    }

    @Test
    void longerPrefixNarrowsTheInterval() {
        assertArrayEquals(new String[]{"prince", "princess", "principle"}, words(TERMS.withPrefix("princ")));
        assertArrayEquals(new String[]{"prince", "princess"}, words(TERMS.withPrefix("prince")));
        assertArrayEquals(new String[]{"princess"}, words(TERMS.withPrefix("princess")));
    }

    @Test
    void noTermStartsWithThePrefix() {
        assertEquals(0, TERMS.withPrefix("prz").length);
        assertEquals(0, TERMS.withPrefix("zzz").length);
        assertEquals(0, TERMS.withPrefix("princesses").length);
    }

    @Test
    void firstAndLastTermsOfTheAlphabet() {
        assertArrayEquals(new String[]{"a"}, words(TERMS.withPrefix("a")));
        assertArrayEquals(new String[]{"spring"}, words(TERMS.withPrefix("s")));
    }

    @Test
    void emptyVocabulary() {
        assertEquals(0, Vocabulary.Terms.of(new int[0], new String[0]).withPrefix("prin").length);
    }
}

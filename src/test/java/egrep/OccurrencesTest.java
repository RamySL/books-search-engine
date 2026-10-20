package egrep;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OccurrencesTest {

    /** @return les occurrences sous la forme "début-fin" pour des comparaisons lisibles */
    private static String occurrences(String regex, boolean kmpAutorise, String ligne) {
        List<int[]> trouvees = Egrep.chercheur(regex, kmpAutorise).occurrences(ligne);
        StringBuilder sb = new StringBuilder();
        for (int[] o : trouvees) {
            sb.append(sb.isEmpty() ? "" : " ").append(o[0]).append('-').append(o[1]);
        }
        return sb.toString();
    }

    @Test
    void kmpAndAutomatonFindTheSameOccurrences() {
        for (boolean kmp : new boolean[]{true, false}) {
            assertEquals("7-13", occurrences("Sargon", kmp, "Raphia Sargon was"));
            assertEquals("0-6 7-13", occurrences("Sargon", kmp, "Sargon Sargon"));
            // non chevauchantes : "aa" dans "aaaaa" donne 0-2 et 2-4
            assertEquals("0-2 2-4", occurrences("aa", kmp, "aaaaa"));
            assertEquals("", occurrences("Sargon", kmp, "Sargodha"));
        }
    }

    @Test
    void longestMatchAtLeftmostPosition() {
        assertEquals("6-12 17-26", occurrences("S(a|g|r)+on", true, "under Sargon and Sarrgagon"));
        assertEquals("0-3", occurrences("a|ab|abc", true, "abc"));
        assertEquals("2-7", occurrences("S.*n", true, "a Sonon b"));
    }

    @Test
    void emptyMatchesAreNotHighlighted() {
        assertEquals("1-3 4-5", occurrences("a*", true, "baaba"));
        assertEquals("", occurrences("a*", true, "xyz"));
    }

    @Test
    void highlightWrapsEachOccurrence() {
        String ligne = "le roi Sargon";
        String surlignee = Egrep.surligner(ligne, Egrep.chercheur("Sargon", true).occurrences(ligne));
        assertEquals("le roi \u001B[1;31mSargon\u001B[0m", surlignee);
    }
}

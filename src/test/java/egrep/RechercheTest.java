package egrep;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RechercheTest {

    /** @return vrai si la stratégie automate trouve le motif dans la ligne */
    private static boolean correspond(String regex, String ligne) {
        return Recherche.pour(regex, false).contient(ligne);
    }

    /** @return les occurrences sous la forme "début-fin" pour des comparaisons lisibles */
    private static String occurrences(String regex, boolean kmpAutorise, String ligne) {
        List<int[]> trouvees = Recherche.pour(regex, kmpAutorise).occurrences(ligne);
        StringBuilder sb = new StringBuilder();
        for (int[] o : trouvees) {
            sb.append(sb.isEmpty() ? "" : " ").append(o[0]).append('-').append(o[1]);
        }
        return sb.toString();
    }

    @Test
    void findsFactorAnywhereInTheLine() {
        assertTrue(correspond("Sargon", "After Raphia, Sargon was"));
        assertTrue(correspond("Sargon", "Sargon"));
        assertTrue(correspond("Sargon", "xxSargon"));
        assertFalse(correspond("Sargon", "le district de Sargodha"));
        assertFalse(correspond("Sargon", ""));
    }

    @Test
    void sujetExample() {
        assertTrue(correspond("S(a|g|r)+on", "under the Sargonids--The policies"));
        assertTrue(correspond("S(a|g|r)+on", "Sargon's quay-wall"));
        assertFalse(correspond("S(a|g|r)+on", "Shabaka and the Egyptians"));
        assertFalse(correspond("S(a|g|r)+on", "Son of Sargo"));
    }

    @Test
    void overlappingPrefixesAreHandled() {
        // le cas de KMP : "mami" dans "mamami"
        assertTrue(correspond("mami", "maman mamami"));
        assertFalse(correspond("mami", "maman mamé mia ! mm maaah !"));
    }

    @Test
    void dotMatchesAnyCharacterInsideTheLine() {
        assertTrue(correspond("S.rgon", "the Sargon"));
        assertTrue(correspond("S.rgon", "the S rgon"));
        assertFalse(correspond("S.rgon", "the Srgon"));
    }

    @Test
    void patternThatMatchesEmptyWordMatchesEveryLine() {
        assertTrue(correspond("a*", ""));
        assertTrue(correspond("a*", "xyz"));
    }

    @Test
    void alternativeAndStar() {
        assertTrue(correspond("a|bc*", "xxbccy"));
        assertFalse(correspond("a|bc*", "xxcy"));
        assertTrue(correspond("(ab)*c", "zzababc"));
    }

    @Test
    void kmpOnlyForASeriesOfConcatenations() {
        assertInstanceOf(Recherche.ParKmp.class, Recherche.pour("Sargon", true));
        // décidé sur l'arbre : les parenthèses n'y laissent pas de trace
        assertInstanceOf(Recherche.ParKmp.class, Recherche.pour("(Sar)gon", true));
        assertEquals("7-13", occurrences("(Sar)gon", true, "le roi Sargon"));
        assertInstanceOf(Recherche.ParAutomate.class, Recherche.pour("S(a|g|r)+on", true));
        assertInstanceOf(Recherche.ParAutomate.class, Recherche.pour("S.rgon", true));
        assertInstanceOf(Recherche.ParAutomate.class, Recherche.pour("ab*", true));
        // --dfa
        assertInstanceOf(Recherche.ParAutomate.class, Recherche.pour("Sargon", false));
        assertThrows(IllegalArgumentException.class, () -> Recherche.pour("", true));
    }

    @Test
    void kmpAndAutomatonAgree() {
        List<String> mots = Mots.tousLesMots("ab", 8);
        for (String facteur : List.of("a", "ab", "aab", "abab", "aabaa", "abaab", "bbb", "aaaa")) {
            Recherche kmp = Recherche.pour(facteur, true);
            Recherche automate = Recherche.pour(facteur, false);
            for (String mot : mots) {
                assertEquals(kmp.contient(mot), automate.contient(mot), facteur + " dans \"" + mot + "\"");
            }
        }
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
}

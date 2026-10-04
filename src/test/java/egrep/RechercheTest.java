package egrep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RechercheTest {

    private static boolean correspond(String regex, String ligne) {
        return Egrep.correspond(Egrep.compiler(regex), ligne);
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
}

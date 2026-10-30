package egrep.automate;

import egrep.Mots;
import egrep.regex.RegexParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterminisationTest {

    private static Dfa dfa(String regex) {
        return Determinisation.sousEnsembles(Ndfa.ahoUllman(RegexParser.analyser(regex)));
    }

    @Test
    void sujetExample() {
        Dfa d = dfa("S(a|g|r)+on");
        assertTrue(d.accepte("Sargon"));
        assertTrue(d.accepte("Saon"));
        assertFalse(d.accepte("Son"));
        assertFalse(d.accepte("Sargo"));
        assertFalse(d.accepte("Sargonx"));
    }

    @Test
    void dotAcceptsCharactersOutsideTheAlphabet() {
        Dfa d = dfa("a.c");
        assertTrue(d.accepte("abc"));
        assertTrue(d.accepte("a c"));
        assertTrue(d.accepte("aac"));
        assertTrue(d.accepte("acc"));
        assertFalse(d.accepte("ac"));
        assertFalse(d.accepte("abbc"));
    }

    @Test
    void emptyWordOnlyWhenInitialStateIsFinal() {
        assertTrue(dfa("a*").accepte(""));
        assertFalse(dfa("a+").accepte(""));
    }

    @Test
    void sameLanguageAsTheNfa() {
        // tous les mots de longueur <= 5 sur {a, b, c, x}
        List<String> mots = Mots.tousLesMots("abcx", 5);
        for (String regex : List.of("a|bc*", "(a|b)*c", "a.b|b.*", "(ab|a)(c|bc)*", "..", "(a*b*)*c", "x(.|a)+")) {
            Ndfa ndfa = Ndfa.ahoUllman(RegexParser.analyser(regex));
            Dfa d = Determinisation.sousEnsembles(ndfa);
            for (String mot : mots) {
                assertEquals(ndfa.accepte(mot), d.accepte(mot), regex + " sur \"" + mot + "\"");
            }
        }
    }
}

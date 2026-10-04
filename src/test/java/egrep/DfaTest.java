package egrep;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DfaTest {

    private static Dfa dfa(String regex) {
        return Dfa.depuisAutomate(AutomateAhoUllman.depuisArbre(Egrep.regexToAst(regex)));
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
    void minimisationOfSujetExampleHasFiveStates() {
        // S, (a|g|r), boucle (a|g|r), o, n : 5 états, sans état puits
        assertEquals(5, dfa("S(a|g|r)+on").minimiser().nbEtats());
    }

    @Test
    void minimisationMergesEquivalentStates() {
        assertEquals(1, dfa("(a|b)*").minimiser().nbEtats());
        assertEquals(2, dfa("a|a").minimiser().nbEtats());
        assertEquals(1, dfa(".*").minimiser().nbEtats());
    }

    @Test
    void minimisationIsIdempotent() {
        for (String regex : List.of("S(a|g|r)+on", "(ab|a)(c|bc)*", "x(.|a)+", "(a*b*)*c")) {
            Dfa minimal = dfa(regex).minimiser();
            assertEquals(minimal.nbEtats(), minimal.minimiser().nbEtats(), regex);
        }
    }

    @Test
    void minimisationPreservesTheLanguage() {
        List<String> mots = tousLesMots("abcx", 5);
        for (String regex : List.of("a|bc*", "(a|b)*c", "a.b|b.*", "(ab|a)(c|bc)*", "..", "(a*b*)*c", "x(.|a)+")) {
            Dfa d = dfa(regex);
            Dfa minimal = d.minimiser();
            assertTrue(minimal.nbEtats() <= d.nbEtats(), regex);
            for (String mot : mots) {
                assertEquals(d.accepte(mot), minimal.accepte(mot), regex + " sur \"" + mot + "\"");
            }
        }
    }

    private static List<String> tousLesMots(String lettres, int longueurMax) {
        List<String> mots = new ArrayList<>();
        mots.add("");
        for (int debut = 0; debut < mots.size() && mots.get(debut).length() < longueurMax; debut++) {
            for (char c : lettres.toCharArray()) {
                mots.add(mots.get(debut) + c);
            }
        }
        return mots;
    }

    @Test
    void sameLanguageAsTheNfa() {
        List<String> mots = new ArrayList<>();
        // tous les mots de longueur <= 5 sur {a, b, c, x}
        String lettres = "abcx";
        mots.add("");
        for (int debut = 0; debut < mots.size() && mots.get(debut).length() < 5; debut++) {
            for (char c : lettres.toCharArray()) {
                mots.add(mots.get(debut) + c);
            }
        }
        for (String regex : List.of("a|bc*", "(a|b)*c", "a.b|b.*", "(ab|a)(c|bc)*", "..", "(a*b*)*c", "x(.|a)+")) {
            AutomateAhoUllman afn = AutomateAhoUllman.depuisArbre(Egrep.regexToAst(regex));
            Dfa d = Dfa.depuisAutomate(afn);
            for (String mot : mots) {
                assertEquals(afn.accepte(mot), d.accepte(mot), regex + " sur \"" + mot + "\"");
            }
        }
    }
}

package egrep.automate;

import egrep.Mots;
import egrep.regex.RegexParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimisationTest {

    private static Dfa dfa(String regex) {
        return Determinisation.sousEnsembles(Ndfa.ahoUllman(RegexParser.analyser(regex)));
    }

    @Test
    void sujetExampleHasFiveStates() {
        // S, (a|g|r), boucle (a|g|r), o, n : 5 états, sans état puits
        assertEquals(5, Minimisation.moore(dfa("S(a|g|r)+on")).nbEtats());
    }

    @Test
    void mergesEquivalentStates() {
        assertEquals(1, Minimisation.moore(dfa("(a|b)*")).nbEtats());
        assertEquals(2, Minimisation.moore(dfa("a|a")).nbEtats());
        assertEquals(1, Minimisation.moore(dfa(".*")).nbEtats());
    }

    @Test
    void isIdempotent() {
        for (String regex : List.of("S(a|g|r)+on", "(ab|a)(c|bc)*", "x(.|a)+", "(a*b*)*c")) {
            Dfa minimal = Minimisation.moore(dfa(regex));
            assertEquals(minimal.nbEtats(), Minimisation.moore(minimal).nbEtats(), regex);
        }
    }

    @Test
    void preservesTheLanguage() {
        List<String> mots = Mots.tousLesMots("abcx", 5);
        for (String regex : List.of("a|bc*", "(a|b)*c", "a.b|b.*", "(ab|a)(c|bc)*", "..", "(a*b*)*c", "x(.|a)+")) {
            Dfa d = dfa(regex);
            Dfa minimal = Minimisation.moore(d);
            assertTrue(minimal.nbEtats() <= d.nbEtats(), regex);
            for (String mot : mots) {
                assertEquals(d.accepte(mot), minimal.accepte(mot), regex + " sur \"" + mot + "\"");
            }
        }
    }
}

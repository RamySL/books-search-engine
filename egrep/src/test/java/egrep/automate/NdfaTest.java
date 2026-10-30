package egrep.automate;

import egrep.regex.RegexParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NdfaTest {

    private static Ndfa ndfa(String regex) {
        return Ndfa.ahoUllman(RegexParser.analyser(regex));
    }

    @Test
    void sujetExample() {
        Ndfa a = ndfa("S(a|g|r)+on");
        assertTrue(a.accepte("Sargon"));
        assertTrue(a.accepte("Saon"));
        assertTrue(a.accepte("Sagrgon"));
        assertFalse(a.accepte("Son"));
        assertFalse(a.accepte("Sargo"));
    }

    @Test
    void dotMatchesAnyCharacter() {
        Ndfa a = ndfa("a.c");
        assertTrue(a.accepte("abc"));
        assertTrue(a.accepte("a c"));
        assertFalse(a.accepte("ac"));
        assertFalse(a.accepte("abbc"));
    }

    @Test
    void starAcceptsEmptyWord() {
        Ndfa a = ndfa("a|bc*");
        assertTrue(a.accepte("a"));
        assertTrue(a.accepte("bccc"));
        assertFalse(a.accepte(""));
        assertFalse(a.accepte("ab"));
    }

    @Test
    void onlyJokersApplyOutsideTheAlphabet() {
        Ndfa a = ndfa("a|.");
        Set<Integer> depart = a.fermetureEpsilon(Set.of(a.etatInitial()));
        // 'a' emprunte la transition 'a' et le joker, un caractère hors alphabet le joker seul
        assertEquals(2, a.transiter(depart, 'a').size());
        assertEquals(1, a.transiterHorsAlphabet(depart).size());
    }
}

package egrep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomateAhoUllmanTest {

    private static AutomateAhoUllman automate(String regex) {
        return AutomateAhoUllman.depuisArbre(Egrep.regexToAst(regex));
    }

    @Test
    void sujetExample() {
        AutomateAhoUllman a = automate("S(a|g|r)+on");
        assertTrue(a.accepte("Sargon"));
        assertTrue(a.accepte("Saon"));
        assertTrue(a.accepte("Sagrgon"));
        assertFalse(a.accepte("Son"));
        assertFalse(a.accepte("Sargo"));
    }

    @Test
    void dotMatchesAnyCharacter() {
        AutomateAhoUllman a = automate("a.c");
        assertTrue(a.accepte("abc"));
        assertTrue(a.accepte("a c"));
        assertFalse(a.accepte("ac"));
        assertFalse(a.accepte("abbc"));
    }

    @Test
    void starAcceptsEmptyWord() {
        AutomateAhoUllman a = automate("a|bc*");
        assertTrue(a.accepte("a"));
        assertTrue(a.accepte("bccc"));
        assertFalse(a.accepte(""));
        assertFalse(a.accepte("ab"));
    }
}

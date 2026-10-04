package egrep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShuntingYardTest {

    private static final String C = String.valueOf(Egrep.CONCAT);

    @Test
    void singleCharacter() {
        assertEquals("a", Egrep.shuntingYard("a"));
    }

    @Test
    void implicitConcatenation() {
        assertEquals("ab" + C, Egrep.shuntingYard("ab"));
    }

    @Test
    void simpleAlternation() {
        assertEquals("ab|", Egrep.shuntingYard("a|b"));
    }

    @Test
    void simpleStar() {
        assertEquals("a*", Egrep.shuntingYard("a*"));
    }

    @Test
    void starThenConcatenation() {
        assertEquals("a*b" + C, Egrep.shuntingYard("a*b"));
    }

    @Test
    void chainedConcatenation() {
        assertEquals("ab" + C + "c" + C, Egrep.shuntingYard("abc"));
    }

    @Test
    void alternationWithStarAndConcatenation() {
        assertEquals("ab*c" + C + "|", Egrep.shuntingYard("a|b*c"));
    }

    @Test
    void parenthesesOverridePriority() {
        assertEquals("ab|c" + C, Egrep.shuntingYard("(a|b)c"));
    }

    @Test
    void starOnParenthesizedGroup() {
        assertEquals("ab|*", Egrep.shuntingYard("(a|b)*"));
    }

    @Test
    void nestedParentheses() {
        assertEquals("abc|" + C, Egrep.shuntingYard("a(b|c)"));
        assertEquals("ab|c|", Egrep.shuntingYard("((a|b)|c)"));
    }

    @Test
    void dotIsAnOperand() {
        assertEquals("a." + C, Egrep.shuntingYard("a."));
    }

    @Test
    void unbalancedParenthesesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Egrep.shuntingYard("(a|b"));
        assertThrows(IllegalArgumentException.class, () -> Egrep.shuntingYard("a|b)"));
    }
}

package egrep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShuntingYardTest {

    @Test
    void singleCharacter() {
        assertEquals("a", Egrep.shuntingYard("a"));
    }

    @Test
    void simpleConcatenation() {
        assertEquals("ab.", Egrep.shuntingYard("a.b"));
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
        assertEquals("a*b.", Egrep.shuntingYard("a*.b"));
    }

    @Test
    void chainedConcatenation() {
        assertEquals("ab.c.", Egrep.shuntingYard("a.b.c"));
    }

    @Test
    void alternationWithStarAndConcatenation() {
        assertEquals("ab*c.|", Egrep.shuntingYard("a|b*.c"));
    }
}

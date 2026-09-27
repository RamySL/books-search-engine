import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegexToAstTest {

    private static RegexTree feuille(char c) {
        return new RegexTree.Feuille(c);
    }

    private static RegexTree etoile(RegexTree fils) {
        return new RegexTree.Etoile(fils);
    }

    private static RegexTree concat(RegexTree gauche, RegexTree droit) {
        return new RegexTree.Concat(gauche, droit);
    }

    private static RegexTree union(RegexTree gauche, RegexTree droit) {
        return new RegexTree.Union(gauche, droit);
    }

    @Test
    void singleCharacter() {
        assertEquals(feuille('a'), Egrep.regexToAst("a"));
    }

    @Test
    void simpleStar() {
        assertEquals(etoile(feuille('a')), Egrep.regexToAst("a*"));
    }

    @Test
    void simpleConcatenation() {
        assertEquals(concat(feuille('a'), feuille('b')), Egrep.regexToAst("a.b"));
    }

    @Test
    void simpleAlternation() {
        assertEquals(union(feuille('a'), feuille('b')), Egrep.regexToAst("a|b"));
    }

    @Test
    void starThenConcatenation() {
        assertEquals(concat(etoile(feuille('a')), feuille('b')), Egrep.regexToAst("a*.b"));
    }

    @Test
    void chainedConcatenationIsLeftAssociative() {
        assertEquals(
                concat(concat(feuille('a'), feuille('b')), feuille('c')),
                Egrep.regexToAst("a.b.c"));
    }

    @Test
    void starBindsTighterThanConcatenation() {
        assertEquals(
                concat(feuille('a'), etoile(feuille('b'))),
                Egrep.regexToAst("a.b*"));
    }

    @Test
    void concatenationBindsTighterThanAlternation() {
        assertEquals(
                union(feuille('a'), concat(etoile(feuille('b')), feuille('c'))),
                Egrep.regexToAst("a|b*.c"));
    }
}

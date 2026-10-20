package egrep.regex;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegexParserTest {

    @Nested
    class ShuntingYard {

        private static final String C = String.valueOf(RegexParser.CONCAT);

        @Test
        void singleCharacter() {
            assertEquals("a", RegexParser.shuntingYard("a"));
        }

        @Test
        void implicitConcatenation() {
            assertEquals("ab" + C, RegexParser.shuntingYard("ab"));
        }

        @Test
        void simpleAlternation() {
            assertEquals("ab|", RegexParser.shuntingYard("a|b"));
        }

        @Test
        void simpleStar() {
            assertEquals("a*", RegexParser.shuntingYard("a*"));
        }

        @Test
        void starThenConcatenation() {
            assertEquals("a*b" + C, RegexParser.shuntingYard("a*b"));
        }

        @Test
        void chainedConcatenation() {
            assertEquals("ab" + C + "c" + C, RegexParser.shuntingYard("abc"));
        }

        @Test
        void alternationWithStarAndConcatenation() {
            assertEquals("ab*c" + C + "|", RegexParser.shuntingYard("a|b*c"));
        }

        @Test
        void parenthesesOverridePriority() {
            assertEquals("ab|c" + C, RegexParser.shuntingYard("(a|b)c"));
        }

        @Test
        void starOnParenthesizedGroup() {
            assertEquals("ab|*", RegexParser.shuntingYard("(a|b)*"));
        }

        @Test
        void nestedParentheses() {
            assertEquals("abc|" + C, RegexParser.shuntingYard("a(b|c)"));
            assertEquals("ab|c|", RegexParser.shuntingYard("((a|b)|c)"));
        }

        @Test
        void dotIsAnOperand() {
            assertEquals("a." + C, RegexParser.shuntingYard("a."));
        }

        @Test
        void unbalancedParenthesesAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> RegexParser.shuntingYard("(a|b"));
            assertThrows(IllegalArgumentException.class, () -> RegexParser.shuntingYard("a|b)"));
        }
    }

    @Nested
    class Arbre {

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
            assertEquals(feuille('a'), RegexParser.analyser("a"));
        }

        @Test
        void simpleStar() {
            assertEquals(etoile(feuille('a')), RegexParser.analyser("a*"));
        }

        @Test
        void simpleConcatenation() {
            assertEquals(concat(feuille('a'), feuille('b')), RegexParser.analyser("ab"));
        }

        @Test
        void simpleAlternation() {
            assertEquals(union(feuille('a'), feuille('b')), RegexParser.analyser("a|b"));
        }

        @Test
        void starThenConcatenation() {
            assertEquals(concat(etoile(feuille('a')), feuille('b')), RegexParser.analyser("a*b"));
        }

        @Test
        void chainedConcatenationIsLeftAssociative() {
            assertEquals(
                    concat(concat(feuille('a'), feuille('b')), feuille('c')),
                    RegexParser.analyser("abc"));
        }

        @Test
        void starBindsTighterThanConcatenation() {
            assertEquals(
                    concat(feuille('a'), etoile(feuille('b'))),
                    RegexParser.analyser("ab*"));
        }

        @Test
        void concatenationBindsTighterThanAlternation() {
            assertEquals(
                    union(feuille('a'), concat(etoile(feuille('b')), feuille('c'))),
                    RegexParser.analyser("a|b*c"));
        }

        @Test
        void parenthesesOverridePriority() {
            assertEquals(
                    concat(union(feuille('a'), feuille('b')), feuille('c')),
                    RegexParser.analyser("(a|b)c"));
        }

        @Test
        void starOnGroup() {
            assertEquals(
                    etoile(union(feuille('a'), feuille('b'))),
                    RegexParser.analyser("(a|b)*"));
        }

        @Test
        void dotIsUniversalCharacter() {
            assertEquals(
                    concat(feuille('a'), new RegexTree.Point()),
                    RegexParser.analyser("a."));
        }

        @Test
        void plusIsOneOrMore() {
            assertEquals(concat(feuille('a'), etoile(feuille('a'))), RegexParser.analyser("a+"));
        }

        @Test
        void sujetExample() {
            RegexTree groupe = union(union(feuille('a'), feuille('g')), feuille('r'));
            RegexTree attendu = concat(
                    concat(
                            concat(feuille('S'), concat(groupe, etoile(groupe))),
                            feuille('o')),
                    feuille('n'));
            assertEquals(attendu, RegexParser.analyser("S(a|g|r)+on"));
        }

        @Test
        void malformedRegexIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> RegexParser.analyser("*a"));
            assertThrows(IllegalArgumentException.class, () -> RegexParser.analyser("a|"));
            assertThrows(IllegalArgumentException.class, () -> RegexParser.analyser("()"));
            assertThrows(IllegalArgumentException.class, () -> RegexParser.analyser(""));
        }
    }
}

package moteur.indexation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokeniseurTest {

    @Test
    void lowercasesSplitsAndRemovesStopWords() {
        assertEquals(List.of("sargon", "akkad", "founded", "empire", "empire", "sargon", "fell"),
                Tokeniseur.mots("Sargon of Akkad founded an empire. The empire of Sargon fell."));
    }

    @Test
    void keepsAccentedWordsWhole() {
        assertEquals(List.of("le", "développement", "économique"),
                Tokeniseur.mots("Le développement économique"));
    }

    @Test
    void decomposedAccentGivesTheComposedWord() {
        // e suivi de l'accent aigu combinant U+0301, qui n'est pas une lettre
        assertEquals(List.of("café"), Tokeniseur.mots("café"));
    }

    @Test
    void apostrophesAndDigitsSeparateWords() {
        // king's -> king + s, won't -> won + t : s et t ont une lettre, won est un mot vide
        assertEquals(List.of("king", "men", "wait", "days"),
                Tokeniseur.mots("The king's men won't wait 12 days"));
    }

    @Test
    void dropsOneLetterWordsButKeepsTwoLetterOnes() {
        assertEquals(List.of("saw", "city", "ur", "chaldees"),
                Tokeniseur.mots("I saw a city: Ur of the Chaldees"));
    }

    @Test
    void queryIsNormalizedLikeText() {
        assertEquals(List.of("sargon"), Tokeniseur.mots("  SARGON!! "));
    }

    @Test
    void textWithoutIndexableWordGivesNoWord() {
        assertEquals(List.of(), Tokeniseur.mots(""));
        assertEquals(List.of(), Tokeniseur.mots("The 42 of and..."));
    }
}

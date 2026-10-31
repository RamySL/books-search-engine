package engine.indexing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenizerTest {

    @Test
    void lowercasesSplitsAndRemovesStopWords() {
        assertEquals(List.of("sargon", "akkad", "founded", "empire", "empire", "sargon", "fell"),
                Tokenizer.words("Sargon of Akkad founded an empire. The empire of Sargon fell."));
    }

    @Test
    void keepsAccentedWordsWhole() {
        assertEquals(List.of("le", "développement", "économique"),
                Tokenizer.words("Le développement économique"));
    }

    @Test
    void decomposedAccentGivesTheComposedWord() {
        // e suivi de l'accent aigu combinant U+0301, qui n'est pas une lettre
        assertEquals(List.of("café"), Tokenizer.words("café"));
    }

    @Test
    void apostrophesAndDigitsSeparateWords() {
        // king's -> king + s, won't -> won + t : s et t ont une lettre, won est un mot vide
        assertEquals(List.of("king", "men", "wait", "days"),
                Tokenizer.words("The king's men won't wait 12 days"));
    }

    @Test
    void dropsOneLetterWordsButKeepsTwoLetterOnes() {
        assertEquals(List.of("saw", "city", "ur", "chaldees"),
                Tokenizer.words("I saw a city: Ur of the Chaldees"));
    }

    @Test
    void queryIsNormalizedLikeText() {
        assertEquals(List.of("sargon"), Tokenizer.words("  SARGON!! "));
    }

    @Test
    void textWithoutIndexableWordGivesNoWord() {
        assertEquals(List.of(), Tokenizer.words(""));
        assertEquals(List.of(), Tokenizer.words("The 42 of and..."));
    }
}

package egrep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AffichageTest {

    @Test
    void highlightWrapsEachOccurrence() {
        String ligne = "le roi Sargon";
        String surlignee = Affichage.surligner(ligne, Recherche.pour("Sargon", true).occurrences(ligne));
        assertEquals("le roi \u001B[1;31mSargon\u001B[0m", surlignee);
    }
}

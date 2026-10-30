package egrep.kmp;

import egrep.Mots;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KmpTest {

    @Test
    void carryTablesOfTheLecture() {
        assertArrayEquals(new int[]{-1, 0, -1, 1, 0}, new Kmp("mami").report());
        assertArrayEquals(new int[]{-1, 0, -1, 0, -1, 3, 0, 0}, new Kmp("mamamia").report());
        assertArrayEquals(new int[]{-1, 0, 0, -1, 0, 2, 0}, new Kmp("chicha").report());
        assertArrayEquals(new int[]{-1, 0, 0, 0, 0, 0, 0}, new Kmp("Sargon").report());
    }

    @Test
    void lectureExamples() {
        assertTrue(new Kmp("Sargon").contient("Raphia, Sargon was"));
        assertFalse(new Kmp("Sargon").contient("le district de Sargodha, ainsi que"));
        assertFalse(new Kmp("mami").contient("maman mamé mia ! mm maaah !"));
        assertTrue(new Kmp("mami").contient("maman mamami"));
    }

    @Test
    void edgeCases() {
        assertTrue(new Kmp("a").contient("a"));
        assertFalse(new Kmp("a").contient(""));
        assertFalse(new Kmp("abc").contient("ab"));
        assertTrue(new Kmp("").contient("xyz"));
        assertTrue(new Kmp("aab").contient("aaaab"));
    }

    @Test
    void agreesWithStringContains() {
        List<String> mots = Mots.tousLesMots("ab", 8);
        for (String facteur : List.of("a", "ab", "aab", "abab", "aabaa", "abaab", "bbb", "aaaa")) {
            Kmp kmp = new Kmp(facteur);
            for (String mot : mots) {
                assertEquals(mot.contains(facteur), kmp.contient(mot), facteur + " dans \"" + mot + "\"");
            }
        }
    }
}

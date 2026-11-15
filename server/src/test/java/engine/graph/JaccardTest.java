package engine.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Distance de Jaccard pondérée sur la mini-bibliothèque D1 à D3, découpée en
 * mots de 3 lettres ou plus : « the », « and » et « was » restent.
 */
class JaccardTest {

    /** Le terme d'indice t a le numéro t. */
    private static final List<String> VOCABULARY = List.of("akkad", "and", "babylon", "city", "empire", "fell",
            "founded", "hammurabi", "laws", "sargon", "the", "was", "wrote");

    // Sargon of Akkad founded an empire. The empire of Sargon fell.
    static final WeightedTable D1 = table(Map.of(
            "sargon", 2, "akkad", 1, "founded", 1, "empire", 2, "the", 1, "fell", 1));
    // Hammurabi of Babylon wrote laws. Babylon was the city of Hammurabi.
    static final WeightedTable D2 = table(Map.of(
            "hammurabi", 2, "babylon", 2, "wrote", 1, "laws", 1, "was", 1, "the", 1, "city", 1));
    // The empire of Akkad and the city of Babylon.
    static final WeightedTable D3 = table(Map.of(
            "the", 2, "empire", 1, "akkad", 1, "and", 1, "city", 1, "babylon", 1));

    /** Table pondérée, termes par numéro croissant. */
    private static WeightedTable table(Map<String, Integer> counts) {
        int[] terms = counts.keySet().stream().mapToInt(VOCABULARY::indexOf).sorted().toArray();
        int[] frequencies = Arrays.stream(terms).map(t -> counts.get(VOCABULARY.get(t))).toArray();
        return new WeightedTable(terms, frequencies);
    }

    @Test
    void miniLibrary() {
        // Σ min = 3 (akkad, empire, the), Σ max = 8 + 7 − 3 = 12
        assertEquals(0.75, Jaccard.distance(D1, D3));
        // seul « the » est commun : Σ min = 1, Σ max = 8 + 9 − 1 = 16
        assertEquals(15.0 / 16, Jaccard.distance(D1, D2));
        // Σ min = 3 (babylon, city, the), Σ max = 9 + 7 − 3 = 13
        assertEquals(10.0 / 13, Jaccard.distance(D2, D3));
    }

    @Test
    void symmetric() {
        assertEquals(Jaccard.distance(D1, D2), Jaccard.distance(D2, D1));
        assertEquals(Jaccard.distance(D1, D3), Jaccard.distance(D3, D1));
        assertEquals(Jaccard.distance(D2, D3), Jaccard.distance(D3, D2));
    }

    @Test
    void identicalTablesAreAtDistanceZero() {
        assertEquals(0, Jaccard.distance(D1, D1));
        assertEquals(0, Jaccard.distance(D1, table(Map.of(
                "fell", 1, "the", 1, "empire", 2, "founded", 1, "akkad", 1, "sargon", 2))));
        WeightedTable empty = table(Map.of());
        assertEquals(0, Jaccard.distance(empty, empty));
    }

    @Test
    void tablesWithoutCommonWordAreAtDistanceOne() {
        assertEquals(1, Jaccard.distance(table(Map.of("sargon", 2, "akkad", 1)),
                table(Map.of("hammurabi", 2, "babylon", 2))));
        assertEquals(1, Jaccard.distance(D1, table(Map.of())));
    }

    @Test
    void weightsCount() {
        // mêmes mots, autres nombres d'occurrences : Σ min = 1 + 1, Σ max = 3 + 1
        assertEquals(0.5, Jaccard.distance(table(Map.of("akkad", 3, "babylon", 1)),
                table(Map.of("akkad", 1, "babylon", 1))));
    }

    @Test
    void matrixIsSymmetricWithZeroDiagonal() {
        double[][] matrix = Jaccard.matrix(List.of(D1, D2, D3));
        assertArrayEquals(new double[] {0, 15.0 / 16, 0.75}, matrix[0]);
        assertArrayEquals(new double[] {15.0 / 16, 0, 10.0 / 13}, matrix[1]);
        assertArrayEquals(new double[] {0.75, 10.0 / 13, 0}, matrix[2]);
    }
}

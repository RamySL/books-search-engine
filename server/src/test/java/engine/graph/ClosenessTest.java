package engine.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ClosenessTest {

    @Test
    void connectedGraphAToH() {
        // (n − 1) / Σ d : E est le centre, C le deuxième
        assertArrayEquals(new double[] {7.0 / 16, 7.0 / 17, 7.0 / 12, 7.0 / 13, 7.0 / 11, 7.0 / 13, 7.0 / 18, 7.0 / 18},
                Closeness.of(TestGraphs.A_TO_H));
    }

    @Test
    void twoComponentsAndAnIsolatedVertex() {
        // chemin 0 - 1 - 2, arête 3 - 4, sommet 5 isolé ; n = 6
        ThresholdGraph graph = TestGraphs.of(6, 0.5,
                new int[] {0, 1}, new int[] {1, 2}, new int[] {3, 4});
        assertArrayEquals(new double[] {
                        // r = 3 : (2/5) · (2/3), puis (2/5) · (2/2)
                        4.0 / 15, 0.4, 4.0 / 15,
                        // r = 2 : (1/5) · (1/1), et non 1
                        0.2, 0.2,
                        0},
                Closeness.of(graph));
    }

    @Test
    void jaccardDistancesDoNotCount() {
        // seul le nombre d'arêtes compte
        ThresholdGraph graph = TestGraphs.weighted(3, new int[][] {{0, 1}, {1, 2}}, new double[] {0.1, 0.9});
        assertArrayEquals(new double[] {2.0 / 3, 1, 2.0 / 3}, Closeness.of(graph));
    }

    @Test
    void singleVertex() {
        assertArrayEquals(new double[] {0}, Closeness.of(TestGraphs.of(1, 0.5)));
    }
}

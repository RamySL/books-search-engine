package engine.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PageRank avec α = 0,85 : le saut répartit 0,15 sur les n sommets. */
class PageRankTest {

    private static final double PRECISION = 1e-10;

    private static double[] scores(ThresholdGraph graph) {
        PageRank.Result result = PageRank.of(graph);
        assertTrue(result.iterations() < PageRank.MAX_ITERATIONS, "convergence avant la limite");
        return result.scores();
    }

    private static void assertScores(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int v = 0; v < expected.length; v++) {
            assertEquals(expected[v], actual[v], PRECISION, "sommet " + v);
        }
    }

    /** Étoile : le centre 0 relié à 4 feuilles. */
    private static ThresholdGraph star(double distance) {
        return TestGraphs.of(5, distance, new int[] {0, 1}, new int[] {0, 2}, new int[] {0, 3}, new int[] {0, 4});
    }

    @Test
    void scoresSumToOne() {
        assertEquals(1, Arrays.stream(scores(TestGraphs.A_TO_H)).sum(), PRECISION);
        ThresholdGraph withIsolated = TestGraphs.of(6, 0.5, new int[] {0, 1}, new int[] {1, 2});
        assertEquals(1, Arrays.stream(scores(withIsolated)).sum(), PRECISION);
    }

    @Test
    void cycleIsUniform() {
        ThresholdGraph cycle = TestGraphs.of(5, 0.5,
                new int[] {0, 1}, new int[] {1, 2}, new int[] {2, 3}, new int[] {3, 4}, new int[] {4, 0});
        assertScores(new double[] {0.2, 0.2, 0.2, 0.2, 0.2}, scores(cycle));
    }

    @Test
    void starWithFourLeaves() {
        // centre c = 0,03 + 0,85 · 4f et feuille f = 0,03 + 0,85 · c/4 : c = 0,88/1,85
        double center = 0.88 / 1.85;
        double leaf = (1 - center) / 4;
        assertEquals(0.4757, center, 1e-4);
        assertEquals(0.1311, leaf, 1e-4);
        assertScores(new double[] {center, leaf, leaf, leaf, leaf}, scores(star(0.5)));
    }

    @Test
    void equalWeightsGiveTheUnweightedResult() {
        assertScores(scores(star(0.5)), scores(star(0.1)));
        assertScores(scores(star(0.5)), scores(star(0.9)));
    }

    @Test
    void weightsCount() {
        // chemin 0 - 1 - 2, similarités 0,8 et 0,2 : depuis 1, la marche va 4 fois plus souvent vers 0
        ThresholdGraph path = TestGraphs.weighted(3, new int[][] {{0, 1}, {1, 2}}, new double[] {0.2, 0.8});
        // centre c = 0,05 + 0,85 (p0 + p2) = 18/37, comme sans poids ; p0 = 0,05 + 0,85 · 0,8 c, p2 = 0,05 + 0,85 · 0,2 c
        assertScores(new double[] {14.09 / 37, 18.0 / 37, 4.91 / 37}, scores(path));
    }

    @Test
    void massOfIsolatedVerticesIsSpreadUniformly() {
        // arête 0 - 1 et sommet 2 isolé : p2 = 0,05 + 0,85 · p2/3, d'où p2 = 3/43
        ThresholdGraph graph = TestGraphs.of(3, 0.5, new int[] {0, 1});
        assertScores(new double[] {20.0 / 43, 20.0 / 43, 3.0 / 43}, scores(graph));
    }

    @Test
    void graphWithoutEdgesIsUniform() {
        assertScores(new double[] {0.25, 0.25, 0.25, 0.25}, scores(TestGraphs.of(4, 0.5)));
    }
}

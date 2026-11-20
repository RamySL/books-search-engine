package engine.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Distances de D1 à D3 : 0,75, 10/13 ≈ 0,769 et 15/16 = 0,9375. Plus proche
 * voisin : D3 pour D1 (0,75), D3 pour D2 (10/13), D1 pour D3 (0,75).
 */
class DistanceDistributionTest {

    private static final DistanceDistribution D1_D3 = DistanceDistribution.of(ThresholdGraphTest.MATRIX);

    @Test
    void edgesBelowTheta() {
        assertEquals(0, D1_D3.edges(0.75));
        assertEquals(1, D1_D3.edges(0.76));
        assertEquals(2, D1_D3.edges(0.8));
        assertEquals(3, D1_D3.edges(1));
        assertEquals(4.0 / 3, D1_D3.meanDegree(0.8));
    }

    @Test
    void edgesAgreeWithTheGraph() {
        for (int k = 1; k <= DistanceDistribution.STEPS; k++) {
            double theta = (double) k / DistanceDistribution.STEPS;
            assertEquals(ThresholdGraph.of(ThresholdGraphTest.MATRIX, theta).edgeCount(), D1_D3.edges(theta));
        }
    }

    @Test
    void isolatedBooks() {
        assertEquals(3, D1_D3.isolated(0.5));
        // inégalité stricte, comme pour les arêtes
        assertEquals(3, D1_D3.isolated(0.75));
        assertEquals(1, D1_D3.isolated(0.76));
        assertEquals(0, D1_D3.isolated(0.8));
        assertEquals(0, D1_D3.isolated(1));
        // un livre seul n'a jamais de voisin
        assertEquals(1, DistanceDistribution.of(new double[][] {{0}}).isolated(1));
    }

    @Test
    void isolatedBooksAgreeWithTheGraph() {
        for (int k = 1; k <= DistanceDistribution.STEPS; k++) {
            double theta = (double) k / DistanceDistribution.STEPS;
            ThresholdGraph graph = ThresholdGraph.of(ThresholdGraphTest.MATRIX, theta);
            long isolated = IntStream.range(0, graph.size()).filter(u -> graph.degree(u) == 0).count();
            assertEquals(isolated, D1_D3.isolated(theta));
        }
    }

    @Test
    void smallestThetaReachingTheMeanDegree() {
        // degré moyen 1 : il faut 2 arêtes, atteintes dès que 10/13 < θ
        assertEquals(0.77, D1_D3.thetaForMeanDegree(1));
        assertEquals(0.94, D1_D3.thetaForMeanDegree(2));
        // inaccessible avec 3 livres : graphe le plus dense possible
        assertEquals(1, D1_D3.thetaForMeanDegree(20));
    }

    @Test
    void histogram() {
        List<String> lines = D1_D3.histogram();
        assertEquals(1 + DistanceDistribution.STEPS, lines.size());
        assertEquals("from,to,pairs,edges,mean_degree,isolated", lines.getFirst());
        assertEquals("0.00,0.01,0,0,0.00,3", lines.get(1));
        assertEquals("0.75,0.76,1,1,0.67,1", lines.get(76));
        assertEquals("0.76,0.77,1,2,1.33,0", lines.get(77));
        assertEquals("0.93,0.94,1,3,2.00,0", lines.get(94));
        assertEquals("0.99,1.00,0,3,2.00,0", lines.getLast());
    }

    @Test
    void lastBinCountsPairsWithoutCommonWord() {
        double[][] matrix = {{0, 1}, {1, 0}};
        DistanceDistribution distribution = DistanceDistribution.of(matrix);
        assertEquals(0, distribution.edges(1));
        assertEquals("0.99,1.00,1,0,0.00,2", distribution.histogram().getLast());
    }
}

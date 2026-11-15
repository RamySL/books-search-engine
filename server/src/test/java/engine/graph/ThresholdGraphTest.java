package engine.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Graphe de seuil sur D1 à D3 : d(D1, D2) = 15/16, d(D1, D3) = 0,75, d(D2, D3) = 10/13. */
class ThresholdGraphTest {

    static final double[][] MATRIX = {
            {0, 15.0 / 16, 0.75},
            {15.0 / 16, 0, 10.0 / 13},
            {0.75, 10.0 / 13, 0}};

    @Test
    void theta08GivesThePathD1D3D2() {
        ThresholdGraph graph = ThresholdGraph.of(MATRIX, 0.8);
        assertEquals(3, graph.size());
        assertEquals(2, graph.edgeCount());
        assertArrayEquals(new int[] {2}, graph.neighbors(0));
        assertArrayEquals(new int[] {2}, graph.neighbors(1));
        assertArrayEquals(new int[] {0, 1}, graph.neighbors(2));
        assertArrayEquals(new double[] {0.75, 10.0 / 13}, graph.distances(2));
        assertArrayEquals(new double[] {0.75}, graph.distances(0));
    }

    @Test
    void theta05GivesNoEdge() {
        ThresholdGraph graph = ThresholdGraph.of(MATRIX, 0.5);
        assertEquals(0, graph.edgeCount());
        assertEquals(0, graph.degree(2));
    }

    @Test
    void theta1GivesTheTriangle() {
        ThresholdGraph graph = ThresholdGraph.of(MATRIX, 1);
        assertEquals(3, graph.edgeCount());
        assertArrayEquals(new int[] {1, 2}, graph.neighbors(0));
    }

    @Test
    void distanceEqualToThetaIsNotAnEdge() {
        ThresholdGraph graph = ThresholdGraph.of(MATRIX, 0.75);
        assertEquals(0, graph.edgeCount());
    }
}

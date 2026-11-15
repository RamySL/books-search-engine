package engine.graph;

import java.util.Arrays;

/** Graphes de test, donnés par leurs arêtes. */
final class TestGraphs {

    /** Le graphe A à H : A–B, A–C, A–D, B–C, C–D, C–E, D–E, E–F, F–G, F–H, G–H. */
    static final ThresholdGraph A_TO_H = of(8, 0.5,
            new int[] {0, 1}, new int[] {0, 2}, new int[] {0, 3}, new int[] {1, 2}, new int[] {2, 3},
            new int[] {2, 4}, new int[] {3, 4}, new int[] {4, 5}, new int[] {5, 6}, new int[] {5, 7},
            new int[] {6, 7});

    private TestGraphs() {
    }

    /** Arêtes {u, v}, toutes à la même distance de Jaccard. */
    static ThresholdGraph of(int n, double distance, int[]... edges) {
        double[] distances = new double[edges.length];
        Arrays.fill(distances, distance);
        return weighted(n, edges, distances);
    }

    /**
     * Arêtes {u, v} et leurs distances, toutes inférieures à 1 : les autres
     * paires sont à distance 1, et le seuil θ = 1 ne garde que les arêtes.
     */
    static ThresholdGraph weighted(int n, int[][] edges, double[] distances) {
        double[][] matrix = new double[n][n];
        for (int u = 0; u < n; u++) {
            Arrays.fill(matrix[u], 1);
            matrix[u][u] = 0;
        }
        for (int e = 0; e < edges.length; e++) {
            matrix[edges[e][0]][edges[e][1]] = distances[e];
            matrix[edges[e][1]][edges[e][0]] = distances[e];
        }
        return ThresholdGraph.of(matrix, 1);
    }
}

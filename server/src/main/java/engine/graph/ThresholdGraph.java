package engine.graph;

/**
 * Graphe de seuil sur les livres : deux livres sont voisins si leur distance
 * de Jaccard est strictement inférieure au seuil θ. Le graphe n'est pas
 * orienté : chaque arête figure dans la liste d'adjacence de ses deux
 * extrémités. Les sommets sont les indices des livres, de 0 à n − 1.
 */
final class ThresholdGraph {

    /** Voisins de chaque sommet, par indice croissant. */
    private final int[][] neighbors;

    /** Distance de chaque arête, dans l'ordre des voisins. */
    private final double[][] distances;

    private ThresholdGraph(int[][] neighbors, double[][] distances) {
        this.neighbors = neighbors;
        this.distances = distances;
    }

    /** @param matrix matrice des distances entre les livres */
    static ThresholdGraph of(double[][] matrix, double theta) {
        int n = matrix.length;
        int[][] neighbors = new int[n][];
        double[][] distances = new double[n][];
        for (int u = 0; u < n; u++) {
            int degree = 0;
            for (int v = 0; v < n; v++) {
                if (v != u && matrix[u][v] < theta) {
                    degree++;
                }
            }
            neighbors[u] = new int[degree];
            distances[u] = new double[degree];
            int k = 0;
            for (int v = 0; v < n; v++) {
                if (v != u && matrix[u][v] < theta) {
                    neighbors[u][k] = v;
                    distances[u][k++] = matrix[u][v];
                }
            }
        }
        return new ThresholdGraph(neighbors, distances);
    }

    /** @return le nombre de sommets */
    int size() {
        return neighbors.length;
    }

    int degree(int u) {
        return neighbors[u].length;
    }

    int[] neighbors(int u) {
        return neighbors[u];
    }

    double[] distances(int u) {
        return distances[u];
    }

    /** @return le nombre d'arêtes : la moitié de la somme des degrés */
    long edgeCount() {
        long degrees = 0;
        for (int[] list : neighbors) {
            degrees += list.length;
        }
        return degrees / 2;
    }
}

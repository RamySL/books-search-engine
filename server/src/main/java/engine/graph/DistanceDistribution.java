package engine.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Distances de toutes les paires de livres, triées. Elles donnent, pour tout
 * seuil θ, le nombre d'arêtes du graphe de seuil : de quoi choisir θ et tracer
 * l'histogramme des distances.
 */
final class DistanceDistribution {

    /** Pas des seuils proposés et largeur des tranches de l'histogramme : 1/100. */
    static final int STEPS = 100;

    private final double[] sorted;
    private final int books;

    private DistanceDistribution(double[] sorted, int books) {
        this.sorted = sorted;
        this.books = books;
    }

    /** @param matrix matrice des distances entre les livres */
    static DistanceDistribution of(double[][] matrix) {
        int n = matrix.length;
        double[] pairs = new double[Math.toIntExact((long) n * (n - 1) / 2)];
        int p = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                pairs[p++] = matrix[i][j];
            }
        }
        Arrays.parallelSort(pairs);
        return new DistanceDistribution(pairs, n);
    }

    /** @return le nombre de paires à distance strictement inférieure à θ : les arêtes du graphe de seuil */
    long edges(double theta) {
        // recherche dichotomique de la première distance qui atteint θ
        int low = 0;
        int high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] < theta) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** @return le degré moyen 2m/n du graphe de seuil θ */
    double meanDegree(double theta) {
        return 2.0 * edges(theta) / books;
    }

    /** @return le plus petit seuil au centième qui atteint ce degré moyen ; 1 si aucun ne l'atteint */
    double thetaForMeanDegree(double target) {
        for (int k = 1; k <= STEPS; k++) {
            double theta = (double) k / STEPS;
            if (meanDegree(theta) >= target) {
                return theta;
            }
        }
        return 1;
    }

    /**
     * Histogramme au format CSV, une ligne par tranche [from, to) de largeur
     * 1/100 ; la dernière est fermée, pour compter aussi les paires sans mot
     * commun (d = 1). Les colonnes edges et mean_degree décrivent le graphe
     * de seuil θ = to.
     */
    List<String> histogram() {
        List<String> lines = new ArrayList<>(List.of("from,to,pairs,edges,mean_degree"));
        long previous = 0;
        for (int k = 1; k <= STEPS; k++) {
            double to = (double) k / STEPS;
            long edges = edges(to);
            long upTo = k == STEPS ? sorted.length : edges;
            lines.add(String.format(Locale.ROOT, "%.2f,%.2f,%d,%d,%.2f",
                    (k - 1.0) / STEPS, to, upTo - previous, edges, 2.0 * edges / books));
            previous = upTo;
        }
        return lines;
    }
}

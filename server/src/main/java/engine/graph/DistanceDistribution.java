package engine.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Distances de toutes les paires de livres, triées, et distance de chaque
 * livre à son plus proche voisin, triée elle aussi. Elles donnent, pour tout
 * seuil θ, le nombre d'arêtes et de livres isolés du graphe de seuil : de quoi
 * choisir θ et tracer l'histogramme des distances.
 */
final class DistanceDistribution {

    /** Pas des seuils proposés et largeur des tranches de l'histogramme : 1/100. */
    static final int STEPS = 100;

    private final double[] sorted;

    /** Distance de chaque livre au plus proche autre livre ; +∞ pour un livre seul. */
    private final double[] nearest;

    private DistanceDistribution(double[] sorted, double[] nearest) {
        this.sorted = sorted;
        this.nearest = nearest;
    }

    /** @param matrix matrice des distances entre les livres */
    static DistanceDistribution of(double[][] matrix) {
        int n = matrix.length;
        double[] pairs = new double[Math.toIntExact((long) n * (n - 1) / 2)];
        double[] nearest = new double[n];
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        int p = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                pairs[p++] = matrix[i][j];
                nearest[i] = Math.min(nearest[i], matrix[i][j]);
                nearest[j] = Math.min(nearest[j], matrix[i][j]);
            }
        }
        Arrays.parallelSort(pairs);
        Arrays.sort(nearest);
        return new DistanceDistribution(pairs, nearest);
    }

    /** @return le nombre de paires à distance strictement inférieure à θ : les arêtes du graphe de seuil */
    long edges(double theta) {
        return below(sorted, theta);
    }

    /** @return le nombre de livres sans voisin dans le graphe de seuil : leur plus proche voisin est à θ ou plus */
    int isolated(double theta) {
        return nearest.length - below(nearest, theta);
    }

    /** Recherche dichotomique de la première valeur qui atteint θ, dans un tableau trié. */
    private static int below(double[] values, double theta) {
        int low = 0;
        int high = values.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values[middle] < theta) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** @return le degré moyen 2m/n du graphe de seuil θ */
    double meanDegree(double theta) {
        return 2.0 * edges(theta) / nearest.length;
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
     * commun (d = 1). Les colonnes edges, mean_degree et isolated décrivent le
     * graphe de seuil θ = to.
     */
    List<String> histogram() {
        List<String> lines = new ArrayList<>(List.of("from,to,pairs,edges,mean_degree,isolated"));
        long previous = 0;
        for (int k = 1; k <= STEPS; k++) {
            double to = (double) k / STEPS;
            long edges = edges(to);
            long upTo = k == STEPS ? sorted.length : edges;
            lines.add(String.format(Locale.ROOT, "%.2f,%.2f,%d,%d,%.2f,%d",
                    (k - 1.0) / STEPS, to, upTo - previous, edges, meanDegree(to), isolated(to)));
            previous = upTo;
        }
        return lines;
    }
}

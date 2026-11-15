package engine.graph;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Distance de Jaccard pondérée entre les tables de deux livres :
 * d = Σ (max − min) / Σ max, où min et max portent sur les nombres
 * d'occurrences d'un mot dans les deux livres, et les sommes sur l'union des
 * deux vocabulaires (un mot absent compte 0 occurrence). d vaut 0 pour deux
 * tables identiques, 1 pour deux tables sans mot commun.
 */
final class Jaccard {

    private Jaccard() {
    }

    /**
     * Les termes étant triés, une fusion des deux tables donne Σ min en
     * O(|V₁| + |V₂|). Σ max s'en déduit sans second parcours : comme
     * max(a, b) = a + b − min(a, b), Σ max = |D₁| + |D₂| − Σ min.
     */
    static double distance(WeightedTable a, WeightedTable b) {
        int[] termsA = a.terms();
        int[] termsB = b.terms();
        long min = 0;
        int i = 0;
        int j = 0;
        while (i < termsA.length && j < termsB.length) {
            if (termsA[i] < termsB[j]) {
                i++;
            } else if (termsA[i] > termsB[j]) {
                j++;
            } else {
                min += Math.min(a.frequencies()[i++], b.frequencies()[j++]);
            }
        }
        long max = a.total() + b.total() - min;
        // deux tables vides sont identiques
        return max == 0 ? 0 : (double) (max - min) / max;
    }

    /**
     * Matrice des distances entre les n livres, symétrique et de diagonale
     * nulle. Les n(n − 1)/2 paires sont calculées en parallèle, ligne par ligne.
     */
    static double[][] matrix(List<WeightedTable> tables) {
        int n = tables.size();
        double[][] distances = new double[n][n];
        IntStream.range(0, n).parallel().forEach(i -> {
            for (int j = i + 1; j < n; j++) {
                distances[i][j] = distances[j][i] = distance(tables.get(i), tables.get(j));
            }
        });
        return distances;
    }
}

package engine.graph;

import java.util.Arrays;

/**
 * PageRank pondéré par la similarité s = 1 − d. Depuis un livre u, la marche
 * aléatoire suit l'arête vers v avec la probabilité s(u, v) / Σ_w s(u, w) ;
 * avec la probabilité 1 − α, elle saute plutôt vers un livre quelconque. Un
 * livre isolé n'a pas d'arête à suivre : sa masse est redistribuée
 * uniformément. Le score d'un livre est la part du temps que la marche passe
 * sur lui.
 * <p>
 * Sans les poids, PageRank sur un graphe non orienté est presque
 * proportionnel au degré ; les poids y ajoutent la proximité des voisins.
 */
final class PageRank {

    /** Probabilité de suivre une arête plutôt que de sauter. */
    static final double ALPHA = 0.85;

    /** Arrêt quand la variation des scores, en norme L1, passe sous ce seuil. */
    private static final double TOLERANCE = 1e-12;

    static final int MAX_ITERATIONS = 200;

    private PageRank() {
    }

    /**
     * @param scores     score de chaque sommet ; leur somme vaut 1
     * @param iterations nombre d'itérations faites
     */
    record Result(double[] scores, int iterations) {
    }

    /** Itération de la puissance, depuis la loi uniforme : O(n + m) par itération. */
    static Result of(ThresholdGraph graph) {
        int n = graph.size();
        // similarité totale des arêtes de chaque sommet : Σ_w s(u, w)
        double[] total = new double[n];
        for (int u = 0; u < n; u++) {
            for (double distance : graph.distances(u)) {
                total[u] += 1 - distance;
            }
        }
        double[] rank = new double[n];
        Arrays.fill(rank, 1.0 / n);
        double[] next = new double[n];
        int iterations = 0;
        double change = Double.POSITIVE_INFINITY;
        while (change >= TOLERANCE && iterations < MAX_ITERATIONS) {
            double isolated = 0;
            for (int u = 0; u < n; u++) {
                if (graph.degree(u) == 0) {
                    isolated += rank[u];
                }
            }
            // le saut, plus la masse des sommets isolés, répartis sur tous
            Arrays.fill(next, ((1 - ALPHA) + ALPHA * isolated) / n);
            for (int u = 0; u < n; u++) {
                int[] neighbors = graph.neighbors(u);
                double[] distances = graph.distances(u);
                for (int k = 0; k < neighbors.length; k++) {
                    next[neighbors[k]] += ALPHA * rank[u] * (1 - distances[k]) / total[u];
                }
            }
            change = 0;
            for (int v = 0; v < n; v++) {
                change += Math.abs(next[v] - rank[v]);
            }
            double[] previous = rank;
            rank = next;
            next = previous;
            iterations++;
        }
        return new Result(rank, iterations);
    }
}

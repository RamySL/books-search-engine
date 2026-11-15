package engine.graph;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Centralité de proximité : l'inverse de la distance moyenne d'un livre aux
 * autres, la distance étant le nombre d'arêtes d'un plus court chemin.
 * <p>
 * Le graphe de seuil n'est pas toujours connexe : on applique la correction de
 * Wasserman et Faust, C(v) = ((r − 1)/(n − 1)) · ((r − 1)/Σ d(v, u)), où r est
 * le nombre de sommets atteignables depuis v, v compris ; C(v) = 0 si v est
 * isolé. Sur un graphe connexe (r = n), c'est (n − 1)/Σ d(v, u). La correction
 * pénalise les petites composantes : sans elle, deux livres reliés seulement
 * entre eux vaudraient 1, le maximum.
 */
final class Closeness {

    private Closeness() {
    }

    /** Un parcours en largeur par sommet, en parallèle : O(n(n + m)). */
    static double[] of(ThresholdGraph graph) {
        double[] closeness = new double[graph.size()];
        IntStream.range(0, graph.size()).parallel().forEach(v -> closeness[v] = of(graph, v));
        return closeness;
    }

    private static double of(ThresholdGraph graph, int v) {
        int n = graph.size();
        int[] distance = new int[n];
        Arrays.fill(distance, -1);
        int[] queue = new int[n];
        distance[v] = 0;
        queue[0] = v;
        int head = 0;
        int tail = 1;
        long sum = 0;
        while (head < tail) {
            int u = queue[head++];
            for (int w : graph.neighbors(u)) {
                if (distance[w] < 0) {
                    distance[w] = distance[u] + 1;
                    sum += distance[w];
                    queue[tail++] = w;
                }
            }
        }
        // les sommets atteints sont ceux passés par la file
        long reached = tail;
        if (reached == 1) {
            return 0;
        }
        // produits entiers, puis une seule division : un seul arrondi
        return (double) ((reached - 1) * (reached - 1)) / ((n - 1) * sum);
    }
}

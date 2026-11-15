package engine.graph;

import java.util.Arrays;

/**
 * Table pondérée d'un livre, telle que l'index la stocke : ses termes par
 * numéro croissant, le nombre d'occurrences de chacun, et leur somme |D|.
 */
record WeightedTable(int[] terms, int[] frequencies, long total) {

    WeightedTable(int[] terms, int[] frequencies) {
        this(terms, frequencies, Arrays.stream(frequencies).asLongStream().sum());
    }
}

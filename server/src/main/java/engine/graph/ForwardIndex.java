package engine.graph;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Index direct lu dans la table occurrence : les livres de l'index par numéro
 * croissant, et la table pondérée de chacun.
 */
record ForwardIndex(int[] ids, List<WeightedTable> tables) {

    /** Lignes lues par aller-retour avec la base : l'index entier ne tient pas d'un bloc. */
    private static final int FETCH_SIZE = 50_000;

    /**
     * Lecture en deux passes sur occurrence : la taille de chaque table, puis
     * son contenu. La seconde suit la clé primaire (terme, livre) : les termes
     * de chaque livre arrivent par numéro croissant, sans tri.
     */
    static ForwardIndex read(JdbcClient jdbc, TransactionTemplate transaction) {
        return transaction.execute(status -> {
            List<int[]> sizes = jdbc.sql("SELECT book_id, count(*) FROM occurrence GROUP BY book_id ORDER BY book_id")
                    .query((rs, row) -> new int[] {rs.getInt(1), rs.getInt(2)})
                    .list();
            if (sizes.isEmpty()) {
                throw new IllegalStateException("Index vide : lancer d'abord l'étape index");
            }
            int n = sizes.size();
            int[] ids = new int[n];
            int[][] terms = new int[n][];
            int[][] frequencies = new int[n][];
            for (int b = 0; b < n; b++) {
                ids[b] = sizes.get(b)[0];
                terms[b] = new int[sizes.get(b)[1]];
                frequencies[b] = new int[sizes.get(b)[1]];
            }
            int[] filled = new int[n];
            // dans une transaction, le pilote lit par paquets de FETCH_SIZE lignes
            jdbc.sql("SELECT term_id, book_id, frequency FROM occurrence ORDER BY term_id, book_id")
                    .withFetchSize(FETCH_SIZE)
                    .query(rs -> {
                        int b = Arrays.binarySearch(ids, rs.getInt(2));
                        terms[b][filled[b]] = rs.getInt(1);
                        frequencies[b][filled[b]++] = rs.getInt(3);
                    });
            List<WeightedTable> tables = IntStream.range(0, n)
                    .mapToObj(b -> new WeightedTable(terms[b], frequencies[b]))
                    .toList();
            return new ForwardIndex(ids, tables);
        });
    }

    /** @return la table pondérée du livre, qui doit être dans l'index */
    WeightedTable table(int id) {
        return tables.get(Arrays.binarySearch(ids, id));
    }

    /** @return le nombre de lignes de l'index : un couple (terme, livre) par ligne */
    long rows() {
        return tables.stream().mapToLong(table -> table.terms().length).sum();
    }
}

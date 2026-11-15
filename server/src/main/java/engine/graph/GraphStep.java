package engine.graph;

import engine.MeasuresProperties;
import engine.OfflineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

/**
 * Étape graph : distances de Jaccard entre tous les livres de l'index, graphe
 * de seuil, puis centralités closeness et PageRank, chargés dans neighbor et
 * book (degree, closeness, pagerank).
 * <ul>
 *   <li>Au dossier des mesures : l'histogramme des distances (distances.csv),
 *       écrit avant tout changement en base, puis les centralités
 *       (centralities.csv).</li>
 *   <li>Option {@code --theta=θ} : le seuil. Sans elle, le plus petit seuil au
 *       centième qui donne un degré moyen d'au moins 20.</li>
 * </ul>
 */
@Component
class GraphStep implements OfflineStep {

    /** Degré moyen visé sans --theta : assez de voisins pour les suggestions, sans noyer le graphe. */
    private static final int TARGET_MEAN_DEGREE = 20;

    /** Lignes lues par aller-retour avec la base : l'index entier ne tient pas d'un bloc. */
    private static final int FETCH_SIZE = 50_000;

    private static final Logger log = LoggerFactory.getLogger(GraphStep.class);

    private final MeasuresProperties measures;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    GraphStep(MeasuresProperties measures, JdbcClient jdbc, TransactionTemplate transaction) {
        this.measures = measures;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /** Les livres de l'index par numéro croissant, et leurs tables pondérées. */
    private record Index(int[] ids, List<WeightedTable> tables) {
    }

    @Override
    public String name() {
        return "graph";
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        OptionalDouble option = theta(args);
        long start = System.nanoTime();
        Index index = read();
        int n = index.ids().length;
        long rows = index.tables().stream().mapToLong(table -> table.terms().length).sum();
        log.info("Index direct : {} livres, {} lignes, lus en {} s", n, rows, seconds(start));

        start = System.nanoTime();
        double[][] matrix = Jaccard.matrix(index.tables());
        log.info("Jaccard : {} paires en {} s", (long) n * (n - 1) / 2, seconds(start));

        DistanceDistribution distribution = DistanceDistribution.of(matrix);
        Path histogram = measures.dir().resolve("distances.csv");
        Files.createDirectories(histogram.toAbsolutePath().getParent());
        Files.write(histogram, distribution.histogram());
        log.info("Histogramme des distances écrit dans {}", histogram);

        double theta = option.orElseGet(() -> distribution.thetaForMeanDegree(TARGET_MEAN_DEGREE));
        ThresholdGraph graph = ThresholdGraph.of(matrix, theta);
        long isolated = IntStream.range(0, n).filter(u -> graph.degree(u) == 0).count();
        int maxDegree = IntStream.range(0, n).map(graph::degree).max().orElse(0);
        log.info("θ = {} ({}) : {} arêtes, degré moyen {}, degré maximal {}, {} livres isolés",
                theta, option.isPresent() ? "--theta" : "degré moyen visé " + TARGET_MEAN_DEGREE,
                graph.edgeCount(), String.format(Locale.ROOT, "%.1f", 2.0 * graph.edgeCount() / n),
                maxDegree, isolated);

        start = System.nanoTime();
        double[] closeness = Closeness.of(graph);
        log.info("Closeness en {} s", seconds(start));
        start = System.nanoTime();
        PageRank.Result pagerank = PageRank.of(graph);
        log.info("PageRank : {} itérations en {} s", pagerank.iterations(), seconds(start));

        start = System.nanoTime();
        save(index.ids(), graph, closeness, pagerank.scores());
        log.info("Graphe et centralités chargés en base en {} s", seconds(start));

        Path centralities = measures.dir().resolve("centralities.csv");
        Files.write(centralities, centralities());
        log.info("Centralités écrites dans {}", centralities);
    }

    /** --theta=θ, entre 0 exclu et 1 inclus. */
    static OptionalDouble theta(ApplicationArguments args) {
        List<String> values = args.getOptionValues("theta");
        if (values == null || values.isEmpty()) {
            return OptionalDouble.empty();
        }
        String value = values.getLast();
        try {
            double theta = Double.parseDouble(value);
            if (!(theta > 0 && theta <= 1)) {
                throw new NumberFormatException();
            }
            return OptionalDouble.of(theta);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--theta attend un seuil entre 0 exclu et 1 inclus : " + value);
        }
    }

    /**
     * L'index direct, en deux passes sur occurrence : la taille de chaque
     * table, puis son contenu. La seconde suit la clé primaire (terme, livre) :
     * les termes de chaque livre arrivent par numéro croissant, sans tri.
     */
    private Index read() {
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
            return new Index(ids, tables);
        });
    }

    /**
     * Remplace le graphe en une transaction : chaque arête dans les deux sens,
     * le degré de chaque livre, compté dans neighbor, puis ses centralités.
     */
    private void save(int[] ids, ThresholdGraph graph, double[] closeness, double[] pagerank) {
        int arcs = Math.toIntExact(2 * graph.edgeCount());
        int[] from = new int[arcs];
        int[] to = new int[arcs];
        double[] distances = new double[arcs];
        int a = 0;
        for (int u = 0; u < graph.size(); u++) {
            for (int k = 0; k < graph.degree(u); k++) {
                from[a] = ids[u];
                to[a] = ids[graph.neighbors(u)[k]];
                distances[a++] = graph.distances(u)[k];
            }
        }
        transaction.executeWithoutResult(status -> {
            jdbc.sql("TRUNCATE neighbor").update();
            jdbc.sql("""
                            INSERT INTO neighbor (book_a, book_b, distance)
                            SELECT * FROM unnest(?::integer[], ?::integer[], ?::double precision[])""")
                    .param(from)
                    .param(to)
                    .param(distances)
                    .update();
            // un livre absent de l'index garde ses centralités à 0
            jdbc.sql("""
                    UPDATE book SET degree = (SELECT count(*) FROM neighbor WHERE book_a = book.id),
                        closeness = 0, pagerank = 0""").update();
            jdbc.sql("""
                            UPDATE book SET closeness = s.closeness, pagerank = s.pagerank
                            FROM unnest(?::integer[], ?::double precision[], ?::double precision[])
                                AS s(id, closeness, pagerank)
                            WHERE book.id = s.id""")
                    .param(ids)
                    .param(closeness)
                    .param(pagerank)
                    .update();
        });
    }

    /** Les centralités telles qu'en base, au format CSV, par numéro de livre. */
    private List<String> centralities() {
        List<String> lines = new ArrayList<>(List.of("id,title,degree,closeness,pagerank"));
        jdbc.sql("SELECT id, title, degree, closeness, pagerank FROM book ORDER BY id")
                .query(rs -> {
                    lines.add(rs.getInt("id") + "," + quote(rs.getString("title")) + "," + rs.getInt("degree")
                            + "," + decimal(rs.getDouble("closeness")) + "," + decimal(rs.getDouble("pagerank")));
                });
        return lines;
    }

    /** Champ CSV entre guillemets, guillemets internes doublés : des titres contiennent des virgules. */
    static String quote(String field) {
        return '"' + field.replace("\"", "\"\"") + '"';
    }

    /** Écriture décimale complète, sans exposant : 0.000075 plutôt que 7.5E-5. */
    static String decimal(double value) {
        return BigDecimal.valueOf(value).toPlainString();
    }

    private static String seconds(long start) {
        return String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1e9);
    }
}

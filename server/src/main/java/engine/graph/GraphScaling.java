package engine.graph;

import engine.MeasuresProperties;
import engine.OfflineStep;
import engine.Scaling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

/**
 * Étape graph-scaling : temps de la matrice de Jaccard, de closeness et de
 * PageRank selon le nombre de livres, sur les échantillons emboîtés de
 * {@link Scaling}, les mêmes que pour index-scaling. Tout se passe en
 * mémoire : la base n'est que lue.
 * <p>
 * Tous les échantillons utilisent le seuil de l'étape graph : --theta, sinon
 * celui qui donne un degré moyen de 20 sur le corpus entier. Le graphe d'un
 * échantillon est donc le sous-graphe induit du graphe complet.
 * <p>
 * Écrit graph-scaling.csv au dossier des mesures : books, pairs,
 * jaccard_seconds, edges, isolated, closeness_seconds, pagerank_seconds,
 * pagerank_iterations. Option {@code --runs=N} : mesures par taille (3 par défaut).
 */
@Component
class GraphScaling implements OfflineStep {

    /** Calculs à vide des centralités sur le graphe complet, avant toute mesure. */
    private static final int WARMUP_ROUNDS = 20;

    private static final Logger log = LoggerFactory.getLogger(GraphScaling.class);

    private final MeasuresProperties measures;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    GraphScaling(MeasuresProperties measures, JdbcClient jdbc, TransactionTemplate transaction) {
        this.measures = measures;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public String name() {
        return "graph-scaling";
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        int runs = Scaling.runs(args);
        OptionalDouble option = GraphStep.theta(args);
        ForwardIndex index = ForwardIndex.read(jdbc, transaction);
        List<Integer> order = Scaling.shuffle(Arrays.stream(index.ids()).boxed().toList());

        // tour à vide sur le corpus entier, qui donne aussi le seuil du graphe complet
        long start = System.nanoTime();
        double[][] full = Jaccard.matrix(index.tables());
        double theta = option.orElseGet(
                () -> DistanceDistribution.of(full).thetaForMeanDegree(GraphStep.TARGET_MEAN_DEGREE));
        ThresholdGraph whole = ThresholdGraph.of(full, theta);
        // les centralités ne durent que quelques millisecondes : le JIT doit les avoir compilées avant la mesure
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            Closeness.of(whole);
            PageRank.of(whole);
        }
        log.info("Tour à vide : {} livres en {} s, θ = {}", order.size(),
                Scaling.seconds((System.nanoTime() - start) / 1e9), theta);

        List<String> lines = new ArrayList<>(List.of("books,pairs,jaccard_seconds,edges,isolated,"
                + "closeness_seconds,pagerank_seconds,pagerank_iterations"));
        for (int n : Scaling.sizes(order.size())) {
            List<WeightedTable> sample = order.subList(0, n).stream().map(index::table).toList();
            double[][][] matrix = new double[1][][];
            double jaccard = Scaling.medianSeconds(runs, () -> matrix[0] = Jaccard.matrix(sample));
            ThresholdGraph graph = ThresholdGraph.of(matrix[0], theta);
            long isolated = IntStream.range(0, n).filter(u -> graph.degree(u) == 0).count();
            double closeness = Scaling.medianSeconds(runs, () -> Closeness.of(graph));
            PageRank.Result[] pagerank = new PageRank.Result[1];
            double pagerankSeconds = Scaling.medianSeconds(runs, () -> pagerank[0] = PageRank.of(graph));
            lines.add(n + "," + (long) n * (n - 1) / 2 + "," + Scaling.seconds(jaccard) + "," + graph.edgeCount()
                    + "," + isolated + "," + Scaling.seconds(closeness) + "," + Scaling.seconds(pagerankSeconds)
                    + "," + pagerank[0].iterations());
            log.info("{} livres : Jaccard {} s, {} arêtes, {} isolés, closeness {} s, PageRank {} s",
                    n, Scaling.seconds(jaccard), graph.edgeCount(), isolated,
                    Scaling.seconds(closeness), Scaling.seconds(pagerankSeconds));
        }
        Files.createDirectories(measures.dir());
        Path file = measures.dir().resolve("graph-scaling.csv");
        Files.write(file, lines);
        log.info("Mesures écrites dans {}", file);
    }
}

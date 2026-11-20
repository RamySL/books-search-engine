package engine.graph;

import engine.MeasuresProperties;
import engine.TestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape graph sur la base de test. L'index du jeu d'essai donne
 * d(D1, D3) = d(D2, D3) = 7/9 et d(D1, D2) = 1 (aucun mot commun) : avec
 * θ = 0,8, l'étape doit redonner le chemin D1 - D3 - D2 du jeu d'essai et ses
 * centralités (closeness 2/3, 2/3, 1 ; PageRank 19/74, 19/74, 18/37).
 */
@SpringBootTest
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class GraphStepTest {

    private static final String NEIGHBORS =
            "SELECT book_a || ' ' || book_b || ' ' || distance FROM neighbor ORDER BY book_a, book_b";
    private static final String DEGREES = "SELECT id || ' ' || degree FROM book ORDER BY id";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @TempDir
    private Path measures;

    private void graph(String... options) throws IOException {
        String[] args = Stream.concat(Stream.of("graph"), Stream.of(options)).toArray(String[]::new);
        new GraphStep(new MeasuresProperties(measures), jdbc, transaction).run(new DefaultApplicationArguments(args));
    }

    private List<String> rows(String sql) {
        return jdbc.sql(sql).query(String.class).list();
    }

    /** Closeness et PageRank de D1, D2, D3. */
    private List<double[]> centralities() {
        return jdbc.sql("SELECT closeness, pagerank FROM book ORDER BY id")
                .query((rs, row) -> new double[] {rs.getDouble(1), rs.getDouble(2)})
                .list();
    }

    /** PageRank est itératif : il s'arrête à 1e-12 près du point fixe. */
    private static void assertCentralities(List<double[]> expected, List<double[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (int b = 0; b < expected.size(); b++) {
            assertArrayEquals(expected.get(b), actual.get(b), 1e-10, "livre D" + (b + 1));
        }
    }

    private void clearGraph() {
        jdbc.sql("TRUNCATE neighbor").update();
        jdbc.sql("UPDATE book SET degree = 0, closeness = 0, pagerank = 0").update();
    }

    @Test
    void rebuildsTheTestDataGraph() throws IOException {
        List<String> neighbors = rows(NEIGHBORS);
        List<String> degrees = rows(DEGREES);
        List<double[]> centralities = centralities();
        clearGraph();

        graph("--theta=0.8");

        assertEquals(neighbors, rows(NEIGHBORS));
        assertEquals(degrees, rows(DEGREES));
        assertCentralities(centralities, centralities());
    }

    @Test
    void replacesThePreviousGraph() throws IOException {
        List<String> neighbors = rows(NEIGHBORS);
        graph("--theta=0.8");
        assertEquals(neighbors, rows(NEIGHBORS));

        // 7/9 n'est pas inférieur à 0,7 : plus aucune arête
        graph("--theta=0.7");
        assertEquals(List.of(), rows(NEIGHBORS));
        assertEquals(List.of("1 0", "2 0", "3 0"), rows(DEGREES));
        // trois livres isolés : closeness nulle, PageRank uniforme
        double[] isolated = {0, 1.0 / 3};
        assertCentralities(List.of(isolated, isolated, isolated), centralities());
    }

    @Test
    void withoutThetaTheTargetMeanDegreeIsOutOfReachOnThreeBooks() throws IOException {
        // θ = 1 : toute paire qui partage un mot est reliée, D1 - D2 ne l'est pas
        List<String> neighbors = rows(NEIGHBORS);
        clearGraph();

        graph();

        assertEquals(neighbors, rows(NEIGHBORS));
    }

    @Test
    void writesTheHistogramOfDistances() throws IOException {
        graph("--theta=0.8");

        List<String> lines = Files.readAllLines(measures.resolve("distances.csv"));
        assertEquals(1 + DistanceDistribution.STEPS, lines.size());
        assertEquals("0.77,0.78,2,2,1.33,0", lines.get(78));
        assertEquals("0.99,1.00,1,2,1.33,0", lines.getLast());
    }

    @Test
    void writesTheCentralities() throws IOException {
        graph("--theta=0.8");

        List<String> lines = Files.readAllLines(measures.resolve("centralities.csv"));
        assertEquals(4, lines.size());
        assertEquals("id,title,word_count,degree,closeness,pagerank", lines.getFirst());
        assertTrue(lines.get(1).startsWith("1,\"D1 : Sargon of Akkad\",11,1,0.6666666666666666,0.25675675675"));
        assertTrue(lines.get(3).startsWith("3,\"D3 : Akkad and Babylon\",9,2,1.0,0.48648648648"));
    }

    @Test
    void writesEachEdgeOnce() throws IOException {
        graph("--theta=0.8");

        // le chemin D1 - D3 - D2, à 7/9 : une ligne par arête, alors que neighbor en a deux
        List<String> lines = Files.readAllLines(measures.resolve("edges.csv"));
        assertEquals(3, lines.size());
        assertEquals("book_a,book_b,distance", lines.getFirst());
        assertTrue(lines.get(1).startsWith("1,3,0.7777777"));
        assertTrue(lines.get(2).startsWith("2,3,0.7777777"));
    }

    @Test
    void csvFields() {
        assertEquals("\"Moby-Dick; or, The Whale\"", GraphStep.quote("Moby-Dick; or, The Whale"));
        assertEquals("\"The \"\"Mayflower\"\"\"", GraphStep.quote("The \"Mayflower\""));
        assertEquals("0.000075", GraphStep.decimal(7.5e-5));
        assertEquals("0.25", GraphStep.decimal(0.25));
    }

    @Test
    void failsOnAnEmptyIndexWithoutTouchingTheGraph() {
        List<String> neighbors = rows(NEIGHBORS);
        jdbc.sql("TRUNCATE occurrence").update();

        assertThrows(IllegalStateException.class, () -> graph("--theta=0.8"));
        assertEquals(neighbors, rows(NEIGHBORS));
    }

    @Test
    void thetaOption() {
        assertEquals(OptionalDouble.of(0.8), GraphStep.theta(new DefaultApplicationArguments("graph", "--theta=0.8")));
        assertEquals(OptionalDouble.of(1), GraphStep.theta(new DefaultApplicationArguments("graph", "--theta=1")));
        assertEquals(OptionalDouble.empty(), GraphStep.theta(new DefaultApplicationArguments("graph")));
        for (String value : List.of("0", "-0.5", "1.5", "0,8", "NaN")) {
            assertThrows(IllegalArgumentException.class,
                    () -> GraphStep.theta(new DefaultApplicationArguments("graph", "--theta=" + value)));
        }
    }
}

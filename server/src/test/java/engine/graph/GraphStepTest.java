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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Étape graph sur la base de test. L'index du jeu d'essai donne
 * d(D1, D3) = d(D2, D3) = 7/9 et d(D1, D2) = 1 (aucun mot commun) : avec
 * θ = 0,8, l'étape doit redonner le chemin D1 - D3 - D2 du jeu d'essai.
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

    private void clearGraph() {
        jdbc.sql("TRUNCATE neighbor").update();
        jdbc.sql("UPDATE book SET degree = 0").update();
    }

    @Test
    void rebuildsTheTestDataGraph() throws IOException {
        List<String> neighbors = rows(NEIGHBORS);
        List<String> degrees = rows(DEGREES);
        clearGraph();

        graph("--theta=0.8");

        assertEquals(neighbors, rows(NEIGHBORS));
        assertEquals(degrees, rows(DEGREES));
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
        assertEquals("0.77,0.78,2,2,1.33", lines.get(78));
        assertEquals("0.99,1.00,1,2,1.33", lines.getLast());
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

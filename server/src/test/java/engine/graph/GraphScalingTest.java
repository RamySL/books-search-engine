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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape graph-scaling sur l'index du jeu d'essai : 3 livres, donc une seule
 * taille d'échantillon. Le graphe à θ = 0,8 est le chemin D1 - D3 - D2.
 */
@SpringBootTest
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class GraphScalingTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @TempDir
    private Path measures;

    private String[] measure(String... options) throws IOException {
        String[] args = Stream.concat(Stream.of("graph-scaling", "--runs=1"), Stream.of(options)).toArray(String[]::new);
        new GraphScaling(new MeasuresProperties(measures), jdbc, transaction).run(new DefaultApplicationArguments(args));
        List<String> lines = Files.readAllLines(measures.resolve("graph-scaling.csv"));
        assertEquals(2, lines.size());
        assertEquals("books,pairs,jaccard_seconds,edges,isolated,closeness_seconds,pagerank_seconds,pagerank_iterations",
                lines.getFirst());
        return lines.get(1).split(",");
    }

    @Test
    void writesOneLinePerSampleSize() throws IOException {
        String[] fields = measure("--theta=0.8");

        assertEquals("3", fields[0]);
        assertEquals("3", fields[1]);
        assertEquals("2", fields[3]);
        assertEquals("0", fields[4]);
        assertTrue(Integer.parseInt(fields[7]) > 0);
    }

    @Test
    void usesTheThresholdOfTheGraphStep() throws IOException {
        // θ = 0,7 : aucune arête ; sans --theta, θ = 1 comme pour l'étape graph : D1 - D2 reste sans arête
        assertEquals("0", measure("--theta=0.7")[3]);
        assertEquals("3", measure("--theta=0.7")[4]);
        assertEquals("2", measure()[3]);
    }

    @Test
    void failsOnAnEmptyIndex() {
        jdbc.sql("TRUNCATE occurrence").update();

        assertThrows(IllegalStateException.class, () -> measure("--theta=0.8"));
    }
}

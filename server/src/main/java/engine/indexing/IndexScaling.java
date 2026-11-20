package engine.indexing;

import engine.MeasuresProperties;
import engine.OfflineStep;
import engine.Scaling;
import engine.corpus.CorpusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Étape index-scaling : temps de construction de l'index inversé et taille de
 * l'index selon le nombre de livres, sur les échantillons emboîtés de
 * {@link Scaling}. Tout se passe en mémoire : la base n'est que lue.
 * <p>
 * Écrit index-scaling.csv au dossier des mesures : books, words (mots des
 * textes, mots vides compris), terms (taille du vocabulaire), rows (lignes de
 * occurrence) et seconds (temps médian). Le temps comprend la lecture des
 * textes, déjà dans le cache disque après le tour à vide.
 * Option {@code --runs=N} : mesures par taille (3 par défaut).
 */
@Component
class IndexScaling implements OfflineStep {

    private static final Logger log = LoggerFactory.getLogger(IndexScaling.class);

    private final CorpusProperties corpus;
    private final MeasuresProperties measures;
    private final JdbcClient jdbc;

    IndexScaling(CorpusProperties corpus, MeasuresProperties measures, JdbcClient jdbc) {
        this.corpus = corpus;
        this.measures = measures;
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "index-scaling";
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        int runs = Scaling.runs(args);
        Map<Integer, Integer> words = new HashMap<>();
        jdbc.sql("SELECT id, word_count FROM book").query(rs -> {
            words.put(rs.getInt("id"), rs.getInt("word_count"));
        });
        if (words.isEmpty()) {
            throw new IllegalStateException("Table book vide : lancer d'abord l'étape corpus");
        }
        List<Integer> order = Scaling.shuffle(new ArrayList<>(words.keySet()));
        Path booksDir = corpus.booksDir();
        IntFunction<String> texts = id -> IndexStep.text(booksDir, id);

        // tour à vide sur le corpus entier : compilation par le JIT et cache disque
        long start = System.nanoTime();
        InvertedIndex.build(order, texts);
        log.info("Tour à vide : {} livres indexés en {} s", order.size(),
                Scaling.seconds((System.nanoTime() - start) / 1e9));

        List<String> lines = new ArrayList<>(List.of("books,words,terms,rows,seconds"));
        for (int n : Scaling.sizes(order.size())) {
            List<Integer> sample = order.subList(0, n);
            InvertedIndex[] index = new InvertedIndex[1];
            double seconds = Scaling.medianSeconds(runs, () -> index[0] = InvertedIndex.build(sample, texts));
            long sampleWords = sample.stream().mapToLong(words::get).sum();
            lines.add(n + "," + sampleWords + "," + index[0].termCount() + "," + index[0].rowCount()
                    + "," + Scaling.seconds(seconds));
            log.info("{} livres : {} termes, {} lignes, {} s", n, index[0].termCount(), index[0].rowCount(),
                    Scaling.seconds(seconds));
        }
        Files.createDirectories(measures.dir());
        Path file = measures.dir().resolve("index-scaling.csv");
        Files.write(file, lines);
        log.info("Mesures écrites dans {}", file);
    }
}

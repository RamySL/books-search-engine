package engine.measures;

import egrep.Lignes;
import egrep.Recherche;
import egrep.automate.Dfa;
import engine.MeasuresProperties;
import engine.OfflineStep;
import engine.api.BookSummary;
import engine.api.Sort;
import engine.corpus.CorpusProperties;
import engine.indexing.Tokenizer;
import engine.recommendation.Recommendations;
import engine.search.IndexRegexSearch;
import engine.search.KeywordSearch;
import engine.search.Ranking;
import engine.search.TextRegexSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Étape bench : temps de calcul des recherches en ligne, sans passer par HTTP
 * (le test de charge, {@link LoadTest}, mesure l'API de bout en bout). Écrit
 * dans le dossier des mesures :
 * <ul>
 *   <li>queries.csv : temps de chaque requête d'une liste fixe, pour les trois
 *       recherches (occurrences, classement de la première page et suggestions) ;</li>
 *   <li>text-scaling.csv : RegEx sur le texte intégral selon le nombre de livres
 *       parcourus, avec KMP et avec l'automate pour un même mot ;</li>
 *   <li>kmp-dfa.csv : KMP contre automate pour des mots de longueurs variées,
 *       sur des lignes déjà en mémoire, pour isoler l'algorithme de la lecture du disque ;</li>
 *   <li>vocabulary-scaling.csv : RegEx sur l'index selon le nombre de termes
 *       du vocabulaire parcourus par l'automate ;</li>
 *   <li>automata.csv : taille du DFA minimal et temps de construction.</li>
 * </ul>
 * Chaque mesure est précédée de trois tours à vide (chauffe de la JVM et du
 * cache disque), puis répétée {@code --runs} fois (5 par défaut) ; on garde la médiane.
 */
@Component
class SearchBenchmark implements OfflineStep {

    private static final Logger log = LoggerFactory.getLogger(SearchBenchmark.class);

    /** Requêtes de queries.csv : des mots rares et fréquents, des RegEx étroites et larges. */
    private static final List<String> KEYWORDS = List.of(
            "sargon", "babylon", "whale", "napoleon", "philosophy", "love",
            "king queen", "ship captain sea", "war peace", "the");
    private static final List<String> INDEX_REGEXES = List.of(
            "sarg(o|a)n", "bab(y|i)lon", "whal(e|es)", "c.ty", "(k|q)ing",
            "love.*", ".*tion", "a.*z", ".*");
    private static final List<String> TEXT_REGEXES = List.of(
            "sargon", "babylon", "whale", "sarg(o|a)n", "of.the.k(i|a)ng", "(k|q)ing", "whal(e|es)");

    /** Mots cherchés avec KMP puis avec l'automate dans text-scaling.csv, et une vraie RegEx. */
    private static final List<String> SCALING_WORDS = List.of("whale", "babylon");
    private static final String SCALING_REGEX = "of.the.k(i|a)ng";
    private static final int[] BOOK_COUNTS = {25, 50, 100, 250, 500, 1000, 1500, 2000};
    /** Mots de kmp-dfa.csv, du plus court au plus long, et taille du texte gardé en mémoire. */
    private static final List<String> KMP_WORDS = List.of("the", "whale", "babylon", "nantucket", "circumnavigation");
    private static final int MEMORY_MEGABYTES = 50;
    private static final int[] TERM_COUNTS = {10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000};
    private static final List<String> VOCABULARY_REGEXES = List.of("sarg(o|a)n", ".*tion", "(a|e)+b.*");

    private final MeasuresProperties measures;
    private final JdbcClient jdbc;
    private final KeywordSearch keywords;
    private final IndexRegexSearch indexRegex;
    private final TextRegexSearch textRegex;
    private final Ranking ranking;
    private final Recommendations recommendations;
    private final Path booksDir;
    private int runs;

    SearchBenchmark(MeasuresProperties measures, JdbcClient jdbc, KeywordSearch keywords,
                    IndexRegexSearch indexRegex, TextRegexSearch textRegex, Ranking ranking,
                    Recommendations recommendations, CorpusProperties corpus) {
        this.measures = measures;
        this.jdbc = jdbc;
        this.keywords = keywords;
        this.indexRegex = indexRegex;
        this.textRegex = textRegex;
        this.ranking = ranking;
        this.recommendations = recommendations;
        this.booksDir = corpus.booksDir();
    }

    @Override
    public String name() {
        return "bench";
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        runs = args.containsOption("runs") ? Integer.parseInt(args.getOptionValues("runs").getFirst()) : 5;
        Files.createDirectories(measures.dir());
        List<Integer> ids = jdbc.sql("SELECT id FROM book ORDER BY id").query(Integer.class).list();
        log.info("Banc d'essai : {} livres, {} cœurs, {} mesures par point",
                ids.size(), Runtime.getRuntime().availableProcessors(), runs);
        queries();
        textScaling(ids);
        kmpVersusAutomaton(ids);
        vocabularyScaling();
        automata();
    }

    /** queries.csv : type, requête, livres trouvés, temps médian, minimal et maximal. */
    private void queries() throws IOException {
        List<String> lines = new ArrayList<>(List.of("type,query,books,median_ms,min_ms,max_ms"));
        for (String q : KEYWORDS) {
            List<String> terms = Tokenizer.words(q).stream().distinct().toList();
            lines.add(query("keyword", q, () -> keywords.search(terms).occurrences()));
        }
        for (String q : INDEX_REGEXES) {
            lines.add(query("index", q, () -> indexRegex.search(q).occurrences()));
        }
        for (String q : TEXT_REGEXES) {
            lines.add(query("text", q, () -> textRegex.occurrences(q)));
        }
        write("queries.csv", lines);
    }

    /** Une requête complète : occurrences, première page classée, suggestions. */
    private String query(String type, String q, Supplier<Map<Integer, Long>> search) {
        int[] found = new int[1];
        double[] times = time(() -> {
            Map<Integer, Long> occurrences = search.get();
            List<BookSummary> page = ranking.page(occurrences, Sort.OCCURRENCES, 0, 20);
            List<Integer> seeds = page.stream().limit(Recommendations.SEEDS).map(BookSummary::id).toList();
            recommendations.suggestions(seeds, page.stream().map(BookSummary::id).toList());
            found[0] = occurrences.size();
        });
        log.info("{} « {} » : {} livres, {} ms", type, q, found[0], format(median(times)));
        return String.join(",", type, '"' + q + '"', String.valueOf(found[0]),
                format(median(times)), format(Arrays.stream(times).min().orElseThrow()),
                format(Arrays.stream(times).max().orElseThrow()));
    }

    /**
     * text-scaling.csv : pour les n premiers livres (par numéro), le temps de
     * la RegEx sur le texte, en parallèle sur les livres comme dans l'API. Pour
     * un mot, on mesure KMP et l'automate ({@code Recherche.pour(mot, false)}).
     * Sur un petit corpus de développement, on va jusqu'à 2000 livres en
     * reprenant les mêmes textes en boucle (colonne distinct_books).
     */
    private void textScaling(List<Integer> ids) throws IOException {
        List<String> lines = new ArrayList<>(List.of("books,distinct_books,megabytes,query,method,median_ms"));
        int[] sizes = ids.size() >= BOOK_COUNTS[BOOK_COUNTS.length - 1] ? sizes(BOOK_COUNTS, ids.size()) : BOOK_COUNTS;
        for (int n : sizes) {
            List<Integer> subset = IntStream.range(0, n).mapToObj(i -> ids.get(i % ids.size())).toList();
            String prefix = n + "," + Math.min(n, ids.size());
            String megabytes = format(megabytes(subset));
            for (String word : SCALING_WORDS) {
                lines.add(String.join(",", prefix, megabytes, word, "kmp",
                        format(median(scan(Recherche.pour(word, true), subset)))));
                lines.add(String.join(",", prefix, megabytes, word, "dfa",
                        format(median(scan(Recherche.pour(word, false), subset)))));
            }
            lines.add(String.join(",", prefix, megabytes, '"' + SCALING_REGEX + '"', "dfa",
                    format(median(scan(Recherche.pour(SCALING_REGEX, true), subset)))));
            log.info("Texte intégral : {} livres ({} Mo) mesurés", n, megabytes);
        }
        write("text-scaling.csv", lines);
    }

    /**
     * kmp-dfa.csv : temps d'un seul cœur pour compter les occurrences d'un mot
     * dans des lignes déjà lues et mises en minuscules (environ {@link #MEMORY_MEGABYTES} Mo),
     * avec KMP et avec l'automate. Sans la lecture du disque ni le découpage en
     * lignes, communs aux deux, on compare les algorithmes eux-mêmes.
     */
    private void kmpVersusAutomaton(List<Integer> ids) throws IOException {
        List<String> lines = new ArrayList<>();
        long chars = 0;
        for (int id : ids) {
            Path file = booksDir.resolve(id + ".txt");
            if (!Files.exists(file)) {
                continue;
            }
            for (String line : Lignes.lire(file)) {
                lines.add(line.toLowerCase(Locale.ROOT));
                chars += line.length();
            }
            if (chars > MEMORY_MEGABYTES * 1_000_000L) {
                break;
            }
        }
        List<String> csv = new ArrayList<>(List.of("word,length,method,states,occurrences,median_ms,megabytes_per_s"));
        for (String word : KMP_WORDS) {
            for (boolean kmp : new boolean[]{true, false}) {
                Recherche recherche = Recherche.pour(word, kmp);
                long[] found = new long[1];
                double[] times = time(() -> {
                    long count = 0;
                    for (String line : lines) {
                        if (recherche.contient(line)) {
                            count += recherche.occurrences(line).size();
                        }
                    }
                    found[0] = count;
                });
                String states = recherche instanceof Recherche.ParAutomate automate
                        ? String.valueOf(automate.facteur().nbEtats()) : "";
                double median = median(times);
                csv.add(String.join(",", word, String.valueOf(word.length()), kmp ? "kmp" : "dfa", states,
                        String.valueOf(found[0]), format(median), format(chars / 1e6 / (median / 1000))));
            }
        }
        log.info("KMP contre automate : {} lignes, {} millions de caractères", lines.size(), chars / 1_000_000);
        write("kmp-dfa.csv", csv);
    }

    private double[] scan(Recherche recherche, List<Integer> subset) {
        return time(() -> subset.parallelStream().mapToLong(id -> textRegex.count(recherche, id)).sum());
    }

    private double megabytes(List<Integer> subset) throws IOException {
        long bytes = 0;
        for (int id : subset) {
            Path file = booksDir.resolve(id + ".txt");
            if (Files.exists(file)) {
                bytes += Files.size(file);
            }
        }
        return bytes / 1e6;
    }

    /**
     * vocabulary-scaling.csv : temps du parcours du vocabulaire par le DFA
     * (seule étape de la RegEx sur l'index qui dépend de sa taille), pour les
     * n premiers termes. Au-delà de la taille réelle du vocabulaire, on le
     * répète pour simuler un vocabulaire plus grand.
     */
    private void vocabularyScaling() throws IOException {
        String[] vocabulary = jdbc.sql("SELECT word FROM term ORDER BY book_count DESC, word")
                .query(String.class).list().toArray(String[]::new);
        List<String> lines = new ArrayList<>(List.of("terms,query,median_ms"));
        // le premier passage, jeté, sert de chauffe : sans lui, les petites tailles,
        // mesurées en premier, paient la compilation par le JIT
        for (int pass = 0; pass < 2; pass++) {
            for (int n : TERM_COUNTS) {
                for (String regex : VOCABULARY_REGEXES) {
                    Dfa dfa = IndexRegexSearch.compile(regex);
                    double[] times = time(() -> IntStream.range(0, n).parallel()
                            .filter(i -> dfa.accepte(vocabulary[i % vocabulary.length]))
                            .count());
                    if (pass == 1) {
                        lines.add(String.join(",", String.valueOf(n), '"' + regex + '"', format(median(times))));
                    }
                }
            }
        }
        log.info("Vocabulaire : {} termes", vocabulary.length);
        write("vocabulary-scaling.csv", lines);
    }

    /** automata.csv : nombre d'états du DFA minimal de R et temps de la chaîne de compilation. */
    private void automata() throws IOException {
        List<String> lines = new ArrayList<>(List.of("query,states,compile_ms"));
        for (String regex : INDEX_REGEXES) {
            int states = IndexRegexSearch.compile(regex).nbEtats();
            double[] times = time(() -> IndexRegexSearch.compile(regex));
            lines.add(String.join(",", '"' + regex + '"', String.valueOf(states), format(median(times))));
        }
        write("automata.csv", lines);
    }

    /** Trois tours à vide (compilation par le JIT), puis {@link #runs} mesures, en millisecondes. */
    private double[] time(Runnable task) {
        for (int i = 0; i < 3; i++) {
            task.run();
        }
        double[] times = new double[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            task.run();
            times[i] = (System.nanoTime() - start) / 1e6;
        }
        return times;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    /** Les tailles de la liste inférieures au total, puis le total lui-même. */
    private static int[] sizes(int[] candidates, int total) {
        return IntStream.concat(Arrays.stream(candidates).filter(n -> n < total), IntStream.of(total)).toArray();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private void write(String name, List<String> lines) throws IOException {
        Path file = measures.dir().resolve(name);
        Files.write(file, lines);
        log.info("Mesures écrites dans {}", file.toAbsolutePath());
    }
}

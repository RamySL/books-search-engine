package engine.corpus;

import engine.OfflineStep;
import engine.indexing.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Étape corpus : choisit les livres, les télécharge, retire l'en-tête et le
 * pied de page de Gutenberg, garde ceux d'au moins 10 000 mots et remplit la
 * table book.
 * <ul>
 *   <li>Les livres sont ceux de la liste versionnée si elle existe : le corpus
 *       est alors reproductible. Sinon, ce sont les textes anglais du
 *       catalogue, par numéro croissant, après deux livres imposés ; un
 *       lancement complet écrit alors la liste.</li>
 *   <li>Après une interruption, l'étape reprend où elle en était : elle ne
 *       retélécharge ni les textes déjà écrits, ni les livres déjà rejetés.</li>
 *   <li>Option {@code --limit=N} : s'arrêter à N livres retenus, pour un petit
 *       corpus de développement.</li>
 * </ul>
 */
@Component
class CorpusStep implements OfflineStep {

    /** Taille minimale d'un livre, en mots. */
    static final int MIN_WORDS = 10_000;

    /** Nombre de livres visé : une marge au-dessus du minimum de 1664. */
    private static final int TARGET = 2_000;

    /** Deux livres sur Sargon d'Akkad, l'exemple type de recherche. */
    private static final List<Integer> REQUIRED = List.of(49345, 56667);

    private static final Logger log = LoggerFactory.getLogger(CorpusStep.class);

    private final CorpusProperties properties;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    CorpusStep(CorpusProperties properties, JdbcClient jdbc, TransactionTemplate transaction) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /** Un livre retenu et sa taille en mots. */
    private record Book(Catalog.Entry entry, int words) {
    }

    @Override
    public String name() {
        return "corpus";
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        OptionalInt limit = limit(args);
        long start = System.nanoTime();
        Files.createDirectories(properties.booksDir());
        log.info("Corpus : textes dans {}, miroir {}", properties.booksDir().toAbsolutePath(), properties.mirror());
        try (Mirror mirror = new Mirror(properties.mirror())) {
            Catalog catalog = catalog(mirror);
            boolean listed = Files.exists(properties.idsFile());
            List<Integer> candidates = listed ? readIds(properties.idsFile()) : candidates(catalog.ids());
            List<Book> books = collect(candidates, limit.orElse(TARGET), catalog, mirror);
            save(books);
            // une liste existante fait référence, et un petit corpus ne la remplace pas
            if (!listed && limit.isEmpty()) {
                writeIds(books);
            }
            log.info("Corpus : {} livres dans la table book, en {} min", books.size(),
                    Duration.ofNanos(System.nanoTime() - start).toMinutes());
        }
    }

    /** --limit=N : nombre de livres à retenir. */
    static OptionalInt limit(ApplicationArguments args) {
        List<String> values = args.getOptionValues("limit");
        if (values == null || values.isEmpty()) {
            return OptionalInt.empty();
        }
        String value = values.getLast();
        try {
            int limit = Integer.parseInt(value);
            if (limit <= 0) {
                throw new NumberFormatException();
            }
            return OptionalInt.of(limit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--limit attend un nombre de livres positif : " + value);
        }
    }

    /** Les livres imposés, puis ceux du catalogue par numéro croissant. */
    static List<Integer> candidates(List<Integer> catalogIds) {
        return Stream.concat(REQUIRED.stream(), catalogIds.stream()).distinct().toList();
    }

    /** Un numéro par ligne ; les lignes vides et celles qui commencent par # sont ignorées. */
    static List<Integer> readIds(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(Integer::valueOf)
                    .toList();
        }
    }

    /** Le catalogue n'est téléchargé qu'une fois. */
    private Catalog catalog(Mirror mirror) throws IOException, InterruptedException {
        Path file = properties.catalogFile();
        if (!Files.exists(file)) {
            log.info("Téléchargement du catalogue de Gutenberg");
            mirror.catalog(file);
        }
        return Catalog.read(file);
    }

    /** Parcourt les candidats jusqu'à retenir {@code target} livres. */
    private List<Book> collect(List<Integer> candidates, int target, Catalog catalog, Mirror mirror)
            throws IOException, InterruptedException {
        Set<Integer> rejected = Files.exists(properties.rejectedFile())
                ? new HashSet<>(readIds(properties.rejectedFile()))
                : Set.of();
        List<Book> books = new ArrayList<>();
        for (int id : candidates) {
            if (books.size() == target) {
                break;
            }
            Optional<Catalog.Entry> entry = catalog.get(id);
            if (entry.isEmpty()) {
                log.warn("Livre {} absent du catalogue des textes anglais : ignoré", id);
                continue;
            }
            if (rejected.contains(id)) {
                continue;
            }
            Path file = properties.booksDir().resolve(id + ".txt");
            boolean present = Files.exists(file);
            Optional<String> text = present ? Optional.of(Files.readString(file)) : download(id, mirror);
            if (text.isEmpty()) {
                continue;
            }
            int words = Tokenizer.countWords(text.get());
            if (words < MIN_WORDS) {
                reject(id, words + " mots");
                Files.deleteIfExists(file);
                continue;
            }
            if (!present) {
                write(file, text.get());
            }
            books.add(new Book(entry.get(), words));
            if (books.size() % 100 == 0) {
                log.info("{} livres retenus sur {}", books.size(), target);
            }
        }
        if (books.size() < target) {
            log.warn("Seulement {} livres retenus sur {} : plus de candidats", books.size(), target);
        }
        return books;
    }

    /** @return le texte nettoyé, vide si le livre est rejeté */
    private Optional<String> download(int id, Mirror mirror) throws IOException, InterruptedException {
        Optional<String> raw = mirror.book(id);
        if (raw.isEmpty()) {
            reject(id, "absent du miroir");
            return Optional.empty();
        }
        Optional<String> body = GutenbergText.body(raw.get());
        if (body.isEmpty()) {
            reject(id, "marqueurs de début ou de fin introuvables");
        }
        return body;
    }

    private void reject(int id, String reason) throws IOException {
        log.info("Livre {} rejeté : {}", id, reason);
        Files.writeString(properties.rejectedFile(), id + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Écrit puis renomme : une interruption ne laisse pas de texte tronqué. */
    private static void write(Path file, String text) throws IOException {
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(part, text);
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Remplace le contenu de book ; CASCADE vide aussi l'index et le graphe de l'ancien corpus. */
    private void save(List<Book> books) {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("TRUNCATE book CASCADE").update();
            for (Book book : books) {
                jdbc.sql("INSERT INTO book (id, title, author, language, word_count) VALUES (?, ?, ?, ?, ?)")
                        .param(book.entry().id())
                        .param(book.entry().title())
                        .param(book.entry().author())
                        .param(Catalog.LANGUAGE)
                        .param(book.words())
                        .update();
            }
        });
    }

    private void writeIds(List<Book> books) throws IOException {
        Path file = properties.idsFile();
        Files.createDirectories(file.toAbsolutePath().getParent());
        List<String> lines = new ArrayList<>(List.of(
                "# Livres du corpus : numéros Project Gutenberg, un par ligne.",
                "# Tant que ce fichier existe, l'étape corpus télécharge exactement ces livres."));
        books.stream().map(book -> book.entry().id()).sorted().map(String::valueOf).forEach(lines::add);
        Files.write(file, lines);
        log.info("Liste des {} livres écrite dans {}", books.size(), file);
    }
}

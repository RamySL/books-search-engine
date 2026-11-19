package engine.search;

import egrep.Lignes;
import egrep.Recherche;
import engine.api.Excerpt;
import engine.corpus.CorpusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Recherche par RegEx sur le texte intégral des livres, ligne par ligne, avec
 * le projet egrep : KMP si la RegEx n'est qu'une suite de lettres, sinon le
 * DFA minimal de {@code .*R}. Contrairement à la RegEx sur l'index, une
 * occurrence peut couvrir plusieurs mots ({@code king.of}) ou un morceau de
 * mot ({@code sarg}).
 * <p>
 * C'est beaucoup plus lent : chaque recherche relit tous les textes (plusieurs
 * centaines de Mo pour le corpus complet). Les livres sont parcourus en
 * parallèle, et un sémaphore limite le nombre de ces recherches en même temps
 * pour qu'elles n'occupent pas tous les cœurs au détriment des autres requêtes.
 * <p>
 * Comme l'index, la RegEx et les lignes sont passées en minuscules.
 */
@Component
public class TextRegexSearch {

    private static final Logger log = LoggerFactory.getLogger(TextRegexSearch.class);

    /** Recherches sur le texte intégral traitées en même temps. */
    static final int MAX_RUNNING = 2;

    /** Attente maximale d'une place libre, ensuite la requête est refusée. */
    private static final long WAIT_SECONDS = 30;

    /** Levée quand trop de recherches sur le texte intégral sont déjà en cours. */
    public static class BusyException extends RuntimeException {
        BusyException() {
            super("Trop de recherches sur le texte intégral en cours, réessayez dans un instant");
        }
    }

    private final JdbcClient jdbc;
    private final Path booksDir;
    private final Semaphore running = new Semaphore(MAX_RUNNING, true);

    TextRegexSearch(JdbcClient jdbc, CorpusProperties corpus) {
        this.jdbc = jdbc;
        this.booksDir = corpus.booksDir();
    }

    /**
     * @return nombre d'occurrences de la RegEx par livre, pour les livres qui
     *         en contiennent au moins une
     * @throws IllegalArgumentException si la RegEx est mal formée
     * @throws BusyException            si aucune place ne se libère à temps
     */
    public Map<Integer, Long> occurrences(String regex) {
        // compilée avant d'attendre : une RegEx mal formée échoue tout de suite
        Recherche recherche = compile(regex);
        List<Integer> ids = jdbc.sql("SELECT id FROM book").query(Integer.class).list();
        acquire();
        try {
            Map<Integer, Long> occurrences = new ConcurrentHashMap<>();
            ids.parallelStream().forEach(id -> {
                long count = count(recherche, id);
                if (count > 0) {
                    occurrences.put(id, count);
                }
            });
            return occurrences;
        } finally {
            running.release();
        }
    }

    /**
     * Lignes d'un livre qui contiennent la RegEx, pour la vue Livre.
     *
     * @param limit nombre maximal de lignes renvoyées, les premières du texte
     * @param words vrai pour surligner une recherche par mot-clé : seules les
     *              occurrences au début d'un mot comptent, et elles s'étendent
     *              jusqu'à la fin du mot (« prin » surligne princess, pas spring)
     * @throws IllegalArgumentException si la RegEx est mal formée
     */
    public List<Excerpt> excerpts(int bookId, String regex, int limit, boolean words) {
        Recherche recherche = compile(regex);
        List<Excerpt> excerpts = new ArrayList<>();
        List<String> lines = lines(bookId);
        for (int i = 0; i < lines.size() && excerpts.size() < limit; i++) {
            String line = lines.get(i);
            String lower = line.toLowerCase(Locale.ROOT);
            if (!recherche.contient(lower)) {
                continue;
            }
            List<int[]> occurrences = recherche.occurrences(lower);
            if (words) {
                occurrences = wholeWords(lower, occurrences);
            }
            if (!occurrences.isEmpty()) {
                // les positions sont celles de la ligne en minuscules : on garde la ligne
                // d'origine seulement si les deux ont la même longueur (presque toujours)
                String shown = lower.length() == line.length() ? line : lower;
                excerpts.add(new Excerpt(i + 1, shown, occurrences));
            }
        }
        return excerpts;
    }

    /** Occurrences qui commencent un mot, prolongées jusqu'à la fin de ce mot. */
    private static List<int[]> wholeWords(String line, List<int[]> occurrences) {
        List<int[]> kept = new ArrayList<>();
        for (int[] occurrence : occurrences) {
            int start = occurrence[0];
            if (start > 0 && Character.isLetter(line.charAt(start - 1))) {
                continue;
            }
            int end = occurrence[1];
            while (end < line.length() && Character.isLetter(line.charAt(end))) {
                end++;
            }
            kept.add(new int[]{start, end});
        }
        return kept;
    }

    /** KMP autorisé : c'est egrep qui choisit entre KMP et l'automate. */
    private static Recherche compile(String regex) {
        return Recherche.pour(regex.toLowerCase(Locale.ROOT), true);
    }

    private void acquire() {
        try {
            if (!running.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new BusyException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusyException();
        }
    }

    /**
     * Somme des occurrences sur toutes les lignes ; seules les lignes retenues
     * sont découpées. Public pour le banc d'essai, qui compare KMP et automate.
     */
    public long count(Recherche recherche, int bookId) {
        long count = 0;
        for (String line : lines(bookId)) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (recherche.contient(lower)) {
                count += recherche.occurrences(lower).size();
            }
        }
        return count;
    }

    /**
     * Lignes du texte, sans le '\r' final des fichiers en CRLF : sinon le
     * point de la RegEx pourrait le reconnaître et l'afficher. Un texte absent
     * (corpus incomplet sur cette machine) est traité comme vide.
     */
    private List<String> lines(int bookId) {
        Path file = booksDir.resolve(bookId + ".txt");
        try {
            List<String> lines = Lignes.lire(file);
            lines.replaceAll(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            return lines;
        } catch (FileNotFoundException e) {
            log.warn("Texte absent : {}", file);
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package engine.search;

import engine.api.BookSummary;
import engine.api.Sort;
import engine.books.BookRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Classement des livres trouvés, commun aux trois recherches (mot-clé, RegEx
 * sur l'index, RegEx sur le texte) : chacune fournit le nombre d'occurrences
 * par livre, le classement trie et découpe en pages.
 * <p>
 * Le tri se fait en base, en joignant les scores à la table book : seule la
 * page demandée remonte.
 */
@Component
public class Ranking {

    private final JdbcClient jdbc;

    Ranking(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param occurrences nombre d'occurrences par livre
     * @return les livres de la page, triés par ordre décroissant du critère ;
     *         à égalité, par occurrences, puis par id
     */
    public List<BookSummary> page(Map<Integer, Long> occurrences, Sort sort, long offset, int limit) {
        if (occurrences.isEmpty()) {
            return List.of();
        }
        Integer[] ids = new Integer[occurrences.size()];
        Long[] counts = new Long[occurrences.size()];
        int i = 0;
        for (Map.Entry<Integer, Long> entry : occurrences.entrySet()) {
            ids[i] = entry.getKey();
            counts[i] = entry.getValue();
            i++;
        }
        // la colonne vient de l'énumération, jamais de la requête : pas d'injection possible
        String criterion = switch (sort) {
            case OCCURRENCES -> "s.occurrences";
            case CLOSENESS -> "b.closeness";
            case PAGERANK -> "b.pagerank";
        };
        return jdbc.sql("""
                        SELECT b.id, b.title, b.author, b.closeness, b.pagerank, s.occurrences
                        FROM unnest(?::integer[], ?::bigint[]) AS s(book_id, occurrences)
                        JOIN book b ON b.id = s.book_id
                        ORDER BY %s DESC, s.occurrences DESC, b.id
                        LIMIT ? OFFSET ?""".formatted(criterion))
                .param(ids)
                .param(counts)
                .param(limit)
                .param(offset)
                .query((row, rowNum) -> BookRepository.summary(row, row.getLong("occurrences")))
                .list();
    }
}

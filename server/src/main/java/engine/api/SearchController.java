package engine.api;

import engine.indexing.Tokenizer;
import engine.recommendation.Recommendations;
import engine.search.IndexRegexSearch;
import engine.search.KeywordSearch;
import engine.search.Matches;
import engine.search.Ranking;
import engine.search.TextRegexSearch;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Recherches dans la bibliothèque. */
@RestController
@RequestMapping("/api/search")
class SearchController {

    private final KeywordSearch keywords;
    private final IndexRegexSearch indexRegex;
    private final TextRegexSearch textRegex;
    private final Ranking ranking;
    private final Recommendations recommendations;

    SearchController(KeywordSearch keywords, IndexRegexSearch indexRegex, TextRegexSearch textRegex,
                     Ranking ranking, Recommendations recommendations) {
        this.keywords = keywords;
        this.indexRegex = indexRegex;
        this.textRegex = textRegex;
        this.ranking = ranking;
        this.recommendations = recommendations;
    }

    /**
     * GET /api/search?q=&sort=&page= : les livres qui contiennent, pour
     * chaque mot de la requête, un terme qui commence par ce mot. La requête
     * passe par le même tokeniseur que les livres : une requête faite
     * seulement de mots vides ne trouve rien.
     */
    @GetMapping
    SearchResponse keyword(@RequestParam String q,
                           @RequestParam(defaultValue = "occurrences") Sort sort,
                           @RequestParam(defaultValue = "1") @Min(1) int page) {
        long start = System.nanoTime();
        Matches found = keywords.search(Tokenizer.words(q).stream().distinct().toList());
        return response(found.occurrences(), sort, page, found.terms(), found.truncated(), start);
    }

    /**
     * GET /api/search/regex?q=&scope=&sort=&page= : les livres qui contiennent
     * un mot reconnu par la RegEx (scope=index), ou une occurrence de la RegEx
     * dans leur texte (scope=text, lent). 400 si la RegEx est mal formée, 503
     * si trop de recherches sur le texte sont en cours.
     */
    @GetMapping("/regex")
    SearchResponse regex(@RequestParam String q,
                         @RequestParam(defaultValue = "index") Scope scope,
                         @RequestParam(defaultValue = "occurrences") Sort sort,
                         @RequestParam(defaultValue = "1") @Min(1) int page) {
        long start = System.nanoTime();
        try {
            if (scope == Scope.TEXT) {
                return response(textRegex.occurrences(q), sort, page, List.of(), false, start);
            }
            Matches found = indexRegex.search(q);
            return response(found.occurrences(), sort, page, found.terms(), found.truncated(), start);
        } catch (IllegalArgumentException e) {
            // message d'egrep : parenthèse sans partenaire, opérateur sans opérande…
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (TextRegexSearch.BusyException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    /**
     * Partie commune aux trois recherches : classement, page demandée et
     * suggestions. Les suggestions partent des 3 premiers résultats du tri
     * choisi, même quand on affiche une page suivante ; elles ne reprennent ni
     * ces 3 livres ni ceux de la page affichée.
     */
    private SearchResponse response(Map<Integer, Long> occurrences, Sort sort, int page,
                                    List<String> terms, boolean truncated, long start) {
        List<BookSummary> results = ranking.page(occurrences, sort, Pages.offset(page), Pages.SIZE);
        List<BookSummary> first = page == 1
                ? results.subList(0, Math.min(Recommendations.SEEDS, results.size()))
                : ranking.page(occurrences, sort, 0, Recommendations.SEEDS);
        List<Integer> seeds = first.stream().map(BookSummary::id).toList();
        Set<Integer> excluded = new HashSet<>(seeds);
        results.forEach(book -> excluded.add(book.id()));
        List<BookSummary> suggestions = recommendations.suggestions(seeds, excluded);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return new SearchResponse(occurrences.size(), Pages.next(page, occurrences.size()), results,
                suggestions, terms, truncated, durationMs);
    }
}

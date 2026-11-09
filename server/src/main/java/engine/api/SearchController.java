package engine.api;

import engine.indexing.Tokenizer;
import engine.search.IndexRegexSearch;
import engine.search.KeywordSearch;
import engine.search.Ranking;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Recherches dans la bibliothèque. */
@RestController
@RequestMapping("/api/search")
class SearchController {

    private final KeywordSearch keywords;
    private final IndexRegexSearch indexRegex;
    private final Ranking ranking;

    SearchController(KeywordSearch keywords, IndexRegexSearch indexRegex, Ranking ranking) {
        this.keywords = keywords;
        this.indexRegex = indexRegex;
        this.ranking = ranking;
    }

    /**
     * GET /api/search?q=&sort=&page= : les livres qui contiennent tous les
     * mots de la requête. La requête passe par le même tokeniseur que les
     * livres : une requête faite seulement de mots vides ne trouve rien.
     */
    @GetMapping
    SearchResponse keyword(@RequestParam String q,
                           @RequestParam(defaultValue = "occurrences") Sort sort,
                           @RequestParam(defaultValue = "1") @Min(1) int page) {
        long start = System.nanoTime();
        List<String> terms = Tokenizer.words(q).stream().distinct().toList();
        Map<Integer, Long> occurrences = keywords.occurrences(terms);
        List<BookSummary> results = ranking.page(occurrences, sort, Pages.offset(page), Pages.SIZE);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        // suggestions : à venir avec les recommandations
        return new SearchResponse(occurrences.size(), Pages.next(page, occurrences.size()), results,
                List.of(), terms, false, durationMs);
    }

    /**
     * GET /api/search/regex?q=&scope=&sort=&page= : les livres qui contiennent
     * un mot reconnu par la RegEx. 400 si la RegEx est mal formée.
     */
    @GetMapping("/regex")
    SearchResponse regex(@RequestParam String q,
                         @RequestParam(defaultValue = "index") Scope scope,
                         @RequestParam(defaultValue = "occurrences") Sort sort,
                         @RequestParam(defaultValue = "1") @Min(1) int page) {
        if (scope == Scope.TEXT) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "La recherche sur le texte intégral n'est pas encore disponible");
        }
        long start = System.nanoTime();
        IndexRegexSearch.Result found;
        try {
            found = indexRegex.search(q);
        } catch (IllegalArgumentException e) {
            // message d'egrep : parenthèse sans partenaire, opérateur sans opérande…
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        Map<Integer, Long> occurrences = found.occurrences();
        List<BookSummary> results = ranking.page(occurrences, sort, Pages.offset(page), Pages.SIZE);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return new SearchResponse(occurrences.size(), Pages.next(page, occurrences.size()), results,
                List.of(), found.terms(), found.truncated(), durationMs);
    }
}

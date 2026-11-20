package engine.api;

import engine.books.BookRepository;
import engine.recommendation.Recommendations;
import engine.search.TextRegexSearch;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Livres par identifiant : le catalogue, une collection, et la
 * fiche d'un livre, un élément, avec ses voisins et ses extraits.
 * <p>
 */
@RestController
@RequestMapping("/api/books")
class BookController {

    private final BookRepository books;
    private final Recommendations recommendations;
    private final TextRegexSearch textRegex;

    BookController(BookRepository books, Recommendations recommendations, TextRegexSearch textRegex) {
        this.books = books;
        this.recommendations = recommendations;
        this.textRegex = textRegex;
    }

    /** GET /api/books?page=1 */
    @GetMapping
    Page<BookSummary> catalog(@RequestParam(defaultValue = "1") @Min(1) int page) {
        long count = books.count();
        return new Page<>(count, Pages.next(page, count), books.catalog(Pages.offset(page), Pages.SIZE));
    }

    /** GET /api/books/{id} : 404 si le livre n'existe pas */
    @GetMapping("/{id}")
    Book book(@PathVariable int id) {
        return books.find(id).orElseThrow(() -> notFound(id));
    }

    /** GET /api/books/{id}/neighbors : voisins dans le graphe de Jaccard, du plus proche au plus éloigné */
    @GetMapping("/{id}/neighbors")
    List<Neighbor> neighbors(@PathVariable int id) {
        requireBook(id);
        return recommendations.neighbors(id);
    }

    /**
     * GET /api/books/{id}/excerpts?regex=&limit=50&words=false : lignes du
     * texte qui contiennent la RegEx, pour les surligner ; avec words=true,
     * seulement en mots entiers, comme la recherche par mot-clé. 400 si la
     * RegEx est mal formée.
     */
    @GetMapping("/{id}/excerpts")
    List<Excerpt> excerpts(@PathVariable int id,
                           @RequestParam String regex,
                           @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
                           @RequestParam(defaultValue = "false") boolean words) {
        requireBook(id);
        try {
            return textRegex.excerpts(id, regex, limit, words);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void requireBook(int id) {
        if (!books.exists(id)) {
            throw notFound(id);
        }
    }

    private static ResponseStatusException notFound(int id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun livre n'a l'identifiant " + id);
    }
}

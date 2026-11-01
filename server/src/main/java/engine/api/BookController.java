package engine.api;

import engine.books.BookRepository;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Livres par identifiant (cours 3, §2) : le catalogue, une collection, et la
 * fiche d'un livre, un élément.
 * <p>
 * Les erreurs sont renvoyées au format Problem Details (RFC 9457), activé
 * dans application.yml : {@code {"status": 404, "detail": "...", ...}}.
 */
@RestController
@RequestMapping("/api/books")
class BookController {

    private final BookRepository books;

    BookController(BookRepository books) {
        this.books = books;
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
        return books.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun livre n'a l'identifiant " + id));
    }
}

package engine.api;

import engine.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forme JSON des réponses de /api/books, sur le jeu d'essai D1 à D3. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class BookControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void bookDetails() throws Exception {
        mvc.perform(get("/api/books/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("D3 : Akkad and Babylon"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.wordCount").value(9))
                .andExpect(jsonPath("$.degree").value(2))
                .andExpect(jsonPath("$.closeness").value(1.0))
                .andExpect(jsonPath("$.pagerank").value(18.0 / 37))
                .andExpect(jsonPath("$.cover").value("https://www.gutenberg.org/cache/epub/3/pg3.cover.medium.jpg"));
    }

    @Test
    void unknownAuthorIsNull() throws Exception {
        mvc.perform(get("/api/books/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value(nullValue()));
    }

    @Test
    void unknownBookGives404WithProblemDetails() throws Exception {
        mvc.perform(get("/api/books/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Aucun livre n'a l'identifiant 999"));
    }

    @Test
    void malformedIdGives400() throws Exception {
        mvc.perform(get("/api/books/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void catalogIsSortedByTitleAndFitsOnOnePage() throws Exception {
        mvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.next").value(nullValue()))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].id").value(1))
                .andExpect(jsonPath("$.results[2].id").value(3))
                .andExpect(jsonPath("$.results[0].occurrences").value(nullValue()));
    }

    @Test
    void pageAfterTheLastOneIsEmpty() throws Exception {
        mvc.perform(get("/api/books").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void pageZeroGives400() throws Exception {
        mvc.perform(get("/api/books").param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientRouteServesIndexHtml() throws Exception {
        mvc.perform(get("/books/1"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}

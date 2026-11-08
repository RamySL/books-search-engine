package engine.api;

import engine.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recherche par mot-clé, sur le jeu d'essai D1 à D3. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void oneWordSortedByOccurrences() throws Exception {
        mvc.perform(get("/api/search").param("q", "babylon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.next").value(nullValue()))
                .andExpect(jsonPath("$.results[*].id", contains(2, 3)))
                .andExpect(jsonPath("$.results[*].occurrences", contains(2, 1)))
                .andExpect(jsonPath("$.results[0].title").value("D2 : Hammurabi of Babylon"))
                .andExpect(jsonPath("$.terms", contains("babylon")))
                .andExpect(jsonPath("$.termsTruncated").value(false))
                .andExpect(jsonPath("$.suggestions", empty()))
                .andExpect(jsonPath("$.durationMs").isNumber());
    }

    @Test
    void severalWordsKeepBooksContainingAllOfThemAndSumFrequencies() throws Exception {
        // D1 : akkad 1 + empire 2 ; D3 : akkad 1 + empire 1
        mvc.perform(get("/api/search").param("q", "Akkad, empire!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.results[*].id", contains(1, 3)))
                .andExpect(jsonPath("$.results[*].occurrences", contains(3, 2)))
                .andExpect(jsonPath("$.terms", contains("akkad", "empire")));
    }

    @Test
    void noBookContainsBothWords() throws Exception {
        mvc.perform(get("/api/search").param("q", "sargon babylon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.results", empty()));
    }

    @Test
    void repeatedWordCountsOnce() throws Exception {
        mvc.perform(get("/api/search").param("q", "babylon BABYLON"))
                .andExpect(jsonPath("$.terms", contains("babylon")))
                .andExpect(jsonPath("$.results[*].occurrences", contains(2, 1)));
    }

    @Test
    void wordMissingFromTheIndexFindsNothing() throws Exception {
        mvc.perform(get("/api/search").param("q", "babylon nineveh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.terms", contains("babylon", "nineveh")));
    }

    @Test
    void stopWordsOnlyFindNothing() throws Exception {
        mvc.perform(get("/api/search").param("q", "the of"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.terms", empty()));
    }

    @Test
    void sortByCentralityThenByOccurrences() throws Exception {
        // akkad : D1 et D3, une occurrence chacun ; D3 est le centre du chemin D1 - D3 - D2
        mvc.perform(get("/api/search").param("q", "akkad").param("sort", "pagerank"))
                .andExpect(jsonPath("$.results[*].id", contains(3, 1)));
        mvc.perform(get("/api/search").param("q", "akkad").param("sort", "closeness"))
                .andExpect(jsonPath("$.results[*].id", contains(3, 1)));
        // à occurrences égales, l'ordre suit l'id
        mvc.perform(get("/api/search").param("q", "akkad"))
                .andExpect(jsonPath("$.results[*].id", contains(1, 3)));
    }

    @Test
    void pageAfterTheLastOneIsEmpty() throws Exception {
        mvc.perform(get("/api/search").param("q", "babylon").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.results", empty()));
    }

    @Test
    void invalidParametersGive400() throws Exception {
        mvc.perform(get("/api/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "babylon").param("sort", "title"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "babylon").param("page", "0"))
                .andExpect(status().isBadRequest());
    }
}

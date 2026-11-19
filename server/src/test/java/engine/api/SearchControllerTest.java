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
                .andExpect(jsonPath("$.terms", contains("babylon")));
    }

    @Test
    void eachWordIsAPrefix() throws Exception {
        // « sarg » commence sargon ; « ham wro » : hammurabi 2 + wrote 1 dans D2
        mvc.perform(get("/api/search").param("q", "sarg"))
                .andExpect(jsonPath("$.results[*].id", contains(1)))
                .andExpect(jsonPath("$.terms", contains("sargon")));
        mvc.perform(get("/api/search").param("q", "ham wro"))
                .andExpect(jsonPath("$.results[*].id", contains(2)))
                .andExpect(jsonPath("$.results[*].occurrences", contains(3)))
                .andExpect(jsonPath("$.terms", contains("hammurabi", "wrote")));
    }

    @Test
    void prefixFindsSeveralTermsAndSumsThem() throws Exception {
        // « fe » : fell seulement (founded ne commence pas par fe)
        mvc.perform(get("/api/search").param("q", "fe"))
                .andExpect(jsonPath("$.terms", contains("fell")));
        // « e » ne passe pas le tokeniseur, « em » : empire, dans D1 (2) et D3 (1)
        mvc.perform(get("/api/search").param("q", "em"))
                .andExpect(jsonPath("$.results[*].id", contains(1, 3)))
                .andExpect(jsonPath("$.results[*].occurrences", contains(2, 1)));
    }

    @Test
    void prefixMustStartTheTerm() throws Exception {
        // « abylon » est dans babylon, mais ne le commence pas
        mvc.perform(get("/api/search").param("q", "abylon"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.terms", empty()));
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
    void suggestionsAreNeighborsOfTheFirstResults() throws Exception {
        // sargon : D1 seul, son voisin D3 est suggéré
        mvc.perform(get("/api/search").param("q", "sargon"))
                .andExpect(jsonPath("$.suggestions[*].id", contains(3)))
                .andExpect(jsonPath("$.suggestions[0].occurrences").value(nullValue()));
        // babylon : D2 et D3 ; D1, voisin de D3, est le seul livre qui n'est pas affiché
        mvc.perform(get("/api/search").param("q", "babylon"))
                .andExpect(jsonPath("$.suggestions[*].id", contains(1)));
        // akkad : D1 et D3 ; D2 est voisin de D3
        mvc.perform(get("/api/search").param("q", "akkad"))
                .andExpect(jsonPath("$.suggestions[*].id", contains(2)));
    }

    @Test
    void suggestionsNeverRepeatTheFirstResults() throws Exception {
        // page 2 vide : les premiers résultats (D2, D3) restent exclus des suggestions
        mvc.perform(get("/api/search").param("q", "babylon").param("page", "2"))
                .andExpect(jsonPath("$.suggestions[*].id", contains(1)));
    }

    @Test
    void noResultNoSuggestion() throws Exception {
        mvc.perform(get("/api/search").param("q", "nineveh"))
                .andExpect(jsonPath("$.suggestions", empty()));
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

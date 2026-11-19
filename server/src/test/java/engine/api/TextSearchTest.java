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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RegEx sur le texte intégral et extraits, sur le jeu d'essai D1 à D3. Les
 * textes sont dans src/test/resources/jeu-essai/books, une phrase par ligne :
 * <pre>
 *   1.txt : Sargon of Akkad founded an empire. / The empire of Sargon fell.
 *   2.txt : Hammurabi of Babylon wrote laws.   / Babylon was the city of Hammurabi.
 *   3.txt : The empire of Akkad and the city of Babylon.
 * </pre>
 */
@SpringBootTest(properties = "corpus.data-dir=src/test/resources/jeu-essai")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class TextSearchTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void wordSearchedWithKmp() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "sargon").param("scope", "text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(1))
                .andExpect(jsonPath("$.results[0].occurrences").value(2))
                .andExpect(jsonPath("$.terms", empty()))
                .andExpect(jsonPath("$.suggestions[*].id", contains(3)));
    }

    @Test
    void partOfAWordIsFoundUnlikeTheIndex() throws Exception {
        // sur l'index, « sarg » ne reconnaît aucun terme
        mvc.perform(get("/api/search/regex").param("q", "SARG").param("scope", "text"))
                .andExpect(jsonPath("$.results[*].id", contains(1)))
                .andExpect(jsonPath("$.results[*].occurrences", contains(2)));
    }

    @Test
    void regexAcrossSeveralWordsWithTheAutomaton() throws Exception {
        // D1 : of akkad ; D2 : of babylon ; D3 : of akkad, of babylon (mots vides compris)
        mvc.perform(get("/api/search/regex").param("q", "of.(akkad|babylon)").param("scope", "text"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.results[*].id", contains(3, 1, 2)))
                .andExpect(jsonPath("$.results[*].occurrences", contains(2, 1, 1)));
    }

    @Test
    void sortByCentrality() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "empire").param("scope", "text").param("sort", "closeness"))
                .andExpect(jsonPath("$.results[*].id", contains(3, 1)));
    }

    @Test
    void malformedRegexGives400() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "(sargon").param("scope", "text"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Parenthese ouvrante sans fermante")));
    }

    @Test
    void excerptsKeepTheOriginalCase() throws Exception {
        mvc.perform(get("/api/books/1/excerpts").param("regex", "sargon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].line").value("Sargon of Akkad founded an empire."))
                .andExpect(jsonPath("$[0].occurrences[0]", contains(0, 6)))
                .andExpect(jsonPath("$[1].number").value(2))
                .andExpect(jsonPath("$[1].occurrences[0]", contains(14, 20)));
    }

    @Test
    void excerptsWithSeveralOccurrencesOnALine() throws Exception {
        mvc.perform(get("/api/books/3/excerpts").param("regex", "of.(akkad|babylon)"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].occurrences[0]", contains(11, 19)))
                .andExpect(jsonPath("$[0].occurrences[1]", contains(33, 43)));
    }

    @Test
    void excerptsOfAKeywordSearchStartWords() throws Exception {
        // « emp » surligne empire en entier ; « pire » ne surligne rien
        mvc.perform(get("/api/books/1/excerpts").param("regex", "emp").param("words", "true"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].occurrences[0]", contains(27, 33)));
        mvc.perform(get("/api/books/1/excerpts").param("regex", "pire").param("words", "true"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/books/1/excerpts").param("regex", "pire"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void excerptsLimit() throws Exception {
        mvc.perform(get("/api/books/1/excerpts").param("regex", "sargon").param("limit", "1"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/books/1/excerpts").param("regex", "sargon").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void excerptsErrors() throws Exception {
        mvc.perform(get("/api/books/999/excerpts").param("regex", "sargon"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/books/1/excerpts").param("regex", "a||"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/books/1/excerpts"))
                .andExpect(status().isBadRequest());
    }
}

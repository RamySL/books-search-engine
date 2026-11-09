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

/** Recherche par RegEx sur l'index, sur le jeu d'essai D1 à D3. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class RegexSearchTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void alternativeInsideAWord() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "sarg(o|a)n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(1))
                .andExpect(jsonPath("$.results[0].occurrences").value(2))
                .andExpect(jsonPath("$.terms", contains("sargon")))
                .andExpect(jsonPath("$.termsTruncated").value(false))
                .andExpect(jsonPath("$.suggestions", empty()));
    }

    @Test
    void termMustBeRecognizedEntirely() throws Exception {
        // « sarg » n'est qu'un préfixe de « sargon »
        mvc.perform(get("/api/search/regex").param("q", "sarg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.terms", empty()));
    }

    @Test
    void regexIsLowercasedLikeTheIndex() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "SARG(O|A)N"))
                .andExpect(jsonPath("$.terms", contains("sargon")));
    }

    @Test
    void severalTermsAddUpPerBook() throws Exception {
        // D1 : akkad 1 ; D2 : babylon 2 ; D3 : akkad 1 + babylon 1
        mvc.perform(get("/api/search/regex").param("q", "akkad|babylon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.results[*].id", contains(2, 3, 1)))
                .andExpect(jsonPath("$.results[*].occurrences", contains(2, 2, 1)))
                .andExpect(jsonPath("$.terms", contains("akkad", "babylon")));
    }

    @Test
    void sortByPagerankThenByOccurrences() throws Exception {
        // D3 est le centre ; D1 et D2 ont le même PageRank, D2 a plus d'occurrences
        mvc.perform(get("/api/search/regex").param("q", "akkad|babylon").param("sort", "pagerank"))
                .andExpect(jsonPath("$.results[*].id", contains(3, 2, 1)));
    }

    @Test
    void universalCharacter() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "c.ty"))
                .andExpect(jsonPath("$.terms", contains("city")))
                .andExpect(jsonPath("$.results[*].id", contains(2, 3)));
    }

    @Test
    void termsAreListedMostWidespreadFirst() throws Exception {
        // .* reconnaît tout le vocabulaire : d'abord les 4 termes présents dans 2 livres
        mvc.perform(get("/api/search/regex").param("q", ".*"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.terms.length()").value(10))
                .andExpect(jsonPath("$.terms[0:4]", contains("akkad", "babylon", "city", "empire")))
                .andExpect(jsonPath("$.termsTruncated").value(false));
    }

    @Test
    void malformedRegexGives400WithEgrepMessage() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "(sargon"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Parenthese ouvrante sans fermante")));
        mvc.perform(get("/api/search/regex").param("q", "|"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/search/regex").param("q", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scope() throws Exception {
        mvc.perform(get("/api/search/regex").param("q", "sargon").param("scope", "index"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/search/regex").param("q", "sargon").param("scope", "text"))
                .andExpect(status().isNotImplemented());
        mvc.perform(get("/api/search/regex").param("q", "sargon").param("scope", "title"))
                .andExpect(status().isBadRequest());
    }
}

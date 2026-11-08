package engine.corpus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatalogTest {

    // extrait au format de pg_catalog.csv : titre sur deux lignes, livre audio, livres non anglais
    private static final String CSV = """
            Text#,Type,Issued,Title,Language,Authors,Subjects,LoCC,Bookshelves
            49345,Text,2015-07-02,"A History of Sumer and Akkad
            An account of the early races of Babylonia",en,"King, L. W. (Leonard William), 1869-1919",Babylonia -- History,DS,
            1342,Text,1998-06-01,Pride and Prejudice,en,"Austen, Jane, 1775-1817",England -- Fiction,PR,
            1571,Text,1999-01-01,Critias,en,"Plato, 428? BCE-348? BCE; Jowett, Benjamin, 1817-1893 [Translator]",,,
            7,Text,1975-01-01,An anonymous text,en,,,,
            8,Sound,2004-01-01,An audio book,en,"Reader, Some, 1950-",,,
            9,Text,2004-01-01,Notre-Dame de Paris,fr,"Hugo, Victor, 1802-1885",,,
            10,Text,2004-01-01,A bilingual book,en; fr,"Someone, 1900-1980",,,
            """;

    private static Catalog catalog() throws IOException {
        return Catalog.parse(new StringReader(CSV));
    }

    @Test
    void keepsEnglishTextsInIdOrder() throws IOException {
        assertEquals(List.of(7, 1342, 1571, 49345), catalog().ids());
    }

    @Test
    void titleIsTheFirstLine() throws IOException {
        assertEquals("A History of Sumer and Akkad", catalog().get(49345).orElseThrow().title());
    }

    @Test
    void authorsLoseTheirDatesAndContributors() throws IOException {
        Catalog catalog = catalog();
        assertEquals("King, L. W. (Leonard William)", catalog.get(49345).orElseThrow().author());
        assertEquals("Austen, Jane", catalog.get(1342).orElseThrow().author());
        assertEquals("Plato", catalog.get(1571).orElseThrow().author());
    }

    @Test
    void noAuthorGivesNull() throws IOException {
        assertNull(catalog().get(7).orElseThrow().author());
    }

    @Test
    void contributorsWithTheirRoleWhenThereIsNoAuthor() {
        assertEquals("Raymond, Eric S. [Editor]; Steele, Guy L. [Editor]",
                Catalog.author("Raymond, Eric S., 1957- [Editor]; Steele, Guy L., 1954- [Editor]"));
    }
}

package egrep;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LignesTest {

    private static List<String> decouper(String texte) {
        return Lignes.decouper(texte.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void keepsCarriageReturnOfCrlfLines() {
        assertEquals(List.of("a\r", "b"), decouper("a\r\nb\n"));
    }

    @Test
    void lonelyCarriageReturnDoesNotSplit() {
        assertEquals(List.of("a\rb"), decouper("a\rb\n"));
    }

    @Test
    void lastLineWithoutNewlineIsKept() {
        assertEquals(List.of("a", "b"), decouper("a\nb"));
    }

    @Test
    void emptyFileHasNoLine() {
        assertEquals(List.of(), decouper(""));
    }

    @Test
    void emptyLinesAreKept() {
        assertEquals(List.of(""), decouper("\n"));
        assertEquals(List.of("a", "", "b"), decouper("a\n\nb\n"));
    }

    @Test
    void decodesUtf8AndReplacesInvalidBytes() {
        assertEquals(List.of("été"), decouper("été\n"));
        byte[] invalide = {'a', (byte) 0xFF, 'b', '\n'};
        assertEquals(List.of("a�b"), Lignes.decouper(invalide));
    }
}

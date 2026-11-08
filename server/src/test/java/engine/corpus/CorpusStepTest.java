package engine.corpus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorpusStepTest {

    @Test
    void requiredBooksFirstThenIdOrder() {
        assertEquals(List.of(49345, 56667, 1, 2, 3), CorpusStep.candidates(List.of(1, 2, 3, 49345, 56667)));
    }

    @Test
    void limitOption() {
        assertEquals(OptionalInt.of(50), CorpusStep.limit(new DefaultApplicationArguments("corpus", "--limit=50")));
        assertEquals(OptionalInt.empty(), CorpusStep.limit(new DefaultApplicationArguments("corpus")));
        assertThrows(IllegalArgumentException.class,
                () -> CorpusStep.limit(new DefaultApplicationArguments("corpus", "--limit=0")));
        assertThrows(IllegalArgumentException.class,
                () -> CorpusStep.limit(new DefaultApplicationArguments("corpus", "--limit=all")));
    }

    @Test
    void idsFileIgnoresCommentsAndBlankLines(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("ids.txt"), "# commentaire\n\n12\n 345 \n");
        assertEquals(List.of(12, 345), CorpusStep.readIds(file));
    }
}

package engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalingTest {

    @Test
    void sizesEndWithTheWholeCorpus() {
        assertArrayEquals(new int[] {100, 250, 500, 1000, 1500, 2000}, Scaling.sizes(2000));
        assertArrayEquals(new int[] {100, 250, 500, 1000, 1200}, Scaling.sizes(1200));
        assertArrayEquals(new int[] {100, 250, 500, 1000, 1500, 2000, 2500}, Scaling.sizes(2500));
        // corpus de développement : une seule taille
        assertArrayEquals(new int[] {3}, Scaling.sizes(3));
    }

    @Test
    void shuffleIsAReproduciblePermutation() {
        List<Integer> ids = IntStream.rangeClosed(1, 50).boxed().toList();
        List<Integer> shuffled = Scaling.shuffle(ids);

        assertEquals(ids, shuffled.stream().sorted().toList());
        assertNotEquals(ids, shuffled);
        // même résultat quel que soit l'ordre reçu : les deux étapes mesurent les mêmes livres
        assertEquals(shuffled, Scaling.shuffle(ids.reversed()));
    }

    @Test
    void runsOption() {
        assertEquals(3, Scaling.runs(new DefaultApplicationArguments("graph-scaling")));
        assertEquals(5, Scaling.runs(new DefaultApplicationArguments("graph-scaling", "--runs=5")));
        for (String value : List.of("0", "-1", "abc")) {
            assertThrows(IllegalArgumentException.class,
                    () -> Scaling.runs(new DefaultApplicationArguments("graph-scaling", "--runs=" + value)));
        }
    }

    @Test
    void medianRunsTheTaskOncePerMeasure() {
        List<Integer> calls = new ArrayList<>();
        double seconds = Scaling.medianSeconds(4, () -> calls.add(1));
        assertEquals(4, calls.size());
        assertTrue(seconds >= 0);
    }

    @Test
    void secondsToTheMicrosecond() {
        assertEquals("1.234560", Scaling.seconds(1.23456));
        assertEquals("0.000012", Scaling.seconds(1.2e-5));
        assertEquals("0.000000", Scaling.seconds(0));
    }
}

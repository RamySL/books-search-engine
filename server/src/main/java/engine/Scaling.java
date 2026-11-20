package engine;

import org.springframework.boot.ApplicationArguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Outils communs aux mesures de passage à l'échelle des étapes hors ligne
 * (index-scaling, graph-scaling) : mêmes échantillons, même chronométrage.
 * <p>
 * Les échantillons sont emboîtés : on mélange les livres avec une graine
 * fixe, puis on prend les n premiers. Chaque échantillon contient donc le
 * précédent, et un nouveau lancement mesure les mêmes livres.
 */
public final class Scaling {

    /** Tailles d'échantillon ; on garde celles sous la taille du corpus, puis le corpus entier. */
    private static final int[] SIZES = {100, 250, 500, 1000, 1500, 2000};

    /** Graine du mélange, commune aux deux étapes. */
    private static final long SEED = 2026;

    /** Mesures par taille sans --runs ; on garde la médiane. */
    private static final int DEFAULT_RUNS = 3;

    private Scaling() {
    }

    /** @return le nombre de mesures par taille : --runs, 3 par défaut */
    public static int runs(ApplicationArguments args) {
        List<String> values = args.getOptionValues("runs");
        if (values == null || values.isEmpty()) {
            return DEFAULT_RUNS;
        }
        try {
            int runs = Integer.parseInt(values.getLast());
            if (runs < 1) {
                throw new NumberFormatException();
            }
            return runs;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--runs attend un entier positif : " + values.getLast());
        }
    }

    /** @return les livres dans un ordre aléatoire reproductible, quel que soit l'ordre reçu */
    public static List<Integer> shuffle(List<Integer> ids) {
        List<Integer> shuffled = new ArrayList<>(ids);
        Collections.sort(shuffled);
        Collections.shuffle(shuffled, new Random(SEED));
        return shuffled;
    }

    /** @return les tailles à mesurer, par ordre croissant ; la dernière est le corpus entier */
    public static int[] sizes(int total) {
        return IntStream.concat(Arrays.stream(SIZES).filter(n -> n < total), IntStream.of(total)).toArray();
    }

    /** @return la durée médiane, en secondes, de {@code runs} exécutions de la tâche */
    public static double medianSeconds(int runs, Runnable task) {
        double[] seconds = new double[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            task.run();
            seconds[i] = (System.nanoTime() - start) / 1e9;
        }
        Arrays.sort(seconds);
        int middle = runs / 2;
        return runs % 2 == 1 ? seconds[middle] : (seconds[middle - 1] + seconds[middle]) / 2;
    }

    /** @return une durée en secondes, à la microseconde près : closeness et PageRank durent quelques millisecondes */
    public static String seconds(double seconds) {
        return String.format(Locale.ROOT, "%.6f", seconds);
    }
}

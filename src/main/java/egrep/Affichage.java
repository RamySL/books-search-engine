package egrep;

import java.io.PrintStream;
import java.util.List;

/**
 * Sortie lisible de l'option --detail : en-tête, lignes numérotées avec les
 * occurrences en rouge, bilan final.
 */
final class Affichage {

    // codes ANSI, mêmes couleurs que grep --color
    private static final String ROUGE = "\u001B[1;31m";
    private static final String VERT = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAS = "\u001B[1m";
    private static final String FIN_COULEUR = "\u001B[0m";

    private final PrintStream sortie;

    Affichage(PrintStream sortie) {
        this.sortie = sortie;
    }

    void entete(String motif, String fichier, Recherche recherche) {
        sortie.println(GRAS + "Motif \"" + motif + "\" dans " + fichier + FIN_COULEUR
                + "  (strategie : " + recherche.description() + ")");
        sortie.println(CYAN + "-".repeat(72) + FIN_COULEUR);
    }

    void ligne(int numero, String ligne, List<int[]> occurrences) {
        sortie.printf("%s%6d%s %s|%s %s%n", VERT, numero, FIN_COULEUR,
                CYAN, FIN_COULEUR, surligner(ligne, occurrences));
    }

    void bilan(int lignesTrouvees, int nbLignes, int nbOccurrences, long millis) {
        sortie.println(CYAN + "-".repeat(72) + FIN_COULEUR);
        sortie.println(GRAS + lignesTrouvees + " ligne(s) trouvee(s)" + FIN_COULEUR
                + " sur " + nbLignes + ", " + nbOccurrences + " occurrence(s), en " + millis + " ms");
    }

    /** @return la ligne où chaque occurrence est encadrée par les codes ANSI du rouge */
    static String surligner(String ligne, List<int[]> occurrences) {
        StringBuilder sb = new StringBuilder();
        int position = 0;
        for (int[] occurrence : occurrences) {
            sb.append(ligne, position, occurrence[0])
                    .append(ROUGE).append(ligne, occurrence[0], occurrence[1]).append(FIN_COULEUR);
            position = occurrence[1];
        }
        return sb.append(ligne, position, ligne.length()).toString();
    }
}

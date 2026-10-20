package egrep;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Point d'entrée : lit les arguments, choisit la stratégie de recherche
 * ({@link Recherche#pour}) et affiche les lignes du fichier qui contiennent
 * une occurrence du motif.
 */
public class Egrep {

    private static void usage() {
        System.err.println("Usage : egrep [--dfa] [--brut] MOTIF FICHIER");
        System.err.println("  --dfa   force l'automate meme si le motif est une simple suite de lettres");
        System.err.println("  --brut  sortie identique a egrep : lignes seules, sans couleur ni numero");
        System.exit(2);
    }

    /**
     * Usage : egrep [--dfa] [--brut] MOTIF FICHIER — affiche les lignes de FICHIER
     * contenant une occurrence de MOTIF, numérotées et avec les occurrences en
     * rouge, suivies d'un récapitulatif. {@code --dfa} interdit KMP (pour comparer
     * les deux méthodes) ; {@code --brut} donne la sortie exacte d'egrep.
     */
    public static void main(String[] args) throws IOException {
        boolean kmpAutorise = true;
        boolean brut = false;
        List<String> positionnels = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "--dfa" -> kmpAutorise = false;
                case "--brut" -> brut = true;
                default -> positionnels.add(arg);
            }
        }
        if (positionnels.size() != 2) {
            usage();
        }
        String motif = positionnels.get(0);
        String fichier = positionnels.get(1);
        long debut = System.nanoTime();
        Recherche recherche;
        try {
            recherche = Recherche.pour(motif, kmpAutorise);
        } catch (IllegalArgumentException e) {
            System.err.println("egrep : " + e.getMessage());
            System.exit(2);
            return;
        }
        PrintStream sortie = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        Affichage affichage = new Affichage(sortie);
        if (!brut) {
            affichage.entete(motif, fichier, recherche);
        }
        int numero = 0;
        int lignesTrouvees = 0;
        int nbOccurrences = 0;
        // le décodage UTF-8 remplace les octets invalides au lieu de lever une exception
        try (BufferedReader lecteur = new BufferedReader(
                new InputStreamReader(new FileInputStream(fichier), StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                numero++;
                if (!recherche.contient(ligne)) {
                    continue;
                }
                lignesTrouvees++;
                if (brut) {
                    sortie.println(ligne);
                } else {
                    List<int[]> occurrences = recherche.occurrences(ligne);
                    nbOccurrences += occurrences.size();
                    affichage.ligne(numero, ligne, occurrences);
                }
            }
        } catch (IOException e) {
            System.err.println("egrep : impossible de lire " + fichier + " (" + e.getMessage() + ")");
            System.exit(2);
        }
        if (!brut) {
            long millis = (System.nanoTime() - debut) / 1_000_000;
            affichage.bilan(lignesTrouvees, numero, nbOccurrences, millis);
        }
        sortie.flush();
        // comme egrep : 0 si au moins une ligne trouvée, 1 sinon
        System.exit(lignesTrouvees > 0 ? 0 : 1);
    }

}

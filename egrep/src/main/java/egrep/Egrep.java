package egrep;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Point d'entrée : lit les arguments, choisit la stratégie de recherche
 * ({@link Recherche#pour}) et affiche les lignes du fichier qui contiennent
 * une occurrence du motif.
 */
public class Egrep {

    private static void usage() {
        System.err.println("Usage : egrep [--detail] [--dfa] MOTIF FICHIER");
        System.err.println("  --detail  en-tete, lignes numerotees avec les occurrences en rouge, bilan");
        System.err.println("  --dfa     force l'automate meme si le motif est une simple suite de lettres");
        System.exit(2);
    }

    /**
     * Usage : egrep [--detail] [--dfa] MOTIF FICHIER — affiche, comme egrep, les
     * lignes de FICHIER qui contiennent une occurrence de MOTIF.
     * {@code --detail} ajoute un en-tête, numérote les lignes, met les
     * occurrences en rouge et finit par un récapitulatif ; {@code --dfa}
     * interdit KMP, pour comparer les deux méthodes.
     */
    public static void main(String[] args) throws IOException {
        boolean kmpAutorise = true;
        boolean detail = false;
        List<String> positionnels = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "--dfa" -> kmpAutorise = false;
                case "--detail" -> detail = true;
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
        if (detail) {
            affichage.entete(motif, fichier, recherche);
        }
        List<String> lignes;
        try {
            lignes = Lignes.lire(Path.of(fichier));
        } catch (IOException e) {
            System.err.println("egrep : impossible de lire " + fichier + " (" + e.getMessage() + ")");
            System.exit(2);
            return;
        }
        int numero = 0;
        int lignesTrouvees = 0;
        int nbOccurrences = 0;
        for (String ligne : lignes) {
            numero++;
            if (!recherche.contient(ligne)) {
                continue;
            }
            lignesTrouvees++;
            if (detail) {
                List<int[]> occurrences = recherche.occurrences(ligne);
                nbOccurrences += occurrences.size();
                affichage.ligne(numero, ligne, occurrences);
            } else {
                sortie.println(ligne);
            }
        }
        if (detail) {
            long millis = (System.nanoTime() - debut) / 1_000_000;
            affichage.bilan(lignesTrouvees, numero, nbOccurrences, millis);
        }
        sortie.flush();
        // comme egrep : 0 si au moins une ligne trouvée, 1 sinon
        System.exit(lignesTrouvees > 0 ? 0 : 1);
    }

}

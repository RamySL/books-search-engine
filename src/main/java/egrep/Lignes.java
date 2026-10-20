package egrep;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Découpage d'un fichier en lignes, comme egrep : seul '\n' sépare deux
 * lignes. Un '\r' reste donc dans la ligne, ce qui compte pour les textes en
 * CRLF du projet Gutenberg : le '.' d'egrep le reconnaît, et il est réécrit tel
 * quel en sortie.
 */
public final class Lignes {

    private Lignes() {
    }

    /**
     * Lit tout le fichier en mémoire, ce qui suffit pour des livres. On passe par
     * FileInputStream plutôt que Files.readAllBytes, dont les messages d'erreur
     * ne donnent que le chemin, sans la cause.
     * @return les lignes du fichier, sans leur '\n'
     */
    public static List<String> lire(Path fichier) throws IOException {
        try (FileInputStream entree = new FileInputStream(fichier.toFile())) {
            return decouper(entree.readAllBytes());
        }
    }

    /**
     * Coupe sur l'octet '\n', qui n'apparaît jamais à l'intérieur d'un caractère
     * UTF-8 de plusieurs octets, puis décode chaque ligne en UTF-8 : les octets
     * invalides deviennent U+FFFD. Un '\n' final ne crée pas de ligne vide.
     */
    static List<String> decouper(byte[] octets) {
        List<String> lignes = new ArrayList<>();
        int debut = 0;
        for (int i = 0; i < octets.length; i++) {
            if (octets[i] == '\n') {
                lignes.add(new String(octets, debut, i - debut, StandardCharsets.UTF_8));
                debut = i + 1;
            }
        }
        if (debut < octets.length) {
            lignes.add(new String(octets, debut, octets.length - debut, StandardCharsets.UTF_8));
        }
        return lignes;
    }
}

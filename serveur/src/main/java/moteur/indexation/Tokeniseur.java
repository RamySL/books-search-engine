package moteur.indexation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Découpage d'un texte en mots indexables. Les livres, à l'indexation, et les
 * requêtes, à la recherche, passent par ce même découpage : un mot saisi par
 * l'utilisateur s'écrit donc exactement comme dans l'index.
 * <ol>
 *   <li>normalisation Unicode NFC : une lettre accentuée devient un seul
 *       caractère, même si le texte l'écrit lettre puis accent combinant ;</li>
 *   <li>découpage sur tout ce qui n'est pas une lettre Unicode (espaces,
 *       ponctuation, chiffres, apostrophes) : les mots accentués restent entiers ;</li>
 *   <li>passage en minuscules ;</li>
 *   <li>retrait des mots d'une lettre et des mots vides anglais.</li>
 * </ol>
 */
public final class Tokeniseur {

    private static final Pattern SEPARATEURS = Pattern.compile("\\P{L}+");

    /**
     * Les mots d'une lettre sont surtout des restes d'apostrophes (king's,
     * don't) et des initiales ; dès deux lettres, il y a des mots utiles comme
     * « ur » ou « ox ».
     */
    private static final int LONGUEUR_MIN = 2;

    private static final Set<String> MOTS_VIDES = charger("/mots-vides-en.txt");

    private Tokeniseur() {
    }

    /** @return les mots indexables du texte, dans l'ordre du texte, répétitions comprises */
    public static List<String> mots(String texte) {
        List<String> mots = new ArrayList<>();
        for (String morceau : SEPARATEURS.split(Normalizer.normalize(texte, Normalizer.Form.NFC))) {
            // la locale racine : le résultat ne dépend pas de la langue de la machine
            String mot = morceau.toLowerCase(Locale.ROOT);
            if (mot.length() >= LONGUEUR_MIN && !MOTS_VIDES.contains(mot)) {
                mots.add(mot);
            }
        }
        return mots;
    }

    /** Un mot par ligne ; les lignes vides et celles qui commencent par # sont ignorées. */
    private static Set<String> charger(String ressource) {
        InputStream flux = Tokeniseur.class.getResourceAsStream(ressource);
        if (flux == null) {
            throw new IllegalStateException("Ressource introuvable : " + ressource);
        }
        try (BufferedReader lecteur = new BufferedReader(new InputStreamReader(flux, StandardCharsets.UTF_8))) {
            return lecteur.lines()
                    .map(String::strip)
                    .filter(ligne -> !ligne.isEmpty() && !ligne.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package egrep;

import java.util.ArrayList;
import java.util.List;

/** Génération exhaustive de mots courts, pour comparer deux implémentations sur toutes les entrées. */
public final class Mots {

    private Mots() {
    }

    /** @return tous les mots sur les lettres données, de longueur 0 à longueurMax, par longueur croissante */
    public static List<String> tousLesMots(String lettres, int longueurMax) {
        List<String> mots = new ArrayList<>();
        mots.add("");
        for (int debut = 0; debut < mots.size() && mots.get(debut).length() < longueurMax; debut++) {
            for (char c : lettres.toCharArray()) {
                mots.add(mots.get(debut) + c);
            }
        }
        return mots;
    }
}

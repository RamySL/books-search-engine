package egrep;

import java.util.ArrayList;
import java.util.List;

/**
 * Stratégie de recherche d'un motif dans une ligne : test rapide de présence,
 * et repérage des occurrences pour l'affichage en couleur.
 */
public sealed interface Recherche {

    /** @return vrai si la ligne contient une occurrence du motif */
    boolean contient(String ligne);

    /**
     * @return l'occurrence la plus à gauche commençant à partir de debut, la plus
     *         longue en cas d'égalité, sous la forme {début, fin exclue}, ou null
     */
    int[] prochaine(String ligne, int debut);

    /** @return description courte de la stratégie, pour l'en-tête de l'affichage */
    String description();

    /**
     * Occurrences non chevauchantes, la plus à gauche puis la plus longue, comme
     * {@code egrep --color} : on reprend la recherche après chaque occurrence.
     * Les occurrences vides (motif comme {@code a*}) ne sont pas retenues.
     * @return liste de paires {début, fin exclue}
     */
    default List<int[]> occurrences(String ligne) {
        List<int[]> resultat = new ArrayList<>();
        int position = 0;
        while (position <= ligne.length()) {
            int[] occurrence = prochaine(ligne, position);
            if (occurrence == null) {
                break;
            }
            if (occurrence[1] > occurrence[0]) {
                resultat.add(occurrence);
                position = occurrence[1];
            } else {
                position = occurrence[0] + 1;
            }
        }
        return resultat;
    }

    /** Motif réduit à une suite de lettres : algorithme de Knuth-Morris-Pratt. */
    record ParKmp(Kmp kmp) implements Recherche {
        @Override
        public boolean contient(String ligne) {
            return kmp.contient(ligne);
        }

        @Override
        public int[] prochaine(String ligne, int debut) {
            int i = kmp.indice(ligne, debut);
            return i == -1 ? null : new int[]{i, i + kmp.longueur()};
        }

        @Override
        public String description() {
            return "KMP";
        }
    }

    /**
     * Motif quelconque : le DFA minimal de {@code .*regex} filtre les lignes en un
     * seul parcours ; celui de la regex seule, essayé à chaque position, délimite
     * les occurrences (uniquement sur les lignes retenues).
     */
    record ParAutomate(Dfa facteur, Dfa motif) implements Recherche {
        @Override
        public boolean contient(String ligne) {
            return facteur.acceptePrefixe(ligne);
        }

        @Override
        public int[] prochaine(String ligne, int debut) {
            for (int i = debut; i <= ligne.length(); i++) {
                int fin = motif.plusLongPrefixe(ligne, i);
                if (fin != -1) {
                    return new int[]{i, fin};
                }
            }
            return null;
        }

        @Override
        public String description() {
            return "automate (DFA minimal, " + facteur.nbEtats() + " etats)";
        }
    }
}

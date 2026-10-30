package egrep.automate;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Automate fini déterministe, produit par {@link Determinisation} (étape 3)
 * puis {@link Minimisation} (étape 4), et parcouru sur chaque ligne (étape 5).</br>
 * L'alphabet n'est pas énuméré : on garde les caractères explicites de la regex
 * et une unique colonne « autre » pour tout caractère absent de la regex (seules
 * les transitions joker de l'{@link Ndfa} y sont empruntables).
 * Les transitions sont rangées dans un tableau {@code int[][]} indexé par l'état
 * et par la colonne du caractère : une transition coûte deux lectures de tableau.
 * L'automate est partiel : l'absence de transition (valeur {@link #AUCUN}) joue
 * le rôle de l'état puits.
 */
public final class Dfa {

    /** Valeur renvoyée par {@link #suivant} quand il n'y a pas de transition. */
    public static final int AUCUN = -1;

    private static final int ETAT_INITIAL = 0;

    private final Set<Character> alphabet;
    // colonnes[c] : colonne du caractère c ; un caractère hors alphabet, ou au-delà du tableau, va dans colonneAutre
    private final int[] colonnes;
    // dernière colonne : tout caractère hors alphabet
    private final int colonneAutre;
    // transitions[q][colonne] : destination depuis q, ou AUCUN ; agrandi au besoin, comme une ArrayList
    private int[][] transitions = new int[8][];
    private boolean[] finaux = new boolean[8];
    private int nbEtats;

    /** Automate sans état, que {@link Determinisation} et {@link Minimisation} remplissent. */
    Dfa(Set<Character> alphabet) {
        this.alphabet = alphabet;
        this.colonneAutre = alphabet.size();
        int plusGrand = alphabet.stream().mapToInt(c -> c).max().orElse(-1);
        this.colonnes = new int[plusGrand + 1];
        Arrays.fill(colonnes, colonneAutre);
        int colonne = 0;
        for (char c : alphabet) {
            colonnes[c] = colonne++;
        }
    }

    /** @return le numéro du nouvel état, sans transition ; le premier créé est l'état initial */
    int creerEtat(boolean final_) {
        if (nbEtats == transitions.length) {
            transitions = Arrays.copyOf(transitions, 2 * nbEtats);
            finaux = Arrays.copyOf(finaux, 2 * nbEtats);
        }
        int[] ligne = new int[colonneAutre + 1];
        Arrays.fill(ligne, AUCUN);
        transitions[nbEtats] = ligne;
        finaux[nbEtats] = final_;
        return nbEtats++;
    }

    void ajouterTransition(int source, char c, int destination) {
        transitions[source][colonnes[c]] = destination;
    }

    void definirAutre(int source, int destination) {
        transitions[source][colonneAutre] = destination;
    }

    /** @return les caractères explicites, qui ont chacun leur colonne de transitions */
    Set<Character> alphabet() {
        return alphabet;
    }

    /** @return la destination depuis l'état en lisant un caractère hors alphabet, ou {@link #AUCUN} */
    int autre(int etat) {
        return transitions[etat][colonneAutre];
    }

    public int nbEtats() {
        return nbEtats;
    }

    public int etatInitial() {
        return ETAT_INITIAL;
    }

    public boolean estFinal(int etat) {
        return finaux[etat];
    }

    /** @return la destination depuis l'état en lisant c, ou {@link #AUCUN} */
    public int suivant(int etat, char c) {
        return transitions[etat][c < colonnes.length ? colonnes[c] : colonneAutre];
    }

    /** @return vrai si le mot complet est reconnu */
    public boolean accepte(String mot) {
        int etat = ETAT_INITIAL;
        for (char c : mot.toCharArray()) {
            etat = suivant(etat, c);
            if (etat == AUCUN) {
                return false;
            }
        }
        return estFinal(etat);
    }

    /**
     * @return vrai si un préfixe du mot (éventuellement vide) est reconnu : on
     *         s'arrête dès qu'un état final est atteint. Avec un DFA construit
     *         sur {@code .*regex}, cela revient à tester si le mot contient un facteur
     *         reconnu par regex.
     */
    public boolean acceptePrefixe(String mot) {
        int etat = ETAT_INITIAL;
        if (estFinal(etat)) {
            return true;
        }
        for (int i = 0; i < mot.length(); i++) {
            etat = suivant(etat, mot.charAt(i));
            if (etat == AUCUN) {
                return false;
            }
            if (estFinal(etat)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return la fin (exclue) du plus long préfixe de mot[debut..] reconnu par
     *         l'automate, ou -1 si aucun préfixe (même vide) n'est reconnu. Avec
     *         le DFA de la regex seule, cela donne l'occurrence la plus longue
     *         commençant en debut.
     */
    public int plusLongPrefixe(String mot, int debut) {
        int etat = ETAT_INITIAL;
        int fin = estFinal(etat) ? debut : -1;
        for (int i = debut; i < mot.length(); i++) {
            etat = suivant(etat, mot.charAt(i));
            if (etat == AUCUN) {
                break;
            }
            if (estFinal(etat)) {
                fin = i + 1;
            }
        }
        return fin;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nombre d'etats : ").append(nbEtats()).append('\n');
        sb.append("Etat initial : q").append(ETAT_INITIAL).append('\n');
        Set<Integer> etatsFinaux = new TreeSet<>();
        for (int q = 0; q < nbEtats(); q++) {
            if (estFinal(q)) {
                etatsFinaux.add(q);
            }
        }
        sb.append("Etats finaux : ").append(etatsFinaux).append("\n\n");
        for (int q = 0; q < nbEtats(); q++) {
            for (char c : new TreeSet<>(alphabet)) {
                int destination = transitions[q][colonnes[c]];
                if (destination != AUCUN) {
                    sb.append('q').append(q).append(" --").append(c).append("--> q")
                            .append(destination).append('\n');
                }
            }
            if (autre(q) != AUCUN) {
                sb.append('q').append(q).append(" --autre--> q").append(autre(q)).append('\n');
            }
        }
        return sb.toString();
    }
}

package egrep.automate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Automate fini déterministe, produit par {@link Determinisation} (étape 3)
 * puis {@link Minimisation} (étape 4), et parcouru sur chaque ligne (étape 5).</br>
 * L'alphabet n'est pas énuméré : on garde les caractères explicites de la regex
 * et une unique colonne « autre » pour tout caractère absent de la regex (seules
 * les transitions joker de l'{@link Ndfa} y sont empruntables).
 * L'automate est partiel : l'absence de transition (valeur {@link #AUCUN}) joue
 * le rôle de l'état puits.
 */
public final class Dfa {

    /** Valeur renvoyée par {@link #suivant} quand il n'y a pas de transition. */
    public static final int AUCUN = -1;

    private final Set<Character> alphabet;
    // transitions.get(q).get(c) : destination depuis q en lisant le caractère explicite c
    // TODO: optimisation avec un tableau natif, et réalisation de tests de performances pour comparer à l'ancienne version
    private final List<Map<Character, Integer>> transitions = new ArrayList<>();
    // autres.get(q) : destination depuis q en lisant tout caractère hors alphabet
    private final List<Integer> autres = new ArrayList<>();
    private final List<Boolean> finaux = new ArrayList<>();
    private static final int ETAT_INITIAL = 0;

    /** Automate sans état, que {@link Determinisation} et {@link Minimisation} remplissent. */
    Dfa(Set<Character> alphabet) {
        this.alphabet = alphabet;
    }

    /** @return le numéro du nouvel état, sans transition ; le premier créé est l'état initial */
    int creerEtat(boolean final_) {
        transitions.add(new HashMap<>());
        autres.add(AUCUN);
        finaux.add(final_);
        return transitions.size() - 1;
    }

    void ajouterTransition(int source, char c, int destination) {
        transitions.get(source).put(c, destination);
    }

    void definirAutre(int source, int destination) {
        autres.set(source, destination);
    }

    /** @return les caractères explicites, qui ont chacun leur colonne de transitions */
    Set<Character> alphabet() {
        return alphabet;
    }

    /** @return la destination depuis l'état en lisant un caractère hors alphabet, ou {@link #AUCUN} */
    int autre(int etat) {
        return autres.get(etat);
    }

    public int nbEtats() {
        return transitions.size();
    }

    public int etatInitial() {
        return ETAT_INITIAL;
    }

    public boolean estFinal(int etat) {
        return finaux.get(etat);
    }

    /** @return la destination depuis l'état en lisant c, ou {@link #AUCUN} */
    public int suivant(int etat, char c) {
        Integer destination = transitions.get(etat).get(c);
        return destination != null ? destination : autres.get(etat);
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
            for (Map.Entry<Character, Integer> t : new java.util.TreeMap<>(transitions.get(q)).entrySet()) {
                sb.append('q').append(q).append(" --").append(t.getKey()).append("--> q")
                        .append(t.getValue()).append('\n');
            }
            if (autres.get(q) != AUCUN) {
                sb.append('q').append(q).append(" --autre--> q").append(autres.get(q)).append('\n');
            }
        }
        return sb.toString();
    }
}

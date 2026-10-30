package egrep.automate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Étape 3 du sujet : déterminisation d'un {@link Ndfa} par la méthode des
 * sous-ensembles. Chaque état du DFA est un ensemble d'états de
 * l'epsilon-AFN, fermé par epsilon-transitions.
 */
public final class Determinisation {

    private final Ndfa ndfa;
    private final Dfa dfa;
    // numeros.get(e) : état du DFA associé à l'ensemble e d'états de l'epsilon-AFN
    private final Map<Set<Integer>, Integer> numeros = new HashMap<>();
    // ensembles.get(q) : ensemble d'états de l'epsilon-AFN représenté par l'état q du DFA
    private final List<Set<Integer>> ensembles = new ArrayList<>();
    // états du DFA dont les transitions restent à calculer
    private final Deque<Integer> aTraiter = new ArrayDeque<>();

    private Determinisation(Ndfa ndfa) {
        this.ndfa = ndfa;
        this.dfa = new Dfa(ndfa.alphabet());
    }

    /**
     * @param ndfa automate à déterminiser
     * @return le DFA équivalent, dont l'état initial est l'état 0
     */
    public static Dfa sousEnsembles(Ndfa ndfa) {
        return new Determinisation(ndfa).construire();
    }

    private Dfa construire() {
        ajouterEtat(ndfa.fermetureEpsilon(Set.of(ndfa.etatInitial())));

        while (!aTraiter.isEmpty()) {
            int q = aTraiter.pop();
            Set<Integer> source = ensembles.get(q);
            for (char c : dfa.alphabet()) {
                int destination = cible(ndfa.transiter(source, c));
                if (destination != Dfa.AUCUN) {
                    dfa.ajouterTransition(q, c, destination);
                }
            }
            // un caractère hors alphabet n'emprunte que les transitions joker
            dfa.definirAutre(q, cible(ndfa.transiterHorsAlphabet(source)));
        }
        return dfa;
    }

    /** @return l'état du DFA associé à la fermeture des états atteints, ou {@link Dfa#AUCUN} s'il n'y en a aucun */
    private int cible(Set<Integer> atteints) {
        if (atteints.isEmpty()) {
            return Dfa.AUCUN;
        }
        return ajouterEtat(ndfa.fermetureEpsilon(atteints));
    }

    /** @return le numéro de l'état associé à l'ensemble, créé et mis en file s'il est nouveau */
    private int ajouterEtat(Set<Integer> ensemble) {
        Integer existant = numeros.get(ensemble);
        if (existant != null) {
            return existant;
        }
        int numero = dfa.creerEtat(ensemble.contains(ndfa.etatFinal()));
        numeros.put(ensemble, numero);
        ensembles.add(ensemble);
        aTraiter.push(numero);
        return numero;
    }
}

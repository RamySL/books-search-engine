package egrep.automate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Étape 4 du sujet : minimisation d'un {@link Dfa} par raffinement de
 * partition (algorithme de Moore).
 */
public final class Minimisation {

    private Minimisation() {
    }

    /**
     * Deux états sont équivalents s'ils sont tous deux finaux ou non finaux et
     * si leurs transitions mènent, symbole par symbole, à des états
     * équivalents. On raffine la partition jusqu'à stabilité. L'état puits
     * implicite est ajouté pour compléter l'automate pendant le calcul, puis sa
     * classe est retirée du résultat (ainsi que tout état mort équivalent à lui).
     * @param dfa automate à minimiser
     * @return un nouveau DFA reconnaissant le même langage avec un nombre minimum d'états
     */
    public static Dfa moore(Dfa dfa) {
        int n = dfa.nbEtats();
        int puits = n;
        int initial = dfa.etatInitial();
        List<Character> symboles = new ArrayList<>(dfa.alphabet());
        int nbSymboles = symboles.size() + 1; // + colonne « autre »

        // automate complété : delta[q][s], le puits boucle sur lui-même
        int[][] delta = new int[n + 1][nbSymboles];
        for (int q = 0; q < n; q++) {
            for (int s = 0; s < nbSymboles; s++) {
                int destination = s < symboles.size() ? dfa.suivant(q, symboles.get(s)) : dfa.autre(q);
                delta[q][s] = destination == Dfa.AUCUN ? puits : destination;
            }
        }
        Arrays.fill(delta[puits], puits);

        // partition initiale : finaux / non finaux
        int[] classe = new int[n + 1];
        for (int q = 0; q < n; q++) {
            classe[q] = dfa.estFinal(q) ? 1 : 0;
        }
        int nbClasses = Arrays.stream(classe).distinct().toArray().length;
        while (true) {
            Map<List<Integer>, Integer> signatures = new HashMap<>();
            int[] nouvelle = new int[n + 1];
            for (int q = 0; q <= n; q++) {
                List<Integer> signature = new ArrayList<>(nbSymboles + 1);
                signature.add(classe[q]);
                for (int s = 0; s < nbSymboles; s++) {
                    signature.add(classe[delta[q][s]]);
                }
                nouvelle[q] = signatures.computeIfAbsent(signature, k -> signatures.size());
            }
            classe = nouvelle;
            if (signatures.size() == nbClasses) {
                break;
            }
            nbClasses = signatures.size();
        }

        // reconstruction à partir d'un représentant par classe, en omettant la classe du puits
        Dfa minimal = new Dfa(dfa.alphabet());
        int classePuits = classe[puits];
        if (classe[initial] == classePuits) {
            minimal.creerEtat(false); // langage vide
            return minimal;
        }
        Map<Integer, Integer> representant = new HashMap<>();
        for (int q = n - 1; q >= 0; q--) {
            representant.put(classe[q], q);
        }
        Map<Integer, Integer> numeros = new HashMap<>();
        Deque<Integer> aTraiter = new ArrayDeque<>();
        numeros.put(classe[initial], minimal.creerEtat(dfa.estFinal(initial)));
        aTraiter.push(classe[initial]);
        while (!aTraiter.isEmpty()) {
            int cl = aTraiter.pop();
            int rep = representant.get(cl);
            int source = numeros.get(cl);
            for (int s = 0; s < nbSymboles; s++) {
                int classeDest = classe[delta[rep][s]];
                if (classeDest == classePuits) {
                    continue;
                }
                Integer destination = numeros.get(classeDest);
                if (destination == null) {
                    destination = minimal.creerEtat(dfa.estFinal(representant.get(classeDest)));
                    numeros.put(classeDest, destination);
                    aTraiter.push(classeDest);
                }
                if (s < symboles.size()) {
                    minimal.ajouterTransition(source, symboles.get(s), destination);
                } else {
                    minimal.definirAutre(source, destination);
                }
            }
        }
        return minimal;
    }
}

package egrep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Automate fini déterministe obtenu par la méthode des sous-ensembles à partir
 * d'un {@link AutomateAhoUllman}.</br>
 * L'alphabet n'est pas énuméré : on garde les caractères explicites de la regex
 * et une unique colonne « autre » pour tout caractère absent de la regex (seules
 * les transitions {@link AutomateAhoUllman#JOKER} y sont empruntables).
 * L'automate est partiel : l'absence de transition (valeur {@link #AUCUN}) joue
 * le rôle de l'état puits.
 */
public final class Dfa {

    /** Valeur renvoyée par {@link #suivant} quand il n'y a pas de transition. */
    public static final int AUCUN = -1;

    private final Set<Character> alphabet;
    // transitions.get(q).get(c) : destination depuis q en lisant le caractère explicite c
    private final List<Map<Character, Integer>> transitions = new ArrayList<>();
    // autres.get(q) : destination depuis q en lisant tout caractère hors alphabet
    private final List<Integer> autres = new ArrayList<>();
    private final List<Boolean> finaux = new ArrayList<>();
    private static final int ETAT_INITIAL = 0;

    private Dfa(Set<Character> alphabet) {
        this.alphabet = alphabet;
    }

    /**
     * Méthode des sous-ensembles : chaque état du DFA est un ensemble d'états de
     * l'epsilon-AFN, fermé par epsilon-transitions.
     * @param afn automate à déterminiser
     * @return le DFA équivalent, dont l'état initial est l'état 0
     */
    public static Dfa depuisAutomate(AutomateAhoUllman afn) {
        Dfa dfa = new Dfa(afn.alphabet());
        Map<Set<Integer>, Integer> numeros = new HashMap<>();
        List<Set<Integer>> ensembles = new ArrayList<>();
        Deque<Integer> aTraiter = new ArrayDeque<>();

        dfa.ajouterEtat(afn.fermetureEpsilon(Set.of(afn.etatInitial())), afn, numeros, ensembles, aTraiter);

        while (!aTraiter.isEmpty()) {
            int q = aTraiter.pop();
            Set<Integer> source = ensembles.get(q);
            for (char c : dfa.alphabet) {
                int destination = dfa.cible(afn, source, c, numeros, ensembles, aTraiter);
                if (destination != AUCUN) {
                    dfa.transitions.get(q).put(c, destination);
                }
            }
            // JOKER n'est emprunté que par les transitions joker
            dfa.autres.set(q, dfa.cible(afn, source, AutomateAhoUllman.JOKER, numeros, ensembles, aTraiter));
        }
        return dfa;
    }

    private int cible(AutomateAhoUllman afn, Set<Integer> source, char c,
                      Map<Set<Integer>, Integer> numeros, List<Set<Integer>> ensembles,
                      Deque<Integer> aTraiter) {
        Set<Integer> atteints = afn.transiter(source, c);
        if (atteints.isEmpty()) {
            return AUCUN;
        }
        return ajouterEtat(afn.fermetureEpsilon(atteints), afn, numeros, ensembles, aTraiter);
    }

    /** @return le numéro de l'état associé à l'ensemble, créé et mis en file s'il est nouveau */
    private int ajouterEtat(Set<Integer> ensemble, AutomateAhoUllman afn,
                            Map<Set<Integer>, Integer> numeros, List<Set<Integer>> ensembles,
                            Deque<Integer> aTraiter) {
        Integer existant = numeros.get(ensemble);
        if (existant != null) {
            return existant;
        }
        int numero = ensembles.size();
        numeros.put(ensemble, numero);
        ensembles.add(ensemble);
        transitions.add(new HashMap<>());
        autres.add(AUCUN);
        finaux.add(ensemble.contains(afn.etatFinal()));
        aTraiter.push(numero);
        return numero;
    }

    private int creerEtat(boolean final_) {
        transitions.add(new HashMap<>());
        autres.add(AUCUN);
        finaux.add(final_);
        return transitions.size() - 1;
    }

    /**
     * Minimisation par raffinement de partition (algorithme de Moore) : deux
     * états sont équivalents s'ils sont tous deux finaux ou non finaux et si leurs
     * transitions mènent, symbole par symbole, à des états équivalents. On
     * raffine la partition jusqu'à stabilité. L'état puits implicite est ajouté
     * pour compléter l'automate pendant le calcul, puis sa classe est retirée du
     * résultat (ainsi que tout état mort équivalent à lui).
     * @return un nouveau DFA reconnaissant le même langage avec un nombre minimum d'états
     */
    public Dfa minimiser() {
        int n = nbEtats();
        int puits = n;
        List<Character> symboles = new ArrayList<>(alphabet);
        int nbSymboles = symboles.size() + 1; // + colonne « autre »

        // automate complété : delta[q][s], le puits boucle sur lui-même
        int[][] delta = new int[n + 1][nbSymboles];
        for (int q = 0; q < n; q++) {
            for (int s = 0; s < nbSymboles; s++) {
                int destination = s < symboles.size() ? suivant(q, symboles.get(s)) : autres.get(q);
                delta[q][s] = destination == AUCUN ? puits : destination;
            }
        }
        java.util.Arrays.fill(delta[puits], puits);

        // partition initiale : finaux / non finaux
        int[] classe = new int[n + 1];
        for (int q = 0; q < n; q++) {
            classe[q] = finaux.get(q) ? 1 : 0;
        }
        int nbClasses = java.util.Arrays.stream(classe).distinct().toArray().length;
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
        Dfa minimal = new Dfa(alphabet);
        int classePuits = classe[puits];
        if (classe[ETAT_INITIAL] == classePuits) {
            minimal.creerEtat(false); // langage vide
            return minimal;
        }
        Map<Integer, Integer> representant = new HashMap<>();
        for (int q = n - 1; q >= 0; q--) {
            representant.put(classe[q], q);
        }
        Map<Integer, Integer> numeros = new HashMap<>();
        Deque<Integer> aTraiter = new ArrayDeque<>();
        numeros.put(classe[ETAT_INITIAL], minimal.creerEtat(finaux.get(ETAT_INITIAL)));
        aTraiter.push(classe[ETAT_INITIAL]);
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
                    destination = minimal.creerEtat(finaux.get(representant.get(classeDest)));
                    numeros.put(classeDest, destination);
                    aTraiter.push(classeDest);
                }
                if (s < symboles.size()) {
                    minimal.transitions.get(source).put(symboles.get(s), destination);
                } else {
                    minimal.autres.set(source, destination);
                }
            }
        }
        return minimal;
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

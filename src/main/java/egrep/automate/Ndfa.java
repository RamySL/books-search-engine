package egrep.automate;

import egrep.regex.RegexTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Étape 2 du sujet : automate fini non-déterministe avec epsilon-transitions
 * (epsilon-AFN), construit à partir d'un {@link RegexTree} selon la méthode
 * d'Aho-Ullman.</br>
 * Les états sont numérotés de 0 à {@code nbEtats() - 1}. Par construction,
 * l'automate a un unique état initial et un unique état final, et chaque état
 * a au plus une transition étiquetée ou au plus deux epsilon-transitions
 * sortantes.
 */
public final class Ndfa {

    /** Étiquette d'une transition : un caractère précis, ou n'importe lequel ('.'). */
    public sealed interface Etiquette {

        record Lettre(char c) implements Etiquette {
            @Override
            public String toString() {
                return String.valueOf(c);
            }
        }

        /** Caractère universel '.' : la transition accepte n'importe quel caractère. */
        record Joker() implements Etiquette {
            @Override
            public String toString() {
                return ".";
            }
        }
    }

    /** Transition étiquetée vers l'état destination. */
    public record Transition(Etiquette etiquette, int destination) {
        /** @return vrai si cette transition peut être empruntée en lisant c */
        public boolean accepte(char c) {
            return switch (etiquette) {
                case Etiquette.Lettre l -> l.c() == c;
                case Etiquette.Joker j -> true;
            };
        }

        @Override
        public String toString() {
            return "--" + etiquette + "--> q" + destination;
        }
    }

    /** Sous-automate associé à un sous-arbre : son état d'entrée et de sortie. */
    private record Fragment(int entree, int sortie) {}

    // transitions.get(q) : transitions étiquetées sortant de q
    private final List<List<Transition>> transitions = new ArrayList<>();
    // epsilons.get(q) : destinations des epsilon-transitions sortant de q
    private final List<List<Integer>> epsilons = new ArrayList<>();
    private int etatInitial;
    private int etatFinal;

    private Ndfa() {
    }

    /**
     * Construit l'epsilon-AFN reconnaissant le langage décrit par l'arbre.
     * @param arbre arbre syntaxique de la regex, non null
     * @return l'automate d'Aho-Ullman correspondant
     */
    public static Ndfa ahoUllman(RegexTree arbre) {
        Ndfa automate = new Ndfa();
        Fragment resultat = automate.construire(arbre);
        automate.etatInitial = resultat.entree();
        automate.etatFinal = resultat.sortie();
        return automate;
    }

    /**
     * Construction récursive d'Aho-Ullman : chaque sous-arbre produit un
     * fragment à une entrée et une sortie, que le noeud parent recolle.
     */
    private Fragment construire(RegexTree noeud) {
        return switch (noeud) {
            // entree --c--> sortie
            case RegexTree.Feuille f -> {
                int entree = nouvelEtat();
                int sortie = nouvelEtat();
                ajouterTransition(entree, new Etiquette.Lettre(f.c()), sortie);
                yield new Fragment(entree, sortie);
            }
            // entree --.--> sortie
            case RegexTree.Point p -> {
                int entree = nouvelEtat();
                int sortie = nouvelEtat();
                ajouterTransition(entree, new Etiquette.Joker(), sortie);
                yield new Fragment(entree, sortie);
            }
            // gauche.sortie --eps--> droit.entree, sans nouvel état
            case RegexTree.Concat c -> {
                Fragment gauche = construire(c.gauche());
                Fragment droit = construire(c.droit());
                ajouterEpsilon(gauche.sortie(), droit.entree());
                yield new Fragment(gauche.entree(), droit.sortie());
            }
            // nouvelle entrée vers les deux branches, les deux branches vers
            // une nouvelle sortie
            case RegexTree.Union u -> {
                int entree = nouvelEtat();
                Fragment gauche = construire(u.gauche());
                Fragment droit = construire(u.droit());
                int sortie = nouvelEtat();
                ajouterEpsilon(entree, gauche.entree());
                ajouterEpsilon(entree, droit.entree());
                ajouterEpsilon(gauche.sortie(), sortie);
                ajouterEpsilon(droit.sortie(), sortie);
                yield new Fragment(entree, sortie);
            }
            // on peut sauter le fils (mot vide) ou le répéter autant qu'on veut
            case RegexTree.Etoile e -> {
                int entree = nouvelEtat();
                Fragment fils = construire(e.fils());
                int sortie = nouvelEtat();
                ajouterEpsilon(entree, fils.entree());
                ajouterEpsilon(entree, sortie);
                ajouterEpsilon(fils.sortie(), fils.entree());
                ajouterEpsilon(fils.sortie(), sortie);
                yield new Fragment(entree, sortie);
            }
        };
    }

    private int nouvelEtat() {
        transitions.add(new ArrayList<>());
        epsilons.add(new ArrayList<>());
        return transitions.size() - 1;
    }

    private void ajouterTransition(int source, Etiquette etiquette, int destination) {
        transitions.get(source).add(new Transition(etiquette, destination));
    }

    private void ajouterEpsilon(int source, int destination) {
        epsilons.get(source).add(destination);
    }

    public int nbEtats() {
        return transitions.size();
    }

    public int etatInitial() {
        return etatInitial;
    }

    public int etatFinal() {
        return etatFinal;
    }

    /** @return les transitions étiquetées sortant de l'état (non modifiable) */
    public List<Transition> transitions(int etat) {
        return Collections.unmodifiableList(transitions.get(etat));
    }

    /** @return les destinations des epsilon-transitions de l'état (non modifiable) */
    public List<Integer> epsilons(int etat) {
        return Collections.unmodifiableList(epsilons.get(etat));
    }

    /** @return les caractères explicites apparaissant sur les transitions (sans les jokers), triés */
    public Set<Character> alphabet() {
        Set<Character> alphabet = new TreeSet<>();
        for (List<Transition> depuisEtat : transitions) {
            for (Transition t : depuisEtat) {
                if (t.etiquette() instanceof Etiquette.Lettre l) {
                    alphabet.add(l.c());
                }
            }
        }
        return alphabet;
    }

    /**
     * Epsilon-fermeture d'un ensemble d'états : tous les états atteignables
     * par des epsilon-transitions uniquement. Sert à la méthode des
     * sous-ensembles (déterminisation).
     * @param etats ensemble de départ
     * @return l'ensemble fermé, trié
     */
    public Set<Integer> fermetureEpsilon(Set<Integer> etats) {
        Set<Integer> fermeture = new TreeSet<>(etats);
        Deque<Integer> aTraiter = new ArrayDeque<>(etats);
        while (!aTraiter.isEmpty()) {
            for (int suivant : epsilons.get(aTraiter.pop())) {
                if (fermeture.add(suivant)) {
                    aTraiter.push(suivant);
                }
            }
        }
        return fermeture;
    }

    /**
     * États atteints depuis un ensemble d'états en lisant un caractère,
     * sans epsilon-fermeture.
     */
    public Set<Integer> transiter(Set<Integer> etats, char symbole) {
        return destinations(etats, t -> t.accepte(symbole));
    }

    /**
     * États atteints en lisant un caractère absent de {@link #alphabet()} :
     * seules les transitions joker s'appliquent. Sans epsilon-fermeture.
     */
    public Set<Integer> transiterHorsAlphabet(Set<Integer> etats) {
        return destinations(etats, t -> t.etiquette() instanceof Etiquette.Joker);
    }

    private Set<Integer> destinations(Set<Integer> etats, Predicate<Transition> empruntable) {
        Set<Integer> resultat = new TreeSet<>();
        for (int etat : etats) {
            for (Transition t : transitions.get(etat)) {
                if (empruntable.test(t)) {
                    resultat.add(t.destination());
                }
            }
        }
        return resultat;
    }

    /**
     * Simule l'epsilon-AFN sur un mot entier.
     * @return vrai si le mot complet appartient au langage de l'automate
     */
    public boolean accepte(String mot) {
        Set<Integer> courants = fermetureEpsilon(Set.of(etatInitial));
        for (char c : mot.toCharArray()) {
            courants = fermetureEpsilon(transiter(courants, c));
            if (courants.isEmpty()) {
                return false;
            }
        }
        return courants.contains(etatFinal);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nombre d'etats : ").append(nbEtats()).append('\n');
        sb.append("Etat initial : q").append(etatInitial).append('\n');
        sb.append("Etat final : q").append(etatFinal).append("\n\n");
        sb.append("Transitions :\n");
        for (int q = 0; q < nbEtats(); q++) {
            for (Transition t : transitions.get(q)) {
                sb.append("q").append(q).append(' ').append(t).append('\n');
            }
            for (int destination : epsilons.get(q)) {
                sb.append("q").append(q).append(" --epsilon--> q")
                        .append(destination).append('\n');
            }
        }
        return sb.toString();
    }
}

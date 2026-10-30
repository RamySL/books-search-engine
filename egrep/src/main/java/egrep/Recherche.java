package egrep;

import egrep.automate.Determinisation;
import egrep.automate.Dfa;
import egrep.automate.Minimisation;
import egrep.automate.Ndfa;
import egrep.kmp.Kmp;
import egrep.regex.RegexParser;
import egrep.regex.RegexTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stratégie de recherche d'un motif dans une ligne : test rapide de présence,
 * et repérage des occurrences pour l'affichage en couleur. Les deux stratégies
 * du sujet sont {@link ParKmp} et {@link ParAutomate}, et {@link #pour} choisit
 * entre elles.
 */
public sealed interface Recherche {

    /**
     * Choisit la stratégie : KMP si la regex n'est qu'une suite de
     * concaténations de lettres (et {@code kmpAutorise}), sinon l'automate
     * déterministe minimal construit par les étapes 1 à 4 du sujet.
     * @throws IllegalArgumentException si la regex est mal formée
     */
    static Recherche pour(String regex, boolean kmpAutorise) {
        RegexTree arbre = RegexParser.analyser(regex);              // étape 1
        Optional<String> mot = suiteDeLettres(arbre);
        if (kmpAutorise && mot.isPresent()) {
            return new ParKmp(new Kmp(mot.get()));
        }
        // .*regex filtre les lignes en un seul parcours ; la regex seule délimite les occurrences
        RegexTree facteur = new RegexTree.Concat(new RegexTree.Etoile(new RegexTree.Point()), arbre);
        return new ParAutomate(compiler(facteur), compiler(arbre));
    }

    /** Étapes 2 à 4 : arbre → epsilon-AFN → DFA → DFA minimal. */
    private static Dfa compiler(RegexTree arbre) {
        Ndfa ndfa = Ndfa.ahoUllman(arbre);                          // étape 2
        Dfa dfa = Determinisation.sousEnsembles(ndfa);              // étape 3
        return Minimisation.moore(dfa);                             // étape 4
    }

    /**
     * @return le mot lu sur les feuilles si l'arbre n'est qu'une suite de
     *         concaténations de lettres (les parenthèses ne laissent pas de trace
     *         dans l'arbre), vide sinon
     */
    private static Optional<String> suiteDeLettres(RegexTree arbre) {
        return switch (arbre) {
            case RegexTree.Feuille f -> Optional.of(String.valueOf(f.c()));
            case RegexTree.Concat c -> suiteDeLettres(c.gauche())
                    .flatMap(gauche -> suiteDeLettres(c.droit()).map(droit -> gauche + droit));
            default -> Optional.empty();
        };
    }

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
     * seul parcours (étape 5) ; celui de la regex seule, essayé à chaque position,
     * délimite les occurrences (uniquement sur les lignes retenues).
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

package egrep;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

public class Egrep {

    /**
     * Opérateur interne de concaténation dans la notation postfixe. En ERE la
     * concaténation est implicite et '.' désigne le caractère universel : on
     * utilise donc un caractère de contrôle qui ne peut pas apparaître dans une
     * regex saisie.
     */
    static final char CONCAT = '\u0001';

    /** Caractères ayant un rôle syntaxique dans la regex d'entrée. */
    private static final String SPECIAUX = "|()*+";

    /**
     * Construction de l'arbre représentant l'expression regex passée.
     * Supporte : lettre, '.' (caractère universel), concaténation implicite,
     * '|', '*', '+' (e+ vaut ee*) et les parenthèses.
     * @param input expression regex
     * @return arbre AST représentant la regex
     * @throws IllegalArgumentException si la regex est mal formée
     */
    static RegexTree regexToAst(String input) {
        String regexPostFixe = shuntingYard(input);
        Deque<RegexTree> pile = new ArrayDeque<>();

        for (char c : regexPostFixe.toCharArray()) {
            switch (c) {
                case '*' -> pile.push(new RegexTree.Etoile(depiler(pile, input)));
                case '+' -> {
                    RegexTree fils = depiler(pile, input);
                    pile.push(new RegexTree.Concat(fils, new RegexTree.Etoile(fils)));
                }
                case CONCAT -> {
                    RegexTree droit = depiler(pile, input);
                    RegexTree gauche = depiler(pile, input);
                    pile.push(new RegexTree.Concat(gauche, droit));
                }
                case '|' -> {
                    RegexTree droit = depiler(pile, input);
                    RegexTree gauche = depiler(pile, input);
                    pile.push(new RegexTree.Union(gauche, droit));
                }
                case '.' -> pile.push(new RegexTree.Point());
                default -> pile.push(new RegexTree.Feuille(c));
            }
        }
        if (pile.size() != 1) {
            throw new IllegalArgumentException("Regex mal formee : " + input);
        }
        return pile.pop();
    }

    private static RegexTree depiler(Deque<RegexTree> pile, String input) {
        if (pile.isEmpty()) {
            throw new IllegalArgumentException("Regex mal formee : " + input);
        }
        return pile.pop();
    }

    /**
     * Algorithme de shuntingYard pour production de notation post fixe.</br>
     * La concaténation implicite est d'abord rendue explicite (voir
     * {@link #CONCAT}). Priorités : '*' et '+' > concaténation > '|'.
     * Les parenthèses sont consommées et n'apparaissent pas en sortie ; '.'
     * (caractère universel) est une opérande comme les lettres.
     * @param input expression regex
     * @return expression regex en notation post fixe (polonaise inverse)
     * @throws IllegalArgumentException si les parenthèses sont mal appariées
     */
    static String shuntingYard(String input) {
        StringBuilder sortie = new StringBuilder();
        Deque<Character> pile = new ArrayDeque<>();

        for (char c : rendreConcatenationExplicite(input).toCharArray()) {
            switch (c) {
                // opérateurs postfixes unaires : s'appliquent à l'opérande déjà en sortie
                case '*', '+' -> sortie.append(c);
                case '(' -> pile.push(c);
                case ')' -> {
                    while (!pile.isEmpty() && pile.peek() != '(') {
                        sortie.append(pile.pop());
                    }
                    if (pile.isEmpty()) {
                        throw new IllegalArgumentException("Parenthese fermante sans ouvrante : " + input);
                    }
                    pile.pop();
                }
                case CONCAT, '|' -> {
                    // associativité à gauche : on dépile les opérateurs de priorité >=
                    while (!pile.isEmpty() && pile.peek() != '('
                            && priorite(pile.peek()) >= priorite(c)) {
                        sortie.append(pile.pop());
                    }
                    pile.push(c);
                }
                default -> sortie.append(c);
            }
        }
        while (!pile.isEmpty()) {
            char c = pile.pop();
            if (c == '(') {
                throw new IllegalArgumentException("Parenthese ouvrante sans fermante : " + input);
            }
            sortie.append(c);
        }
        return sortie.toString();
    }

    private static int priorite(char operateur) {
        return operateur == CONCAT ? 2 : 1;
    }

    /**
     * Insère {@link #CONCAT} entre deux éléments consécutifs : quand ce qui
     * précède peut terminer une opérande (lettre, '.', ')', '*', '+') et ce qui
     * suit peut en commencer une (lettre, '.', '(').
     */
    static String rendreConcatenationExplicite(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (i > 0) {
                char prec = input.charAt(i - 1);
                boolean finOperande = SPECIAUX.indexOf(prec) == -1 || prec == ')'
                        || prec == '*' || prec == '+';
                boolean debutOperande = SPECIAUX.indexOf(c) == -1 || c == '(';
                if (finOperande && debutOperande) {
                    sb.append(CONCAT);
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Chaîne complète regex → arbre → ε-AFN → DFA → DFA minimal, appliquée à
     * {@code .*regex} pour qu'un seul parcours d'une ligne détecte un facteur.
     * @param regex expression ERE (sous-ensemble supporté)
     * @return le DFA minimal à utiliser avec {@link Dfa#acceptePrefixe(String)}
     */
    static Dfa compiler(String regex) {
        RegexTree facteur = new RegexTree.Concat(
                new RegexTree.Etoile(new RegexTree.Point()), regexToAst(regex));
        return Dfa.depuisAutomate(AutomateAhoUllman.depuisArbre(facteur)).minimiser();
    }

    /** @return vrai si la ligne contient un facteur reconnu par la regex compilée */
    static boolean correspond(Dfa dfa, String ligne) {
        return dfa.acceptePrefixe(ligne);
    }

    /**
     * @return vrai si la regex est une simple suite de lettres : ni opérateur, ni
     *         parenthèse, ni point. La recherche se ramène alors à celle d'un facteur.
     */
    static boolean estFacteurSimple(String regex) {
        if (regex.isEmpty()) {
            return false;
        }
        for (char c : regex.toCharArray()) {
            if ("|()*+.".indexOf(c) != -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Choisit la stratégie de recherche : KMP si le motif est un facteur simple
     * (et {@code kmpAutorise}), sinon l'automate déterministe minimal.
     * @return un prédicat vrai sur les lignes contenant le motif
     */
    static Predicate<String> chercheur(String regex, boolean kmpAutorise) {
        if (kmpAutorise && estFacteurSimple(regex)) {
            return new Kmp(regex)::contient;
        }
        Dfa dfa = compiler(regex);
        return ligne -> correspond(dfa, ligne);
    }

    /**
     * Usage : egrep [--dfa] MOTIF FICHIER — affiche les lignes de FICHIER contenant
     * une occurrence de MOTIF. {@code --dfa} interdit KMP (pour comparer les deux méthodes).
     */
    public static void main(String[] args) throws IOException {
        boolean optionDfa = args.length == 3 && args[0].equals("--dfa");
        boolean kmpAutorise = !optionDfa;
        if (args.length != 2 && !optionDfa) {
            System.err.println("Usage : egrep [--dfa] MOTIF FICHIER");
            System.exit(2);
        }
        String motif = args[args.length - 2];
        String fichier = args[args.length - 1];
        Predicate<String> chercheur;
        try {
            chercheur = chercheur(motif, kmpAutorise);
        } catch (IllegalArgumentException e) {
            System.err.println("egrep : " + e.getMessage());
            System.exit(2);
            return;
        }
        PrintStream sortie = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        // le décodage UTF-8 remplace les octets invalides au lieu de lever une exception
        try (BufferedReader lecteur = new BufferedReader(
                new InputStreamReader(new FileInputStream(fichier), StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                if (chercheur.test(ligne)) {
                    sortie.println(ligne);
                }
            }
        }
        sortie.flush();
    }

}

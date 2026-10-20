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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
     */
    static Recherche chercheur(String regex, boolean kmpAutorise) {
        if (kmpAutorise && estFacteurSimple(regex)) {
            return new Recherche.ParKmp(new Kmp(regex));
        }
        Dfa motif = Dfa.depuisAutomate(AutomateAhoUllman.depuisArbre(regexToAst(regex))).minimiser();
        return new Recherche.ParAutomate(compiler(regex), motif);
    }

    // codes ANSI, mêmes couleurs que grep --color
    private static final String ROUGE = "\u001B[1;31m";
    private static final String VERT = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAS = "\u001B[1m";
    private static final String FIN_COULEUR = "\u001B[0m";

    /** @return la ligne où chaque occurrence est encadrée par les codes ANSI du rouge */
    static String surligner(String ligne, List<int[]> occurrences) {
        StringBuilder sb = new StringBuilder();
        int position = 0;
        for (int[] occurrence : occurrences) {
            sb.append(ligne, position, occurrence[0])
                    .append(ROUGE).append(ligne, occurrence[0], occurrence[1]).append(FIN_COULEUR);
            position = occurrence[1];
        }
        return sb.append(ligne, position, ligne.length()).toString();
    }

    private static void usage() {
        System.err.println("Usage : egrep [--dfa] [--brut] MOTIF FICHIER");
        System.err.println("  --dfa   force l'automate meme si le motif est une simple suite de lettres");
        System.err.println("  --brut  sortie identique a egrep : lignes seules, sans couleur ni numero");
        System.exit(2);
    }

    /**
     * Usage : egrep [--dfa] [--brut] MOTIF FICHIER — affiche les lignes de FICHIER
     * contenant une occurrence de MOTIF, numérotées et avec les occurrences en
     * rouge, suivies d'un récapitulatif. {@code --dfa} interdit KMP (pour comparer
     * les deux méthodes) ; {@code --brut} donne la sortie exacte d'egrep.
     */
    public static void main(String[] args) throws IOException {
        boolean kmpAutorise = true;
        boolean brut = false;
        List<String> positionnels = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "--dfa" -> kmpAutorise = false;
                case "--brut" -> brut = true;
                default -> positionnels.add(arg);
            }
        }
        if (positionnels.size() != 2) {
            usage();
        }
        String motif = positionnels.get(0);
        String fichier = positionnels.get(1);
        long debut = System.nanoTime();
        Recherche recherche;
        try {
            recherche = chercheur(motif, kmpAutorise);
        } catch (IllegalArgumentException e) {
            System.err.println("egrep : " + e.getMessage());
            System.exit(2);
            return;
        }
        PrintStream sortie = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        if (!brut) {
            sortie.println(GRAS + "Motif \"" + motif + "\" dans " + fichier + FIN_COULEUR
                    + "  (strategie : " + recherche.description() + ")");
            sortie.println(CYAN + "-".repeat(72) + FIN_COULEUR);
        }
        int numero = 0;
        int lignesTrouvees = 0;
        int nbOccurrences = 0;
        // le décodage UTF-8 remplace les octets invalides au lieu de lever une exception
        try (BufferedReader lecteur = new BufferedReader(
                new InputStreamReader(new FileInputStream(fichier), StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                numero++;
                if (!recherche.contient(ligne)) {
                    continue;
                }
                lignesTrouvees++;
                if (brut) {
                    sortie.println(ligne);
                } else {
                    List<int[]> occurrences = recherche.occurrences(ligne);
                    nbOccurrences += occurrences.size();
                    sortie.printf("%s%6d%s %s|%s %s%n", VERT, numero, FIN_COULEUR,
                            CYAN, FIN_COULEUR, surligner(ligne, occurrences));
                }
            }
        } catch (IOException e) {
            System.err.println("egrep : impossible de lire " + fichier + " (" + e.getMessage() + ")");
            System.exit(2);
        }
        if (!brut) {
            long millis = (System.nanoTime() - debut) / 1_000_000;
            sortie.println(CYAN + "-".repeat(72) + FIN_COULEUR);
            sortie.println(GRAS + lignesTrouvees + " ligne(s) trouvee(s)" + FIN_COULEUR
                    + " sur " + numero + ", " + nbOccurrences + " occurrence(s), en " + millis + " ms");
        }
        sortie.flush();
        // comme egrep : 0 si au moins une ligne trouvée, 1 sinon
        System.exit(lignesTrouvees > 0 ? 0 : 1);
    }

}

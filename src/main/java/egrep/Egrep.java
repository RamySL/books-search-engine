package egrep;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO: faire une interface de pile pour faire plus propre
// TODO: optimisation des algos

public class Egrep {
    /**
     * encapsultation de l'état courant de la pile et de la sortie 
     * pour l'algorithme de shuntingYard
     */
    private static class PopResult {
        List<Character> stack;
        String output;

        PopResult(List<Character> stack, String output) {
            this.stack = stack;
            this.output = output;
        }
    }
    /**
     * Construction de l'arbre représentant l'expression regex passée
     * @pre Suppose que input forme une regex correct
     * @param input expression regex
     * @return arbre AST représentant la regex
     */
    static RegexTree regexToAst(String input) {
        String regexPostFixe = shuntingYard(input);
        List<RegexTree> stack = new ArrayList<>();

        for(char c : regexPostFixe.toCharArray()){

            if(c == '*'){
                // pop
                RegexTree topNode = stack.remove(stack.size() - 1);
                stack.add(new RegexTree.Etoile(topNode));
            }
            else if(c == '.'){
                // pop
                RegexTree topNode = stack.remove(stack.size() - 1);
                RegexTree secTopNode = stack.remove(stack.size() - 1);
                stack.add(new RegexTree.Concat(secTopNode, topNode));
            }else if(c == '|'){
                // TODO: redondance avec if précédent
                RegexTree topNode = stack.remove(stack.size() - 1);
                RegexTree secTopNode = stack.remove(stack.size() - 1);
                stack.add(new RegexTree.Union(secTopNode, topNode));
            }
            else{
                // ASCII
                stack.add(new RegexTree.Feuille(c));
            }
        }
        // d'après précondition on est sure d'avoir qu'un élement restant
        return stack.get(0);
    }

    /**
     * Algorithme de shuntingYard pour production de notation post fixe.</br>
     * Il prend en compte les regex simple, non étendu avec les règles de priorité
     * suivantes : '*' > '.' > '|'
     * @param input expression regex
     * @return expression regex en notation post fixe (polonaise inverse)
     */
    // @FIXME : les parenthèse à traiter
    static String shuntingYard(String input) {
        StringBuilder output = new StringBuilder();
        List<Character> stack = new ArrayList<>();
        PopResult obj = null;

        for (char c : input.toCharArray()) {
            if (c == '*') {
                // on dépile juste les '*' vers la sortie qui viennent avant puis empile
                obj = popOps(stack, "*");
                stack = obj.stack;
                stack.add(c);
            }
            if (c == '.') {
                // dépile : '.', '*', puis empile
                obj = popOps(stack, "*.");
                stack = obj.stack;
                stack.add(c);
            }
            if (c == '|') {
                // on dépile tous les opérateurs, puis empile
                obj = popOps(stack, "*.|");
                stack = obj.stack;
                stack.add(c);
            }
            if ("|.*".indexOf(c) == -1) {
                // ASCII
                output.append(c);
            } else {
                output.append(obj.output);
            }
        }
        // on dépile tout ce qui reste comme opérateurs
        Collections.reverse(stack);
        StringBuilder reste = new StringBuilder();
        for (char c : stack) {
            reste.append(c);
        }
        return output.toString() + reste.toString();
    }
    /**
     * Dépile toutes les oprérondes incluent dans ops et qui se suivent de la pile</br>
     * et les empilent en sortie.
     * @param stack Pile des charactères de l'expression regex
     * @param ops les opérateurs à dépiler
     * @return La pile résultantes des dépilements, sortie avec les opérondes dépilés ajoutés
     */
    static PopResult popOps(List<Character> stack, String ops) {
        StringBuilder output = new StringBuilder();
        char curr;
        boolean end = false;

        while (!end && !stack.isEmpty()) {
            curr = stack.get(stack.size() - 1);
            // si c'est un opérateur
            if (ops.indexOf(curr) != -1) {
                stack.remove(stack.size() - 1);
                output.append(curr);
            } else {
                end = true;
            }
        }

        return new PopResult(stack, output.toString());
    }

    public static void main(String[] args) {
        System.out.println(regexToAst("ab*c.|"));
    }

}

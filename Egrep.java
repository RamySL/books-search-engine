import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Egrep {

    private static class PopResult {
        List<Character> stack;
        String output;

        PopResult(List<Character> stack, String output) {
            this.stack = stack;
            this.output = output;
        }
    }

    static void regexToAst(String input) {

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
                obj = popInfOp(stack, "*");
                stack = obj.stack;
                stack.add(c);
            }
            if (c == '.') {
                // dépile : '.', '*', puis empile
                obj = popInfOp(stack, "*.");
                stack = obj.stack;
                stack.add(c);
            }
            if (c == '|') {
                // on dépile tous les opérateurs, puis empile
                obj = popInfOp(stack, "*.|");
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
     * 
     * @param stack Pile des charactères de l'expression regex
     * @param ops les opérateurs à dépiler
     * @return
     */
    static PopResult popInfOp(List<Character> stack, String ops) {
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
        System.out.println(shuntingYard("a|b*.c"));
    }
}

package egrep;

/**
 * Arbre syntaxique d'une regex : lettre, point, concaténation, '|' et '*'.</br>
 * L'arité de chaque opérateur est portée par le type.
 */
public sealed interface RegexTree {

    record Feuille(char c) implements RegexTree {
        @Override
        public String toString() {
            return String.valueOf(c);
        }
    }

    /** Caractère universel '.' : accepte n'importe quel caractère. */
    record Point() implements RegexTree {
        @Override
        public String toString() {
            return ".";
        }
    }

    record Etoile(RegexTree fils) implements RegexTree {
        @Override
        public String toString() {
            return "*(" + fils + ")";
        }
    }

    record Concat(RegexTree gauche, RegexTree droit) implements RegexTree {
        @Override
        public String toString() {
            return ".(" + gauche + "," + droit + ")";
        }
    }

    record Union(RegexTree gauche, RegexTree droit) implements RegexTree {
        @Override
        public String toString() {
            return "|(" + gauche + "," + droit + ")";
        }
    }
}

package egrep;

public class AutomateAhoUllman {
    private static final int MAX_ETATS = 100;
    private static final int MAX_SYMBOLES = 256;

    enum TypeNoeud {
        CARACTERE,
        UNION,
        CONCAT,
        ETOILE,
        ALTERNATIVE
    }

    public static class Noeud {
        private final TypeNoeud type;
        private final char valeur;
        private final int position;
        private final boolean nullable;
        private final Noeud gauche;
        private final Noeud droit;

        Noeud(TypeNoeud type, char valeur, Noeud gauche, Noeud droit) {
            this.type = type;
            this.valeur = valeur;
            this.position = 0;
            this.nullable = false;
            this.gauche = gauche;
            this.droit = droit;
        }
    }

    private static class Automate {
        private int nbEtats;
        private int etatInitial;
        private final boolean[] etatsFinaux = new boolean[MAX_ETATS];
        private final boolean[][][] transitions =
                new boolean[MAX_ETATS][MAX_SYMBOLES][MAX_ETATS];
        private final boolean[][] epsilon =
                new boolean[MAX_ETATS][MAX_ETATS];
    }

    private static class Fragment {
        private final int entree;
        private final int sortie;

        private Fragment(int entree, int sortie) {
            this.entree = entree;
            this.sortie = sortie;
        }
    }

    private static int nouvelEtat(Automate automate) {
        if (automate.nbEtats >= MAX_ETATS) {
            return -1;
        }
        return automate.nbEtats++;
    }

    private static void ajouterTransition(
            Automate automate, int source, char symbole, int destination) {
        if (source >= 0 && destination >= 0 && symbole < MAX_SYMBOLES) {
            automate.transitions[source][symbole][destination] = true;
        }
    }

    private static void ajouterEpsilon(
            Automate automate, int source, int destination) {
        if (source >= 0 && destination >= 0) {
            automate.epsilon[source][destination] = true;
        }
    }

    private static Fragment construireFragment(Noeud noeud, Automate automate) {
        if (noeud == null) {
            return new Fragment(-1, -1);
        }

        Fragment fragment = new Fragment(
                nouvelEtat(automate), nouvelEtat(automate));
        if (fragment.entree < 0 || fragment.sortie < 0) {
            return new Fragment(-1, -1);
        }

        if (noeud.type == TypeNoeud.CARACTERE) {
            ajouterTransition(automate, fragment.entree,
                    noeud.valeur, fragment.sortie);
            return fragment;
        }

        if (noeud.type == TypeNoeud.ETOILE) {
            Fragment enfant = construireFragment(noeud.gauche, automate);
            if (enfant.entree < 0) {
                return new Fragment(-1, -1);
            }

            ajouterEpsilon(automate, fragment.entree, fragment.sortie);
            ajouterEpsilon(automate, fragment.entree, enfant.entree);
            ajouterEpsilon(automate, enfant.sortie, enfant.entree);
            ajouterEpsilon(automate, enfant.sortie, fragment.sortie);
            return fragment;
        }

        Fragment gauche = construireFragment(noeud.gauche, automate);
        Fragment droite = construireFragment(noeud.droit, automate);
        if (gauche.entree < 0 || droite.entree < 0) {
            return new Fragment(-1, -1);
        }

        if (noeud.type == TypeNoeud.CONCAT) {
            ajouterEpsilon(automate, fragment.entree, gauche.entree);
            ajouterEpsilon(automate, gauche.sortie, droite.entree);
            ajouterEpsilon(automate, droite.sortie, fragment.sortie);
        } else {
            // UNION et ALTERNATIVE sont construits comme une union.
            ajouterEpsilon(automate, fragment.entree, gauche.entree);
            ajouterEpsilon(automate, fragment.entree, droite.entree);
            ajouterEpsilon(automate, gauche.sortie, fragment.sortie);
            ajouterEpsilon(automate, droite.sortie, fragment.sortie);
        }
        return fragment;
    }

    /** Construit un AFN avec transitions epsilon par la methode d'Aho-Ullman. */
    public static void automateDepuisArbre(Noeud racine, Automate automate) {
        if (automate == null) {
            return;
        }

        automate.nbEtats = 0;
        automate.etatInitial = 0;
        java.util.Arrays.fill(automate.etatsFinaux, false);
        for (boolean[][] matrice : automate.transitions) {
            for (boolean[] ligne : matrice) {
                java.util.Arrays.fill(ligne, false);
            }
        }
        for (boolean[] ligne : automate.epsilon) {
            java.util.Arrays.fill(ligne, false);
        }

        if (racine == null) {
            return;
        }

        Fragment resultat = construireFragment(racine, automate);
        if (resultat.entree < 0) {
            automate.nbEtats = 0;
            return;
        }

        automate.etatInitial = resultat.entree;
        automate.etatsFinaux[resultat.sortie] = true;
    }

    private static void afficherAutomate(Automate automate) {
        System.out.println("Nombre d'etats : " + automate.nbEtats);
        System.out.println("Etat initial : q" + automate.etatInitial);

        System.out.print("Etats finaux :");
        for (int etat = 0; etat < automate.nbEtats; etat++) {
            if (automate.etatsFinaux[etat]) {
                System.out.print(" q" + etat);
            }
        }
        System.out.println("\n\nTransitions :");

        for (int source = 0; source < automate.nbEtats; source++) {
            for (int symbole = 0; symbole < MAX_SYMBOLES; symbole++) {
                for (int destination = 0;
                     destination < automate.nbEtats; destination++) {
                    if (automate.transitions[source][symbole][destination]) {
                        System.out.printf("q%d --%c--> q%d%n",
                                source, (char) symbole, destination);
                    }
                }
            }
            for (int destination = 0;
                 destination < automate.nbEtats; destination++) {
                if (automate.epsilon[source][destination]) {
                    System.out.printf("q%d --epsilon--> q%d%n",
                            source, destination);
                }
            }
        }
    }

    public static void main(String[] args) {
        // Expression choisie : a(b|c)*
        Noeud feuilleA = new Noeud(TypeNoeud.CARACTERE, 'a', null, null);
        Noeud feuilleB = new Noeud(TypeNoeud.CARACTERE, 'b', null, null);
        Noeud feuilleC = new Noeud(TypeNoeud.CARACTERE, 'c', null, null);

        Noeud unionBC = new Noeud(TypeNoeud.UNION, '\0', feuilleB, feuilleC);
        Noeud etoileBC = new Noeud(TypeNoeud.ETOILE, '\0', unionBC, null);
        Noeud expression = new Noeud(TypeNoeud.CONCAT, '\0', feuilleA, etoileBC);

        Automate automate = new Automate();
        automateDepuisArbre(expression, automate);
        afficherAutomate(automate);
    }
}

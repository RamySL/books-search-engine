package egrep;

/**
 * Recherche d'un facteur (suite de lettres, sans opérateur) dans un texte par
 * l'algorithme de Knuth-Morris-Pratt : O(m) de précalcul, puis O(n) pour un
 * texte de longueur n, sans jamais reculer dans le texte.
 */
public final class Kmp {

    private final char[] facteur;
    // report[j] : indice du facteur où reprendre quand F[j] échoue ; -1 = avancer dans le texte
    private final int[] report;

    public Kmp(String facteur) {
        this.facteur = facteur.toCharArray();
        this.report = calculerReport(this.facteur);
    }

    /**
     * Table de report de Knuth : report[j] est la longueur du plus long bord
     * propre de F[0..j-1] (préfixe qui est aussi suffixe), sauf que si
     * F[report[j]] == F[j] on prend directement report[report[j]], car
     * reprendre là échouerait sur le même caractère. report[0] vaut -1.
     * Exemple : "mami" donne [-1,0,-1,1,0].
     */
    static int[] calculerReport(char[] f) {
        int m = f.length;
        int[] report = new int[m + 1];
        report[0] = -1;
        int bord = -1; // longueur du plus long bord de F[0..i-1]
        for (int i = 0; i < m; i++) {
            while (bord >= 0 && f[bord] != f[i]) {
                bord = report[bord];
            }
            bord++; // bord de F[0..i]
            report[i + 1] = (i + 1 < m && f[bord] == f[i + 1]) ? report[bord] : bord;
        }
        return report;
    }

    int[] report() {
        return report.clone();
    }

    /** @return vrai si le facteur apparaît dans le texte */
    public boolean contient(String texte) {
        int m = facteur.length;
        if (m == 0) {
            return true;
        }
        int j = 0; // nombre de caractères du facteur déjà alignés
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            while (j >= 0 && facteur[j] != c) {
                j = report[j];
            }
            j++;
            if (j == m) {
                return true;
            }
        }
        return false;
    }
}

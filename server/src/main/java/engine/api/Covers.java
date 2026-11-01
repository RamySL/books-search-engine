package engine.api;

/**
 * Couvertures des livres. On ne les télécharge pas : l'URL se déduit du
 * numéro Gutenberg, et le navigateur va la chercher directement. Certains
 * livres n'en ont pas ; le client affiche alors une image de remplacement.
 */
public final class Covers {

    private Covers() {
    }

    /** @return l'URL de la couverture moyenne du livre sur gutenberg.org */
    public static String url(int bookId) {
        return "https://www.gutenberg.org/cache/epub/" + bookId + "/pg" + bookId + ".cover.medium.jpg";
    }
}

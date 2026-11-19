package engine.api;

/**
 * Couvertures des livres. On ne les télécharge pas : l'URL se déduit du
 * numéro Gutenberg, et le navigateur va la chercher directement. Certains
 * livres n'en ont pas ; le client affiche alors une image de remplacement.
 * <p>
 * On passe par le miroir pglaf, comme l'étape corpus : www.gutenberg.org
 * répond 503 aux images demandées depuis une autre page.
 */
public final class Covers {

    private Covers() {
    }

    /** @return l'URL de la couverture moyenne du livre sur le miroir pglaf de Gutenberg */
    public static String url(int bookId) {
        return "https://gutenberg.pglaf.org/cache/epub/" + bookId + "/pg" + bookId + ".cover.medium.jpg";
    }
}

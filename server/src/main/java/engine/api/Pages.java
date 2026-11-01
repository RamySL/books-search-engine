package engine.api;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

/** Pagination commune à toutes les listes de l'API : pages de 20, numérotées à partir de 1. */
public final class Pages {

    public static final int SIZE = 20;

    private Pages() {
    }

    /** @return le nombre d'éléments à sauter pour arriver à la page (OFFSET en SQL) */
    public static long offset(int page) {
        return (long) (page - 1) * SIZE;
    }

    /**
     * @return l'URL relative de la page suivante, c'est-à-dire la requête en
     *         cours avec {@code page + 1}, ou null si la page est la dernière.
     *         Elle est relative pour rester juste derrière le proxy de Vite.
     */
    public static String next(int page, long count) {
        if ((long) page * SIZE >= count) {
            return null;
        }
        UriComponents uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page + 1)
                .build();
        return uri.getPath() + "?" + uri.getQuery();
    }
}

package engine.api;

import java.util.List;

/**
 * Une page d'une liste trop longue pour être envoyée d'un coup (cours 3, §2).
 *
 * @param count   nombre total d'éléments, toutes pages confondues
 * @param next    URL relative de la page suivante, ou null sur la dernière page
 * @param results éléments de cette page, {@link Pages#SIZE} au plus
 */
public record Page<T>(long count, String next, List<T> results) {
}

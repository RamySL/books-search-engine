package engine.api;

import java.util.List;

/**
 * Ligne d'un livre qui contient au moins une occurrence d'une RegEx, pour
 * l'afficher surlignée dans la vue Livre.
 *
 * @param number      numéro de la ligne dans le texte, à partir de 1
 * @param line        la ligne
 * @param occurrences positions des occurrences dans la ligne, sous la forme
 *                    {début, fin exclue}, comme {@code Recherche.occurrences} d'egrep
 */
public record Excerpt(int number, String line, List<int[]> occurrences) {
}

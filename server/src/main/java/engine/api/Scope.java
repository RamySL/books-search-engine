package engine.api;

/** Portée d'une recherche par RegEx (paramètre {@code scope}). */
public enum Scope {
    /** les termes de l'index : rapide, mais une RegEx ne peut pas couvrir plusieurs mots */
    INDEX,
    /** le texte intégral des livres, ligne par ligne : beaucoup plus lent */
    TEXT
}

// Contrat d'API entre le client et le serveur. Les types reprennent un à un
// les records Java du package engine.api : toute modification se fait des
// deux côtés en même temps.

/** Ordre des résultats, toujours décroissant. */
export type Sort = 'occurrences' | 'closeness' | 'pagerank'

/** Portée d'une recherche par RegEx : les termes de l'index, ou le texte intégral (lent). */
export type Scope = 'index' | 'text'

/** Livre dans une liste (résultats, suggestions, catalogue, voisins). */
export interface BookSummary {
  id: number
  title: string
  author: string | null
  cover: string
  /** null hors d'une recherche */
  occurrences: number | null
  closeness: number
  pagerank: number
}

/** Fiche complète d'un livre. */
export interface Book {
  id: number
  title: string
  author: string | null
  language: string
  wordCount: number
  /** nombre de voisins dans le graphe de Jaccard */
  degree: number
  closeness: number
  pagerank: number
  cover: string
}

/** Une page d'une liste : 20 éléments au plus, `next` vaut null sur la dernière page. */
export interface Page<T> {
  count: number
  next: string | null
  results: T[]
}

export interface SearchResponse extends Page<BookSummary> {
  /** livres proches des premiers résultats dans le graphe de Jaccard */
  suggestions: BookSummary[]
  /** mots de l'index cherchés (requête normalisée, ou termes reconnus par la RegEx) */
  terms: string[]
  /** vrai si la RegEx reconnaît trop de termes et qu'on n'a gardé que les plus fréquents */
  termsTruncated: boolean
  durationMs: number
}

export interface Neighbor {
  book: BookSummary
  /** distance de Jaccard, entre 0 et 1 */
  distance: number
}

/** Ligne d'un livre qui contient la RegEx, avec les positions [début, fin exclue] à surligner. */
export interface Excerpt {
  number: number
  line: string
  occurrences: [number, number][]
}

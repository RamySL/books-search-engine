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

/** Erreur renvoyée par le serveur (format Problem Details, RFC 9457). */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

type Params = Record<string, string | number | undefined>

async function get<T>(path: string, params: Params = {}): Promise<T> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) {
      query.set(key, String(value))
    }
  }
  const url = query.toString() ? `${path}?${query}` : path
  const response = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!response.ok) {
    // le message du serveur (detail) est lisible par l'utilisateur, par exemple « Regex mal formee »
    const problem = await response.json().catch(() => null)
    throw new ApiError(response.status, problem?.detail ?? `Erreur ${response.status}`)
  }
  return response.json()
}

/** Recherche par mot-clé : livres qui contiennent tous les mots de `q`. */
export function search(q: string, sort?: Sort, page?: number): Promise<SearchResponse> {
  return get('/api/search', { q, sort, page })
}

/** Recherche par RegEx (syntaxe d'egrep : lettres, `.`, `|`, `*`, `+` et parenthèses). */
export function searchRegex(q: string, scope?: Scope, sort?: Sort, page?: number): Promise<SearchResponse> {
  return get('/api/search/regex', { q, scope, sort, page })
}

/** Catalogue de tous les livres, par ordre alphabétique. */
export function books(page?: number): Promise<Page<BookSummary>> {
  return get('/api/books', { page })
}

export function book(id: number): Promise<Book> {
  return get(`/api/books/${id}`)
}

/** Voisins du livre dans le graphe de Jaccard, du plus proche au plus éloigné. */
export function neighbors(id: number): Promise<Neighbor[]> {
  return get(`/api/books/${id}/neighbors`)
}

/** Lignes du livre qui contiennent la RegEx (50 au plus par défaut). */
export function excerpts(id: number, regex: string, limit?: number): Promise<Excerpt[]> {
  return get(`/api/books/${id}/excerpts`, { regex, limit })
}

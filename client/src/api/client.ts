// Appels à l'API REST du serveur, un par requête.
import type { Book, BookSummary, Excerpt, Neighbor, Page, Scope, SearchResponse, Sort } from './types.ts'

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

/**
 * Lignes du livre qui contiennent la RegEx (50 au plus par défaut). Avec
 * `words`, seulement en mots entiers, comme la recherche par mot-clé.
 */
export function excerpts(id: number, regex: string, words?: boolean, limit?: number): Promise<Excerpt[]> {
  return get(`/api/books/${id}/excerpts`, { regex, words: words ? 'true' : undefined, limit })
}

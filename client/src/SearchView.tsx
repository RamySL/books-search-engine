import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { books, search, searchRegex, type BookSummary, type Page, type SearchResponse, type Sort } from './api.ts'
import { BookCard, type Highlight } from './components.tsx'
import { useApi, type Loaded } from './useApi.ts'

/** Mode de recherche : mot-clé, RegEx sur l'index, RegEx sur le texte intégral. */
type Mode = 'keyword' | 'index' | 'text'

const MODES: { value: Mode; label: string }[] = [
  { value: 'keyword', label: 'Mot-clé' },
  { value: 'index', label: 'RegEx (index)' },
  { value: 'text', label: 'RegEx (texte intégral)' },
]

const SORTS: { value: Sort; label: string }[] = [
  { value: 'occurrences', label: 'Occurrences' },
  { value: 'closeness', label: 'Closeness' },
  { value: 'pagerank', label: 'PageRank' },
]

const PAGE_SIZE = 20

function parseMode(value: string | null): Mode {
  return value === 'index' || value === 'text' ? value : 'keyword'
}


function parseSort(value: string | null): Sort {
  return value === 'closeness' || value === 'pagerank' ? value : 'occurrences'
}

/**
 * Vue Recherche : /?q=&mode=&sort=&page=. L'état est entièrement dans l'URL,
 * ce qui permet de partager une recherche et d'utiliser le bouton Retour.
 * Sans requête, on affiche le catalogue.
 */
function SearchView() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q')?.trim() ?? ''
  const mode = parseMode(params.get('mode'))
  const sort = parseSort(params.get('sort'))
  const page = Math.max(1, Number(params.get('page')) || 1)

  // texte en cours de saisie, envoyé seulement à la validation du formulaire
  const [input, setInput] = useState(q)
  const [shownQ, setShownQ] = useState(q)
  if (q !== shownQ) {
    setShownQ(q)
    setInput(q)
  }

  const found = useApi<SearchResponse>(q ? JSON.stringify([q, mode, sort, page]) : null,
    () => mode === 'keyword' ? search(q, sort, page) : searchRegex(q, mode, sort, page))
  const catalog = useApi<Page<BookSummary>>(q ? null : `catalog ${page}`, () => books(page))

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      next.set(key, value)
    }
    // un nouveau critère repart de la première page
    if (!('page' in changes)) next.delete('page')
    else window.scrollTo(0, 0)
    setParams(next)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    update({ q: input.trim(), mode, sort })
  }

  // pour la vue Livre : la RegEx à surligner ; pour un mot-clé, les mots cherchés
  // (la requête après le tokeniseur du serveur), surlignés en mots entiers
  const highlight: Highlight | undefined = mode !== 'keyword'
    ? { regex: q, words: false }
    : found.data ? { regex: found.data.terms.join('|'), words: true } : undefined

  return (
    <>
      <form className="search" onSubmit={submit}>
        <input
          type="search"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder={mode === 'keyword' ? 'Mots-clés, par exemple : sargon babylon' : 'RegEx, par exemple : sarg(o|a)n'}
          aria-label="Recherche"
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
        />
        <button type="submit">Chercher</button>
      </form>

      <div className="options">
        <div className="segmented" role="group" aria-label="Mode de recherche">
          {MODES.map((m) => (
            <button key={m.value} type="button" aria-pressed={mode === m.value}
                    onClick={() => update({ mode: m.value, q: input.trim() })}>
              {m.label}
            </button>
          ))}
        </div>
        <label className="sort">
          Tri
          <select value={sort} onChange={(event) => update({ sort: event.target.value })}>
            {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </label>
      </div>

      {mode === 'keyword' && (
        <p className="hint">
          Mots exacts : un livre doit contenir tous les mots. Pour les mots qui commencent par « prin »
          (prince, princess…), utilisez la RegEx sur l'index : <code>prin.*</code>
        </p>
      )}
      {mode !== 'keyword' && (
        <p className="hint">
          Syntaxe : lettres, <code>.</code> (n'importe quel caractère), <code>|</code>, <code>*</code>, <code>+</code> et
          parenthèses.
          {mode === 'index'
            ? ' La RegEx doit reconnaître un mot entier de l\'index.'
            : ' La RegEx est cherchée dans chaque ligne du texte, et peut couvrir plusieurs mots.'}
        </p>
      )}
      {mode === 'text' && (
        <p className="warning">
          La recherche dans le texte intégral relit tous les livres : elle peut prendre plusieurs secondes.
        </p>
      )}

      {q ? <Results found={found} sort={sort} page={page} highlight={highlight}
                    onPage={(p) => update({ page: String(p) })} />
         : <Catalog catalog={catalog} page={page} onPage={(p) => update({ page: String(p) })} />}
    </>
  )
}

function Results({ found, sort, page, highlight, onPage }: {
  found: Loaded<SearchResponse>
  sort: Sort
  page: number
  highlight?: Highlight
  onPage: (page: number) => void
}) {
  if (found.error) return <p className="error">{found.error}</p>
  if (!found.data) return found.loading ? <p className="loading">Recherche en cours…</p> : null
  const data = found.data
  return (
    <div className={found.loading ? 'stale' : undefined}>
      <p className="summary">
        {data.count} livre{data.count > 1 ? 's' : ''} en {data.durationMs} ms
        {sort !== 'occurrences' && <> · triés par {SORTS.find((s) => s.value === sort)?.label}</>}
      </p>
      {data.terms.length > 0 && (
        <p className="terms">
          Termes cherchés : {data.terms.slice(0, 30).join(', ')}
          {data.terms.length > 30 && <> … ({data.terms.length} en tout)</>}
          {data.termsTruncated && <> — trop de termes reconnus, seuls les plus répandus sont gardés</>}
        </p>
      )}
      <div className="layout">
        <section>
          <ul className="cards">
            {data.results.map((b) => <BookCard key={b.id} book={b} highlight={highlight} />)}
          </ul>
          <Pager page={page} count={data.count} onPage={onPage} />
        </section>
        {data.suggestions.length > 0 && (
          <aside className="suggestions">
            <h2>Vous aimerez aussi</h2>
            <p className="hint">Voisins des premiers résultats dans le graphe de Jaccard.</p>
            <ul className="cards compact">
              {data.suggestions.map((b) => <BookCard key={b.id} book={b} />)}
            </ul>
          </aside>
        )}
      </div>
    </div>
  )
}

function Catalog({ catalog, page, onPage }: {
  catalog: Loaded<Page<BookSummary>>
  page: number
  onPage: (page: number) => void
}) {
  if (catalog.error) return <p className="error">{catalog.error}</p>
  if (!catalog.data) return <p className="loading">Chargement…</p>
  return (
    <section>
      <p className="summary">Catalogue : {catalog.data.count} livres, par ordre alphabétique</p>
      <ul className="cards">
        {catalog.data.results.map((b) => <BookCard key={b.id} book={b} />)}
      </ul>
      <Pager page={page} count={catalog.data.count} onPage={onPage} />
    </section>
  )
}

function Pager({ page, count, onPage }: { page: number; count: number; onPage: (page: number) => void }) {
  const pages = Math.ceil(count / PAGE_SIZE)
  if (pages <= 1) return null
  return (
    <nav className="pager">
      <button type="button" disabled={page <= 1} onClick={() => onPage(page - 1)}>← Précédente</button>
      <span>Page {page} / {pages}</span>
      <button type="button" disabled={page >= pages} onClick={() => onPage(page + 1)}>Suivante →</button>
    </nav>
  )
}

export default SearchView

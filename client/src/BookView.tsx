import { useState, type FormEvent } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { book, excerpts, neighbors, type Book, type Excerpt, type Neighbor } from './api.ts'
import { BookCard, Cover, Highlighted } from './components.tsx'
import { formatCloseness, formatPagerank, useApi } from './useApi.ts'

/**
 * Vue Livre : /books/:id?hl=&words=. Métadonnées et centralités, extraits où la
 * dernière RegEx (paramètre hl) est surlignée, voisins dans le graphe de
 * Jaccard.
 */
function BookView() {
  const id = Number(useParams().id)
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const hl = params.get('hl') ?? ''
  // recherche par mot-clé : surligner au début des mots seulement
  const words = params.get('words') === '1'

  const [input, setInput] = useState(hl)
  const [shownHl, setShownHl] = useState(hl)
  if (hl !== shownHl) {
    setShownHl(hl)
    setInput(hl)
  }

  const details = useApi<Book>(`book ${id}`, () => book(id))
  const near = useApi<Neighbor[]>(`neighbors ${id}`, () => neighbors(id))
  const lines = useApi<Excerpt[]>(hl ? JSON.stringify([id, hl, words]) : null, () => excerpts(id, hl, words))

  function submit(event: FormEvent) {
    event.preventDefault()
    setParams(input.trim() ? { hl: input.trim() } : {})
  }

  if (details.error) return <p className="error">{details.error}</p>
  if (!details.data) return <p className="loading">Chargement…</p>
  const b = details.data

  return (
    <article className="book">
      <button type="button" className="back" onClick={() => navigate(-1)}>← Retour</button>
      <div className="book-head">
        <Cover book={b} large />
        <div>
          <h1>{b.title}</h1>
          <p className="author">{b.author ?? 'Auteur inconnu'}</p>
          <dl className="facts">
            <dt>Numéro Gutenberg</dt>
            <dd><a href={`https://www.gutenberg.org/ebooks/${b.id}`} target="_blank" rel="noreferrer">{b.id}</a></dd>
            <dt>Langue</dt><dd>{b.language}</dd>
            <dt>Mots</dt><dd>{b.wordCount.toLocaleString('fr-FR')}</dd>
            <dt>Voisins (degré)</dt><dd>{b.degree}</dd>
            <dt>Closeness</dt><dd>{formatCloseness(b.closeness)}</dd>
            <dt>PageRank</dt><dd>{formatPagerank(b.pagerank)}</dd>
          </dl>
        </div>
      </div>

      <section>
        <h2>Extraits</h2>
        <form className="search" onSubmit={submit}>
          <input type="search" value={input} onChange={(event) => setInput(event.target.value)}
                 placeholder="RegEx à chercher dans ce livre" aria-label="RegEx à chercher dans ce livre"
                 autoCapitalize="off" autoCorrect="off" spellCheck={false} />
          <button type="submit">Surligner</button>
        </form>
        {lines.error && <p className="error">{lines.error}</p>}
        {lines.loading && <p className="loading">Recherche dans le texte…</p>}
        {lines.data && lines.data.length === 0 && <p className="hint">Aucune ligne ne contient « {hl} ».</p>}
        {lines.data && lines.data.length > 0 && (
          <ol className="excerpts">
            {lines.data.map((e) => (
              <li key={e.number}>
                <span className="line-number">{e.number}</span>
                <span><Highlighted line={e.line} occurrences={e.occurrences} /></span>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section>
        <h2>Livres voisins</h2>
        <p className="hint">Livres à une distance de Jaccard inférieure au seuil du graphe, du plus proche au plus éloigné.</p>
        {near.error && <p className="error">{near.error}</p>}
        {near.data && near.data.length === 0 && <p className="hint">Ce livre n'a pas de voisin.</p>}
        {near.data && (
          <ul className="cards compact">
            {near.data.map((n) => <BookCard key={n.book.id} book={n.book} distance={n.distance} highlight={hl ? { regex: hl, words } : undefined} />)}
          </ul>
        )}
      </section>
    </article>
  )
}

export default BookView

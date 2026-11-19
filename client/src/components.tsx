import { useState } from 'react'
import { Link } from 'react-router'
import type { BookSummary } from './api.ts'
import { formatCloseness, formatPagerank } from './useApi.ts'

/** Couverture du livre ; certains livres n'en ont pas, on affiche alors les initiales du titre. */
export function Cover({ book, large = false }: { book: { title: string; cover: string }; large?: boolean }) {
  const [failed, setFailed] = useState(false)
  const className = large ? 'cover large' : 'cover'
  if (failed) {
    const initials = book.title.split(/\s+/).slice(0, 2).map((word) => word[0]).join('')
    return <div className={`${className} placeholder`} aria-hidden="true">{initials}</div>
  }
  return <img className={className} src={book.cover} alt="" loading="lazy" onError={() => setFailed(true)} />
}

/**
 * Ce que la vue Livre surligne : une RegEx, ou les mots d'une recherche par
 * mot-clé (`words`), surlignés seulement au début des mots.
 */
export interface Highlight {
  regex: string
  words: boolean
}

/** Livre dans une liste ; `distance` est donnée pour un voisin. */
export function BookCard({ book, highlight, distance }: { book: BookSummary; highlight?: Highlight; distance?: number }) {
  const params = new URLSearchParams()
  if (highlight) {
    params.set('hl', highlight.regex)
    if (highlight.words) params.set('words', '1')
  }
  const to = params.size ? `/books/${book.id}?${params}` : `/books/${book.id}`
  return (
    <li className="card">
      <Link to={to} className="card-link">
        <Cover book={book} />
        <div className="card-body">
          <span className="title">{book.title}</span>
          <span className="author">{book.author ?? 'Auteur inconnu'}</span>
          <span className="stats">
            {book.occurrences !== null && <b>{book.occurrences} occ.</b>}
            {distance !== undefined && <b>distance {distance.toFixed(3)}</b>}
            <span title="centralité de proximité">closeness {formatCloseness(book.closeness)}</span>
            <span title="PageRank pondéré">PR {formatPagerank(book.pagerank)}</span>
          </span>
        </div>
      </Link>
    </li>
  )
}

/** Une ligne avec ses occurrences surlignées ; `occurrences` : paires [début, fin exclue], dans l'ordre. */
export function Highlighted({ line, occurrences }: { line: string; occurrences: [number, number][] }) {
  const parts = []
  let position = 0
  for (const [start, end] of occurrences) {
    parts.push(line.slice(position, start))
    parts.push(<mark key={start}>{line.slice(start, end)}</mark>)
    position = end
  }
  parts.push(line.slice(position))
  return <>{parts}</>
}

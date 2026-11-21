import { Link } from 'react-router'
import type { BookSummary } from '../api/types.ts'
import { formatCloseness, formatPagerank } from '../utils/format.ts'
import { Cover } from './Cover.tsx'

/**
 * Ce que la vue Livre surligne : une RegEx, ou les mots d'une recherche par
 * mot-clé (`words`), surlignés seulement en mots entiers.
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

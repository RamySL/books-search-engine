import { useState } from 'react'

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

/** Livres par page, comme côté serveur. */
const PAGE_SIZE = 20

/** Navigation entre les pages d'une liste ; rien s'il n'y a qu'une page. */
export function Pager({ page, count, onPage }: { page: number; count: number; onPage: (page: number) => void }) {
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

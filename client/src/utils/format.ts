// Affichage des centralités dans les cartes et la fiche d'un livre.

export function formatCloseness(value: number): string {
  return value.toFixed(3)
}

/** Le PageRank d'un livre vaut environ 1/n : on l'affiche multiplié par 1000 (‰). */
export function formatPagerank(value: number): string {
  return `${(value * 1000).toFixed(3)} ‰`
}

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

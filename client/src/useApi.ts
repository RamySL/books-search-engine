import { useEffect, useEffectEvent, useState } from 'react'

/** Résultat d'un appel à l'API : les données, ou l'erreur, ou le chargement en cours. */
export interface Loaded<T> {
  data: T | null
  error: string | null
  loading: boolean
}

/**
 * Appelle l'API chaque fois que `key` change (la clé résume les paramètres
 * de la requête) ; `key` null : rien à charger. Pendant un chargement, on
 * garde les données précédentes pour éviter que la page ne saute. Une réponse
 * arrivée après une requête plus récente est ignorée : sinon une recherche
 * lente (texte intégral) pourrait écraser le résultat de la suivante.
 */
export function useApi<T>(key: string | null, load: () => Promise<T>): Loaded<T> {
  const [state, setState] = useState<{ key: string | null; data: T | null; error: string | null }>(
    { key: null, data: null, error: null })
  const run = useEffectEvent(load)

  useEffect(() => {
    if (key === null) return
    let current = true
    run()
      .then((data) => current && setState({ key, data, error: null }))
      .catch((e: Error) => current && setState({ key, data: null, error: e.message }))
    return () => {
      current = false
    }
  }, [key])

  if (key === null) return { data: null, error: null, loading: false }
  const loading = state.key !== key
  return { data: state.data, error: loading ? null : state.error, loading }
}

export function formatCloseness(value: number): string {
  return value.toFixed(3)
}

/** Le PageRank d'un livre vaut environ 1/n : on l'affiche multiplié par 1000 (‰). */
export function formatPagerank(value: number): string {
  return `${(value * 1000).toFixed(3)} ‰`
}

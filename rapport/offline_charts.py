"""Graphiques et chiffres de la partie hors ligne du rapport, à partir des CSV de measures/.

Usage, depuis la racine du dépôt, après les étapes graph, index-scaling et graph-scaling :
    python3 rapport/offline_charts.py measures rapport/figures

Reprend le style et l'écriture des figures de graphiques.py (PDF pour LaTeX et
PNG). Les tableaux du rapport (classements, corrélations, livres isolés par
taille, petites composantes) sont affichés dans le terminal. Un CSV absent est
ignoré : les figures qui en dépendent ne sont pas produites.
"""

import sys
from collections import Counter, defaultdict
from pathlib import Path

# importer graphiques.py ne doit pas laisser de dossier __pycache__ dans rapport/
sys.dont_write_bytecode = True

from graphiques import AUTOMATE, ENCRE_2, KMP, REGEX, enregistrer as save, lire as read_csv, plt

GREEN = "#1baf7a"
GREY = "#a8a6a0"


def fit_square(ns, ts):
    """Ajustement t = a · n² par moindres carrés, sans terme constant."""
    a = sum(t * n * n for n, t in zip(ns, ts)) / sum(n ** 4 for n in ns)
    return a


def index_scaling(measures, figures):
    """Vocabulaire, lignes de l'index et temps de construction selon la taille."""
    rows = read_csv(measures / "index-scaling.csv")
    if not rows:
        return
    books = [int(r["books"]) for r in rows]
    terms = [int(r["terms"]) for r in rows]
    lines = [int(r["rows"]) for r in rows]
    seconds = [float(r["seconds"]) for r in rows]

    fig, (vocabulary, size, time) = plt.subplots(1, 3, figsize=(7.2, 2.4))
    vocabulary.plot(books, [t / 1000 for t in terms], "o-", color=KMP)
    vocabulary.set_xlabel("livres")
    vocabulary.set_ylabel("termes distincts (milliers)")
    vocabulary.set_title("Vocabulaire")
    vocabulary.set_xlim(left=0)
    vocabulary.set_ylim(bottom=0)

    size.plot(books, [r / 1e6 for r in lines], "o-", color=GREEN)
    size.set_xlabel("livres")
    size.set_ylabel("lignes de occurrence (millions)")
    size.set_title("Taille de l'index")
    size.set_xlim(left=0)
    size.set_ylim(bottom=0)

    time.plot(books, seconds, "o-", color=AUTOMATE)
    time.set_xlabel("livres")
    time.set_ylabel("temps médian (s)")
    time.set_title("Construction en mémoire")
    time.set_xlim(left=0)
    time.set_ylim(bottom=0)
    fig.tight_layout()
    save(fig, figures, "index-scaling")


def graph_scaling(measures, figures):
    """Temps de Jaccard (ajusté en n²), de closeness et de PageRank selon le nombre de livres."""
    rows = read_csv(measures / "graph-scaling.csv")
    if not rows:
        return
    books = [int(r["books"]) for r in rows]
    jaccard = [float(r["jaccard_seconds"]) for r in rows]
    closeness = [float(r["closeness_seconds"]) * 1000 for r in rows]
    pagerank = [float(r["pagerank_seconds"]) * 1000 for r in rows]
    a = fit_square(books, jaccard)
    curve = list(range(0, books[-1] + 1, max(1, books[-1] // 100)))

    fig, (left, right) = plt.subplots(1, 2, figsize=(6.4, 2.5))
    left.plot(books, jaccard, "o", color=KMP, label="mesure")
    left.plot(curve, [a * n * n for n in curve], "--", color=ENCRE_2, linewidth=1, label="ajustement $a\\,n^2$")
    left.set_xlabel("livres")
    left.set_ylabel("temps médian (s)")
    left.set_title("Matrice de Jaccard")
    left.set_xlim(left=0)
    left.set_ylim(bottom=0)
    left.legend()

    right.plot(books, closeness, "o-", color=AUTOMATE, label="closeness")
    right.plot(books, pagerank, "o-", color=REGEX, label="PageRank")
    right.set_xlabel("livres")
    right.set_ylabel("temps médian (ms)")
    right.set_title("Centralités")
    right.set_xlim(left=0)
    right.set_ylim(bottom=0)
    right.legend()
    fig.tight_layout()
    save(fig, figures, "graph-scaling")
    print(f"Jaccard : t ≈ {a:.3e} · n² s, soit {a * 70_000 ** 2 / 3600:.0f} h pour 70 000 livres")


def threshold(measures, figures, theta):
    """Histogramme des distances, puis degré moyen et livres isolés selon le seuil."""
    rows = read_csv(measures / "distances.csv")
    if not rows:
        return
    lows = [float(r["from"]) for r in rows]
    pairs = [int(r["pairs"]) for r in rows]
    fig, (left, right) = plt.subplots(1, 2, figsize=(6.4, 2.5))
    left.bar(lows, [p / 1000 for p in pairs], width=0.01, align="edge", color=KMP)
    if theta is not None:
        left.axvline(theta, color=AUTOMATE, linestyle="--", linewidth=1)
        left.text(theta - 0.01, max(pairs) / 1000 * 0.9, f"θ = {theta:.2f}".replace(".", ","), color=AUTOMATE,
                  ha="right")
    left.set_xlabel("distance de Jaccard")
    left.set_ylabel("paires de livres (milliers)")
    left.set_title("Distances entre livres")
    left.set_xlim(0.3, 1)

    window = [r for r in rows if 0.55 <= float(r["to"]) <= 0.85]
    tos = [float(r["to"]) for r in window]
    right.semilogy(tos, [max(float(r["mean_degree"]), 0.01) for r in window], "o-", color=GREEN,
                   markersize=3)
    right.set_xlabel("seuil θ")
    isolated = right.twinx()
    isolated.plot(tos, [int(r["isolated"]) for r in window], "s-", color=AUTOMATE, markersize=3)
    isolated.grid(False)
    isolated.spines["right"].set_visible(True)
    if theta is not None:
        right.axvline(theta, color=ENCRE_2, linestyle="--", linewidth=1)
    right.set_title("Choix du seuil")
    # pas de légende, qui recouvrirait les courbes : chaque axe prend la couleur de sa courbe
    right.set_ylabel("degré moyen (échelle log)", color=GREEN)
    right.tick_params(axis="y", colors=GREEN)
    isolated.set_ylabel("livres isolés", color=AUTOMATE)
    isolated.tick_params(axis="y", colors=AUTOMATE)
    fig.tight_layout()
    save(fig, figures, "threshold")


def components(ids, edges):
    """Composantes connexes par union-find : la liste des composantes, de la plus grande à la plus petite."""
    parent = {i: i for i in ids}

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for a, b, _ in edges:
        parent[find(a)] = find(b)
    groups = defaultdict(list)
    for i in ids:
        groups[find(i)].append(i)
    return sorted(groups.values(), key=len, reverse=True)


def structure(figures, books, comps):
    """Distribution des degrés et taille des composantes connexes."""
    degrees = [b["degree"] for b in books.values()]
    fig, (left, right) = plt.subplots(1, 2, figsize=(6.4, 2.5))
    bounds = [1, 2, 4, 8, 16, 32, 64, 128, 256, 512]
    counts = [sum(lo <= d < hi for d in degrees) for lo, hi in zip(bounds, bounds[1:])]
    labels = [f"{lo}–{hi - 1}" if hi - lo > 1 else str(lo) for lo, hi in zip(bounds, bounds[1:])]
    isolated = sum(d == 0 for d in degrees)
    left.bar(["0"] + labels, [isolated] + counts, color=[AUTOMATE] + [KMP] * len(counts))
    left.set_xlabel("degré")
    left.set_ylabel("livres")
    left.set_title("Distribution des degrés")
    left.tick_params(axis="x", labelsize=7, rotation=45)
    left.grid(axis="x", visible=False)

    sizes = Counter(len(c) for c in comps)
    classes = [("1", 1, 1), ("2", 2, 2), ("3", 3, 3), ("4–9", 4, 9), ("10–99", 10, 99), ("100+", 100, 10 ** 9)]
    values = [sum(n for s, n in sizes.items() if lo <= s <= hi) for _, lo, hi in classes]
    bars = right.bar([c[0] for c in classes], values, color=[AUTOMATE] + [KMP] * (len(classes) - 1))
    for bar, value in zip(bars, values):
        right.text(bar.get_x() + bar.get_width() / 2, value, str(value), ha="center", va="bottom", fontsize=7)
    right.set_yscale("log")
    right.set_xlabel("taille de la composante (livres)")
    right.set_ylabel("composantes (échelle log)")
    right.set_title("Composantes connexes")
    right.grid(axis="x", visible=False)
    fig.tight_layout()
    save(fig, figures, "graph-structure")


def ranks(values):
    """Rangs de 1 à n, les ex aequo recevant leur rang moyen."""
    order = sorted(range(len(values)), key=lambda i: values[i])
    result = [0.0] * len(values)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and values[order[j + 1]] == values[order[i]]:
            j += 1
        for k in range(i, j + 1):
            result[order[k]] = (i + j) / 2 + 1
        i = j + 1
    return result


def spearman(xs, ys):
    """Corrélation de rang de Spearman : la corrélation de Pearson des rangs."""
    rx, ry = ranks(xs), ranks(ys)
    n = len(xs)
    mx, my = sum(rx) / n, sum(ry) / n
    cov = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    return cov / (sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry)) ** 0.5


def print_tables(books, edges, comps, theta):
    """Les chiffres et tableaux du rapport, dans le terminal."""
    n, m = len(books), len(edges)
    print(f"\nGraphe : {n} livres, θ = {theta}, {m} arêtes, degré moyen {2 * m / n:.1f}, "
          f"{sum(b['degree'] == 0 for b in books.values())} isolés, {len(comps)} composantes, "
          f"la plus grande de {len(comps[0])} livres")

    criteria = ("degree", "closeness", "pagerank")
    tops = {}
    for criterion in criteria:
        top = sorted(books.values(), key=lambda b: (-b[criterion], b["id"]))[:10]
        tops[criterion] = [b["id"] for b in top]
        print(f"\nTop 10 par {criterion} :")
        for b in top:
            print(f"  {b['id']:>6}  {b[criterion]:<12.6g} deg {b['degree']:<4} {b['word_count']:>8} mots  "
                  f"{b['title'][:70]}")

    print("\nCorrélations de rang de Spearman (tous les livres ; livres non isolés) :")
    connected = [b for b in books.values() if b["degree"] > 0]
    for i, a in enumerate(criteria):
        for b in criteria[i + 1:]:
            everyone = spearman([x[a] for x in books.values()], [x[b] for x in books.values()])
            linked = spearman([x[a] for x in connected], [x[b] for x in connected])
            common = len(set(tops[a]) & set(tops[b]))
            print(f"  {a:<9} / {b:<9} : {everyone:.2f} ; {linked:.2f} ; {common} livres communs aux top 10")

    print("\nLivres isolés par décile de taille :")
    by_size = sorted(books.values(), key=lambda b: b["word_count"])
    tenth = len(by_size) // 10
    for d in range(10):
        part = by_size[d * tenth:(d + 1) * tenth if d < 9 else len(by_size)]
        isolated = sum(b["degree"] == 0 for b in part)
        mean = sum(b["degree"] for b in part) / len(part)
        print(f"  décile {d + 1:>2} : {part[0]['word_count']:>9} – {part[-1]['word_count']:>9} mots, "
              f"{isolated:>3} isolés sur {len(part)}, degré moyen {mean:.1f}")

    print("\nPetites composantes (3 à 8 livres), candidates pour l'exemple de calcul :")
    weight = {(a, b): d for a, b, d in edges}
    for comp in comps:
        if 3 <= len(comp) <= 8:
            members = sorted(comp)
            inside = [(a, b, d) for (a, b), d in weight.items() if a in comp and b in comp]
            print(f"  {len(comp)} livres, {len(inside)} arêtes :")
            for i in members:
                b = books[i]
                print(f"    {i:>6} deg {b['degree']} closeness {b['closeness']:.6f} pagerank {b['pagerank']:.6f}"
                      f"  {b['title'][:60]}")
            for a, b, d in sorted(inside):
                print(f"      {a} – {b} : {d:.4f}")


def main():
    measures = Path(sys.argv[1] if len(sys.argv) > 1 else "measures")
    figures = Path(sys.argv[2] if len(sys.argv) > 2 else "rapport/figures")
    figures.mkdir(parents=True, exist_ok=True)
    index_scaling(measures, figures)
    graph_scaling(measures, figures)

    rows = read_csv(measures / "centralities.csv")
    edge_rows = read_csv(measures / "edges.csv")
    histogram = read_csv(measures / "distances.csv")
    if not (rows and edge_rows is not None and histogram):
        return
    books = {int(r["id"]): {"id": int(r["id"]), "title": r["title"], "word_count": int(r["word_count"]),
                            "degree": int(r["degree"]), "closeness": float(r["closeness"]),
                            "pagerank": float(r["pagerank"])} for r in rows}
    edges = [(int(r["book_a"]), int(r["book_b"]), float(r["distance"])) for r in edge_rows]
    # le seuil du graphe : le premier θ dont le nombre d'arêtes est celui de edges.csv
    theta = next((float(r["to"]) for r in histogram if int(r["edges"]) == len(edges)), None)
    comps = components(list(books), edges)
    threshold(measures, figures, theta)
    structure(figures, books, comps)
    print_tables(books, edges, comps, theta)


if __name__ == "__main__":
    main()

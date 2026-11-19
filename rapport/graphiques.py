"""Graphiques de la partie en ligne du rapport, à partir des CSV de measures/.

Usage, depuis la racine du dépôt, après les étapes bench et load :
    python3 rapport/graphiques.py measures rapport/figures

Chaque figure est écrite en PDF (pour LaTeX) et en PNG. Un CSV absent est
ignoré : la figure correspondante n'est simplement pas produite.
"""

import csv
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch

# Mêmes réglages que les figures du projet 1 ; une couleur par méthode ou par recherche.
KMP = "#2a78d6"
AUTOMATE = "#eb6834"
REGEX = "#8b5cf6"
TYPES = {"keyword": ("Mot-clé", "#1baf7a"), "index": ("RegEx sur l'index", KMP),
         "text": ("RegEx sur le texte", AUTOMATE), "book": ("Fiche d'un livre", "#a8a6a0")}
ENCRE = "#0b0b0b"
ENCRE_2 = "#52514e"
GRILLE = "#e4e3df"

plt.rcParams.update({
    "font.family": "DejaVu Sans",
    "font.size": 9,
    "axes.edgecolor": ENCRE_2,
    "axes.labelcolor": ENCRE,
    "axes.titlesize": 10,
    "axes.titleweight": "bold",
    "axes.titlelocation": "left",
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.grid": True,
    "grid.color": GRILLE,
    "grid.linewidth": 0.6,
    "axes.axisbelow": True,
    "xtick.color": ENCRE_2,
    "ytick.color": ENCRE_2,
    "legend.frameon": False,
    "legend.fontsize": 8,
    "lines.linewidth": 2,
    "lines.markersize": 5,
    "savefig.dpi": 200,
    "savefig.bbox": "tight",
})


def lire(chemin):
    if not chemin.exists():
        print(f"absent : {chemin}")
        return None
    with open(chemin, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def enregistrer(fig, dossier, nom):
    for extension in ("pdf", "png"):
        fig.savefig(dossier / f"{nom}.{extension}")
    plt.close(fig)
    print(f"écrit : {dossier / nom}.pdf")


def requetes(mesures, dossier):
    """Barres horizontales : temps médian de chaque requête, échelle log."""
    lignes = lire(mesures / "queries.csv")
    if not lignes:
        return
    lignes = list(reversed(lignes))
    fig, ax = plt.subplots(figsize=(6.4, 0.16 * len(lignes) + 0.8))
    y = range(len(lignes))
    temps = [max(float(l["median_ms"]), 0.01) for l in lignes]
    ax.barh(y, temps, color=[TYPES[l["type"]][1] for l in lignes], height=0.7)
    ax.set_yticks(list(y), [f"{l['query']}  ({l['books']} livres)" for l in lignes], fontsize=7)
    ax.set_xscale("log")
    ax.set_xlabel("temps médian (ms, échelle log)")
    ax.grid(axis="y", visible=False)
    for i, t in enumerate(temps):
        ax.text(t * 1.08, i, f"{t:.1f}" if t < 100 else f"{t:.0f}", va="center", fontsize=6.5, color=ENCRE_2)
    ax.legend(handles=[Patch(color=c, label=n) for k, (n, c) in TYPES.items() if k != "book"],
              loc="center right")
    ax.set_title("Temps de calcul d'une recherche (occurrences, 1re page, suggestions)")
    enregistrer(fig, dossier, "requetes")


def texte(mesures, dossier):
    """Courbes : RegEx sur le texte selon la taille lue, KMP contre automate."""
    lignes = lire(mesures / "text-scaling.csv")
    if not lignes:
        return
    series = defaultdict(list)
    for l in lignes:
        series[(l["query"], l["method"])].append((float(l["megabytes"]), float(l["median_ms"])))
    fig, ax = plt.subplots(figsize=(6.4, 3.0))
    styles = ["-", "--", ":"]
    mots = sorted({q for q, _ in series if "." not in q})
    for (q, methode), points in sorted(series.items()):
        x, t = zip(*sorted(points))
        if "." in q:
            ax.plot(x, t, "o-", color=REGEX, label=f"{q} (automate)")
        else:
            couleur = KMP if methode == "kmp" else AUTOMATE
            nom = "KMP" if methode == "kmp" else "automate"
            ax.plot(x, t, "o", linestyle=styles[mots.index(q) % 3], color=couleur, label=f"{q} ({nom})")
    ax.set_xlabel("taille des textes parcourus (Mo)")
    ax.set_ylabel("temps médian (ms)")
    ax.set_xlim(left=0)
    ax.set_ylim(bottom=0)
    ax.legend(ncols=2)
    ax.set_title("RegEx sur le texte intégral : temps selon la taille du corpus")
    enregistrer(fig, dossier, "texte")


def kmp_automate(mesures, dossier):
    """Barres groupées : débit de KMP et de l'automate selon la longueur du mot."""
    lignes = lire(mesures / "kmp-dfa.csv")
    if not lignes:
        return
    mots = list(dict.fromkeys(l["word"] for l in lignes))
    debit = {(l["word"], l["method"]): float(l["megabytes_per_s"]) for l in lignes}
    etats = {l["word"]: l["states"] for l in lignes if l["method"] == "dfa"}
    fig, ax = plt.subplots(figsize=(6.4, 2.6))
    largeur = 0.38
    for decalage, methode, couleur, nom in ((-largeur / 2, "kmp", KMP, "KMP"),
                                            (largeur / 2, "dfa", AUTOMATE, "automate (DFA minimal)")):
        x = [i + decalage for i in range(len(mots))]
        valeurs = [debit[(m, methode)] for m in mots]
        ax.bar(x, valeurs, largeur, color=couleur, label=nom)
        for xi, v in zip(x, valeurs):
            ax.text(xi, v, f"{v:.0f}", ha="center", va="bottom", fontsize=6.5, color=ENCRE_2)
    ax.set_xticks(range(len(mots)), [f"{m}\n{len(m)} lettres, {etats[m]} états" for m in mots], fontsize=7)
    ax.set_ylabel("débit (millions de caractères/s)")
    ax.grid(axis="x", visible=False)
    ax.legend(loc="upper left")
    ax.set_title("Recherche d'un mot dans des lignes en mémoire, un cœur")
    enregistrer(fig, dossier, "kmp-automate")


def vocabulaire(mesures, dossier):
    """Courbes : parcours du vocabulaire par le DFA, selon le nombre de termes."""
    lignes = lire(mesures / "vocabulary-scaling.csv")
    if not lignes:
        return
    series = defaultdict(list)
    for l in lignes:
        series[l["query"]].append((int(l["terms"]) / 1000, float(l["median_ms"])))
    fig, ax = plt.subplots(figsize=(6.4, 2.6))
    for (q, points), couleur in zip(series.items(), [KMP, AUTOMATE, REGEX]):
        x, t = zip(*points)
        ax.plot(x, t, "o-", color=couleur, label=q)
    ax.set_xlabel("termes parcourus (milliers)")
    ax.set_ylabel("temps médian (ms)")
    ax.set_xlim(left=0)
    ax.set_ylim(bottom=0)
    ax.legend()
    ax.set_title("RegEx sur l'index : parcours du vocabulaire par l'automate")
    enregistrer(fig, dossier, "vocabulaire")


def charge(mesures, dossier):
    """Deux graphiques côte à côte : débit et latence p95 selon le nombre de clients."""
    lignes = lire(mesures / "load.csv")
    if not lignes:
        return
    series = defaultdict(list)
    for l in lignes:
        series[l["type"]].append((int(l["clients"]), float(l["throughput_rps"]), float(l["p95_ms"])))
    fig, (debit, latence) = plt.subplots(1, 2, figsize=(6.4, 2.6))
    for type_, points in series.items():
        nom, couleur = TYPES[type_]
        clients, d, p95 = zip(*points)
        debit.plot(clients, d, "o-", color=couleur, label=nom)
        latence.plot(clients, p95, "o-", color=couleur, label=nom)
    for ax in (debit, latence):
        ax.set_xscale("log", base=2)
        ax.set_xticks(sorted({c for p in series.values() for c, _, _ in p}))
        ax.get_xaxis().set_major_formatter(matplotlib.ticker.ScalarFormatter())
        ax.set_xlabel("clients simultanés")
        ax.set_ylim(bottom=0)
    debit.set_ylabel("requêtes par seconde")
    debit.set_title("Débit")
    latence.set_ylabel("latence p95 (ms)")
    latence.set_title("Latence (95e centile)")
    debit.legend()
    fig.tight_layout()
    enregistrer(fig, dossier, "charge")


def main():
    mesures = Path(sys.argv[1] if len(sys.argv) > 1 else "measures")
    dossier = Path(sys.argv[2] if len(sys.argv) > 2 else "rapport/figures")
    dossier.mkdir(parents=True, exist_ok=True)
    requetes(mesures, dossier)
    texte(mesures, dossier)
    kmp_automate(mesures, dossier)
    vocabulaire(mesures, dossier)
    charge(mesures, dossier)


if __name__ == "__main__":
    main()

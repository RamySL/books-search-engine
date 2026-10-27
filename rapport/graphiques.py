"""Graphiques du rapport, à partir des CSV du banc d'essai (egrep.mesures.Mesures).

Usage, depuis la racine du projet :
    python3 rapport/graphiques.py mesures/avant rapport/figures

Chaque figure est écrite en PDF (pour LaTeX) et en PNG. Les barres d'erreur
représentent l'écart type sur les 10 mesures.
"""

import csv
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch
from matplotlib.ticker import FuncFormatter

# Une couleur par méthode, la même dans toutes les figures.
KMP = "#2a78d6"
AUTOMATE = "#eb6834"
EGREP = "#1baf7a"
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
    "errorbar.capsize": 2.5,
    "savefig.dpi": 200,
    "savefig.bbox": "tight",
})


def lire(chemin):
    with open(chemin, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def colonne(lignes, nom, type_=float):
    return [type_(l[nom]) for l in lignes]


def enregistrer(fig, dossier, nom):
    for ext in ("pdf", "png"):
        fig.savefig(dossier / f"{nom}.{ext}")
    plt.close(fig)
    print(f"{nom} : ok")


def courbe(ax, x, lignes, prefixe, couleur, etiquette, style="-", marqueur="o"):
    ax.errorbar(x, colonne(lignes, prefixe + "_moy"), yerr=colonne(lignes, prefixe + "_ecart"),
                color=couleur, linestyle=style, marker=marqueur, label=etiquette)


def taille(mesures, figures):
    """Temps selon la taille du texte : dans le programme, puis commande complète."""
    lignes = lire(mesures / "taille.csv")
    x = colonne(lignes, "taille_mo")
    fig, (a, b) = plt.subplots(1, 2, figsize=(7.2, 3.0))

    courbe(a, x, lignes, "kmp", KMP, "KMP, Babylon")
    courbe(a, x, lignes, "automate", AUTOMATE, "automate, Babylon")
    courbe(a, x, lignes, "figure1", AUTOMATE, "automate, S(a|g|r)+on", style="--", marqueur="s")
    a.set_title("Recherche seule (lignes en mémoire)")

    courbe(b, x, lignes, "clone_automate", AUTOMATE, "clone, --dfa")
    courbe(b, x, lignes, "clone_kmp", KMP, "clone, KMP")
    courbe(b, x, lignes, "egrep", EGREP, "egrep")
    b.set_title("Commande complète, motif Babylon")

    for ax in (a, b):
        ax.set_xticks([1, 4, 8, 16, 32])
        ax.set_xlim(0, 33)
        ax.set_xlabel("taille du texte (Mo)")
        ax.set_ylabel("temps (ms)")
        ax.set_ylim(bottom=0)
        ax.legend(loc="upper left")
    fig.tight_layout(w_pad=2.5)
    enregistrer(fig, figures, "taille")


def motifs(mesures, figures):
    """Temps par motif : recherche seule (KMP / automate), puis clone face à egrep."""
    lignes = lire(mesures / "motifs.csv")
    noms = list(dict.fromkeys(l["motif"] for l in lignes))
    par = {(l["motif"], l["strategie"]): l for l in lignes}
    noms_affiches = [n.replace("(a|e|i|o|u)(a|e|i|o|u)(a|e|i|o|u)", "(a|e|i|o|u)×3") for n in noms]
    y = range(len(noms))
    h = 0.38

    fig, (a, b) = plt.subplots(1, 2, figsize=(7.2, 3.8), sharey=True)
    erreur = {"ecolor": ENCRE_2, "elinewidth": 0.8}

    # Recherche seule : une barre automate pour chaque motif, une barre KMP pour les mots.
    for i, n in enumerate(noms):
        auto = par[(n, "automate")]
        kmp = par.get((n, "kmp"))
        decalage = h / 2 if kmp else 0
        a.barh(i + decalage, float(auto["recherche_moy"]), height=h, xerr=float(auto["recherche_ecart"]),
               color=AUTOMATE, error_kw=erreur)
        if kmp:
            a.barh(i - h / 2, float(kmp["recherche_moy"]), height=h, xerr=float(kmp["recherche_ecart"]),
                   color=KMP, error_kw=erreur)
    a.set_title("Recherche seule, corpus de 6,4 Mo")
    a.set_xlabel("temps (ms)")

    # Commande complète : le clone avec la stratégie qu'il choisit, puis egrep.
    for i, n in enumerate(noms):
        choisie = par.get((n, "kmp")) or par[(n, "automate")]
        couleur = KMP if (n, "kmp") in par else AUTOMATE
        b.barh(i - h / 2, float(choisie["clone_moy"]), height=h, xerr=float(choisie["clone_ecart"]),
               color=couleur, error_kw=erreur)
        b.barh(i + h / 2, float(choisie["egrep_moy"]), height=h, xerr=float(choisie["egrep_ecart"]),
               color=EGREP, error_kw=erreur)
        b.annotate(f"×{float(choisie['clone_moy']) / float(choisie['egrep_moy']):.0f}",
                   (float(choisie["clone_moy"]) + float(choisie["clone_ecart"]), i - h / 2),
                   xytext=(4, 0), textcoords="offset points", va="center", fontsize=7.5, color=ENCRE_2)
    b.set_title("Commande complète : clone puis egrep")
    b.set_xlabel("temps (ms)")
    b.set_xlim(right=520)

    a.set_yticks(list(y), noms_affiches)
    a.invert_yaxis()
    for ax in (a, b):
        ax.grid(axis="y", visible=False)
        ax.tick_params(axis="y", length=0)
    fig.legend([Patch(color=KMP), Patch(color=AUTOMATE), Patch(color=EGREP)],
               ["KMP", "automate", "egrep"], loc="upper center", ncol=3, bbox_to_anchor=(0.5, 1.04))
    fig.tight_layout(w_pad=1.5)
    enregistrer(fig, figures, "motifs")


def etats(mesures, figures):
    """Explosion de la méthode des sous-ensembles sur a(a|b)…(a|b)."""
    lignes = lire(mesures / "etats.csv")
    k = colonne(lignes, "k", int)
    fig, (a, b) = plt.subplots(1, 2, figsize=(7.2, 3.0))

    a.plot(k, colonne(lignes, "etats_afn"), color=KMP, marker="o", label="ε-AFN (Aho-Ullman)")
    a.plot(k, colonne(lignes, "etats_dfa"), color=AUTOMATE, marker="s", label="DFA (sous-ensembles)")
    a.plot(k, colonne(lignes, "etats_min"), color=EGREP, marker="^", linestyle="--", label="DFA minimal (Moore)")
    a.set_yscale("log", base=2)
    a.yaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}"))
    a.set_ylabel("nombre d'états (échelle log)")
    a.set_title("Taille des automates")
    a.legend(loc="upper left")

    b.errorbar(k, colonne(lignes, "construction_moy"), yerr=colonne(lignes, "construction_ecart"),
               color=AUTOMATE, marker="o")
    b.set_ylim(bottom=0)
    b.set_ylabel("temps de construction (ms)")
    b.set_title("Temps des étapes 1 à 4")
    dernier = lignes[-1]
    b.annotate(f"{float(dernier['construction_moy']):.0f} ms\n{int(dernier['etats_min'])} états",
               (k[-1], float(dernier["construction_moy"])), xytext=(-8, 0), textcoords="offset points",
               ha="right", va="center", color=ENCRE, fontsize=8)

    for ax in (a, b):
        ax.set_xlabel("k, nombre de (a|b) après le a")
        ax.set_xticks(k)
    fig.tight_layout(w_pad=2.5)
    enregistrer(fig, figures, "etats")


def main():
    mesures = Path(sys.argv[1] if len(sys.argv) > 1 else "mesures/avant")
    figures = Path(sys.argv[2] if len(sys.argv) > 2 else "rapport/figures")
    figures.mkdir(parents=True, exist_ok=True)
    taille(mesures, figures)
    motifs(mesures, figures)
    etats(mesures, figures)


if __name__ == "__main__":
    main()

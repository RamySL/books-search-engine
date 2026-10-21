# Le banc d'essai : `Mesures.java`

Ce programme produit les chiffres de la partie performances du rapport. Il compare trois façons de chercher un motif :

- KMP ;
- l'automate du cours 1 ;
- egrep lui-même.

Il écrit ses résultats dans des fichiers CSV, que le rapport transformera en graphiques. Il est compilé dans `egrep.jar` avec le reste du code, mais il ne fait pas partie de la commande egrep.

## Le lancer

Depuis la racine du projet, après `make` :

```sh
java -cp egrep.jar egrep.mesures.Mesures mesures/avant
```

Il lui faut `egrep.jar`, les textes de `instances/` et la commande `egrep`. Il tourne en deux minutes environ et écrit ses fichiers dans le dossier donné, ici `mesures/avant/`.

## Deux façons de mesurer

| | Dans le programme | Commande complète |
|---|---|---|
| Ce qu'on chronomètre | `Recherche.contient` sur chaque ligne, les lignes étant déjà en mémoire | `java -jar egrep.jar …` ou `egrep …`, lancés comme dans un terminal |
| Ce que ça montre | le coût de l'algorithme seul | ce que voit l'utilisateur, démarrage de Java compris |

Seule la commande complète permet de comparer le clone à egrep, qui est un programme à part.

## Les fichiers produits

Chaque fichier répond à une question du rapport. Les temps sont en millisecondes, donnés par leur moyenne puis leur écart type.

### `demarrage.csv` : que coûte le simple lancement ?

Le clone et egrep, sur un fichier vide. Sur notre machine, le clone met environ 54 ms à démarrer Java, egrep 1,4 ms.

### `taille.csv` : comment le temps grandit-il avec la taille du texte ?

Le corpus est répété jusqu'à faire 1, 2, 4, 8, 16 et 32 Mo. Pour chaque taille :

- dans le programme : le mot `Babylon` avec KMP puis avec l'automate, et `S(a|g|r)+on` avec l'automate ;
- en commande complète : le clone avec KMP, le clone avec `--dfa` et egrep, sur un fichier de cette taille.

`Babylon` est un mot rare : presque toutes les lignes sont lues jusqu'au bout, ce qui mesure bien le parcours.

### `motifs.csv` : que coûte chaque motif ?

Huit motifs, sur tout le corpus (6,4 Mo) :

- trois mots plus ou moins fréquents : `the`, `Holmes`, `Babylon` ;
- un exemple par opérateur : `S(a|g|r)+on`, `th(e|is|at)`, `w.s`, `colou*r`, et trois voyelles de suite.

Un mot a deux lignes, une avec KMP et une avec l'automate. Chaque ligne donne le nombre de lignes trouvées, puis cinq temps : la construction (`Recherche.pour`), la recherche dans le programme, le clone et egrep. Par exemple, dans `mesures/avant/` :

```
S(a|g|r)+on,automate,29,0.2817,0.0369,96.2444,7.6784,284.0183,16.7368,15.0418,0.6107
```

Cette ligne se lit ainsi :

- 29 lignes trouvées ;
- construction en 0,28 ms ;
- recherche en 96 ms ;
- 284 ms pour le clone ;
- 15 ms pour egrep.

### `etats.csv` : la méthode des sous-ensembles peut-elle exploser ?

Le motif est `a(a|b)…(a|b)`, avec k copies de `(a|b)`, pour k de 1 à 12. Pour chaque k, le fichier donne :

- le nombre d'états de l'ε-AFN, du DFA et du DFA minimal ;
- le temps de construction.

Pour savoir si une ligne contient ce motif, l'automate doit retenir lesquels des k + 1 derniers caractères étaient des `a`. Il lui faut donc 2^(k+1) états, et la minimisation ne peut rien fusionner. Par exemple :

```
12,78,8193,8192,120.6969,10.0368
```

Cette ligne se lit ainsi : pour k = 12, l'ε-AFN a 78 états, le DFA 8193 et le DFA minimal 8192, construits en 121 ms.

### `machine.txt` : dans quelles conditions ?

Il donne :

- le processeur et le système ;
- les versions de Java et d'egrep ;
- la taille du corpus ;
- le nombre de répétitions.

C'est à citer dans le rapport avec les graphiques.

## Comment une mesure est faite

- **Répétitions.** Chaque mesure est faite 10 fois, et le programme garde la moyenne et l'écart type, comme le demande le sujet.
- **Tours à vide.** Avant les 10 mesures, 3 tours ne sont pas comptés. En effet, Java compile pendant l'exécution le code le plus utilisé (le JIT) : les premiers tours sont plus lents et fausseraient la moyenne. Pour une commande complète, un seul tour à vide suffit, pour que le texte soit dans le cache du système.
- **Même résultat à chaque tour.** Chaque tour rend un nombre, par exemple le nombre de lignes trouvées, qui doit être le même à chaque fois. Ça détecte un bug. Ça empêche aussi le JIT de supprimer un calcul dont personne n'utilise le résultat. Pour la construction, le champ `puits` garde le résultat, pour la même raison.
- **Sortie dans un fichier.** La sortie des commandes va dans un fichier temporaire, pas vers `/dev/null`. Quand sa sortie est `/dev/null`, GNU grep s'arrête à la première ligne trouvée : on mesurait 1 ms au lieu de 18.
- **`LC_ALL=C.UTF-8`.** Les commandes tournent en UTF-8 : le `.` d'egrep reconnaît alors un caractère accentué entier, comme le clone.
- **De vraies copies.** Pour fabriquer 32 Mo avec les 6,4 Mo du corpus, `repeter` copie les lignes. Si on réutilisait les mêmes chaînes, elles tiendraient dans le cache du processeur, et les grandes tailles paraîtraient plus rapides qu'en vrai.
- **Vérifications.** Le programme vérifie que KMP et l'automate trouvent les mêmes lignes, et que le clone écrit exactement la même sortie qu'egrep. Sinon, il affiche « ATTENTION ».

## Lire le code

Le fichier se lit de haut en bas :

| Partie | Rôle |
|---|---|
| constantes | nombre de répétitions, motifs, tailles, k maximal |
| `Stats` | moyenne et écart type d'une série de mesures |
| `main` | crée un dossier temporaire, lance les cinq expériences, puis supprime le dossier |
| constructeur | lit les textes de `instances/` et les recopie dans un seul fichier, pour les commandes |
| `machine`, `demarrage`, `taille`, `motifs`, `etats` | une méthode par fichier produit |
| `chronometrer(IntSupplier)` | une mesure dans le programme |
| `chronometrer(List<String>, Path)` | une mesure de commande complète |
| le reste | petits outils : compter les lignes, construire les commandes, écrire les fichiers |

Les cinq méthodes d'expérience sont courtes. La longueur du fichier vient surtout des précautions de la partie précédente. Ce sont elles qui rendent les chiffres fiables.

## Avant et après

`mesures/avant/` contient les résultats avec la table de transitions actuelle de `Dfa`, à base de `HashMap`. Le prochain commit remplacera cette table par un tableau `int[][]`, puis relancera le banc d'essai dans `mesures/apres/`. Seul `Dfa` changera entre les deux, donc l'écart mesuré viendra de la table seule.

# Clone d'egrep — UE DAAR, projet 1

Ramy SAIL et Younes Bensallah

## Contenu de l'archive

| Chemin | Contenu |
|---|---|
| `egrep.jar` | le binaire |
| `rapport/` | le rapport |
| `src/main/java/` | les sources commentées |
| `src/test/java/` | les tests JUnit |
| `instances/` | les textes de test |
| `mesures/` | les résultats du banc d'essai, en CSV |
| `Makefile` | la compilation, sans Maven |
| `pom.xml` | les tests JUnit, avec Maven |

## Prérequis

- Java 21 ou plus récent (`java -version`), car le code utilise des `switch` avec motifs de type, qui datent de Java 21 ;
- pour recompiler : un JDK 21 ou plus récent et `make`.

`egrep.jar` est livré déjà compilé : pour le lancer, Java suffit. `make` le recompile directement avec `javac`, sans Maven, car le code n'a aucune dépendance. Seuls les tests JUnit (`make test`) passent par Maven, qui doit alors être installé : au premier lancement, il télécharge ses plugins et JUnit, ce qui demande un accès à internet.

## Lancer le binaire

```sh
java -jar egrep.jar [--detail] [--dfa] MOTIF FICHIER
```

Par exemple, pour la figure 1 du sujet :

```sh
java -jar egrep.jar 'S(a|g|r)+on' instances/56667-0.txt
```

Le motif doit être entre apostrophes, sinon le shell interprète `|`, `*` et les parenthèses.

Comme egrep, le programme affiche seulement les lignes qui contiennent une occurrence du motif. Deux options :

| Option | Effet |
|---|---|
| `--detail` | affichage enrichi : un en-tête avec la stratégie choisie, les lignes numérotées avec les occurrences en rouge, puis un bilan |
| `--dfa` | force l'automate même quand KMP s'applique, pour comparer les deux méthodes |

Le code de retour est celui d'egrep : 0 si au moins une ligne est trouvée, 1 sinon, 2 en cas d'erreur (motif mal formé, fichier illisible).

Pour vérifier que le résultat est identique à celui d'egrep (`grep -E`), avec bash :

```sh
diff <(java -jar egrep.jar 'S(a|g|r)+on' instances/56667-0.txt) \
     <(grep -E 'S(a|g|r)+on' instances/56667-0.txt) && echo identique
```

## Compiler et tester

| Commande | Effet |
|---|---|
| `make` | compile avec `javac` et produit `egrep.jar` |
| `make test` | lance les tests JUnit avec Maven (`mvn test`) |
| `make clean` | supprime les fichiers produits |

## Organisation du code

Les sources sont dans `src/main/java/egrep`, avec un fichier par étape du sujet :

| Étape du sujet | Classe |
|---|---|
| 1. regex → arbre syntaxique | `regex/RegexParser`, qui produit un `regex/RegexTree` |
| 2. arbre → ε-AFN, méthode d'Aho-Ullman | `automate/Ndfa` |
| 3. ε-AFN → DFA, méthode des sous-ensembles | `automate/Determinisation` |
| 4. DFA → DFA minimal, algorithme de Moore | `automate/Minimisation` |
| 5. recherche dans chaque ligne | `automate/Dfa` |
| KMP | `kmp/Kmp` |
| choix entre KMP et automate | `Recherche` |
| ligne de commande et affichage | `Egrep`, `Affichage` |

À l'étape 5, l'automate est construit pour `.*R` plutôt que pour `R` : un seul parcours de la ligne suffit pour savoir si elle contient une occurrence de `R`.

Les tests sont dans `src/test/java/egrep`, dans les mêmes packages que les classes qu'ils testent.

## Instances de test

Le dossier `instances/` contient six livres du projet Gutenberg, de tailles et de styles différents :

| Fichier | Livre | Auteur | Taille |
|---|---|---|---|
| `56667-0.txt` | *A History of Babylon*, le texte de la figure 1 du sujet | Leonard William King | 763 Ko |
| `pg11.txt` | *Alice's Adventures in Wonderland* | Lewis Carroll | 174 Ko |
| `pg1661.txt` | *The Adventures of Sherlock Holmes* | Arthur Conan Doyle | 608 Ko |
| `pg2701.txt` | *Moby Dick; Or, The Whale* | Herman Melville | 1,3 Mo |
| `pg2600.txt` | *War and Peace* | Léon Tolstoï | 3,4 Mo |
| `pg4650.txt` | *Candide, ou l'optimisme*, en français | Voltaire | 234 Ko |

Ce sont les textes tels que Gutenberg les distribue, en UTF-8 avec des fins de ligne CRLF. 

## Mesures de performance

Le banc d'essai du rapport compare KMP, l'automate et egrep sur les textes de `instances/`. Il se lance depuis la racine du projet, demande egrep et tourne en deux minutes environ :

```sh
make
java -cp egrep.jar egrep.mesures.Mesures mesures/avant
```

Chaque mesure est répétée 10 fois, après 3 tours à vide, et donne la moyenne et l'écart type en millisecondes. Le dossier donné reçoit :

| Fichier | Contenu |
|---|---|
| `machine.txt` | les conditions de mesure : processeur, système, versions de Java et d'egrep |
| `demarrage.csv` | le coût fixe du clone et d'egrep, sur un fichier vide |
| `taille.csv` | le temps selon la taille du texte, de 1 à 32 Mo, dans le programme et pour les commandes complètes |
| `motifs.csv` | motif par motif, sur tout le corpus : construction, recherche, commandes complètes du clone et d'egrep |
| `etats.csv` | le nombre d'états et le temps de construction pour `a(a\|b)…(a\|b)`, dont le DFA a 2^(k+1) états |

`mesures/avant/` contient les résultats obtenus avec la table de transitions à base de `HashMap`.

## Limites connues

- Seuls les opérateurs du sujet (et `+`) sont reconnus. Les autres opérateurs ERE (`?`, `{}`, `[]`, `^`, `$`, `\`) sont lus comme des caractères ordinaires.
- Le motif vide, `()` et une alternative vide comme `a||b` sont refusés, alors qu'egrep les accepte.
- Le fichier est lu en UTF-8. Une ligne qui contient des octets invalides peut s'afficher différemment qu'avec egrep.

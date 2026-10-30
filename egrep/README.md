# Clone d'egrep (projet 1)

Ramy SAIL et Younes Bensallah

Ce module est le projet 1 de l'UE DAAR. Le moteur de recherche du projet 2 (module `serveur`) s'en sert comme bibliothèque pour la recherche par expression régulière.

## Contenu du module

| Chemin | Contenu |
|---|---|
| `src/main/java/` | les sources commentées |
| `src/test/java/` | les tests JUnit |
| `instances/` | les textes de test |
| `pom.xml` | la compilation et les tests, avec Maven |

## Prérequis

Java 21 ou plus récent (`java -version`), car le code utilise des `switch` avec motifs de type, qui datent de Java 21. Le code n'a aucune dépendance.

Les commandes ci-dessous se lancent depuis la racine du dépôt. `./mvnw` télécharge Maven, puis JUnit, au premier lancement, ce qui demande un accès à internet.

## Compiler et tester

| Commande | Effet |
|---|---|
| `./mvnw -pl egrep package` | compile, lance les tests JUnit et produit `egrep/target/egrep-1.0.jar` |
| `./mvnw -pl egrep test` | lance seulement les tests JUnit |

## Lancer le binaire

```sh
java -jar egrep/target/egrep-1.0.jar [--detail] [--dfa] MOTIF FICHIER
```

Par exemple, pour la figure 1 du sujet :

```sh
java -jar egrep/target/egrep-1.0.jar 'S(a|g|r)+on' egrep/instances/56667-0.txt
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
diff <(java -jar egrep/target/egrep-1.0.jar 'S(a|g|r)+on' egrep/instances/56667-0.txt) \
     <(grep -E 'S(a|g|r)+on' egrep/instances/56667-0.txt) && echo identique
```

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

## Rapport et mesures

Le rapport du projet 1, son banc d'essai (`egrep.mesures.Mesures`) et les résultats des mesures ne sont plus dans le dépôt depuis le début du projet 2. On les retrouve dans l'historique git, au commit `c7823e3`, le dernier qui les contient.

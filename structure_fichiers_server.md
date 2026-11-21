# Arborescence du serveur (`server/src/main`)

Le serveur est un seul programme Spring Boot (Java 21), empaqueté dans `server/target/server-1.0.jar`. Il sert de deux façons :
- **sans argument**, c'est le serveur web : l'API REST sous `/api`, plus le client compilé ;
- **avec un nom d'étape**, il lance un calcul hors ligne, puis s'arrête. Les étapes sont `corpus`, `index`, `graph`, `index-scaling`, `graph-scaling`, `bench` et `load`.

Le code est rangé par fonctionnalité : un paquet par étape du traitement. On accède à PostgreSQL par `JdbcClient`, sans JPA. Les expressions régulières passent par le module `egrep`, c'est-à-dire le projet 1.

```
server/src/main/
├── java/engine/
│   ├── SearchEngineApplication.java, OfflineStep.java, Scaling.java, MeasuresProperties.java
│   ├── corpus/          hors ligne : téléchargement et nettoyage des livres
│   ├── indexing/        hors ligne : découpage en mots et index inversé
│   ├── graph/           hors ligne : distances de Jaccard, graphe de seuil, centralités
│   ├── books/           en ligne : lecture de la table book
│   ├── search/          en ligne : les trois recherches et le classement
│   ├── recommendation/  en ligne : suggestions par le graphe de Jaccard
│   ├── api/             en ligne : contrôleurs REST et formes JSON des réponses
│   └── measures/        mesures en ligne pour le rapport
└── resources/           configuration, schéma de la base, mots vides
```

Les paquets hors ligne remplissent les tables. Les paquets en ligne ne font que les lire.

---

## `corpus/` : constituer la bibliothèque (étape `corpus`)

Ce paquet choisit les livres, les télécharge depuis le miroir de Project Gutenberg, retire l'en-tête et le pied de page, garde les livres d'au moins 10 000 mots et remplit la table `book`.

| Fichier | Rôle |
|---|---|
| `CorpusStep.java` | **L'étape `corpus`.** Prend les livres de `corpus/ids.txt` s'il existe ; sinon, ceux du catalogue par numéro croissant, et écrit alors cette liste. Télécharge et nettoie chaque livre, compte ses mots, écarte les livres trop courts, puis remplit `book`. Reprend où elle en était après une interruption. Option `--limit=N`. |
| `Catalog.java` | **Lit le catalogue de Gutenberg** (`pg_catalog.csv`, avec commons-csv) et ne garde que les textes en anglais. Fournit les livres candidats et leurs métadonnées : titre, auteur sans les dates, langue. |
| `Mirror.java` | **Client HTTP du miroir** (`gutenberg.pglaf.org`). Télécharge le catalogue et les textes une requête à la fois, avec une pause de 2 s entre deux requêtes et des délais d'attente maximum. |
| `GutenbergText.java` | **Retire l'en-tête et le pied de page de Gutenberg** (présentation et licence, environ 3 000 mots communs à tous les livres), qui fausseraient l'index et les distances. |
| `Corpus.java` | **Constantes du sujet** : au moins 1664 livres, et au moins 10 000 mots par livre. |
| `CorpusProperties.java` | **Réglages de l'étape** : le dossier de travail (`data/`, avec un fichier `books/<id>.txt` par livre), la liste `corpus/ids.txt` et l'adresse du miroir. |

## `indexing/` : construire l'index inversé (étape `index`)

Ce paquet découpe les textes en mots, construit l'index et le charge dans les tables `term` et `occurrence`.

| Fichier | Rôle |
|---|---|
| `IndexStep.java` | **L'étape `index`.** Lit les textes des livres de `book` et construit l'index en mémoire. Le charge ensuite par `COPY` dans `term` et `occurrence`, en une seule transaction : les clés sont retirées puis recréées une fois, et un échec laisse l'ancien index intact. |
| `Tokenizer.java` | **Découpage en mots**, commun à l'indexation et aux requêtes : normalisation Unicode NFC, coupure sur tout ce qui n'est pas une lettre, minuscules, retrait des mots d'une lettre et des mots vides anglais. |
| `InvertedIndex.java` | **L'index en mémoire.** Construit d'abord la table pondérée de chaque livre (mot → nombre d'occurrences, en parallèle), puis le vocabulaire, trié et numéroté par ordre alphabétique. Inverse enfin les tables par un tri par dénombrement, pour obtenir les lignes (terme, livre, fréquence) dans l'ordre de la clé primaire. |
| `IndexScaling.java` | **L'étape `index-scaling`** (mesure) : temps de construction et taille de l'index (termes, lignes) sur des échantillons de 100 à 2000 livres. Écrit `index-scaling.csv`. Elle n'écrit rien en base. |

## `graph/` : graphe de Jaccard et centralités (étape `graph`)

Ce paquet calcule la distance entre tous les livres, garde comme arêtes les paires assez proches, puis calcule closeness et PageRank. Il remplit `neighbor` et les colonnes `degree`, `closeness` et `pagerank` de `book`.

| Fichier | Rôle |
|---|---|
| `GraphStep.java` | **L'étape `graph`.** Lit l'index direct, calcule toutes les distances, choisit le seuil θ (le plus petit qui donne un degré moyen d'au moins 20, sauf si `--theta` est donné), puis construit le graphe et calcule les centralités. Charge le tout en une transaction. Écrit `distances.csv`, `centralities.csv` et `edges.csv`. |
| `ForwardIndex.java` | **Index direct lu en base** : pour chaque livre, sa table pondérée, relue dans `occurrence`. |
| `WeightedTable.java` | **Table pondérée d'un livre** : ses numéros de termes triés, leurs nombres d'occurrences et leur somme \|D\|. |
| `Jaccard.java` | **Distance de Jaccard pondérée** entre deux tables, d = 1 − Σmin / Σmax. Calculée par une fusion des deux listes triées, et pour toutes les paires en parallèle (`matrix`). |
| `DistanceDistribution.java` | **Distances de toutes les paires, triées**, et distance de chaque livre à son plus proche voisin. Elles donnent, pour tout seuil θ, le nombre d'arêtes, le degré moyen et le nombre de livres isolés. Servent à choisir θ et à écrire l'histogramme. |
| `ThresholdGraph.java` | **Graphe de seuil** : deux livres sont voisins si leur distance est inférieure à θ. Listes d'adjacence non orientées. |
| `Closeness.java` | **Centralité de proximité** : un parcours en largeur par livre, avec la correction de Wasserman et Faust, puisque le graphe n'est pas connexe. |
| `PageRank.java` | **PageRank pondéré** par la similarité 1 − d. Calculé par itération de la puissance (α = 0,85), et la masse des livres isolés est redistribuée. |
| `GraphScaling.java` | **L'étape `graph-scaling`** (mesure) : temps de la matrice de Jaccard, de closeness et de PageRank sur les mêmes échantillons que `index-scaling`. Écrit `graph-scaling.csv`. Elle n'écrit rien en base. |

## `books/` : lecture des livres

| Fichier | Rôle |
|---|---|
| `BookRepository.java` | **Lecture de la table `book`** : la fiche d'un livre (`find`), son existence (`exists`), le nombre de livres (`count`), le catalogue par ordre alphabétique (`catalog`), et la conversion d'une ligne SQL en résumé de livre (`summary`), réutilisée par les autres paquets. |

## `search/` : les trois recherches et le classement

| Fichier | Rôle |
|---|---|
| `KeywordSearch.java` | **Recherche par mot-clé** dans `term` et `occurrence`. Les mots de la requête passent par le même `Tokenizer` que les livres. Un livre est retenu s'il contient tous les mots tels quels ; son score est la somme de leurs fréquences. |
| `IndexRegexSearch.java` | **RegEx sur l'index** : compile la RegEx en DFA minimal (projet 1), puis teste chaque terme du vocabulaire en entier. Garde au plus 2000 termes, les plus répandus d'abord, et lit leurs livres en une seule requête SQL. |
| `Vocabulary.java` | **Vocabulaire gardé en mémoire** pour la RegEx sur l'index, sous forme de tableaux triés par nombre de livres décroissant. Chargé à la première recherche, et rechargé tant qu'il est vide. |
| `TextRegexSearch.java` | **RegEx sur le texte intégral**, ligne par ligne : KMP pour une suite de lettres, sinon le DFA du projet 1. Les livres sont parcourus en parallèle. Un sémaphore limite ces recherches à 2 en même temps (erreur 503 après 30 s d'attente). Produit aussi les extraits surlignés d'un livre. |
| `Matches.java` | **Résultat d'une recherche dans l'index** : les occurrences par livre, les termes cherchés, et un indicateur de troncature au-delà de 2000 termes. |
| `Ranking.java` | **Classement commun aux trois recherches** : joint les scores à `book` et trie en base par occurrences, closeness ou PageRank, avec départage par occurrences puis par numéro. Seule la page demandée remonte. |

## `recommendation/` : suggestions

| Fichier | Rôle |
|---|---|
| `Recommendations.java` | **Voisins dans le graphe de Jaccard** (table `neighbor`). Fournit les suggestions d'une recherche (au plus 10 voisins des 3 premiers résultats, sans les livres déjà affichés) et les voisins d'un livre pour sa fiche, du plus proche au plus éloigné. |

## `api/` : l'API REST

Ce paquet contient les contrôleurs, qui reçoivent les requêtes HTTP, et les *records* qui donnent la forme JSON des réponses. Le client en reprend les types un à un.

| Fichier | Rôle |
|---|---|
| `SearchController.java` | **`GET /api/search` et `GET /api/search/regex`.** Lance la recherche demandée, le classement et les suggestions, et mesure le temps de calcul. Transforme les erreurs en 400 (RegEx mal formée) ou 503 (trop de recherches sur le texte). |
| `BookController.java` | **`GET /api/books`** (catalogue), **`/api/books/{id}`** (fiche), **`/{id}/neighbors`** (voisins) et **`/{id}/excerpts`** (lignes surlignées). Renvoie 404 pour un livre inconnu. |
| `SearchResponse.java` | **Réponse d'une recherche** : une page de livres, les suggestions, les termes cherchés, l'indicateur de troncature et le temps de calcul. |
| `Page.java` | **Page d'une liste** : `{count, next, results}`. |
| `Pages.java` | **Calcul de la pagination** : pages de 20 numérotées à partir de 1, avec le décalage SQL et l'URL de la page suivante. |
| `Book.java` | **Fiche d'un livre** : titre, auteur, langue, nombre de mots, degré, closeness, PageRank et couverture. |
| `BookSummary.java` | **Livre dans une liste** (résultats, suggestions, catalogue, voisins), avec ses occurrences pendant une recherche. |
| `Neighbor.java` | **Voisin d'un livre** : le livre et sa distance de Jaccard. |
| `Excerpt.java` | **Ligne d'un livre** qui contient la RegEx, avec les positions des occurrences à surligner. |
| `Sort.java` | **Valeurs du paramètre `sort`** : `occurrences`, `closeness` ou `pagerank`. |
| `Scope.java` | **Valeurs du paramètre `scope`** : `index` ou `text`. |
| `Covers.java` | **URL de la couverture** d'un livre, déduite de son numéro sur le miroir pglaf. Les images ne sont pas stockées. |
| `WebConfig.java` | **Accepte `sort` et `scope` en minuscules** (`?sort=pagerank`) ; une valeur inconnue donne une erreur 400. |
| `ClientRoutes.java` | **Renvoie `index.html` pour les routes du client** (`/books/42`), pour qu'un rechargement ou un lien partagé ouvre la bonne page. |

## `measures/` : mesures en ligne pour le rapport

| Fichier | Rôle |
|---|---|
| `SearchBenchmark.java` | **L'étape `bench`** : temps des recherches dans le serveur, sans passer par HTTP. Écrit `queries.csv`, `text-scaling.csv`, `kmp-dfa.csv`, `vocabulary-scaling.csv` et `automata.csv`. |
| `LoadTest.java` | **L'étape `load`** : test de charge de l'API d'un serveur déjà lancé, avec 1 à 32 clients simultanés. Écrit `load.csv` : débit et latences médiane, p95 et p99. |

## `resources/`

| Fichier | Rôle |
|---|---|
| `application.yml` | **Configuration** : connexion à la base (`DB_URL`, `DB_USER`, `DB_PASSWORD`), création du schéma au démarrage, format d'erreur Problem Details, dossiers des données et des mesures, adresse du miroir, port. |
| `schema.sql` | **Schéma de la base** : les tables `book`, `term`, `occurrence` (la matrice termes-documents creuse, c'est-à-dire l'index inversé) et `neighbor` (les arêtes du graphe). Spring l'applique à chaque démarrage, et ne crée que les tables absentes. |
| `stopwords-en.txt` | **Mots vides anglais** retirés par le `Tokenizer`. C'est la liste de Snowball, un mot par ligne. |

Le client compilé n'est pas dans `resources/` : `make client` le copie dans `resources/static/`, un dossier ignoré par git. Sous Docker, c'est nginx qui le sert.

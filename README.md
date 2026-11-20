# Moteur de recherche de livres

Ramy SAIL et Younes Bensallah — UE DAAR, projet 2

Application web de recherche dans une bibliothèque d'au moins 1664 livres du projet Gutenberg : recherche par mot-clé et par expression régulière, classement par centralité dans le graphe de Jaccard, suggestions de livres proches.

## Organisation

| Chemin | Contenu |
|---|---|
| `egrep/` | le projet 1, clone d'egrep, utilisé comme bibliothèque pour les expressions régulières (voir `egrep/README.md`) |
| `server/` | le serveur Spring Boot : API REST et accès à la base |
| `server/src/main/resources/db/migration/` | le schéma de la base, appliqué par Flyway au démarrage du serveur |
| `client/` | le client web : React, Vite et TypeScript |
| `docker-compose.yml` | l'application complète : base PostgreSQL, serveur et client |
| `server/Dockerfile`, `client/Dockerfile` | les images du serveur et du client (nginx) |

## Prérequis

- Java 21 ou plus récent ;
- Docker, pour PostgreSQL ;
- Node.js, seulement pour recompiler le client.

Maven n'a pas besoin d'être installé : `./mvnw` le télécharge au premier lancement.

## Lancer

```sh
make db
make client
make run
```

L'application répond sur http://localhost:8080, et depuis une autre machine du réseau local sur `http://ADRESSE-DU-SERVEUR:8080`.

Pour travailler sur le client avec rechargement automatique, laisser `make run` tourner et lancer `npm run dev` dans `client/` : Vite sert le client sur http://localhost:5173 et transmet les requêtes `/api` au serveur.

## Lancer avec Docker

Seul Docker est nécessaire :

```sh
docker compose up -d --build
docker compose run --rm server corpus --limit=50
docker compose run --rm server index
docker compose run --rm server graph
```

L'application répond sur http://localhost:8080. Le client est servi par nginx, qui transmet `/api` au serveur. Deux réseaux séparent les couches : `front` relie le client au serveur, `back` le serveur à la base ; le client n'atteint donc jamais la base. Deux volumes gardent les données : `data` pour PostgreSQL, `books` pour les textes des livres. Les dossiers `corpus/` (liste des livres) et `measures/` (mesures pour le rapport) du dépôt sont montés dans le conteneur du serveur.

`docker compose down` arrête l'application ; `docker compose down -v` efface aussi la base et les textes.

## Commandes

| Commande | Effet |
|---|---|
| `make db` | démarre PostgreSQL et attend qu'il accepte les connexions |
| `make client` | compile le client et le copie dans `server/src/main/resources/static` |
| `make build` | produit le jar exécutable `server/target/server-1.0.jar` |
| `make run` | compile puis lance le serveur |
| `make corpus`, `make index`, `make graph` | précalcul hors ligne, dans cet ordre : corpus, index inversé, graphe de Jaccard et centralités ; options de l'étape après `ARGS=` |
| `make index-scaling`, `make graph-scaling` | mesures hors ligne selon la taille du corpus, pour le rapport |
| `make test` | lance les tests JUnit des deux modules |
| `make up`, `make down` | démarre ou arrête l'application complète dans Docker |
| `make docker-corpus`, `make docker-index`, `make docker-graph` | précalcul hors ligne dans le conteneur du serveur ; options après `ARGS=` |
| `make clean` | supprime les fichiers produits |

`make test` lance un PostgreSQL jetable par Testcontainers : Docker doit tourner.

## Précalcul hors ligne

Trois étapes, dans cet ordre : `corpus`, `index`, `graph` (`make corpus`, ou `docker compose run --rm server corpus` dans Docker).

### `corpus`

Télécharge les livres depuis un miroir de Project Gutenberg, retire l'en-tête et le pied de page de Gutenberg, garde les livres d'au moins 10 000 mots et remplit la table `book`. Un mot est une suite de lettres ; les mots vides comptent.

- **Livres** : ceux de `corpus/ids.txt` s'il existe ; sinon, les textes anglais du catalogue de Gutenberg par numéro croissant, jusqu'à 2000 livres retenus. Le premier lancement complet écrit cette liste, qui n'est plus réécrite ensuite.
- **Durée** : une requête à la fois, avec une pause de 2 s entre deux téléchargements ; compter plusieurs heures pour le corpus complet. Interrompue, l'étape reprend où elle en était.
- **`--limit=N`** : s'arrêter à N livres, pour un petit corpus de développement (`make corpus ARGS=--limit=50`). N'écrit pas `corpus/ids.txt`.
- **Fichiers** : textes nettoyés dans `data/books/<id>.txt` ; catalogue de Gutenberg et livres rejetés dans `data/`.
- **Réglages**, par variables d'environnement : `DATA_DIR` (dossier `data`), `CORPUS_IDS` (liste des livres), `GUTENBERG_MIRROR` (par défaut `https://gutenberg.pglaf.org`).

Relancer `corpus` vide aussi l'index et le graphe, qu'il faut alors recalculer.

### `index`

Construit l'index inversé des livres de la table `book` et le charge dans les tables `term` et `occurrence`.

- Les textes passent par le même découpage que les requêtes : minuscules, mots d'au moins deux lettres, sans les mots vides anglais. Les termes sont numérotés par ordre alphabétique.
- Le chargement tient en une transaction : interrompue, l'étape laisse l'ancien index en place.
- L'étape échoue si un livre a moins de 10 000 mots ou si un texte manque. Sous 1664 livres (corpus de développement), elle avertit seulement.

### `graph`

Calcule la distance de Jaccard entre tous les livres de l'index, puis le graphe de seuil : deux livres sont voisins si leur distance est inférieure au seuil θ. Calcule enfin deux centralités dans ce graphe. Remplit la table `neighbor`, où chaque arête figure dans les deux sens, et les colonnes `degree`, `closeness` et `pagerank` de `book`.

- **Distance** : d = 1 − Σ min / Σ max, sur les nombres d'occurrences de chaque mot dans les deux livres, les sommes portant sur l'union de leurs vocabulaires. Elle vaut 0 pour deux livres identiques, 1 s'ils n'ont aucun mot commun.
- **`--theta=θ`** : le seuil, entre 0 exclu et 1 inclus (`make graph ARGS=--theta=0.6`). Sans cette option, l'étape prend le plus petit seuil au centième qui donne un degré moyen d'au moins 20.
- **Histogramme des distances** : `measures/distances.csv`, une ligne par tranche de 0,01. `pairs` compte les paires de livres dont la distance tombe dans la tranche ; `edges`, `mean_degree` et `isolated` donnent le nombre d'arêtes, le degré moyen et le nombre de livres sans voisin du graphe pour θ = `to`, de quoi choisir θ.
- **Closeness** : l'inverse de la distance moyenne d'un livre aux autres, en nombre d'arêtes. Le graphe n'étant pas toujours connexe, elle suit la correction de Wasserman et Faust : ((r − 1)/(n − 1)) · ((r − 1)/Σ d), où r est le nombre de livres atteignables, le livre compris. Un livre isolé vaut 0.
- **PageRank** : la part du temps qu'une marche aléatoire passe sur un livre. Elle suit une arête avec une probabilité proportionnelle à la similarité 1 − d, ou saute vers un livre quelconque avec la probabilité 0,15. Calcul par itération de la puissance, jusqu'à une variation inférieure à 10⁻¹² (200 itérations au plus).
- **Centralités** : `measures/centralities.csv` (`id`, `title`, `word_count`, `degree`, `closeness`, `pagerank`).
- **Arêtes** : `measures/edges.csv` (`book_a`, `book_b`, `distance`), une ligne par arête.
- Le chargement tient en une transaction : interrompue, l'étape laisse l'ancien graphe en place. Changer θ ne demande pas de refaire l'index.
- **Réglage**, par variable d'environnement : `MEASURES_DIR` (dossier `measures`).

## API

Les réponses sont en JSON ; les types sont les records de `engine.api` côté serveur et ceux de `client/src/api.ts` côté client. Les listes sont paginées par 20 (`page` commence à 1) sous la forme `{count, next, results}`, où `next` est l'URL de la page suivante ou `null`. Les erreurs suivent le format Problem Details (RFC 9457) : 400 pour un paramètre invalide ou une RegEx mal formée, 404 pour un livre inconnu, 503 si trop de recherches sur le texte intégral sont en cours (2 à la fois au plus).

| Requête | Réponse |
|---|---|
| `GET /api/search?q=&sort=&page=` | `SearchResponse` : page de `BookSummary`, plus `suggestions`, `terms`, `termsTruncated`, `durationMs` |
| `GET /api/search/regex?q=&scope=index\|text&sort=&page=` | idem |
| `GET /api/books?page=` | catalogue : page de `BookSummary` |
| `GET /api/books/{id}` | `Book` |
| `GET /api/books/{id}/neighbors` | liste de `Neighbor` : `{book, distance}` |
| `GET /api/books/{id}/excerpts?regex=&limit=50&words=false` | liste de `Excerpt` : `{number, line, occurrences: [[début, fin]]}` |

Recherche par mot-clé : chaque mot de `q` doit figurer tel quel dans l'index du livre, et un livre doit contenir tous les mots (ET). `prin` ne trouve donc pas princess ; pour cela, RegEx sur l'index `prin.*`. Pour les extraits, `words=true` ne surligne que les occurrences qui forment un mot entier, comme la recherche par mot-clé.

`sort` vaut `occurrences` (par défaut), `closeness` ou `pagerank` ; `scope` vaut `index` (par défaut) ou `text`. La RegEx sur le texte intégral lit les textes dans `data/books` (`DATA_DIR`).

Les `suggestions` sont les voisins, dans le graphe de Jaccard, des 3 premiers résultats du tri choisi : au plus 10 livres, par distance croissante à l'un d'eux, sans ces 3 livres ni ceux de la page affichée.

La connexion à la base se règle par les variables d'environnement `DB_URL`, `DB_USER` et `DB_PASSWORD`. Par défaut, elle vise le conteneur de `docker-compose.yml`.

## Mesures hors ligne

Deux étapes du jar, à lancer après `index` et `graph`, mesurent le passage à l'échelle selon le nombre de livres. Elles travaillent en mémoire et ne modifient pas la base. Les échantillons comptent 100, 250, 500, 1000, 1500 et 2000 livres ; ils sont tirés au hasard avec une graine fixe, chacun contient le précédent, et les deux étapes mesurent les mêmes livres. Option `--runs=N` : mesures par taille (3 par défaut), dont on garde la médiane.

- `index-scaling` (`make index-scaling`) : construction de l'index inversé, dans `measures/index-scaling.csv` (`books`, `words`, `terms`, `rows`, `seconds`) ;
- `graph-scaling` (`make graph-scaling`) : matrice de Jaccard, closeness et PageRank, au seuil de l'étape `graph`, dans `measures/graph-scaling.csv` (`books`, `pairs`, `jaccard_seconds`, `edges`, `isolated`, `closeness_seconds`, `pagerank_seconds`, `pagerank_iterations`).

`python3 rapport/offline_charts.py measures rapport/figures` (avec matplotlib) produit ensuite les figures de la partie hors ligne, à partir de ces fichiers et de ceux de l'étape `graph`, et affiche les tableaux du rapport : classements par centralité, corrélations de rang, livres isolés selon leur taille, petites composantes connexes.

## Mesures en ligne

Deux étapes du jar, à lancer sur le corpus complet, écrivent les CSV de `measures/` utilisés par le rapport :

- `bench` (`java -jar server/target/server-1.0.jar bench`, ou `docker compose run --rm server bench`) chronomètre les recherches dans le serveur, sans réseau : `queries.csv` (une liste fixe de requêtes pour les trois recherches), `text-scaling.csv` (RegEx sur le texte selon le nombre de livres, KMP contre automate), `kmp-dfa.csv` (KMP contre automate sur des lignes en mémoire), `vocabulary-scaling.csv` et `automata.csv` (RegEx sur l'index). Option `--runs=N` (5 par défaut) ;
- `load` est un test de charge de l'API d'un serveur déjà lancé : `load.csv` (débit et latences pour 1 à 32 clients simultanés). Options `--url=` (par défaut http://localhost:8080), `--seconds=` et `--levels=1,2,4,8,16,32`. Dans Docker : `docker compose run --rm server load --url=http://server:8080`.

`python3 rapport/graphiques.py measures rapport/figures` (avec matplotlib) produit ensuite les figures.

## Archive du rendu

```sh
git archive --format=zip --prefix=daar-assignment2-SAIL-BENSALLAH/ -o daar-assignment2-SAIL-BENSALLAH.zip HEAD
```

`git archive` ne prend que les fichiers suivis : ni les textes des livres, ni `node_modules`, ni `target`. Le corpus se reconstruit avec `corpus/ids.txt` et l'étape `corpus`. Le rapport compilé, `rapport/rapport.pdf`, doit être commité avant.

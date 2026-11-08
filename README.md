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

L'application répond sur http://localhost:8080. Le client est servi par nginx, qui transmet `/api` au serveur. Deux réseaux séparent les couches : `front` relie le client au serveur, `back` le serveur à la base ; le client n'atteint donc jamais la base. Deux volumes gardent les données : `data` pour PostgreSQL, `books` pour les textes des livres.

`docker compose down` arrête l'application ; `docker compose down -v` efface aussi la base et les textes.

## Commandes

| Commande | Effet |
|---|---|
| `make db` | démarre PostgreSQL et attend qu'il accepte les connexions |
| `make client` | compile le client et le copie dans `server/src/main/resources/static` |
| `make build` | produit le jar exécutable `server/target/server-1.0.jar` |
| `make run` | compile puis lance le serveur |
| `make corpus`, `make index`, `make graph` | précalcul hors ligne, dans cet ordre : corpus, index inversé, graphe de Jaccard et centralités ; options de l'étape après `ARGS=` |
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

## API

Les réponses sont en JSON ; les types sont les records de `engine.api` côté serveur et ceux de `client/src/api.ts` côté client. Les listes sont paginées par 20 (`page` commence à 1) sous la forme `{count, next, results}`, où `next` est l'URL de la page suivante ou `null`. Les erreurs suivent le format Problem Details (RFC 9457) : 400 pour un paramètre invalide ou une RegEx mal formée, 404 pour un livre inconnu.

| Requête | Réponse |
|---|---|
| `GET /api/search?q=&sort=&page=` | `SearchResponse` : page de `BookSummary`, plus `suggestions`, `terms`, `termsTruncated`, `durationMs` |
| `GET /api/search/regex?q=&scope=index\|text&sort=&page=` | idem |
| `GET /api/books?page=` | catalogue : page de `BookSummary` |
| `GET /api/books/{id}` | `Book` |
| `GET /api/books/{id}/neighbors` | liste de `Neighbor` : `{book, distance}` |
| `GET /api/books/{id}/excerpts?regex=&limit=50` | liste de `Excerpt` : `{number, line, occurrences: [[début, fin]]}` |

`sort` vaut `occurrences` (par défaut), `closeness` ou `pagerank` ; `scope` vaut `index` (par défaut) ou `text`.

La connexion à la base se règle par les variables d'environnement `DB_URL`, `DB_USER` et `DB_PASSWORD`. Par défaut, elle vise le conteneur de `docker-compose.yml`.

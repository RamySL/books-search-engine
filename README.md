# Moteur de recherche de livres

Ramy SAIL et Younes Bensallah — UE DAAR, projet 2

Application web de recherche dans une bibliothèque d'au moins 1664 livres du projet Gutenberg : recherche par mot-clé et par expression régulière, classement par centralité dans le graphe de Jaccard, suggestions de livres proches.

## Prérequis

- **Docker**, avec Docker Compose : il suffit pour lancer l'application.
- **Java 21** ou plus récent : seulement pour lancer les tests. Maven n'a pas besoin d'être installé : `./mvnw` le télécharge au premier lancement.

## Lancer avec Docker

```sh
# construit les images, puis démarre la base, le serveur et le client
docker compose up -d --build

# télécharge les livres depuis un miroir de Gutenberg :
# 50 livres en 5 à 10 minutes, pour essayer...
docker compose run --rm server corpus --limit=50
# ... ou les 2000 livres du corpus (corpus/ids.txt), en 3 à 5 heures selon le débit du miroir
docker compose run --rm server corpus

# construit l'index inversé
docker compose run --rm server index

# calcule le graphe de Jaccard et les centralités (closeness, PageRank)
docker compose run --rm server graph
```

L'application répond sur http://localhost:8080. Les recherches fonctionnent dès la fin de `graph`, sans redémarrage.

## Tests

Seuls les tests ne passent pas par Docker Compose :

```sh
# tests JUnit du projet 1 et du serveur ; Docker doit tourner, pour le PostgreSQL jetable de Testcontainers
make test        # ou, sans make : ./mvnw test
```

## Organisation

| Chemin | Contenu |
|---|---|
| `egrep/` | le projet 1, clone d'egrep, utilisé comme bibliothèque pour les expressions régulières (voir `egrep/README.md`) |
| `server/` | le serveur Spring Boot : API REST, précalcul hors ligne et accès à la base ; le détail, dossier par dossier et fichier par fichier, est dans `structure_fichiers_server.md` |
| `client/` | le client web : React, Vite et TypeScript ; `src/` se divise en `pages/` (Recherche, Livre), `components/`, `api/` (types et appels de l'API), `hooks/` et `utils/` |
| `corpus/ids.txt` | la liste des 2000 livres du corpus, pour le reproduire |
| `measures/` | les mesures du rapport (CSV) |


## Précalcul hors ligne

Trois étapes du jar, dans cet ordre ; le rapport décrit leurs algorithmes.

| Étape | Effet | Option |
|---|---|---|
| `corpus` | télécharge les livres de `corpus/ids.txt`, sans l'en-tête ni le pied de page de Gutenberg, et remplit la table `book` ; une pause de 2 s sépare deux téléchargements ; interrompue, l'étape reprend où elle en était | `--limit=N` : les N premiers livres seulement |
| `index` | construit l'index inversé : tables `term` et `occurrence` | |
| `graph` | calcule les distances de Jaccard, le graphe de seuil, closeness et PageRank : table `neighbor` et colonnes de `book` ; écrit `distances.csv`, `centralities.csv` et `edges.csv` dans `measures/` | `--theta=θ` : le seuil ; par défaut, le plus petit qui donne un degré moyen d'au moins 20 |

Relancer `corpus` vide l'index et le graphe, qu'il faut alors recalculer.

Réglages, par variables d'environnement : `DB_URL`, `DB_USER`, `DB_PASSWORD` (par défaut, le conteneur de `docker-compose.yml`), `DATA_DIR` (textes des livres, `data/`), `CORPUS_IDS`, `GUTENBERG_MIRROR` (`https://gutenberg.pglaf.org`), `MEASURES_DIR` (`measures/`).

## Mesures

Les CSV de `measures/` viennent de quatre étapes du jar, lancées sur le corpus complet après `graph` (dans Docker : `docker compose run --rm server <étape>`). Le rapport présente leur méthode et leurs résultats.

- `index-scaling`, `graph-scaling` : passage à l'échelle de l'index, de Jaccard et des centralités, sur des échantillons de 100 à 2000 livres (`--runs=N`) ;
- `bench` : temps des recherches dans le serveur, sans réseau (`--runs=N`) ;
- `load` : test de charge de l'API d'un serveur déjà lancé (`--url=`, `--seconds=`, `--levels=`). `load-docker.csv` est le même test, à travers nginx, contre l'application de `docker compose up`.

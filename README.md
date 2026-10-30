# Moteur de recherche de livres

Ramy SAIL et Younes Bensallah — UE DAAR, projet 2

Application web de recherche dans une bibliothèque d'au moins 1664 livres du projet Gutenberg : recherche par mot-clé et par expression régulière, classement par centralité dans le graphe de Jaccard, suggestions de livres proches.

## Organisation

| Chemin | Contenu |
|---|---|
| `egrep/` | le projet 1, clone d'egrep, utilisé comme bibliothèque pour les expressions régulières (voir `egrep/README.md`) |
| `serveur/` | le serveur Spring Boot : API REST et accès à la base |
| `serveur/src/main/resources/db/migration/` | le schéma de la base, appliqué par Flyway au démarrage du serveur |
| `client/` | le client web : React, Vite et TypeScript |
| `docker-compose.yml` | la base PostgreSQL |

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

## Commandes

| Commande | Effet |
|---|---|
| `make db` | démarre PostgreSQL et attend qu'il accepte les connexions |
| `make client` | compile le client et le copie dans `serveur/src/main/resources/static` |
| `make build` | produit le jar exécutable `serveur/target/serveur-1.0.jar` |
| `make run` | compile puis lance le serveur |
| `make test` | lance les tests JUnit des deux modules |
| `make clean` | supprime les fichiers produits |

La connexion à la base se règle par les variables d'environnement `DB_URL`, `DB_USER` et `DB_PASSWORD`. Par défaut, elle vise le conteneur de `docker-compose.yml`.

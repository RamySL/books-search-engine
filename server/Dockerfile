# Image du serveur : API REST et étapes hors ligne (corpus, index, graph).
# Contexte de build : la racine du dépôt, car le serveur dépend du module egrep/.

# Étape 1 : compilation des deux modules Maven
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY egrep/pom.xml egrep/
COPY server/pom.xml server/
COPY egrep/src egrep/src
COPY server/src server/src

RUN ./mvnw -q package -DskipTests

# Étape 2 : seulement un JRE et le jar, sans les sources ni Maven
FROM eclipse-temurin:21-jre
# uid 1000, comme le premier utilisateur d'un Linux : il peut écrire dans
# corpus/, monté depuis le dépôt (l'image fournit déjà ubuntu avec cet uid)
RUN userdel --remove ubuntu && useradd --uid 1000 --create-home engine
WORKDIR /app
COPY --from=build /src/server/target/server-1.0.jar server.jar
# dossiers écrits par les étapes hors ligne, montés par docker-compose.yml :
# data (textes des livres) et corpus (liste des livres)
RUN mkdir data corpus && chown engine:engine data corpus
USER engine
EXPOSE 8080
# sans argument : serveur web ; sinon le nom de l'étape et ses options,
# par exemple : docker compose run --rm server corpus --limit=50
ENTRYPOINT ["java", "-jar", "server.jar"]

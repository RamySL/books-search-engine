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
# le cache garde les dépendances Maven d'un build à l'autre
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q package -DskipTests

# Étape 2 : seulement un JRE et le jar, sans les sources ni Maven
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home engine
WORKDIR /app
COPY --from=build /src/server/target/server-1.0.jar server.jar
# dossier de travail des étapes hors ligne (textes des livres), monté en volume
RUN mkdir data && chown engine:engine data
USER engine
EXPOSE 8080
# sans argument : serveur web ; sinon le nom de l'étape et ses options,
# par exemple : docker compose run --rm server corpus --limit=50
ENTRYPOINT ["java", "-jar", "server.jar"]

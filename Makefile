# Moteur de recherche de livres (UE DAAR, projet 2).
# Prérequis : JDK 21 ou plus récent et Docker ; Node.js seulement pour
# recompiler le client. Maven n'a pas besoin d'être installé : ./mvnw le
# télécharge au premier lancement.

JAR := server/target/server-1.0.jar
STATIC := server/src/main/resources/static

.PHONY: all db client build run corpus index graph test clean

all: build

# Couche 3 : PostgreSQL dans Docker ; rend la main quand il accepte les connexions.
db:
	docker compose up -d --wait db

# Couche 1 : compile le client et le copie là où Spring Boot le sert.
client:
	cd client && npm ci && npm run build
	rm -rf $(STATIC)
	cp -r client/dist $(STATIC)

# Couche 2 : jar exécutable du serveur, avec le client compilé s'il a été copié.
build:
	./mvnw -q package -DskipTests

run: build
	java -jar $(JAR)

# Précalcul hors ligne, dans cet ordre : corpus, index inversé, graphe de
# Jaccard et centralités. Options de l'étape : make corpus ARGS=--limit=50
corpus index graph: build
	java -jar $(JAR) $@ $(ARGS)

test:
	./mvnw test

clean:
	./mvnw -q clean
	rm -rf client/dist $(STATIC)

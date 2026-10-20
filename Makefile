# Clone d'egrep (UE DAAR, projet 1).
# Compiler et lancer ne demandent qu'un JDK 21 ou plus récent : le code n'a
# aucune dépendance. Seul make test passe par Maven.

SOURCES := $(shell find src/main/java -name '*.java')

.PHONY: all test clean

all: egrep.jar

egrep.jar: $(SOURCES)
	rm -rf build
	javac --release 21 -encoding UTF-8 -d build $(SOURCES)
	jar --create --file egrep.jar --main-class egrep.Egrep -C build .

test:
	mvn test

clean:
	rm -rf build target egrep.jar

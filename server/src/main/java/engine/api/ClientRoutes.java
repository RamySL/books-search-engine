package engine.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Routes du client React. Le routage se fait dans le navigateur
 * (react-router) : si l'utilisateur recharge {@code /books/42} ou ouvre un
 * lien partagé, le serveur doit renvoyer index.html, et c'est le client qui
 * affiche la bonne vue. {@code /} est déjà servi par Spring Boot.
 * <p>
 * On liste les routes une à une plutôt qu'un motif générique, qui
 * intercepterait aussi les fichiers statiques et les URL /api inconnues
 * (qui doivent donner une 404).
 */
@Controller
class ClientRoutes {

    @GetMapping("/books/{id}")
    String book() {
        return "forward:/index.html";
    }
}

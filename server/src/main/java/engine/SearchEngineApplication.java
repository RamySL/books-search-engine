package engine;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Point d'entrée du jar, qui a deux usages :
 * <ul>
 *   <li>sans argument, le serveur web : l'API REST sous /api et le client
 *       compilé (resources/static) ;</li>
 *   <li>avec le nom d'une {@link OfflineStep} en premier argument, Spring sans
 *       serveur web : la base est migrée, l'étape tourne, puis le programme
 *       s'arrête.</li>
 * </ul>
 */
@SpringBootApplication
public class SearchEngineApplication {

    public static void main(String[] args) throws Exception {
        // les options (--limit=50, --server.port=…) ne sont pas des noms d'étape
        Optional<String> step = Arrays.stream(args).filter(arg -> !arg.startsWith("--")).findFirst();
        if (step.isEmpty()) {
            SpringApplication.run(SearchEngineApplication.class, args);
            return;
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SearchEngineApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            find(context, step.get()).run(context.getBean(ApplicationArguments.class));
        }
    }

    private static OfflineStep find(ConfigurableApplicationContext context, String name) {
        Map<String, OfflineStep> steps = context.getBeansOfType(OfflineStep.class).values().stream()
                .collect(Collectors.toMap(OfflineStep::name, step -> step));
        OfflineStep step = steps.get(name);
        if (step == null) {
            throw new IllegalArgumentException(
                    "Étape inconnue : " + name + " ; étapes disponibles : " + steps.keySet());
        }
        return step;
    }
}

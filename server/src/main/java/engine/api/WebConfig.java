package engine.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

/**
 * Paramètres de requête en minuscules ({@code ?sort=pagerank}) : par défaut,
 * Spring n'accepte que le nom exact de la constante ({@code PAGERANK}). Une
 * valeur inconnue donne une erreur 400.
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Sort.class, value -> Sort.valueOf(value.strip().toUpperCase(Locale.ROOT)));
        registry.addConverter(String.class, Scope.class, value -> Scope.valueOf(value.strip().toUpperCase(Locale.ROOT)));
    }
}

package co.elpatio.infraestructura.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Como viaja el JSON hacia el frontend.
 *
 * Las fechas salen en ISO-8601 y no como numero de milisegundos, porque el
 * frontend las recibe en campos declarados `string` en tipos.ts y las pasa
 * directo a `new Date(...)`. Los nulos se omiten para que un campo opcional
 * llegue como `undefined` en TypeScript y no como `null`, que es lo que el
 * modelo del prototipo espera.
 */
@Configuration
public class ConfiguracionJackson {

  @Bean
  Jackson2ObjectMapperBuilderCustomizer ajustesDeSerializacion() {
    return builder -> {
      builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      builder.serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
    };
  }
}

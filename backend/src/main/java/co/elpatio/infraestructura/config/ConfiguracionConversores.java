package co.elpatio.infraestructura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Conversion de enumerados que llegan como parametro de consulta.
 *
 * En el cuerpo de una peticion los enumerados los resuelve Jackson con el
 * @JsonCreator de cada uno, que acepta la forma en minuscula que usa tipos.ts.
 * En la URL no interviene Jackson: Spring llama a Enum.valueOf con el texto
 * crudo, y "cocina" no es "COCINA". Sin esto, `?destino=cocina` revienta con un
 * 500 en vez de resolverse, que fue exactamente lo que paso al probarlo.
 */
@Configuration
public class ConfiguracionConversores implements WebMvcConfigurer {

  @Override
  public void addFormatters(@NonNull FormatterRegistry registro) {
    registro.addConverterFactory(new EnumeradosEnMinuscula());
  }

  private static class EnumeradosEnMinuscula implements ConverterFactory<String, Enum<?>> {

    @Override
    @NonNull
    public <T extends Enum<?>> Converter<String, T> getConverter(@NonNull Class<T> destino) {
      return texto -> {
        if (texto == null || texto.isBlank()) return null;
        String buscado = texto.trim().toUpperCase();
        for (T valor : destino.getEnumConstants()) {
          if (valor.name().equals(buscado)) return valor;
        }
        throw new IllegalArgumentException(
            "«" + texto + "» no es un valor válido de " + destino.getSimpleName());
      };
    }
  }
}

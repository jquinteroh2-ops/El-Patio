package co.elpatio.infraestructura.ia;

import co.elpatio.dominio.puertos.InterpretePedidoTexto;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma el interprete de texto libre con las credenciales del ambiente.
 *
 * El modelo es configuracion, no una constante fija en el codigo: cuando el
 * volumen de mensajes lo justifique, el restaurante puede bajar a un modelo
 * mas barato sin tocar una linea de este servicio.
 */
@Configuration
public class ConfiguracionIA {

  @Bean
  public InterpretePedidoTexto interpretePedidoTexto(
      @Value("${elpatio.ia.llave}") String llave, @Value("${elpatio.ia.modelo}") String modelo) {
    AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(llave).build();
    return new InterpreteClaude(client, modelo);
  }
}

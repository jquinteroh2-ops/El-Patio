package co.elpatio.infraestructura.correo;

import co.elpatio.dominio.puertos.CorreoDeReservas;
import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Decide desde qué buzón sale el aviso de una reserva.
 *
 * <p>Con {@code ELPATIO_ZOHO_RESERVAS_REFRESH_TOKEN} sale de reservas@ por la API
 * de Zoho, con su propio «Self Client» (creado con la sesión de reservas@: el de
 * rrhh@ no puede mandar a nombre de otro buzón). Sin esa variable sale por el
 * correo general, el mismo de las hojas de vida.
 */
@Configuration
public class ConfiguracionCorreoDeReservas {

  private static final Logger registro = LoggerFactory.getLogger(ConfiguracionCorreoDeReservas.class);

  @Bean
  CorreoDeReservas correoDeReservas(
      NotificadorPorCorreo general,
      @Value("${elpatio.zoho.reservas.refresh-token:}") String token,
      @Value("${elpatio.zoho.reservas.client-id:}") String clienteId,
      @Value("${elpatio.zoho.reservas.client-secret:}") String clienteSecreto,
      @Value("${elpatio.zoho.dominio:zoho.com}") String dominio) {
    if (token == null || token.isBlank()) {
      return new CorreoDeReservas(general);
    }
    registro.info("Avisos de reservas por Zoho, desde el buzón de reservas");
    return new CorreoDeReservas(new CorreoPorZoho(clienteId, clienteSecreto, token, dominio, "", ""));
  }
}

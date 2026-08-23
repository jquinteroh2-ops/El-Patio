package co.elpatio.infraestructura.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Arma el cliente saliente de WhatsApp con las credenciales del ambiente. */
@Configuration
public class ConfiguracionWhatsApp {

  @Bean
  public ClienteGraphApi clienteGraphApi(
      @Value("${elpatio.whatsapp.url-base}") String urlBase,
      @Value("${elpatio.whatsapp.phone-number-id}") String phoneNumberId,
      @Value("${elpatio.whatsapp.token-acceso}") String tokenAcceso) {
    return new ClienteGraphApi(urlBase, phoneNumberId, tokenAcceso);
  }
}

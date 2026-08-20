package co.elpatio.infraestructura.tiemporeal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal de tiempo real.
 *
 * Reemplaza al BroadcastChannel del prototipo, que solo llegaba a las pestanas
 * del mismo navegador: por eso una comanda tomada en la tablet del mesero nunca
 * aparecia en la pantalla de cocina, que es otro dispositivo.
 *
 * Los topicos van por area para que cada pantalla escuche solo lo suyo: la
 * pantalla de cocina no tiene por que despertarse cuando cambia una reserva.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

  private final String[] origenesPermitidos;

  public ConfiguracionWebSocket(@Value("${elpatio.cors.origenes}") String[] origenesPermitidos) {
    this.origenesPermitidos = origenesPermitidos;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registro) {
    registro.enableSimpleBroker(Topicos.COMANDAS, Topicos.MESAS, Topicos.PEDIDOS, Topicos.GENERAL);
    registro.setApplicationDestinationPrefixes("/app");
  }

  /**
   * El canal no exige token, y puede no exigirlo porque por el no viaja ningun
   * dato del negocio: el evento solo dice que algo cambio y en que area. La
   * pantalla que lo recibe vuelve a pedir los datos por el API, que si esta
   * autenticado. Quien se conecte sin credencial se entera de que hubo un
   * movimiento y de nada mas.
   *
   * Si algun dia el evento empieza a llevar la comanda adentro, esta decision
   * deja de ser valida y hay que autenticar el handshake.
   */
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registro) {
    // Sin SockJS: el salon corre sobre navegadores modernos y el respaldo por
    // sondeo largo solo agregaria peso al bundle del frontend.
    registro.addEndpoint("/ws").setAllowedOriginPatterns(origenesPermitidos);
  }
}

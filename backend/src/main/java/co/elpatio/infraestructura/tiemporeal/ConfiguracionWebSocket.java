package co.elpatio.infraestructura.tiemporeal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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

  /**
   * El latido del canal.
   *
   * El broker simple no late si no se le da con que medir el tiempo, y sin
   * `setTaskScheduler` ignora en silencio el `heart-beat` que pide el
   * navegador: el servidor contesta `0,0` y nadie vuelve a decir nada.
   *
   * Eso es justo lo que rompe una pantalla de salon. Un proxy corta lo que
   * lleva rato callado y el socket queda medio abierto: el navegador cree que
   * sigue conectado, no reconecta, y la pantalla se queda con la foto que tenia
   * -al dia solo por la reconsulta de seguridad, un minuto tarde-. Con latido,
   * la caida se nota a los pocos segundos y el cliente vuelve a entrar solo.
   */
  @Bean
  public TaskScheduler planificadorLatido() {
    ThreadPoolTaskScheduler planificador = new ThreadPoolTaskScheduler();
    planificador.setPoolSize(1);
    planificador.setThreadNamePrefix("latido-ws-");
    planificador.initialize();
    return planificador;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registro) {
    registro
        .enableSimpleBroker(Topicos.COMANDAS, Topicos.MESAS, Topicos.PEDIDOS, Topicos.GENERAL)
        // Los mismos 10 s que pide el frontend en almacen.ts. Si cambia uno,
        // cambia el otro: el valor negociado es el mayor de los dos.
        .setHeartbeatValue(new long[] {10000, 10000})
        .setTaskScheduler(planificadorLatido());
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

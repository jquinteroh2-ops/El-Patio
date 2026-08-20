package co.elpatio.infraestructura.tiemporeal;

import co.elpatio.dominio.puertos.PublicadorEventos;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica los cambios por STOMP.
 *
 * El evento conserva la forma de `EventoSync` de almacen.ts (version, origen,
 * cambios) para que la suscripcion del frontend no cambie de contrato al pasar
 * del BroadcastChannel al WebSocket.
 *
 * El aviso sale despues del commit, no durante la transaccion. Si saliera antes
 * y la transaccion se revirtiera, la pantalla de cocina se habria refrescado
 * para leer una comanda que nunca existio.
 */
@Component
public class PublicadorStomp implements PublicadorEventos {

  private final SimpMessagingTemplate mensajeria;

  /**
   * Version incremental del evento. No identifica una version de los datos:
   * sirve para que el frontend descarte avisos que le llegan desordenados.
   */
  private final AtomicLong version = new AtomicLong(System.currentTimeMillis());

  public PublicadorStomp(SimpMessagingTemplate mensajeria) {
    this.mensajeria = mensajeria;
  }

  /** Lo que viaja por el canal. Mismos campos que EventoSync en almacen.ts. */
  public record EventoSync(long version, String origen, List<String> cambios) {}

  @Override
  public void publicar(List<String> cambios) {
    if (cambios == null || cambios.isEmpty()) return;
    EventoSync evento = new EventoSync(version.incrementAndGet(), "servidor", List.copyOf(cambios));

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              enviar(evento);
            }
          });
      return;
    }
    enviar(evento);
  }

  private void enviar(EventoSync evento) {
    for (String topico : topicosDe(evento.cambios())) {
      mensajeria.convertAndSend(topico, evento);
    }
  }

  /**
   * Reparte el aviso por area. Un cambio puede tocar varias: enviar una comanda
   * mueve la cocina y tambien el mapa de mesas.
   *
   * Todo cambio va ademas a /topic/general, que es al que se suscriben las
   * pantallas administrativas: sin ese cajon, un cambio de carta o de ajustes
   * no llegaria a ninguna parte.
   */
  private Set<String> topicosDe(List<String> cambios) {
    Set<String> topicos = new LinkedHashSet<>();
    for (String cambio : cambios) {
      switch (cambio) {
        case "ordenes", "cocina" -> topicos.add(Topicos.COMANDAS);
        case "mesas" -> topicos.add(Topicos.MESAS);
        case "pedidos" -> topicos.add(Topicos.PEDIDOS);
        default -> { /* las demas areas solo viajan por el canal general */ }
      }
    }
    topicos.add(Topicos.GENERAL);
    return topicos;
  }
}

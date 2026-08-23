package co.elpatio.infraestructura.whatsapp;

import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.puertos.NotificadorDeClientes;
import org.springframework.stereotype.Component;

/**
 * El unico canal automatizado con adaptador hoy: si el pedido no vino de
 * WhatsApp, este notificador no hace nada. Un canal nuevo (el agente de voz)
 * agregara su propia implementacion del puerto sin que esta se entere.
 */
@Component
public class NotificadorWhatsApp implements NotificadorDeClientes {

  private final ClienteGraphApi cliente;

  public NotificadorWhatsApp(ClienteGraphApi cliente) {
    this.cliente = cliente;
  }

  @Override
  public void avisarAnticipoConfirmado(Orden orden) {
    if (orden.getCanal() != Canal.WHATSAPP || orden.getCliente() == null) return;
    cliente.enviarTexto(
        orden.getCliente().telefono(),
        "¡Pago confirmado! Tu pedido "
            + orden.etiquetaCanal()
            + " ya está en cocina. Te avisamos cuando esté listo.");
  }

  @Override
  public void avisarAnticipoRechazado(Orden orden) {
    if (orden.getCanal() != Canal.WHATSAPP || orden.getCliente() == null) return;
    cliente.enviarTexto(
        orden.getCliente().telefono(),
        "No pudimos confirmar el pago de tu pedido "
            + orden.etiquetaCanal()
            + ". Si el banco te descontó algo, se reversa solo. Escríbenos si quieres intentar de nuevo.");
  }
}

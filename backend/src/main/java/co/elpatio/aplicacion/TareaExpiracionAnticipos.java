package co.elpatio.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cierra los anticipos vencidos que nadie pago.
 *
 * Sin esto, un cliente que abre el link y nunca paga dejaria su pedido
 * congelado en `esperando_anticipo` para siempre, ocupando un cupo en la
 * columna de espera del panel. Cada minuto es suficiente: el link ya vence en
 * Wompi a los veinte, asi que un minuto de mas no cambia la experiencia de
 * nadie.
 */
@Component
public class TareaExpiracionAnticipos {

  private static final Logger registro = LoggerFactory.getLogger(TareaExpiracionAnticipos.class);

  private final ServicioAnticipos servicio;

  public TareaExpiracionAnticipos(ServicioAnticipos servicio) {
    this.servicio = servicio;
  }

  @Scheduled(fixedDelay = 60_000)
  public void ejecutar() {
    try {
      servicio.expirarVencidos();
    } catch (RuntimeException e) {
      registro.error("Fallo el cierre de anticipos vencidos", e);
    }
  }
}

package co.elpatio.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drena la bandeja de salida hacia el ERP.
 *
 * Cada minuto, como la tarea de anticipos. No hace falta mas: el documento
 * fiscal no tiene que existir en el segundo en que el cliente paga, tiene que
 * existir antes del cierre contable, y un minuto de retraso no le cambia la
 * vida a nadie. Ir mas rapido solo multiplicaria las llamadas contra un ERP que
 * puede estar en la maquina del restaurante.
 *
 * El bucle vive aqui y no dentro del servicio a proposito: cada envio necesita
 * su propia transaccion, y una llamada del servicio a su propio metodo no pasa
 * por el proxy de Spring, con lo que la anotacion se perderia sin avisar.
 */
@Component
public class TareaEnvioAlErp {

  private static final Logger registro = LoggerFactory.getLogger(TareaEnvioAlErp.class);

  private final ServicioIntegracionErp servicio;

  public TareaEnvioAlErp(ServicioIntegracionErp servicio) {
    this.servicio = servicio;
  }

  @Scheduled(fixedDelay = 60_000)
  public void ejecutar() {
    try {
      for (String envioId : servicio.pendientes()) {
        try {
          servicio.procesar(envioId);
        } catch (RuntimeException e) {
          // Un envio que revienta no se lleva por delante a los demas de la
          // tanda. El estado del que fallo ya quedo escrito por el servicio.
          registro.error("Fallo el envio {} al ERP", envioId, e);
        }
      }
    } catch (RuntimeException e) {
      registro.error("Fallo la pasada de envios al ERP", e);
    }
  }
}

package co.elpatio.aplicacion;

import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.conversacion.Conversacion;
import co.elpatio.dominio.conversacion.EstadoConversacion;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El hilo de charla con un cliente, sin saber nada de por que canal habla.
 *
 * Es deliberadamente delgado: no decide que responder ni cuando cobrar, solo
 * abre, recupera y guarda el hilo. La logica de que preguntar segun el paso en
 * que va la charla vive en el adaptador de cada canal (hoy
 * `infraestructura.whatsapp`, manana el agente de voz), que es quien de verdad
 * sabe hablarle al cliente por ese medio.
 */
@Service
public class ServicioConversaciones {

  private final Repositorios.DeConversaciones conversaciones;
  private final GeneradorIds ids;
  private final Reloj reloj;

  public ServicioConversaciones(
      Repositorios.DeConversaciones conversaciones, GeneradorIds ids, Reloj reloj) {
    this.conversaciones = conversaciones;
    this.ids = ids;
    this.reloj = reloj;
  }

  /**
   * La conversacion abierta con ese identificador en ese canal, o una nueva si
   * no hay ninguna (la primera vez que ese cliente escribe, o si la ultima ya
   * termino).
   */
  @Transactional
  public Conversacion obtenerOCrear(Canal canal, String identificadorExterno) {
    return conversaciones
        .abiertaPara(canal, identificadorExterno)
        .orElseGet(
            () -> {
              Conversacion nueva = new Conversacion();
              nueva.setId(ids.nuevo("conv"));
              nueva.setCanal(canal);
              nueva.setIdentificadorExterno(identificadorExterno);
              nueva.setEstado(EstadoConversacion.INICIADA);
              nueva.setIniciadaEn(reloj.ahora());
              nueva.setActualizadaEn(reloj.ahora());
              return conversaciones.guardar(nueva);
            });
  }

  @Transactional
  public Conversacion guardar(Conversacion conversacion) {
    return conversaciones.guardar(conversacion);
  }
}

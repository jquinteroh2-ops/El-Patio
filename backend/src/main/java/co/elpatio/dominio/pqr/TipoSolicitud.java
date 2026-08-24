package co.elpatio.dominio.pqr;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Que clase de solicitud radica el cliente.
 *
 * Las cinco no son lo mismo aunque compartan formulario: una felicitacion no
 * tiene termino de respuesta y un reclamo si. Se guardan separadas porque el
 * reporte por tipo es justo lo que le dice al restaurante si tiene un problema
 * de servicio o de cocina.
 */
public enum TipoSolicitud {
  PETICION("Petición"),
  QUEJA("Queja"),
  RECLAMO("Reclamo"),
  SUGERENCIA("Sugerencia"),
  FELICITACION("Felicitación");

  private final String etiqueta;

  TipoSolicitud(String etiqueta) { this.etiqueta = etiqueta; }

  public String etiqueta() { return etiqueta; }

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static TipoSolicitud de(String valor) { return valueOf(valor.toUpperCase()); }

  /**
   * Si la ley obliga a responder dentro de un termino.
   *
   * Una felicitacion no exige respuesta —agradecerla es cortesia, no
   * obligacion—, y contarle un vencimiento la pondria en rojo en el panel
   * compitiendo por atencion con un reclamo que si vence.
   */
  public boolean exigeRespuestaEnTermino() {
    return this != FELICITACION;
  }
}

package co.elpatio.dominio.publicacion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Que es lo que el restaurante esta publicando.
 *
 * No es una etiqueta: decide donde sale en el sitio publico. Una promocion
 * compite por la atencion del que esta decidiendo si viene; una foto del local
 * acompana esa decision pero no la interrumpe.
 */
public enum TipoPublicacion {
  /** Una oferta con fecha. Sale primero y con su vigencia a la vista. */
  PROMOCION,
  /** Una noche de musica, un menu especial, una fecha. */
  EVENTO,
  /** Como se ve el local. Sin urgencia y sin vencimiento. */
  GALERIA;

  /**
   * Como se escribe hacia afuera: en minuscula.
   *
   * Es la misma convencion que `Destino`, y no es cosmetica: asi se guarda en
   * la base -donde la restriccion de la tabla espera minusculas- y asi viaja en
   * el JSON que lee el navegador. Un solo valor para las dos puntas.
   */
  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static TipoPublicacion de(String valor) { return valueOf(valor.toUpperCase()); }
}

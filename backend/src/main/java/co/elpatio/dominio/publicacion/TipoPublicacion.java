package co.elpatio.dominio.publicacion;

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
  GALERIA
}

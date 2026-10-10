package co.elpatio.agente;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Quien le pone la venta a Globalsoft en la mano.
 *
 * Es la unica pieza que depende de como recibe Globalsoft: un archivo para
 * importar, su base de datos o un servicio web. Todavia no se sabe cual, y por
 * eso esta separada: cuando se sepa se escribe otra implementacion y lo demas
 * del agente no cambia.
 *
 * <p>Como el puerto de la nube, no lanza por fallos esperables: que Globalsoft
 * este cerrado o rechace la venta es un {@link Resultado#rechazado}, no una
 * excepcion.
 */
public interface Entregador {

  Resultado entregar(JsonNode venta);

  /** Para la bitacora. */
  String nombre();
}

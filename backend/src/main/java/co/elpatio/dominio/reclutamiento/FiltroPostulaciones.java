package co.elpatio.dominio.reclutamiento;

import java.time.LocalDate;

/**
 * Lo que el administrador tiene puesto en la bandeja.
 *
 * Todo opcional: un campo en nulo no filtra. Es lo que permite combinar los
 * filtros sin escribir una consulta por combinacion posible.
 */
public record FiltroPostulaciones(
    EstadoPostulacion estado,
    CargoDeInteres cargo,
    LocalDate desde,
    LocalDate hasta,
    /** Nombre o numero de documento. Es como se busca de verdad a alguien. */
    String busqueda,
    int pagina,
    int tamano) {

  /** Un tope duro: pedir diez mil filas de una vez no ayuda a nadie. */
  private static final int TAMANO_MAXIMO = 100;

  public FiltroPostulaciones {
    pagina = Math.max(0, pagina);
    tamano = tamano <= 0 ? 20 : Math.min(tamano, TAMANO_MAXIMO);
    busqueda = (busqueda == null || busqueda.isBlank()) ? null : busqueda.trim();
  }
}

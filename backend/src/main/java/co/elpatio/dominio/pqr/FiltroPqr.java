package co.elpatio.dominio.pqr;

import java.time.LocalDate;

/**
 * Lo que el administrador tiene puesto en la bandeja de PQR.
 *
 * Todo opcional: un campo en nulo no filtra.
 */
public record FiltroPqr(
    TipoSolicitud tipo,
    EstadoPqr estado,
    LocalDate desde,
    LocalDate hasta,
    /** Nombre, radicado o asunto. Es como se busca una solicitud concreta. */
    String busqueda,
    int pagina,
    int tamano) {

  private static final int TAMANO_MAXIMO = 100;

  public FiltroPqr {
    pagina = Math.max(0, pagina);
    tamano = tamano <= 0 ? 20 : Math.min(tamano, TAMANO_MAXIMO);
    busqueda = (busqueda == null || busqueda.isBlank()) ? null : busqueda.trim();
  }
}

package co.elpatio.dominio.puertos;

import co.elpatio.dominio.reporte.DefinicionReporte;
import co.elpatio.dominio.reporte.FilaReporte;
import java.io.OutputStream;
import java.util.stream.Stream;

/**
 * Convierte un reporte en un archivo descargable.
 *
 * Hay una implementacion por formato y ninguna sabe de que reporte se trata:
 * reciben la definicion y las filas, y las pintan. Por eso agregar un reporte
 * nuevo no obliga a escribir exportacion, y agregar un formato nuevo no obliga
 * a tocar los reportes.
 *
 * <p><b>Escribe en streaming y nunca acumula.</b> Las filas llegan como
 * {@link Stream} y salen directo al {@link OutputStream} de la respuesta. Un
 * mes de ventas son decenas de miles de filas; juntarlas todas en una lista
 * antes de escribir es como se queda sin memoria un contenedor pequeño, y pasa
 * justo el dia que el reporte importa.
 */
public interface Exportador {

  /**
   * Escribe el reporte.
   *
   * Quien llame es el dueño del {@code destino} y de cerrarlo. Quien implemente
   * es el dueño de consumir el {@code filas} una sola vez.
   */
  void exportar(DefinicionReporte definicion, Stream<FilaReporte> filas, OutputStream destino);

  /** `xlsx` o `pdf`. Con esto se arma el nombre del archivo y el Content-Type. */
  String extension();

  String tipoDeContenido();
}

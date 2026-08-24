package co.elpatio.dominio.reporte;

import java.util.Arrays;
import java.util.List;

/**
 * Una fila de datos de un reporte.
 *
 * Los valores viajan como objetos y NO como cadenas, y eso es deliberado: un
 * `long` tiene que llegar al exportador siendo un `long` para que en Excel
 * quede como numero. Convertirlo a texto aqui —«$ 58.000»— produce una hoja
 * bonita e inutil, porque el contador no puede sumar una columna de texto.
 *
 * Los tipos admitidos se corresponden con `ColumnaReporte.Tipo`:
 * String, Long/Integer, LocalDate e Instant. Un nulo se pinta vacio.
 */
public record FilaReporte(List<Object> valores) {

  public static FilaReporte de(Object... valores) {
    return new FilaReporte(Arrays.asList(valores));
  }

  public Object valor(int columna) {
    return columna < valores.size() ? valores.get(columna) : null;
  }
}

package co.elpatio.dominio.reporte;

/**
 * Una columna de un reporte, con lo que hace falta para pintarla bien.
 *
 * El {@link Tipo} no es decoracion: es lo que decide si el valor llega a Excel
 * como numero o como texto. Una columna de plata que llega como texto arruina
 * el archivo —el contador no puede sumarla, ni filtrarla por mayor que, ni
 * hacerle una formula— y es el error mas comun al generar hojas de calculo.
 */
public record ColumnaReporte(String titulo, Tipo tipo, int anchoCaracteres) {

  public enum Tipo {
    TEXTO,
    /** Cantidades, unidades, conteos. Entero sin separador de miles. */
    ENTERO,
    /** Pesos colombianos. Sin decimales: el peso no los tiene en la practica. */
    DINERO,
    /** Solo el dia. Va como fecha real, no como la cadena «24/08/2026». */
    FECHA,
    /** Dia y hora. */
    FECHA_HORA,
    /** Un porcentaje ya calculado, de 0 a 100. */
    PORCENTAJE
  }

  public static ColumnaReporte texto(String titulo, int ancho) {
    return new ColumnaReporte(titulo, Tipo.TEXTO, ancho);
  }

  public static ColumnaReporte entero(String titulo) {
    return new ColumnaReporte(titulo, Tipo.ENTERO, 10);
  }

  public static ColumnaReporte dinero(String titulo) {
    // Ancho generoso: «$ 1.234.567» no cabe en diez caracteres, y una columna
    // de plata que se corta sale con almohadillas en Excel.
    return new ColumnaReporte(titulo, Tipo.DINERO, 16);
  }

  public static ColumnaReporte fecha(String titulo) {
    return new ColumnaReporte(titulo, Tipo.FECHA, 12);
  }

  public static ColumnaReporte fechaHora(String titulo) {
    return new ColumnaReporte(titulo, Tipo.FECHA_HORA, 18);
  }

  public static ColumnaReporte porcentaje(String titulo) {
    return new ColumnaReporte(titulo, Tipo.PORCENTAJE, 10);
  }

  /** Los numeros van a la derecha; el texto, a la izquierda. */
  public boolean alineaDerecha() {
    return tipo != Tipo.TEXTO;
  }

  /** Si esta columna admite una suma al pie. */
  public boolean sumable() {
    return tipo == Tipo.DINERO || tipo == Tipo.ENTERO;
  }
}

package co.elpatio.dominio.erp;

/**
 * Lo que respondio el ERP cuando se le mando una venta.
 *
 * Son tres desenlaces y hay que distinguirlos, porque el sistema hace algo
 * distinto con cada uno:
 *
 * <ul>
 *   <li>{@link #confirmado} — hay documento. Se guarda su numero y se cierra.
 *   <li>{@link #rechazado} — el ERP dijo que no. Reintentar sirve solo si la
 *       causa es pasajera (no responde, esta ocupado); si el ERP rechazo por el
 *       contenido —un producto que no existe en su catalogo—, reintentar lo
 *       mismo va a dar lo mismo, y por eso el motivo se guarda en claro.
 *   <li>{@link #enEspera} — no hubo respuesta porque no habia a quien
 *       preguntar. Es lo que devuelve el adaptador manual, y NO es un error: la
 *       venta esta bien y le toca a una persona digitarla.
 * </ul>
 */
public record ResultadoFacturacion(
    Desenlace desenlace,
    /** El numero del documento que emitio el ERP. Solo viene si confirmo. */
    String numeroDocumento,
    /** Lo que respondio, tal cual. Va a la bitacora sin interpretar. */
    String respuestaCruda,
    /** Por que no salio, en texto que un administrador pueda leer. */
    String motivo) {

  public enum Desenlace {
    CONFIRMADO,
    RECHAZADO,
    EN_ESPERA
  }

  public static ResultadoFacturacion confirmado(String numeroDocumento, String respuestaCruda) {
    if (numeroDocumento == null || numeroDocumento.isBlank()) {
      // Un ERP que confirma sin decir con que numero no confirmo nada: sin ese
      // dato la conciliacion del contador no tiene contra que cruzar la venta.
      throw new IllegalArgumentException("Un documento confirmado necesita numero");
    }
    return new ResultadoFacturacion(Desenlace.CONFIRMADO, numeroDocumento, respuestaCruda, null);
  }

  public static ResultadoFacturacion rechazado(String motivo, String respuestaCruda) {
    return new ResultadoFacturacion(Desenlace.RECHAZADO, null, respuestaCruda, motivo);
  }

  public static ResultadoFacturacion enEspera(String motivo) {
    return new ResultadoFacturacion(Desenlace.EN_ESPERA, null, null, motivo);
  }

  public boolean confirmo() { return desenlace == Desenlace.CONFIRMADO; }
}

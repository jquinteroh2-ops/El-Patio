package co.elpatio.agente;

/**
 * Como le fue a una venta con Globalsoft. Es lo que se le contesta a la nube.
 *
 * Los mismos tres desenlaces que entiende El Patio:
 *
 * <ul>
 *   <li>{@code confirmado} — Globalsoft emitio el documento y dio su numero.
 *   <li>{@code rechazado} — no salio. La nube la vuelve a ofrecer mas tarde.
 *   <li>{@code en_espera} — se le entrego a Globalsoft pero todavia no hay
 *       numero (por ejemplo, un archivo que alguien tiene que importar).
 * </ul>
 */
public record Resultado(
    String desenlace, String numeroDocumento, String motivo, String respuestaCruda) {

  public static Resultado confirmado(String numeroDocumento, String respuestaCruda) {
    return new Resultado("confirmado", numeroDocumento, null, respuestaCruda);
  }

  public static Resultado rechazado(String motivo) {
    return new Resultado("rechazado", null, motivo, null);
  }

  public static Resultado enEspera(String motivo) {
    return new Resultado("en_espera", null, motivo, null);
  }
}

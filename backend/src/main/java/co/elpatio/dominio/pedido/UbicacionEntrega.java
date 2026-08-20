package co.elpatio.dominio.pedido;

import co.elpatio.dominio.error.ReglaDeNegocioError;

/**
 * Donde hay que llevar el pedido, en coordenadas.
 *
 * Complementa la direccion escrita, no la reemplaza: en Turbaco las direcciones
 * fallan seguido y el domiciliario termina llamando para que lo guien, pero la
 * coordenada tampoco es infalible. Si el cliente pidio desde el trabajo para que
 * le lleven a la casa, la buena es la direccion.
 *
 * `precisionMetros` es el radio dentro del cual el navegador afirma que esta el
 * punto. Se guarda porque un GPS da quince metros y una posicion deducida de la
 * IP da dos kilometros: la segunda parece precisa y no lo es, y quien despacha
 * tiene que poder distinguirlas antes de salir a buscar.
 */
public record UbicacionEntrega(double latitud, double longitud, Integer precisionMetros) {

  /**
   * Por encima de esto la coordenada no ubica una casa, ubica un barrio. Se
   * sigue mostrando, pero advertida: es la diferencia entre orientar al
   * domiciliario y mandarlo a dar vueltas confiando en un punto falso.
   */
  public static final int PRECISION_ACEPTABLE_METROS = 100;

  public UbicacionEntrega {
    if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
      throw new ReglaDeNegocioError("La ubicación recibida no corresponde a un punto del mapa");
    }
    if (precisionMetros != null && precisionMetros < 0) precisionMetros = null;
  }

  public boolean esConfiable() {
    return precisionMetros == null || precisionMetros <= PRECISION_ACEPTABLE_METROS;
  }

  /**
   * Distancia en metros hasta otro punto, por la formula del semiverseno.
   *
   * Sirve para una sola cosa: avisarle a recepcion cuando el cliente pidio
   * desde muy lejos del local, que casi siempre significa que estaba en otro
   * sitio y la coordenada no es la de la entrega.
   */
  public long metrosHasta(double otraLatitud, double otraLongitud) {
    final double radioTierra = 6_371_000;
    double dLat = Math.toRadians(otraLatitud - latitud);
    double dLon = Math.toRadians(otraLongitud - longitud);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(latitud))
                * Math.cos(Math.toRadians(otraLatitud))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    return Math.round(radioTierra * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
  }
}

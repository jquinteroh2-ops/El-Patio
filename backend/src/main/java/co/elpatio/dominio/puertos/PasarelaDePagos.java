package co.elpatio.dominio.puertos;

import java.time.Instant;

/**
 * Puerto de salida hacia la pasarela de pagos que cobra los anticipos.
 *
 * Nombrado por lo que hace, no por quien lo implementa: el unico adaptador de
 * hoy es Wompi, pero nada en el dominio ni en `ServicioAnticipos` menciona ese
 * nombre. Cambiar de pasarela algun dia es reemplazar la implementacion, no
 * reescribir un caso de uso.
 */
public interface PasarelaDePagos {

  /**
   * Crea un link de pago de un solo uso y devuelve la direccion donde el
   * cliente lo paga.
   *
   * El monto SIEMPRE lo calculo el backend antes de llamar aqui: quien
   * implementa este puerto no decide cuanto cobrar, solo lo cobra.
   */
  String crearLinkDePago(String referencia, long montoCentavos, String descripcion, Instant expiraEn);
}

package co.elpatio.dominio.puertos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * La hora, inyectada.
 *
 * El dia operativo y el turno se calculan en la zona del restaurante, no en UTC
 * ni en la del navegador del mesero: una venta de las 11 de la noche pertenece
 * al cierre de ese dia, y con UTC caeria en el siguiente.
 */
public interface Reloj {

  Instant ahora();

  /** Dia operativo en la zona del restaurante. */
  LocalDate hoy();

  LocalDate diaDe(Instant instante);

  LocalTime horaDe(Instant instante);

  /** Primer instante de un dia operativo, para acotar consultas por rango. */
  Instant inicioDelDia(LocalDate dia);
}

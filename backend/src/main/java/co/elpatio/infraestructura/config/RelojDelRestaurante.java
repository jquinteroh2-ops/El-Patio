package co.elpatio.infraestructura.config;

import co.elpatio.dominio.puertos.Reloj;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La hora del restaurante.
 *
 * El dia operativo y el turno se calculan en America/Bogota y no en UTC: una
 * venta de las 11 de la noche pertenece al cierre de ese dia, y en UTC ya seria
 * el siguiente. La zona es configurable por si el sistema se instala en otra
 * ciudad, pero nunca se toma la del navegador del mesero.
 */
@Component
public class RelojDelRestaurante implements Reloj {

  private final Clock reloj;

  public RelojDelRestaurante(@Value("${elpatio.zona-horaria:America/Bogota}") String zona) {
    this.reloj = Clock.system(ZoneId.of(zona));
  }

  public ZoneId zona() {
    return reloj.getZone();
  }

  @Override
  public Instant ahora() {
    return reloj.instant();
  }

  @Override
  public LocalDate hoy() {
    return LocalDate.now(reloj);
  }

  @Override
  public LocalDate diaDe(Instant instante) {
    return instante.atZone(reloj.getZone()).toLocalDate();
  }

  @Override
  public LocalTime horaDe(Instant instante) {
    return instante.atZone(reloj.getZone()).toLocalTime();
  }

  @Override
  public Instant inicioDelDia(LocalDate dia) {
    return dia.atStartOfDay(reloj.getZone()).toInstant();
  }
}

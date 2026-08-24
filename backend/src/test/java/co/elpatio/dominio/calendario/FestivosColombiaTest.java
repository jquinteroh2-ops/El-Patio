package co.elpatio.dominio.calendario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * El calendario se comprueba contra fechas verificables, no contra si mismo.
 *
 * Si estas pruebas fallan, lo que esta mal es el calculo: las fechas de aqui
 * son las del calendario oficial colombiano y se pueden cotejar con cualquier
 * almanaque.
 */
class FestivosColombiaTest {

  @Test
  void laPascuaCoincideConElCalendario() {
    assertThat(FestivosColombia.domingoDePascua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
    assertThat(FestivosColombia.domingoDePascua(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    assertThat(FestivosColombia.domingoDePascua(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
    assertThat(FestivosColombia.domingoDePascua(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
  }

  @Test
  void losFestivosFijosCaenEnSuDia() {
    Set<LocalDate> festivos = FestivosColombia.delAno(2026);

    assertThat(festivos)
        .contains(
            LocalDate.of(2026, 1, 1),   // Año Nuevo
            LocalDate.of(2026, 5, 1),   // Trabajo
            LocalDate.of(2026, 7, 20),  // Independencia
            LocalDate.of(2026, 8, 7),   // Boyaca
            LocalDate.of(2026, 12, 8),  // Inmaculada
            LocalDate.of(2026, 12, 25)); // Navidad
  }

  /**
   * La Ley Emiliani: los trasladables se corren al lunes.
   *
   * En 2026 el 6 de enero cae martes, asi que Reyes se celebra el lunes 12. Es
   * exactamente el caso que un calendario escrito a mano se equivoca.
   */
  @Test
  void losTrasladablesSeCorrenAlLunes() {
    Set<LocalDate> festivos = FestivosColombia.delAno(2026);

    // 6 de enero de 2026 es martes → el festivo es el lunes 12.
    assertThat(LocalDate.of(2026, 1, 6).getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
    assertThat(festivos).contains(LocalDate.of(2026, 1, 12));
    assertThat(festivos).doesNotContain(LocalDate.of(2026, 1, 6));
  }

  /** Si ya cae en lunes, se queda donde esta. */
  @Test
  void unTrasladableQueYaEsLunesNoSeMueve() {
    // 12 de octubre de 2026 es lunes.
    assertThat(LocalDate.of(2026, 10, 12).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    assertThat(FestivosColombia.delAno(2026)).contains(LocalDate.of(2026, 10, 12));
  }

  /** Jueves y Viernes Santo NO se trasladan: son los unicos moviles que no. */
  @Test
  void laSemanaSantaCaeEnSuDia() {
    Set<LocalDate> festivos = FestivosColombia.delAno(2026);

    assertThat(festivos).contains(LocalDate.of(2026, 4, 2)); // Jueves Santo
    assertThat(festivos).contains(LocalDate.of(2026, 4, 3)); // Viernes Santo
    assertThat(LocalDate.of(2026, 4, 2).getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
  }

  /** Colombia tiene 18 festivos al año. Es la comprobacion de conjunto. */
  @Test
  void sonDieciochoFestivosAlAno() {
    assertThat(FestivosColombia.delAno(2024)).hasSize(18);
    assertThat(FestivosColombia.delAno(2026)).hasSize(18);
    assertThat(FestivosColombia.delAno(2027)).hasSize(18);
    assertThat(FestivosColombia.delAno(2028)).hasSize(18);
  }

  /**
   * Salvo cuando dos caen el mismo lunes, y entonces son 17.
   *
   * Pasa de verdad: en 2025 el Sagrado Corazon cayo el viernes 27 de junio y
   * San Pedro el domingo 29, y los dos se trasladaron al lunes 30. Ese año
   * Colombia tuvo 17 dias festivos, no 18.
   *
   * Se prueba a proposito porque es justo el caso que rompe una implementacion
   * que da los festivos por contados, y porque a quien mire este codigo dentro
   * de un año le va a parecer un error antes que una regla.
   */
  @Test
  void dosFestivosPuedenCaerElMismoLunesYEntoncesSonDiecisiete() {
    LocalDate lunes = LocalDate.of(2025, 6, 30);

    // El 29 de junio de 2025 es domingo: San Pedro se corre al lunes 30.
    assertThat(LocalDate.of(2025, 6, 29).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    // El Sagrado Corazon de 2025 es el viernes 27: tambien se corre al lunes 30.
    assertThat(FestivosColombia.domingoDePascua(2025).plusDays(68))
        .isEqualTo(LocalDate.of(2025, 6, 27));

    assertThat(FestivosColombia.delAno(2025)).contains(lunes).hasSize(17);
  }

  @Test
  void findeYFestivoNoSonHabiles() {
    // 8 de agosto de 2026 es sabado.
    assertThat(FestivosColombia.esHabil(LocalDate.of(2026, 8, 8))).isFalse();
    assertThat(FestivosColombia.esHabil(LocalDate.of(2026, 8, 9))).isFalse();
    // 7 de agosto de 2026, Boyaca, es viernes: festivo aunque sea entre semana.
    assertThat(FestivosColombia.esHabil(LocalDate.of(2026, 8, 7))).isFalse();
    // 6 de agosto de 2026 es jueves normal.
    assertThat(FestivosColombia.esHabil(LocalDate.of(2026, 8, 6))).isTrue();
  }

  /**
   * El dia de radicacion no cuenta.
   *
   * Una PQR radicada el lunes con un dia habil vence el martes, no el lunes:
   * contar el mismo dia le quitaria un dia entero al plazo.
   */
  @Test
  void elDiaDeInicioNoCuenta() {
    // Lunes 24 de agosto de 2026.
    LocalDate lunes = LocalDate.of(2026, 8, 24);
    assertThat(lunes.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

    assertThat(FestivosColombia.sumarDiasHabiles(lunes, 1)).isEqualTo(LocalDate.of(2026, 8, 25));
  }

  /** El conteo salta el fin de semana. */
  @Test
  void elConteoSaltaSabadosYDomingos() {
    // Viernes 21 de agosto de 2026 + 1 hábil = lunes 24.
    LocalDate viernes = LocalDate.of(2026, 8, 21);
    assertThat(viernes.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);

    assertThat(FestivosColombia.sumarDiasHabiles(viernes, 1)).isEqualTo(LocalDate.of(2026, 8, 24));
  }

  /** Y salta los festivos. */
  @Test
  void elConteoSaltaLosFestivos() {
    // Jueves 6 de agosto de 2026 + 1 hábil: el viernes 7 es Boyacá y el 8 y 9
    // son fin de semana, así que cae el lunes 10.
    assertThat(FestivosColombia.sumarDiasHabiles(LocalDate.of(2026, 8, 6), 1))
        .isEqualTo(LocalDate.of(2026, 8, 10));
  }

  /**
   * Un plazo de quince dias habiles cruza el cambio de año sin perderse.
   *
   * Es donde falla una implementacion que cachea los festivos de un solo año:
   * al pasar a enero deja de reconocerlos y empieza a contar el 1 de enero como
   * habil.
   */
  @Test
  void unPlazoQueCruzaElAnoSigueContandoBien() {
    // Desde el lunes 21 de diciembre de 2026, quince días hábiles.
    LocalDate vencimiento = FestivosColombia.sumarDiasHabiles(LocalDate.of(2026, 12, 21), 15);

    assertThat(vencimiento.getYear()).isEqualTo(2027);
    assertThat(FestivosColombia.esHabil(vencimiento)).isTrue();
    // Y por el camino no contó ni Navidad, ni Año Nuevo, ni Reyes.
    assertThat(FestivosColombia.esHabil(LocalDate.of(2026, 12, 25))).isFalse();
    assertThat(FestivosColombia.esHabil(LocalDate.of(2027, 1, 1))).isFalse();
  }

  @Test
  void quinceDiasHabilesSonSiempreMasDeQuinceDiasCorridos() {
    LocalDate radicacion = LocalDate.of(2026, 8, 24);
    LocalDate limite = FestivosColombia.sumarDiasHabiles(radicacion, 15);

    assertThat(java.time.temporal.ChronoUnit.DAYS.between(radicacion, limite))
        .isGreaterThanOrEqualTo(19);
    assertThat(FestivosColombia.diasHabilesEntre(radicacion, limite)).isEqualTo(15);
  }

  @Test
  void unPlazoDeCeroDiasNoMueveLaFecha() {
    LocalDate hoy = LocalDate.of(2026, 8, 24);
    assertThat(FestivosColombia.sumarDiasHabiles(hoy, 0)).isEqualTo(hoy);
  }
}

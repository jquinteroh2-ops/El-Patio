package co.elpatio.dominio.calendario;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/**
 * Los festivos de Colombia, calculados por la regla y no escritos a mano.
 *
 * <p><b>Por que se calcula en vez de listarse.</b> Una lista de fechas hay que
 * mantenerla cada año, y el año que a alguien se le olvide, el sistema empieza
 * a prometer respuestas de PQR para fechas que caen en festivo sin que nadie lo
 * note. La regla, en cambio, es la misma desde 1983 y no vence.
 *
 * <p><b>De donde sale la regla.</b> La Ley 51 de 1983 —la «Ley Emiliani»— fija
 * cuales festivos se celebran en su fecha y cuales se trasladan al lunes
 * siguiente. Los moviles dependen de la Pascua, que se calcula con el algoritmo
 * gregoriano estandar (Meeus/Jones/Butcher), no con una tabla.
 *
 * <p><b>Lo que esto NO cubre.</b> Los dias civicos que decrete un alcalde o el
 * Gobierno para una fecha concreta —un dia sin IVA, un puente extraordinario—
 * no son festivos de ley y no aparecen aqui. Si alguno llegara a importar para
 * el conteo de un plazo, hay que agregarlo como excepcion, y conviene que se
 * decida a proposito y no que se cuele en un calculo.
 *
 * <p><b>Un año no siempre tiene 18 festivos.</b> Cuando dos trasladables caen
 * en la misma semana pueden terminar en el mismo lunes, y entonces son 17. Paso
 * en 2025: el Sagrado Corazon cayo el viernes 27 de junio y San Pedro el
 * domingo 29, y los dos se corrieron al lunes 30. Por eso lo que se devuelve es
 * un conjunto de fechas y no una lista de nombres: contarlos por el nombre da
 * un festivo que no existe.
 */
public final class FestivosColombia {

  private FestivosColombia() {}

  /** Los que se celebran el dia exacto, caiga cuando caiga. */
  private static final int[][] FIJOS = {
    {1, 1},   // Año Nuevo
    {5, 1},   // Dia del Trabajo
    {7, 20},  // Grito de Independencia
    {8, 7},   // Batalla de Boyaca
    {12, 8},  // Inmaculada Concepcion
    {12, 25}, // Navidad
  };

  /**
   * Los que se corren al lunes siguiente si no caen en lunes.
   *
   * Es el nucleo de la Ley Emiliani: se trasladaron para armar puentes, y por
   * eso «12 de octubre» casi nunca es el festivo real.
   */
  private static final int[][] TRASLADABLES = {
    {1, 6},   // Reyes Magos
    {3, 19},  // San Jose
    {6, 29},  // San Pedro y San Pablo
    {8, 15},  // Asuncion de la Virgen
    {10, 12}, // Dia de la Raza
    {11, 1},  // Todos los Santos
    {11, 11}, // Independencia de Cartagena
  };

  /**
   * Todos los festivos de un año.
   *
   * Se calcula entero y se devuelve como conjunto: quien pregunta suele
   * preguntar muchas veces seguidas —contando dias habiles— y recorrer la
   * regla en cada consulta seria trabajo repetido sin motivo.
   */
  public static Set<LocalDate> delAno(int ano) {
    Set<LocalDate> festivos = new HashSet<>();

    for (int[] fecha : FIJOS) {
      festivos.add(LocalDate.of(ano, fecha[0], fecha[1]));
    }
    for (int[] fecha : TRASLADABLES) {
      festivos.add(alLunesSiguiente(LocalDate.of(ano, fecha[0], fecha[1])));
    }

    LocalDate pascua = domingoDePascua(ano);
    // Jueves y Viernes Santo NO se trasladan: caen siempre en su dia.
    festivos.add(pascua.minusDays(3));
    festivos.add(pascua.minusDays(2));
    // Los tres que si se trasladan. Se calculan desde su fecha liturgica y se
    // corren, en vez de sumar los dias ya desplazados: asi la razon de cada
    // numero se puede comprobar contra el calendario de la Iglesia.
    festivos.add(alLunesSiguiente(pascua.plusDays(39))); // Ascension
    festivos.add(alLunesSiguiente(pascua.plusDays(60))); // Corpus Christi
    festivos.add(alLunesSiguiente(pascua.plusDays(68))); // Sagrado Corazon

    return festivos;
  }

  public static boolean esFestivo(LocalDate fecha) {
    return delAno(fecha.getYear()).contains(fecha);
  }

  /** Si es dia habil: ni sabado, ni domingo, ni festivo. */
  public static boolean esHabil(LocalDate fecha) {
    DayOfWeek dia = fecha.getDayOfWeek();
    if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) return false;
    return !esFestivo(fecha);
  }

  /**
   * La fecha que resulta de contar `diasHabiles` desde `inicio`.
   *
   * El dia de inicio NO cuenta: una PQR radicada hoy con quince dias habiles
   * empieza a contar mañana. Es como se cuentan los terminos, y contar el mismo
   * dia le quitaria un dia al plazo.
   *
   * Los festivos se calculan por año y se cachean dentro de la llamada: un
   * plazo de quince dias habiles cruza dos meses y a veces dos años, y pedir el
   * conjunto en cada vuelta seria recalcular la Pascua veinte veces.
   */
  public static LocalDate sumarDiasHabiles(LocalDate inicio, int diasHabiles) {
    if (diasHabiles <= 0) return inicio;

    LocalDate fecha = inicio;
    int anoEnCache = fecha.getYear();
    Set<LocalDate> festivos = delAno(anoEnCache);
    int contados = 0;

    while (contados < diasHabiles) {
      fecha = fecha.plusDays(1);
      if (fecha.getYear() != anoEnCache) {
        anoEnCache = fecha.getYear();
        festivos = delAno(anoEnCache);
      }
      DayOfWeek dia = fecha.getDayOfWeek();
      boolean finDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
      if (!finDeSemana && !festivos.contains(fecha)) contados++;
    }
    return fecha;
  }

  /** Cuantos dias habiles hay entre dos fechas, sin contar la de inicio. */
  public static int diasHabilesEntre(LocalDate desde, LocalDate hasta) {
    if (!hasta.isAfter(desde)) return 0;
    int habiles = 0;
    LocalDate fecha = desde;
    while (fecha.isBefore(hasta)) {
      fecha = fecha.plusDays(1);
      if (esHabil(fecha)) habiles++;
    }
    return habiles;
  }

  private static LocalDate alLunesSiguiente(LocalDate fecha) {
    return fecha.getDayOfWeek() == DayOfWeek.MONDAY
        ? fecha
        : fecha.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
  }

  /**
   * El Domingo de Pascua, por el algoritmo gregoriano estandar.
   *
   * Es el de Meeus/Jones/Butcher, el mismo que usa cualquier calendario
   * eclesiastico. Las variables se llaman con una letra a proposito: son las
   * del algoritmo publicado, y renombrarlas a algo «legible» haria imposible
   * cotejarlo con la fuente, que es lo unico que de verdad lo hace verificable.
   */
  public static LocalDate domingoDePascua(int ano) {
    int a = ano % 19;
    int b = ano / 100;
    int c = ano % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int mes = (h + l - 7 * m + 114) / 31;
    int dia = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(ano, mes, dia);
  }
}

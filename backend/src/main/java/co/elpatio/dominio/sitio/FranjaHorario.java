package co.elpatio.dominio.sitio;

/**
 * Una linea del horario de atencion: «Viernes y Sabado — 12:00 m. a 12:00 a. m.»
 *
 * Los dos campos son texto y no horas ni dias de la semana a proposito. Lo que
 * se pinta en el sitio es una frase que el cliente lee de un vistazo, y una
 * estructura de dias sueltos con dos `LocalTime` no se vuelve a componer en esa
 * frase sin inventar reglas de redaccion. Quien escribe el horario es la misma
 * persona que lo diria por telefono.
 */
public record FranjaHorario(String dias, String horas) {}

package co.elpatio.dominio.puertos;

/**
 * Identificadores con prefijo legible: ord_, io_, pg_.
 *
 * Se conservo el formato del prototipo porque aparece en los registros y en las
 * URLs de la comandera, y leer "ord_m3k1a2" ahorra una consulta cuando se
 * rastrea un problema en sala.
 */
public interface GeneradorIds {

  String nuevo(String prefijo);
}
